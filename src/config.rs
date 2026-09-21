use once_cell::sync::Lazy;
use parking_lot::RwLock;
use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, AtomicI32, AtomicI64, Ordering};
use std::time::{Duration, Instant};

// ---------------------------------------------------------------------------
// Constants & Configuration — портировано из proxy/utils.py + tg_ws_proxy.py
// ---------------------------------------------------------------------------

pub const DEFAULT_PORT: u16 = 1443;
pub const TCP_NODELAY: bool = true;
pub const DEFAULT_RECV_BUF: usize = 256 * 1024;
pub const DEFAULT_SEND_BUF: usize = 256 * 1024;
pub const DEFAULT_POOL_SZ: i32 = 4;

// proxy/tg_ws_proxy.py: IP_FAIL_COOLDOWN / DC_FAIL_COOLDOWN / WS_FAIL_TIMEOUT
pub const IP_FAIL_COOLDOWN: f64 = 3600.0;
pub const DC_FAIL_COOLDOWN: f64 = 60.0;
pub const WS_FAIL_TIMEOUT: f64 = 2.0;
// Прямой WS-коннект при отсутствии cooldown — 5.0 как в оригинале.
pub const WS_DIRECT_TIMEOUT: f64 = 5.0;

// proxy/utils.py — MTProto handshake layout
pub const HANDSHAKE_LEN: usize = 64;
pub const SKIP_LEN: usize = 8;
pub const PREKEY_LEN: usize = 32;
pub const KEY_LEN: usize = 32;
pub const IV_LEN: usize = 16;
pub const PROTO_TAG_POS: usize = 56;
pub const DC_IDX_POS: usize = 60;

pub const PROTO_TAG_ABRIDGED: u32 = 0xEFEFEFEF;
pub const PROTO_TAG_INTERMEDIATE: u32 = 0xEEEEEEEE;
pub const PROTO_TAG_SECURE: u32 = 0xDDDDDDDD; // padded intermediate

pub const PROTO_ABRIDGED_INT: u32 = 0xEFEFEFEF;
pub const PROTO_INTERMEDIATE_INT: u32 = 0xEEEEEEEE;
pub const PROTO_PADDED_INTERMEDIATE_INT: u32 = 0xDDDDDDDD;

// proxy/utils.py — зарезервированные префиксы relay_init
pub const RESERVED_FIRST_BYTE: u8 = 0xEF;

pub const BRIDGE_READ_TIMEOUT: Duration = Duration::from_secs(120);
pub const BRIDGE_PING_INTERVAL: Duration = Duration::from_secs(30);
pub const WS_WRITE_TIMEOUT: Duration = Duration::from_secs(5);
pub const WS_CONTROL_TIMEOUT: Duration = Duration::from_secs(2);
pub const WS_BRIDGE_CHUNK_SIZE: usize = 64 * 1024;
pub const POOLED_FRAME_CAP: usize = WS_BRIDGE_CHUNK_SIZE + 32;

// proxy/pool.py::_WsPool
pub const WS_POOL_REUSE_MAX_AGE: f64 = 120.0;
pub const WS_POOL_CHECK_INTERVAL: f64 = 5.0;
pub const WS_POOL_REFILL_BACKOFF_INITIAL: f64 = 1.0;
pub const WS_POOL_REFILL_BACKOFF_MAX: f64 = 3600.0;
pub const WS_POOL_CONNECT_TIMEOUT: f64 = 8.0;
pub const WS_POOL_FRONTING_TIMEOUT: f64 = 7.0;
pub const FRONTING_SNI: &str = "sprinthost.ru";

// proxy/pool.py::_CfWorkerPool
pub const CF_WORKER_POOL_MAX_AGE: f64 = 100.0;
pub const CF_WORKER_PER_DC_LIMIT: usize = 1;

// WS пути — proxy/utils.py
pub const WS_PATH: &str = "/apiws";
pub const WS_PATH_TEST: &str = "/apiws_test";

