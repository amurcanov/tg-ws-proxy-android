use crate::config::*;
use crate::crypto::xor_mask_in_place;
use crate::{ldebug};
use base64::Engine;
use byteorder::{BigEndian, ByteOrder};
use rand::RngCore;
use rustls::client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier};
use rustls::{ClientConfig, DigitallySignedStruct, SignatureScheme};
use rustls_pki_types::{CertificateDer, ServerName, UnixTime};
use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpStream;
use tokio_rustls::client::TlsStream;
use tokio_rustls::TlsConnector;

// ---------------------------------------------------------------------------
// WS opcodes
// ---------------------------------------------------------------------------

pub const OP_TEXT: u8 = 0x1;
pub const OP_BINARY: u8 = 0x2;
pub const OP_CLOSE: u8 = 0x8;
pub const OP_PING: u8 = 0x9;
pub const OP_PONG: u8 = 0xA;

// ---------------------------------------------------------------------------
// TLS config: InsecureSkipVerify + session cache (как в Go)
// ---------------------------------------------------------------------------

#[derive(Debug)]
struct NoVerify;

impl ServerCertVerifier for NoVerify {
    fn verify_server_cert(
        &self,
        _end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp_response: &[u8],
        _now: UnixTime,
    ) -> Result<ServerCertVerified, rustls::Error> {
        Ok(ServerCertVerified::assertion())
    }

    fn verify_tls12_signature(
        &self,
        _message: &[u8],
        _cert: &CertificateDer<'_>,
        _dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        Ok(HandshakeSignatureValid::assertion())
    }

    fn verify_tls13_signature(
        &self,
        _message: &[u8],
        _cert: &CertificateDer<'_>,
        _dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        Ok(HandshakeSignatureValid::assertion())
    }

    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        vec![
            SignatureScheme::RSA_PKCS1_SHA256,
            SignatureScheme::RSA_PKCS1_SHA384,
            SignatureScheme::RSA_PKCS1_SHA512,
            SignatureScheme::ECDSA_NISTP256_SHA256,
            SignatureScheme::ECDSA_NISTP384_SHA384,
            SignatureScheme::ECDSA_NISTP521_SHA512,
            SignatureScheme::RSA_PSS_SHA256,
            SignatureScheme::RSA_PSS_SHA384,
            SignatureScheme::RSA_PSS_SHA512,
            SignatureScheme::ED25519,
        ]
    }
}

use once_cell::sync::Lazy;

// Глобальный TLS-конфиг с session resumption cache (аналог tls.NewLRUClientSessionCache(100))
static TLS_CONFIG: Lazy<Arc<ClientConfig>> = Lazy::new(|| {
    let mut cfg = ClientConfig::builder()
        .dangerous()
        .with_custom_certificate_verifier(Arc::new(NoVerify))
        .with_no_client_auth();
    cfg.resumption = rustls::client::Resumption::in_memory_sessions(100);
    Arc::new(cfg)
});

// ---------------------------------------------------------------------------
// WsHandshakeError
// ---------------------------------------------------------------------------

#[derive(Debug, Clone)]
pub struct WsHandshakeError {
    pub status_code: i32,
    pub status_line: String,
    pub headers: HashMap<String, String>,
    pub location: String,
}

impl WsHandshakeError {
    pub fn is_redirect(&self) -> bool {
        matches!(self.status_code, 301 | 302 | 303 | 307 | 308)
    }
}

#[derive(Debug)]
pub enum WsError {
    Io(std::io::Error),
    Handshake(WsHandshakeError),
    Timeout,
    Canceled,
    Other(String),
}

impl WsError {
    pub fn compact(&self) -> String {
        match self {
            WsError::Canceled => "canceled".to_string(),
            WsError::Timeout => "timeout".to_string(),
            WsError::Handshake(h) => format!("http {}", h.status_code),
            WsError::Io(e) => {
                if e.kind() == std::io::ErrorKind::TimedOut
                    || e.kind() == std::io::ErrorKind::WouldBlock
                {
                    "timeout".to_string()
                } else {
                    e.to_string()
                }
            }
            WsError::Other(s) => s.clone(),
        }
    }
    pub fn handshake_status(&self) -> Option<i32> {
        if let WsError::Handshake(h) = self {
            Some(h.status_code)
        } else {
            None
        }
    }
    pub fn handshake(&self) -> Option<&WsHandshakeError> {
        if let WsError::Handshake(h) = self {
            Some(h)
        } else {
            None
        }
    }
}

pub fn is_http_status_error(err: &WsError, code: i32) -> bool {
    err.handshake_status() == Some(code)
}

impl From<std::io::Error> for WsError {
    fn from(e: std::io::Error) -> Self {
        WsError::Io(e)
    }
}

// ---------------------------------------------------------------------------
// RawWebSocket
// ---------------------------------------------------------------------------

pub struct RawWebSocket {
    reader: tokio::sync::Mutex<BufReader<tokio::io::ReadHalf<TlsStream<TcpStream>>>>,
    writer: tokio::sync::Mutex<tokio::io::WriteHalf<TlsStream<TcpStream>>>,
    pub closed: AtomicBool,
}

impl RawWebSocket {
    pub fn is_closed(&self) -> bool {
        self.closed.load(Ordering::Relaxed)
    }

    pub async fn send(&self, data: &[u8]) -> Result<(), WsError> {
        if self.is_closed() {
            return Err(WsError::Other("WebSocket closed".to_string()));
        }
        let frame = build_frame(OP_BINARY, data, true);
        self.write_frame(&frame, WS_WRITE_TIMEOUT).await
    }

    pub async fn send_batch(&self, parts: &[Vec<u8>]) -> Result<(), WsError> {
        if self.is_closed() {
            return Err(WsError::Other("WebSocket closed".to_string()));
        }
        let mut writer = self.writer.lock().await;
        for part in parts {
            let frame = build_frame(OP_BINARY, part, true);
            match tokio::time::timeout(WS_WRITE_TIMEOUT, writer.write_all(&frame)).await {
                Ok(Ok(())) => {}
                Ok(Err(e)) => {
                    self.closed.store(true, Ordering::Relaxed);
                    return Err(WsError::Io(e));
                }
                Err(_) => {
                    self.closed.store(true, Ordering::Relaxed);
                    return Err(WsError::Timeout);
                }
            }
        }
        Ok(())
    }

    pub async fn send_ping(&self) -> Result<(), WsError> {
        if self.is_closed() {
            return Err(WsError::Other("WebSocket closed".to_string()));
        }
        let frame = build_frame(OP_PING, &[], true);
        self.write_frame(&frame, WS_CONTROL_TIMEOUT).await
    }

    async fn write_frame(&self, frame: &[u8], timeout: Duration) -> Result<(), WsError> {
        let mut writer = self.writer.lock().await;
        let res = if timeout > Duration::ZERO {
            tokio::time::timeout(timeout, writer.write_all(frame)).await
        } else {
            Ok(writer.write_all(frame).await)
        };
        match res {
            Ok(Ok(())) => Ok(()),
            Ok(Err(e)) => {
                self.closed.store(true, Ordering::Relaxed);
                Err(WsError::Io(e))
            }
            Err(_) => {
                self.closed.store(true, Ordering::Relaxed);
                Err(WsError::Timeout)
            }
        }
    }