pub const CFPROXY_CACHE_FILE_NAME: &str = "cfproxy-domains-cache.txt";
pub const CFPROXY_ACTIVE_FILE_NAME: &str = "cfproxy-active-domain.txt";
// Оригинал обновляет пул раз в час (3600с) фоновым потоком.
pub const CFPROXY_REFRESH_INTERVAL: Duration = Duration::from_secs(3600);
pub const CFPROXY_MIN_VALID_DOMAINS: usize = 3;
pub const CFPROXY_DIAL_PHASE_TIMEOUT: Duration = Duration::from_secs(4);
pub const CFPROXY_FALLBACK_PARALLEL: usize = 2;
pub const CFPROXY_429_COOLDOWN: Duration = Duration::from_secs(45);
pub const CFPROXY_429_MAX_COOLDOWN: Duration = Duration::from_secs(300);
pub const CFPROXY_GLOBAL_PARALLEL: usize = 4;

pub static RECV_BUF: AtomicI32 = AtomicI32::new(DEFAULT_RECV_BUF as i32);
pub static SEND_BUF: AtomicI32 = AtomicI32::new(DEFAULT_SEND_BUF as i32);
pub static BUFFER_SIZE: AtomicI32 = AtomicI32::new(256 * 1024);
pub static POOL_SIZE: AtomicI32 = AtomicI32::new(DEFAULT_POOL_SZ);
pub static LOG_VERBOSE: AtomicBool = AtomicBool::new(false);
// proxy/config.py::ProxyConfig — мобильные эквиваленты
pub static DISABLE_SECURE: AtomicBool = AtomicBool::new(false);
pub static FORCE_TEST_DC: AtomicBool = AtomicBool::new(false);
pub static PROXY_PROTOCOL: AtomicBool = AtomicBool::new(false);

#[derive(Clone)]
pub struct Cfproxy429State {
    pub until: Option<Instant>,
    pub strikes: i32,
}

impl Default for Cfproxy429State {
    fn default() -> Self {
        Cfproxy429State { until: None, strikes: 0 }
    }
}

// Cloudflare proxy config
pub static CFPROXY_ENABLED: AtomicBool = AtomicBool::new(true);

pub struct CfproxyConfig {
    pub user_domain: String,
    pub domains: Vec<String>,
    pub active: String,
    pub cache_dir: String,
}

pub static CFPROXY: Lazy<RwLock<CfproxyConfig>> = Lazy::new(|| {
    RwLock::new(CfproxyConfig {
        user_domain: String::new(),
        domains: Vec::new(),
        active: String::new(),
        cache_dir: String::new(),
    })
});

// proxy/config.py: cfproxy_worker_domains + fake_tls_domain
pub static CF_WORKER_DOMAINS: Lazy<RwLock<Vec<String>>> =
    Lazy::new(|| RwLock::new(Vec::new()));
pub static FAKE_TLS_DOMAIN: Lazy<RwLock<String>> =
    Lazy::new(|| RwLock::new(String::new()));

pub static CFPROXY_429: Lazy<RwLock<HashMap<String, Cfproxy429State>>> =
    Lazy::new(|| RwLock::new(HashMap::new()));

pub const CFPROXY_DOMAINS_URL: &str =
    "https://raw.githubusercontent.com/Flowseal/tg-ws-proxy/main/.github/cfproxy-domains.txt";

// MTProto proxy secret
pub static PROXY_SECRET: Lazy<RwLock<String>> =
    Lazy::new(|| RwLock::new("00000000000000000000000000000000".to_string()));

// proxy/config.py::_CFPROXY_ENC — полный список из 20 доменов оригинала.
pub static CFPROXY_ENC: &[&str] = &[
    "virkgj.com",
    "vmmzovy.com",
    "mkuosckvso.com",
    "zaewayzmplad.com",
    "twdmbzcm.com",
    "awzwsldi.com",
    "clngqrflngqin.com",
    "tjacxbqtj.com",
    "bxaxtxmrw.com",
    "dmohrsgmohcrwb.com",
    "vwbmtmoi.com",
    "khgrre.com",
    "ulihssf.com",
    "tmhqsdqmfpmk.com",
    "xwuwoqbm.com",
    "orgcnunpj.com",
    "zhkuldz.com",
    "zypoljnslxa.com",
    "efabnxaowuzs.com",
    "zaftuzsftqdq.com",
];

// DC default IPs — proxy/utils.py::DC_DEFAULT_IPS
pub static DC_DEFAULT_IPS: Lazy<HashMap<i32, &'static str>> = Lazy::new(|| {
    let mut m = HashMap::new();
    m.insert(1, "149.154.175.50");
    m.insert(2, "149.154.167.51");
    m.insert(3, "149.154.175.100");
    m.insert(4, "149.154.167.91");
    m.insert(5, "149.154.171.5");
    m.insert(203, "91.105.192.100");
    m
});

// proxy/utils.py::DC_TEST_IPS
pub static DC_TEST_IPS: Lazy<HashMap<i32, &'static str>> = Lazy::new(|| {
    let mut m = HashMap::new();
    m.insert(1, "149.154.175.10");
    m.insert(2, "149.154.167.40");
    m.insert(3, "149.154.175.117");
    m
});

// Telegram protocols & DC mapping
pub fn valid_proto(p: u32) -> bool {
    matches!(p, 0xEFEFEFEF | 0xEEEEEEEE | 0xDDDDDDDD)
}

pub static DC_OVERRIDES: Lazy<HashMap<i32, i32>> = Lazy::new(|| {
    let mut m = HashMap::new();
    m.insert(203, 2);
    m
});

// Ключ состояния DC: (dc, is_media, is_test) — аналог строки
// f'{dc}{"t" if is_test else ""}{"m" if is_media else ""}' из оригинала.
#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub struct DcKey {
    pub dc: i32,
    pub is_media: i32,
    pub is_test: i32,
}

impl DcKey {
    pub fn new(dc: i32, is_media: bool, is_test: bool) -> Self {
        DcKey {
            dc,
            is_media: if is_media { 1 } else { 0 },
            is_test: if is_test { 1 } else { 0 },
        }
    }

    pub fn as_str_key(&self) -> String {
        format!(
            "{}{}{}",
            self.dc,
            if self.is_test != 0 { "t" } else { "" },
            if self.is_media != 0 { "m" } else { "" }
        )
    }
}

// Global state — proxy/tg_ws_proxy.py: ws_blacklist / dc_fail_until / ip_fail_until
pub static DC_OPT: Lazy<RwLock<HashMap<i32, String>>> = Lazy::new(|| RwLock::new(HashMap::new()));
pub static WS_BLACKLIST: Lazy<RwLock<HashMap<DcKey, bool>>> =
    Lazy::new(|| RwLock::new(HashMap::new()));
pub static DC_FAIL_UNTIL: Lazy<RwLock<HashMap<DcKey, f64>>> =
    Lazy::new(|| RwLock::new(HashMap::new()));
pub static IP_FAIL_UNTIL: Lazy<RwLock<HashMap<String, f64>>> =
    Lazy::new(|| RwLock::new(HashMap::new()));

pub static ZERO64: [u8; 64] = [0u8; 64];

// ---------------------------------------------------------------------------
// Domain validation — proxy/config.py::_is_valid_domain / _normalize_domain_pool
// ---------------------------------------------------------------------------

pub fn is_valid_domain(domain: &str) -> bool {
    if domain.is_empty() || domain.len() > 253 {
        return false;
    }
    if domain.starts_with('.') || domain.ends_with('.') {
        return false;
    }
    let labels: Vec<&str> = domain.split('.').collect();
    if labels.len() < 2 {
        return false;
    }
    for label in &labels {
        if label.is_empty() || label.len() > 63 {
            return false;
        }
        if label.starts_with('-') || label.ends_with('-') {
            return false;
        }
        if !label.chars().all(|ch| ch.is_alphanumeric() || ch == '-') {
            return false;
        }
    }
    let tld = labels[labels.len() - 1];
    if tld.len() < 2 || !tld.chars().any(|ch| ch.is_alphabetic()) {
        return false;
    }
    true
}