    // Recv обрабатывает контрольные фреймы (как Go Recv)
    pub async fn recv(&self) -> Result<Vec<u8>, WsError> {
        while !self.is_closed() {
            let (opcode, payload) = match self.read_frame().await {
                Ok(v) => v,
                Err(e) => {
                    self.closed.store(true, Ordering::Relaxed);
                    return Err(e);
                }
            };
            match opcode {
                OP_CLOSE => {
                    self.closed.store(true, Ordering::Relaxed);
                    let mut close_payload = payload;
                    if close_payload.len() > 2 {
                        close_payload.truncate(2);
                    }
                    let reply = build_frame(OP_CLOSE, &close_payload, true);
                    let _ = self.write_frame(&reply, WS_CONTROL_TIMEOUT).await;
                    return Err(WsError::Io(std::io::Error::new(
                        std::io::ErrorKind::UnexpectedEof,
                        "EOF",
                    )));
                }
                OP_PING => {
                    let pong = build_frame(OP_PONG, &payload, true);
                    let _ = self.write_frame(&pong, WS_CONTROL_TIMEOUT).await;
                    continue;
                }
                OP_PONG => continue,
                OP_TEXT | OP_BINARY => return Ok(payload),
                _ => {}
            }
        }
        Err(WsError::Io(std::io::Error::new(
            std::io::ErrorKind::UnexpectedEof,
            "EOF",
        )))
    }

    pub async fn close(&self) {
        if self.closed.swap(true, Ordering::Relaxed) {
            return;
        }
        let frame = build_frame(OP_CLOSE, &[], true);
        let _ = self.write_frame(&frame, WS_CONTROL_TIMEOUT).await;
        // Skipping writer.shutdown().await to avoid hanging on dead connections
    }

    // recv с дедлайном чтения (для bridge).
    // ВАЖНО: таймаут оборачивает только чтение ОДНОГО фрейма целиком под
    // удержанием lock, чтобы НЕ дропать future посреди read_exact (иначе
    // теряются уже прочитанные байты BufReader → рассинхрон потока).
    pub async fn recv_with_timeout(&self, dur: Duration) -> Result<Vec<u8>, WsError> {
        loop {
            if self.is_closed() {
                return Err(WsError::Io(std::io::Error::new(
                    std::io::ErrorKind::UnexpectedEof,
                    "EOF",
                )));
            }
            let frame = {
                let mut reader = self.reader.lock().await;
                match tokio::time::timeout(dur, read_frame_locked(&mut reader)).await {
                    Ok(Ok(v)) => v,
                    Ok(Err(e)) => {
                        self.closed.store(true, Ordering::Relaxed);
                        return Err(e);
                    }
                    Err(_) => return Err(WsError::Timeout),
                }
            };
            let (opcode, payload) = frame;
            match opcode {
                OP_CLOSE => {
                    self.closed.store(true, Ordering::Relaxed);
                    let mut close_payload = payload;
                    if close_payload.len() > 2 {
                        close_payload.truncate(2);
                    }
                    let reply = build_frame(OP_CLOSE, &close_payload, true);
                    let _ = self.write_frame(&reply, WS_CONTROL_TIMEOUT).await;
                    return Err(WsError::Io(std::io::Error::new(
                        std::io::ErrorKind::UnexpectedEof,
                        "EOF",
                    )));
                }
                OP_PING => {
                    let pong = build_frame(OP_PONG, &payload, true);
                    let _ = self.write_frame(&pong, WS_CONTROL_TIMEOUT).await;
                    continue;
                }
                OP_PONG => continue,
                OP_TEXT | OP_BINARY => return Ok(payload),
                _ => continue,
            }
        }
    }

    async fn read_frame(&self) -> Result<(u8, Vec<u8>), WsError> {
        let mut reader = self.reader.lock().await;
        let mut hdr = [0u8; 2];
        reader.read_exact(&mut hdr).await?;

        let opcode = hdr[0] & 0x0F;
        let mut length = (hdr[1] & 0x7F) as u64;

        if length == 126 {
            let mut buf = [0u8; 2];
            reader.read_exact(&mut buf).await?;
            length = BigEndian::read_u16(&buf) as u64;
        } else if length == 127 {
            let mut buf = [0u8; 8];
            reader.read_exact(&mut buf).await?;
            length = BigEndian::read_u64(&buf);
        }

        let has_mask = (hdr[1] & 0x80) != 0;
        let mut mask_key = [0u8; 4];
        if has_mask {
            reader.read_exact(&mut mask_key).await?;
        }

        const MAX_FRAME_PAYLOAD: u64 = 16 * 1024 * 1024;
        if length > MAX_FRAME_PAYLOAD {
            return Err(WsError::Other(format!("frame too large: {} bytes", length)));
        }
        let mut payload = vec![0u8; length as usize];
        if length > 0 {
            reader.read_exact(&mut payload).await?;
        }
        if has_mask {
            xor_mask_in_place(&mut payload, &mask_key);
        }
        Ok((opcode, payload))
    }
}