pub fn normalize_domain_pool(domains: &[String]) -> Vec<String> {
    use std::collections::HashSet;
    let mut seen = HashSet::new();
    let mut out = Vec::new();
    for d in domains {
        let item = d.trim().to_lowercase();
        if !is_valid_domain(&item) {
            continue;
        }
        if seen.contains(&item) {
            continue;
        }
        seen.insert(item.clone());
        out.push(item);
    }
    out
}

/// proxy/config.py::coerce_domain_list — строка или список через , ; пробелы.
pub fn coerce_domain_list_str(value: &str) -> Vec<String> {
    let mut seen = std::collections::HashSet::new();
    let mut result = Vec::new();
    for item in value.replace(',', " ").replace(';', " ").split_whitespace() {
        let item = item.trim();
        if item.is_empty() {
            continue;
        }
        let key = item.to_lowercase();
        if seen.contains(&key) {
            continue;
        }
        seen.insert(key);
        result.push(item.to_string());
    }
    result
}

// ---------------------------------------------------------------------------
// Reserved relay_init checks — proxy/utils.py
// ---------------------------------------------------------------------------

pub fn is_reserved_start(s: &[u8]) -> bool {
    if s.len() < 4 {
        return false;
    }
    let s4 = &s[..4];
    s4 == b"HEAD"
        || s4 == b"POST"
        || s4 == b"GET "
        || s4 == [0xee, 0xee, 0xee, 0xee]
        || s4 == [0xdd, 0xdd, 0xdd, 0xdd]
        || s4 == [0x16, 0x03, 0x01, 0x02]
}

// ---------------------------------------------------------------------------
// Stats — proxy/stats.py
// ---------------------------------------------------------------------------

#[derive(Default)]
pub struct Stats {
    pub connections_total: AtomicI64,
    pub connections_active: AtomicI64,
    pub connections_ws: AtomicI64,
    pub connections_tcp_fallback: AtomicI64,
    pub connections_cfproxy: AtomicI64,
    pub connections_fronting: AtomicI64,
    pub connections_bad: AtomicI64,
    pub connections_masked: AtomicI64,
    pub connections_http_reject: AtomicI64,
    pub connections_passthrough: AtomicI64,
    pub ws_errors: AtomicI64,
    pub bytes_up: AtomicI64,
    pub bytes_down: AtomicI64,
    pub pool_hits: AtomicI64,
    pub pool_misses: AtomicI64,
    pub cf_pool_hits: AtomicI64,
    pub cf_pool_misses: AtomicI64,
}

pub static STATS: Lazy<Stats> = Lazy::new(Stats::default);

impl Stats {
    pub fn summary(&self) -> String {
        let ph = self.pool_hits.load(Ordering::Relaxed);
        let pm = self.pool_misses.load(Ordering::Relaxed);
        let pool_total = ph + pm;
        let pool_s = if pool_total > 0 {
            format!("{}/{}", ph, pool_total)
        } else {
            "n/a".to_string()
        };
        let cph = self.cf_pool_hits.load(Ordering::Relaxed);
        let cpm = self.cf_pool_misses.load(Ordering::Relaxed);
        let cf_total = cph + cpm;
        let cf_pool_s = if cf_total > 0 {
            format!("{}/{}", cph, cf_total)
        } else {
            "n/a".to_string()
        };
        format!(
            "total={} active={} ws={} tcp_fb={} cf={} front={} bad={} masked={} err={} pool={} cf_pool={} up={} down={}",
            self.connections_total.load(Ordering::Relaxed),
            self.connections_active.load(Ordering::Relaxed),
            self.connections_ws.load(Ordering::Relaxed),
            self.connections_tcp_fallback.load(Ordering::Relaxed),
            self.connections_cfproxy.load(Ordering::Relaxed),
            self.connections_fronting.load(Ordering::Relaxed),
            self.connections_bad.load(Ordering::Relaxed),
            self.connections_masked.load(Ordering::Relaxed),
            self.ws_errors.load(Ordering::Relaxed),
            pool_s,
            cf_pool_s,
            human_bytes(self.bytes_up.load(Ordering::Relaxed)),
            human_bytes(self.bytes_down.load(Ordering::Relaxed)),
        )
    }