// Чтение одного фрейма из уже захваченного reader (без повторного lock).
// Используется recv_with_timeout, чтобы держать lock на всё время чтения фрейма.
async fn read_frame_locked(
    reader: &mut BufReader<tokio::io::ReadHalf<TlsStream<TcpStream>>>,
) -> Result<(u8, Vec<u8>), WsError> {
    let mut hdr = [0u8; 2];
    reader.read_exact(&mut hdr).await?;

    let opcode = hdr[0] & 0x0F;
    let mut length = (hdr[1] & 0x7F) as u64;

    if length == 126 {
        let mut buf = [0u8; 2];
        reader.read_exact(&mut buf).await?;
        length = BigEndian::read_u16(&buf) as u64;
    } else if length == 127 {
        let mut buf = [0u8; 8];
        reader.read_exact(&mut buf).await?;
        length = BigEndian::read_u64(&buf);
    }

    let has_mask = (hdr[1] & 0x80) != 0;
    let mut mask_key = [0u8; 4];
    if has_mask {
        reader.read_exact(&mut mask_key).await?;
    }

    const MAX_FRAME_PAYLOAD: u64 = 16 * 1024 * 1024;
    if length > MAX_FRAME_PAYLOAD {
        return Err(WsError::Other(format!("frame too large: {} bytes", length)));
    }
    let mut payload = vec![0u8; length as usize];
    if length > 0 {
        reader.read_exact(&mut payload).await?;
    }
    if has_mask {
        xor_mask_in_place(&mut payload, &mask_key);
    }
    Ok((opcode, payload))
}

// ---------------------------------------------------------------------------
// Frame builder (mask=true всегда для клиента)
// ---------------------------------------------------------------------------

pub fn build_frame(opcode: u8, data: &[u8], mask: bool) -> Vec<u8> {
    let length = data.len();
    let fb = 0x80 | opcode;

    let mut header_size = 2;
    if mask {
        header_size += 4;
    }
    if length >= 126 && length < 65536 {
        header_size += 2;
    } else if length >= 65536 {
        header_size += 8;
    }

    let total_size = header_size + length;
    let mut result = vec![0u8; total_size];

    let mut pos = 0;
    result[pos] = fb;
    pos += 1;

    let mut mask_key = [0u8; 4];
    if mask {
        rand::thread_rng().fill_bytes(&mut mask_key);
    }

    if length < 126 {
        let mut lb = length as u8;
        if mask {
            lb |= 0x80;
        }
        result[pos] = lb;
        pos += 1;
    } else if length < 65536 {
        let mut lb = 126u8;
        if mask {
            lb |= 0x80;
        }
        result[pos] = lb;
        pos += 1;
        BigEndian::write_u16(&mut result[pos..], length as u16);
        pos += 2;
    } else {
        let mut lb = 127u8;
        if mask {
            lb |= 0x80;
        }
        result[pos] = lb;
        pos += 1;
        BigEndian::write_u64(&mut result[pos..], length as u64);
        pos += 8;
    }

    if mask {
        result[pos..pos + 4].copy_from_slice(&mask_key);
        pos += 4;
        result[pos..pos + length].copy_from_slice(data);
        xor_mask_in_place(&mut result[pos..pos + length], &mask_key);
    } else {
        result[pos..pos + length].copy_from_slice(data);
    }
    result
}