    pub fn summary_ru(&self) -> String {
        let mut parts = vec![format!("акт:{}", self.connections_active.load(Ordering::Relaxed))];
        let ws = self.connections_ws.load(Ordering::Relaxed);
        if ws > 0 {
            parts.push(format!("ws:{}", ws));
        }
        let cf = self.connections_cfproxy.load(Ordering::Relaxed);
        if cf > 0 {
            parts.push(format!("cf:{}", cf));
        }
        let tcp = self.connections_tcp_fallback.load(Ordering::Relaxed);
        if tcp > 0 {
            parts.push(format!("tcp:{}", tcp));
        }
        let err = self.ws_errors.load(Ordering::Relaxed);
        if err > 0 {
            parts.push(format!("ош:{}", err));
        }
        parts.push(format!(
            "↑{} ↓{}",
            human_bytes(self.bytes_up.load(Ordering::Relaxed)),
            human_bytes(self.bytes_down.load(Ordering::Relaxed))
        ));
        parts.join(" | ")
    }

    pub fn reset(&self) {
        self.connections_total.store(0, Ordering::Relaxed);
        self.connections_active.store(0, Ordering::Relaxed);
        self.connections_ws.store(0, Ordering::Relaxed);
        self.connections_tcp_fallback.store(0, Ordering::Relaxed);
        self.connections_cfproxy.store(0, Ordering::Relaxed);
        self.connections_fronting.store(0, Ordering::Relaxed);
        self.connections_bad.store(0, Ordering::Relaxed);
        self.connections_masked.store(0, Ordering::Relaxed);
        self.connections_http_reject.store(0, Ordering::Relaxed);
        self.connections_passthrough.store(0, Ordering::Relaxed);
        self.ws_errors.store(0, Ordering::Relaxed);
        self.bytes_up.store(0, Ordering::Relaxed);
        self.bytes_down.store(0, Ordering::Relaxed);
        self.pool_hits.store(0, Ordering::Relaxed);
        self.pool_misses.store(0, Ordering::Relaxed);
        self.cf_pool_hits.store(0, Ordering::Relaxed);
        self.cf_pool_misses.store(0, Ordering::Relaxed);
    }
}

pub fn human_bytes(n: i64) -> String {
    let units = ["B", "KB", "MB", "GB", "TB"];
    let mut f = n as f64;
    for (i, u) in units.iter().enumerate() {
        if f.abs() < 1024.0 || i == units.len() - 1 {
            return format!("{:.1}{}", f, u);
        }
        f /= 1024.0;
    }
    format!("{:.1}TB", f)
}

// ---------------------------------------------------------------------------
// Logger (Android log + stderr, 1-в-1 префиксы)
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
fn android_log_line(line: &str) {
    use std::ffi::CString;
    unsafe extern "C" {
        fn __android_log_print(prio: i32, tag: *const i8, fmt: *const i8, ...) -> i32;
    }
    const ANDROID_LOG_INFO: i32 = 4;
    if let (Ok(tag), Ok(fmt), Ok(msg)) =
        (CString::new("TgWsProxy"), CString::new("%s"), CString::new(line))
    {
        unsafe {
            __android_log_print(
                ANDROID_LOG_INFO,
                tag.as_ptr() as *const i8,
                fmt.as_ptr() as *const i8,
                msg.as_ptr() as *const i8,
            );
        }
    }
}

#[cfg(not(target_os = "android"))]
fn android_log_line(_line: &str) {}

// Цензура доменов в логах — аналог DomainCensorFilter из proxy/utils.py:
// telegram.org и *.telegram.org не маскируем, остальное частично маскируем.
fn censor_domains(msg: &str) -> String {
    let mut out = String::with_capacity(msg.len());
    let bytes = msg.as_bytes();
    let mut i = 0;
    while i < msg.len() {
        let start = i;
        while i < msg.len()
            && (bytes[i].is_ascii_alphanumeric() || bytes[i] == b'-' || bytes[i] == b'.')
        {
            i += 1;
        }
        if start == i {
            let ch = msg[i..].chars().next().unwrap();
            out.push(ch);
            i += ch.len_utf8();
            continue;
        }
        let token = &msg[start..i];
        if token.contains('.') && token.len() > 4 && is_likely_domain(token) {
            let lower = token.to_lowercase();
            let trimmed = lower.trim_end_matches('.');
            if trimmed == "telegram.org"
                || trimmed.ends_with(".telegram.org")
                || trimmed.ends_with(".log")
            {
                out.push_str(token);
            } else {
                out.push_str(&mask_domain(token));
            }
        } else {
            out.push_str(token);
        }
    }
    out
}

fn is_likely_domain(token: &str) -> bool {
    let t = token.trim_end_matches('.');
    let parts: Vec<&str> = t.split('.').collect();
    if parts.len() < 2 {
        return false;
    }
    let tld = parts[parts.len() - 1];
    if tld.len() < 2 || !tld.chars().all(|c| c.is_ascii_alphabetic()) {
        return false;
    }
    parts.iter().all(|p| {
        !p.is_empty()
            && p.len() <= 63
            && p.chars().all(|c| c.is_ascii_alphanumeric() || c == '-')
    })
}

fn mask_domain(domain: &str) -> String {
    let parts: Vec<&str> = domain.split('.').collect();
    if parts.len() < 2 {
        return domain.to_string();
    }
    parts
        .iter()
        .enumerate()
        .map(|(idx, p)| {
            if idx == parts.len() - 1 {
                p.to_string()
            } else {
                let keep = p.len() / 2;
                format!("{}{}", &p[..keep], "*".repeat(p.len() - keep))
            }
        })
        .collect::<Vec<_>>()
        .join(".")
}

fn emit(prefix: &str, msg: &str) {
    let line = format!("{}{}", prefix, censor_domains(msg));
    eprintln!("{}", line);
    android_log_line(&line);
}

pub fn log_info(msg: &str) {
    emit("", msg);
}
pub fn log_warn(msg: &str) {
    emit("[WARN] ", msg);
}
pub fn log_error(msg: &str) {
    emit("[ERROR] ", msg);
}
pub fn log_debug(msg: &str) {
    if LOG_VERBOSE.load(Ordering::Relaxed) {
        emit("[DEBUG] ", msg);
    }
}

#[macro_export]
macro_rules! linfo  { ($($a:tt)*) => { $crate::config::log_info(&format!($($a)*)) }; }
#[macro_export]
macro_rules! lwarn  { ($($a:tt)*) => { $crate::config::log_warn(&format!($($a)*)) }; }
#[macro_export]
macro_rules! lerror { ($($a:tt)*) => { $crate::config::log_error(&format!($($a)*)) }; }
#[macro_export]
macro_rules! ldebug { ($($a:tt)*) => { $crate::config::log_debug(&format!($($a)*)) }; }

pub fn init_logging(verbose: bool) {
    LOG_VERBOSE.store(verbose, Ordering::Relaxed);
}

pub fn now_unix_f64() -> f64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs_f64())
        .unwrap_or(0.0)
}

pub fn now_unix() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

pub fn now_monotonic() -> f64 {
    use std::time::Instant;
    static START: Lazy<Instant> = Lazy::new(Instant::now);
    START.elapsed().as_secs_f64()
}