// ---------------------------------------------------------------------------
// Connection helpers
// ---------------------------------------------------------------------------

fn set_sock_opts(stream: &TcpStream) {
    if TCP_NODELAY {
        let _ = stream.set_nodelay(true);
    }
    // Аналог Go: SetKeepAlive(true)+SetKeepAlivePeriod(30s) — детект мёртвых соединений на мобиле.
    let sock = socket2::SockRef::from(stream);
    let ka = socket2::TcpKeepalive::new().with_time(Duration::from_secs(30));
    let _ = sock.set_tcp_keepalive(&ka);
}

pub fn ws_connect_timeout(timeout: f64) -> Duration {
    if timeout <= 0.0 {
        Duration::from_secs(5)
    } else {
        Duration::from_secs_f64(timeout)
    }
}

pub fn ws_handshake_timeout(total: Duration) -> Duration {
    if total <= Duration::ZERO {
        Duration::from_secs(3)
    } else if total > Duration::from_secs(3) {
        Duration::from_secs(3)
    } else {
        total
    }
}

fn server_name(domain: &str) -> ServerName<'static> {
    ServerName::try_from(domain.to_string())
        .unwrap_or_else(|_| ServerName::IpAddress("127.0.0.1".parse::<IpAddr>().unwrap().into()))
}

// wsConnectOnce — заголовки 1-в-1 как в Python raw_websocket.py (без User-Agent).
pub async fn ws_connect_once(
    dial_addr: &str,
    domain: &str,
    path: &str,
    timeout: Duration,
) -> Result<RawWebSocket, WsError> {
    if dial_addr.is_empty() {
        return Err(WsError::Other("empty dial address".to_string()));
    }

    let target_addr = format!("{}:443", dial_addr);

    let raw_conn = match tokio::time::timeout(timeout, TcpStream::connect(&target_addr)).await {
        Ok(Ok(c)) => c,
        Ok(Err(e)) => return Err(WsError::Io(e)),
        Err(_) => return Err(WsError::Timeout),
    };
    set_sock_opts(&raw_conn);

    let connector = TlsConnector::from(TLS_CONFIG.clone());
    let sni = server_name(domain);

    let handshake_timeout = ws_handshake_timeout(timeout);
    let tls_conn =
        match tokio::time::timeout(handshake_timeout, connector.connect(sni, raw_conn)).await {
            Ok(Ok(c)) => c,
            Ok(Err(e)) => {
                if e.kind() != std::io::ErrorKind::ConnectionReset {
                    ldebug!(" ws tls fail {} via {}: {}", domain, dial_addr, e);
                }
                return Err(WsError::Io(e));
            }
            Err(_) => {
                ldebug!(" ws tls fail {} via {}: timeout", domain, dial_addr);
                return Err(WsError::Timeout);
            }
        };

    let (read_half, mut write_half) = tokio::io::split(tls_conn);

    // websocket key
    let mut ws_key_bytes = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut ws_key_bytes);
    let ws_key = base64::engine::general_purpose::STANDARD.encode(ws_key_bytes);

    let req = format!(
        "GET {} HTTP/1.1\r\n\
         Host: {}\r\n\
         Upgrade: websocket\r\n\
         Connection: Upgrade\r\n\
         Sec-WebSocket-Key: {}\r\n\
         Sec-WebSocket-Version: 13\r\n\
         Sec-WebSocket-Protocol: binary\r\n\r\n",
        path, domain, ws_key
    );

    match tokio::time::timeout(timeout, write_half.write_all(req.as_bytes())).await {
        Ok(Ok(())) => {}
        Ok(Err(e)) => return Err(WsError::Io(e)),
        Err(_) => return Err(WsError::Timeout),
    }

    let mut bufreader = BufReader::with_capacity(4096, read_half);

    // читаем заголовки строками
    let mut response_lines: Vec<String> = Vec::new();
    let read_result = tokio::time::timeout(timeout, async {
        loop {
            let line = read_line(&mut bufreader).await?;
            let line = line.trim_end_matches(['\r', '\n']).to_string();
            if line.is_empty() {
                break;
            }
            response_lines.push(line);
            if response_lines.len() > 100 {
                return Err(WsError::Other("too many HTTP headers".to_string()));
            }
        }
        Ok::<(), WsError>(())
    })
    .await;

    match read_result {
        Ok(Ok(())) => {}
        Ok(Err(e)) => return Err(e),
        Err(_) => return Err(WsError::Timeout),
    }

    if response_lines.is_empty() {
        return Err(WsError::Handshake(WsHandshakeError {
            status_code: 0,
            status_line: "empty response".to_string(),
            headers: HashMap::new(),
            location: String::new(),
        }));
    }

    let first_line = response_lines[0].clone();
    let parts: Vec<&str> = first_line.splitn(3, ' ').collect();
    let mut status_code = 0;
    if parts.len() >= 2 {
        status_code = parts[1].parse::<i32>().unwrap_or(0);
    }

    if status_code == 101 {
        return Ok(RawWebSocket {
            reader: tokio::sync::Mutex::new(bufreader),
            writer: tokio::sync::Mutex::new(write_half),
            closed: AtomicBool::new(false),
        });
    }

    let mut headers = HashMap::new();
    for hl in &response_lines[1..] {
        if let Some(idx) = hl.find(':') {
            headers.insert(
                hl[..idx].trim().to_lowercase(),
                hl[idx + 1..].trim().to_string(),
            );
        }
    }
    let location = headers.get("location").cloned().unwrap_or_default();
    Err(WsError::Handshake(WsHandshakeError {
        status_code,
        status_line: first_line,
        headers,
        location,
    }))
}

async fn read_line<R: AsyncReadExt + Unpin>(reader: &mut R) -> Result<String, WsError> {
    let mut buf = Vec::with_capacity(128);
    let mut byte = [0u8; 1];
    loop {
        let n = reader.read(&mut byte).await?;
        if n == 0 {
            return Err(WsError::Io(std::io::Error::new(
                std::io::ErrorKind::UnexpectedEof,
                "EOF",
            )));
        }
        buf.push(byte[0]);
        if byte[0] == b'\n' {
            break;
        }
        if buf.len() > 16384 {
            return Err(WsError::Other("header line too long".to_string()));
        }
    }
    Ok(String::from_utf8_lossy(&buf).to_string())
}

// wsConnect: пытается ip, при необходимости резолвит DoH
pub async fn ws_connect(
    ip: &str,
    domain: &str,
    path: &str,
    timeout: f64,
) -> Result<RawWebSocket, WsError> {
    let path = if path.is_empty() { "/apiws" } else { path };
    let attempt_timeout = ws_connect_timeout(timeout);

    let primary_addr = if ip.trim().is_empty() {
        domain.to_string()
    } else {
        ip.trim().to_string()
    };

    match ws_connect_once(&primary_addr, domain, path, attempt_timeout).await {
        Ok(ws) => return Ok(ws),
        Err(e) => {
            if primary_addr == domain && primary_addr.parse::<IpAddr>().is_err() {
                if let Some(resolved) = crate::cfproxy::resolve_doh(domain).await {
                    if !resolved.is_empty() && resolved != primary_addr {
                        return ws_connect_once(&resolved, domain, path, attempt_timeout).await;
                    }
                }
            }
            Err(e)
        }
    }
}

// connectOneWS: перебор доменов
pub async fn connect_one_ws(ip: &str, domains: &[String]) -> Option<RawWebSocket> {
    for d in domains {
        if let Ok(ws) = ws_connect(ip, d, "/apiws", WS_POOL_CONNECT_TIMEOUT).await {
            return Some(ws);
        }
    }
    None
}