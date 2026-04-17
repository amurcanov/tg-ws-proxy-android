package main

/*
#cgo android LDFLAGS: -llog
#include <stdlib.h>
#include <signal.h>
#ifdef __ANDROID__
#include <android/log.h>
#endif

static void androidLogProxy(char *msg) {
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_INFO, "TgWsProxy", "%s", msg);
#endif
}
*/
import "C"

import (
	"bufio"
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha1"
	"crypto/sha256"
	"crypto/tls"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"math"
	"net"
	"net/http"
	"os"
	"os/signal"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"
	"unsafe"
)

// ---------------------------------------------------------------------------
// Constants & Configuration
// ---------------------------------------------------------------------------

const (
	defaultPort    = 1443
	tcpNodelay     = true
	defaultRecvBuf = 256 * 1024
	defaultSendBuf = 256 * 1024
	defaultPoolSz  = 4

	wsPoolMaxAge = 20.0

	dcFailCooldown = 10.0

	wsFailTimeout = 2.0

	poolMaintainInterval = 5

	// Bridge read deadlines — short enough to detect dead connections on mobile
	bridgeReadTimeout  = 2 * time.Minute
	bridgePingInterval = 30 * time.Second
	wsWriteTimeout     = 5 * time.Second
	wsControlTimeout   = 2 * time.Second
	wsPoolProbeTimeout = 1200 * time.Millisecond
	wsPoolProbeAfter   = 8.0
	wsBridgeChunkSize  = 64 * 1024
	pooledFrameCap     = wsBridgeChunkSize + 32
)

var (
	recvBuf    = defaultRecvBuf
	sendBuf    = defaultSendBuf
	poolSize   atomic.Int32
	logVerbose = false
)

func init() {
	poolSize.Store(defaultPoolSz)
}

// Cloudflare proxy config
var (
	cfproxyEnabled    = true
	cfproxyUserDomain = ""
	cfproxyDomains    []string
	activeCfDomain    string
	cfproxyMu         sync.RWMutex
)

const cfproxyDomainsURL = "https://raw.githubusercontent.com/Flowseal/tg-ws-proxy/main/.github/cfproxy-domains.txt"

// MTProto proxy secret (hex, 32 chars = 16 bytes)
var (
	proxySecret   = "00000000000000000000000000000000"
	proxySecretMu sync.RWMutex
)

// FakeTLS config (ee-secret)
var (
	fakeTlsEnabled = false
	fakeTlsDomain  = ""
	fakeTlsMu      sync.RWMutex
)

// DNS over HTTPS (DoH) Cache and Clients
type dohCacheEntry struct {
	ip  string
	exp time.Time
}

var (
	dohCache  sync.Map
	dohClient = &http.Client{
		Timeout: 1500 * time.Millisecond,
		Transport: &http.Transport{
			MaxIdleConns:        10,
			IdleConnTimeout:     90 * time.Second,
			TLSHandshakeTimeout: 1 * time.Second,
		},
	}
	githubClient = &http.Client{
		Timeout: 10 * time.Second,
	}
)

func connectOneWS(ctx context.Context, ip string, domains []string) *RawWebSocket {
	for _, d := range domains {
		ws, err := wsConnect(ctx, ip, d, "/apiws", 5.0)
		if err == nil {
			return ws
		}
	}
	return nil
}

var dcDefaultIPs = map[int]string{
	1:   "149.154.175.50",
	2:   "149.154.167.51",
	3:   "149.154.175.100",
	4:   "149.154.167.91",
	5:   "149.154.171.5",
	203: "91.105.192.100",
}

// ---------------------------------------------------------------------------
// Logger
// ---------------------------------------------------------------------------

var (
	logInfo  *log.Logger
	logWarn  *log.Logger
	logError *log.Logger
	logDebug *log.Logger
)

type androidLogWriter struct{}

func (w androidLogWriter) Write(p []byte) (n int, err error) {
	_, _ = os.Stderr.Write(p)
	cs := C.CString(string(p))
	C.androidLogProxy(cs)
	C.free(unsafe.Pointer(cs))
	return len(p), nil
}

func initLogging(verbose bool) {
	logVerbose = verbose
	flags := 0
	out := androidLogWriter{}
	logInfo = log.New(out, "", flags)
	logWarn = log.New(out, "[WARN] ", flags)
	logError = log.New(out, "[ERROR] ", flags)
	if verbose {
		logDebug = log.New(out, "[DEBUG] ", flags)
	} else {
		logDebug = log.New(io.Discard, "", 0)
	}
	signal.Ignore(syscall.SIGPIPE)
}

// ---------------------------------------------------------------------------
// Cloudflare proxy domain decoding
// ---------------------------------------------------------------------------

var cfproxyEnc = []string{"virkgj.com", "vmmzovy.com", "mkuosckvso.com", "zaewayzmplad.com", "twdmbzcm.com"}

func decodeCfDomain(s string) string {
	if !strings.HasSuffix(s, ".com") {
		return s
	}
	suffix := string([]byte{46, 99, 111, 46, 117, 107})
	p := s[:len(s)-4]
	n := 0
	for _, c := range p {
		if (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') {
			n++
		}
	}
	var result []byte
	for _, c := range []byte(p) {
		if c >= 'a' && c <= 'z' {
			result = append(result, byte((int(c-'a')-n%26+26)%26+'a'))
		} else if c >= 'A' && c <= 'Z' {
			result = append(result, byte((int(c-'A')-n%26+26)%26+'A'))
		} else {
			result = append(result, c)
		}
	}
	return string(result) + suffix
}

func initCfproxyDomains() {
	cfproxyMu.Lock()
	defer cfproxyMu.Unlock()
	if cfproxyUserDomain != "" {
		cfproxyDomains = []string{cfproxyUserDomain}
		activeCfDomain = cfproxyUserDomain
		return
	}
	cfproxyDomains = make([]string, len(cfproxyEnc))
	for i, enc := range cfproxyEnc {
		cfproxyDomains[i] = decodeCfDomain(enc)
	}
	if len(cfproxyDomains) > 0 {
		activeCfDomain = cfproxyDomains[0]
	}
}

func startCfproxyRefresh(ctx context.Context) {
	go func() {
		for i := 0; i < 3; i++ {
			if tryRefreshCfproxyDomains(ctx) {
				return
			}
			select {
			case <-ctx.Done():
				return
			case <-time.After(10 * time.Second):
				continue
			}
		}
	}()
}

func tryRefreshCfproxyDomains(ctx context.Context) bool {
	cfproxyMu.RLock()
	hasUserDomain := cfproxyUserDomain != ""
	cfproxyMu.RUnlock()
	if hasUserDomain {
		return true
	}

	url := fmt.Sprintf("%s?v=%d", cfproxyDomainsURL, time.Now().Unix())
	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return false
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 tg-ws-proxy-android")

	resp, err := githubClient.Do(req)
	if err != nil {
		return false
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return false
	}

	var newDomains []string
	scanner := bufio.NewScanner(resp.Body)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		decoded := decodeCfDomain(line)
		if strings.HasSuffix(decoded, ".uk") {
			newDomains = append(newDomains, decoded)
		}
	}

	if len(newDomains) > 0 {
		cfproxyMu.Lock()
		if cfproxyUserDomain != "" {
			cfproxyMu.Unlock()
			return true
		}
		cfproxyDomains = newDomains
		idx := 0
		rb := make([]byte, 1)
		if _, err := rand.Read(rb); err == nil {
			idx = int(rb[0]) % len(newDomains)
		}
		activeCfDomain = newDomains[idx]
		cfproxyMu.Unlock()
		logInfo.Printf(" CF: список доменов обновлен (%d шт.)", len(newDomains))
		return true
	}
	return false
}

// ---------------------------------------------------------------------------
// Telegram IP ranges & DC mapping
// ---------------------------------------------------------------------------

var validProtos = map[uint32]bool{
	0xEFEFEFEF: true,
	0xEEEEEEEE: true,
	0xDDDDDDDD: true,
}

var dcOverrides = map[int]int{
	203: 2,
}

// ---------------------------------------------------------------------------
// Global state
// ---------------------------------------------------------------------------

var (
	dcOpt   map[int]string
	dcOptMu sync.RWMutex

	wsBlackMu   sync.RWMutex
	wsBlacklist = make(map[[2]int]bool)

	dcFailMu    sync.RWMutex
	dcFailUntil = make(map[[2]int]float64)

	zero64 = make([]byte, 64)
)

// ---------------------------------------------------------------------------
// Stats
// ---------------------------------------------------------------------------

type Stats struct {
	connectionsTotal       atomic.Int64
	connectionsActive      atomic.Int64
	connectionsWs          atomic.Int64
	connectionsTcpFallback atomic.Int64
	connectionsCfproxy     atomic.Int64
	connectionsHttpReject  atomic.Int64
	connectionsPassthrough atomic.Int64
	connectionsBad         atomic.Int64
	wsErrors               atomic.Int64
	bytesUp                atomic.Int64
	bytesDown              atomic.Int64
	poolHits               atomic.Int64
	poolMisses             atomic.Int64
}

func (s *Stats) Summary() string {
	ph := s.poolHits.Load()
	pm := s.poolMisses.Load()
	return fmt.Sprintf(
		"total=%d active=%d ws=%d tcp_fb=%d cf=%d bad=%d err=%d pool=%d/%d up=%s down=%s",
		s.connectionsTotal.Load(), s.connectionsActive.Load(), s.connectionsWs.Load(),
		s.connectionsTcpFallback.Load(), s.connectionsCfproxy.Load(), s.connectionsBad.Load(),
		s.wsErrors.Load(), ph, ph+pm, humanBytes(s.bytesUp.Load()), humanBytes(s.bytesDown.Load()),
	)
}

func (s *Stats) SummaryRu() string {
	parts := []string{fmt.Sprintf("акт:%d", s.connectionsActive.Load())}
	if ws := s.connectionsWs.Load(); ws > 0 {
		parts = append(parts, fmt.Sprintf("ws:%d", ws))
	}
	if cf := s.connectionsCfproxy.Load(); cf > 0 {
		parts = append(parts, fmt.Sprintf("cf:%d", cf))
	}
	if tcp := s.connectionsTcpFallback.Load(); tcp > 0 {
		parts = append(parts, fmt.Sprintf("tcp:%d", tcp))
	}
	if errCount := s.wsErrors.Load(); errCount > 0 {
		parts = append(parts, fmt.Sprintf("ош:%d", errCount))
	}
	parts = append(parts, fmt.Sprintf("↑%s ↓%s", humanBytes(s.bytesUp.Load()), humanBytes(s.bytesDown.Load())))
	return strings.Join(parts, " | ")
}

func (s *Stats) Reset() {
	s.connectionsTotal.Store(0)
	s.connectionsActive.Store(0)
	s.connectionsWs.Store(0)
	s.connectionsTcpFallback.Store(0)
	s.connectionsCfproxy.Store(0)
	s.connectionsHttpReject.Store(0)
	s.connectionsPassthrough.Store(0)
	s.connectionsBad.Store(0)
	s.wsErrors.Store(0)
	s.bytesUp.Store(0)
	s.bytesDown.Store(0)
	s.poolHits.Store(0)
	s.poolMisses.Store(0)
}

var stats Stats

func humanBytes(n int64) string {
	units := []string{"B", "KB", "MB", "GB", "TB"}
	f := float64(n)
	for i, u := range units {
		if math.Abs(f) < 1024 || i == len(units)-1 {
			return fmt.Sprintf("%.1f%s", f, u)
		}
		f /= 1024
	}
	return fmt.Sprintf("%.1f%s", f, "TB")
}

// ---------------------------------------------------------------------------
// Socket helpers
// ---------------------------------------------------------------------------

func setSockOpts(conn net.Conn) {
	if tc, ok := conn.(*net.TCPConn); ok {
		if tcpNodelay {
			_ = tc.SetNoDelay(true)
		}
		_ = tc.SetKeepAlive(true)
		_ = tc.SetKeepAlivePeriod(30 * time.Second)
		raw, err := tc.SyscallConn()
		if err == nil {
			_ = raw.Control(func(fd uintptr) {
				_ = syscall.SetsockoptInt(int(fd), syscall.SOL_SOCKET, syscall.SO_RCVBUF, recvBuf)
				_ = syscall.SetsockoptInt(int(fd), syscall.SOL_SOCKET, syscall.SO_SNDBUF, sendBuf)
			})
		}
	}
}

// ---------------------------------------------------------------------------
// XOR mask
// ---------------------------------------------------------------------------

func xorMaskInPlace(data, mask []byte) {
	n := len(data)
	if n == 0 {
		return
	}
	mask8 := uint64(mask[0]) | uint64(mask[1])<<8 | uint64(mask[2])<<16 | uint64(mask[3])<<24 |
		uint64(mask[0])<<32 | uint64(mask[1])<<40 | uint64(mask[2])<<48 | uint64(mask[3])<<56

	i := 0
	for ; i+8 <= n; i += 8 {
		v := binary.LittleEndian.Uint64(data[i:])
		binary.LittleEndian.PutUint64(data[i:], v^mask8)
	}
	for ; i < n; i++ {
		data[i] ^= mask[i&3]
	}
}

// ---------------------------------------------------------------------------
// RawWebSocket
// ---------------------------------------------------------------------------

var bytesPool = sync.Pool{
	New: func() any { return make([]byte, 131072) },
}

func SafeClose(conn net.Conn) {
	if conn == nil {
		return
	}
	if tc, ok := conn.(*net.TCPConn); ok {
		_ = tc.SetLinger(0)
	}
	_ = conn.Close()
}

var tlsConfigPool = &tls.Config{
	ClientSessionCache: tls.NewLRUClientSessionCache(100),
}

const (
	opText   = 0x1
	opBinary = 0x2
	opClose  = 0x8
	opPing   = 0x9
	opPong   = 0xA
)

type WsHandshakeError struct {
	StatusCode int
	StatusLine string
	Headers    map[string]string
	Location   string
}

func (e *WsHandshakeError) Error() string {
	return fmt.Sprintf("HTTP %d: %s", e.StatusCode, e.StatusLine)
}
func (e *WsHandshakeError) IsRedirect() bool {
	switch e.StatusCode {
	case 301, 302, 303, 307, 308:
		return true
	}
	return false
}

const wsAcceptGUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

func expectedWebSocketAccept(key string) string {
	sum := sha1.Sum([]byte(key + wsAcceptGUID))
	return base64.StdEncoding.EncodeToString(sum[:])
}

type RawWebSocket struct {
	conn      net.Conn
	bufReader *bufio.Reader
	writeMu   sync.Mutex
	closed    atomic.Bool
}

type dohResponse struct {
	Answer []struct {
		Data string `json:"data"`
		Type int    `json:"type"`
	} `json:"Answer"`
}

func resolveDoH(ctx context.Context, domain string) string {
	if val, ok := dohCache.Load(domain); ok {
		entry := val.(dohCacheEntry)
		if time.Now().Before(entry.exp) {
			return entry.ip
		}
	}

	resCh := make(chan string, 10)
	dnsCtx, cancel := context.WithTimeout(ctx, 1500*time.Millisecond)
	defer cancel()

	udpServers := []string{"1.1.1.1:53", "8.8.8.8:53", "77.88.8.8:53"}
	for _, srv := range udpServers {
		go func(s string) {
			r := &net.Resolver{
				PreferGo: true,
				Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
					d := net.Dialer{Timeout: 800 * time.Millisecond}
					return d.DialContext(ctx, "udp", s)
				},
			}
			ips, err := r.LookupHost(dnsCtx, domain)
			if err == nil && len(ips) > 0 {
				select {
				case resCh <- ips[0]:
				default:
				}
			}
		}(srv)
	}

	endpoints := []string{
		"https://cloudflare-dns.com/dns-query",
		"https://dns.google/dns-query",
		"https://dns.quad9.net/dns-query",
		"https://dns.adguard-dns.com/dns-query",
	}

	for _, url := range endpoints {
		go func(u string) {
			fullURL := fmt.Sprintf("%s?name=%s&type=A", u, domain)
			req, err := http.NewRequestWithContext(dnsCtx, "GET", fullURL, nil)
			if err != nil {
				return
			}
			req.Header.Set("Accept", "application/dns-json")
			resp, err := dohClient.Do(req)
			if err != nil {
				return
			}
			defer resp.Body.Close()
			if resp.StatusCode != 200 {
				return
			}
			var r dohResponse
			if err := json.NewDecoder(resp.Body).Decode(&r); err != nil {
				return
			}
			for _, ans := range r.Answer {
				if ans.Type == 1 {
					select {
					case resCh <- ans.Data:
					default:
					}
					return
				}
			}
		}(url)
	}

	select {
	case ip := <-resCh:
		dohCache.Store(domain, dohCacheEntry{ip: ip, exp: time.Now().Add(5 * time.Minute)})
		return ip
	case <-dnsCtx.Done():
		return ""
	}
}

func wsConnectTimeout(timeout float64) time.Duration {
	if timeout <= 0 {
		return 5 * time.Second
	}
	return time.Duration(timeout * float64(time.Second))
}

func wsHandshakeTimeout(total time.Duration) time.Duration {
	if total <= 0 {
		return 3 * time.Second
	}
	if total > 3*time.Second {
		return 3 * time.Second
	}
	return total
}

func contextRemainingTimeout(ctx context.Context, fallback time.Duration) time.Duration {
	if deadline, ok := ctx.Deadline(); ok {
		remaining := time.Until(deadline)
		if remaining > 0 {
			return remaining
		}
		return time.Millisecond
	}
	return fallback
}

func compactConnError(err error) string {
	if err == nil {
		return ""
	}
	if errors.Is(err, context.Canceled) {
		return "canceled"
	}
	if errors.Is(err, context.DeadlineExceeded) {
		return "timeout"
	}
	var wsErr *WsHandshakeError
	if errors.As(err, &wsErr) {
		return fmt.Sprintf("http %d", wsErr.StatusCode)
	}
	var netErr net.Error
	if errors.As(err, &netErr) && netErr.Timeout() {
		return "timeout"
	}
	return err.Error()
}

func wsConnectOnce(ctx context.Context, dialAddr, domain, path string, timeout time.Duration) (*RawWebSocket, error) {
	if dialAddr == "" {
		return nil, fmt.Errorf("empty dial address")
	}

	dialer := &net.Dialer{
		Timeout: timeout,
	}

	tlsCfg := tlsConfigPool.Clone()
	tlsCfg.ServerName = domain

	targetAddr := net.JoinHostPort(dialAddr, "443")
	rawConn, err := dialer.DialContext(ctx, "tcp", targetAddr)
	if err != nil {
		return nil, err
	}

	setSockOpts(rawConn)

	tlsConn := tls.Client(rawConn, tlsCfg)
	handshakeTimeout := wsHandshakeTimeout(timeout)
	handshakeCtx, cancel := context.WithTimeout(ctx, handshakeTimeout)
	defer cancel()

	_ = tlsConn.SetDeadline(time.Now().Add(handshakeTimeout))
	if err := tlsConn.HandshakeContext(handshakeCtx); err != nil {
		rawConn.Close()
		return nil, err
	}
	_ = tlsConn.SetDeadline(time.Time{})
	rawConn = tlsConn

	wsKeyBytes := make([]byte, 16)
	_, _ = rand.Read(wsKeyBytes)
	wsKey := base64.StdEncoding.EncodeToString(wsKeyBytes)

	req := fmt.Sprintf(
		"GET %s HTTP/1.1\r\n"+
			"Host: %s\r\n"+
			"Upgrade: websocket\r\n"+
			"Connection: Upgrade\r\n"+
			"Sec-WebSocket-Key: %s\r\n"+
			"Sec-WebSocket-Version: 13\r\n"+
			"Sec-WebSocket-Protocol: binary\r\n"+
			"Origin: https://web.telegram.org\r\n"+
			"User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) "+
			"AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36\r\n\r\n",
		path, domain, wsKey,
	)

	_ = rawConn.SetWriteDeadline(time.Now().Add(timeout))
	if _, err = rawConn.Write([]byte(req)); err != nil {
		rawConn.Close()
		return nil, err
	}
	_ = rawConn.SetWriteDeadline(time.Time{})

	bufReader := bufio.NewReaderSize(rawConn, 4096)
	_ = rawConn.SetReadDeadline(time.Now().Add(timeout))

	var responseLines []string
	for {
		line, err := bufReader.ReadString('\n')
		if err != nil {
			rawConn.Close()
			return nil, err
		}
		line = strings.TrimRight(line, "\r\n")
		if line == "" {
			break
		}
		responseLines = append(responseLines, line)
		if len(responseLines) > 100 {
			rawConn.Close()
			return nil, fmt.Errorf("too many HTTP headers")
		}
	}
	_ = rawConn.SetReadDeadline(time.Time{})

	if len(responseLines) == 0 {
		rawConn.Close()
		return nil, &WsHandshakeError{StatusCode: 0, StatusLine: "empty response"}
	}

	firstLine := responseLines[0]
	parts := strings.SplitN(firstLine, " ", 3)
	statusCode := 0
	if len(parts) >= 2 {
		statusCode, _ = strconv.Atoi(parts[1])
	}

	headers := make(map[string]string)
	for _, hl := range responseLines[1:] {
		if idx := strings.IndexByte(hl, ':'); idx >= 0 {
			headers[strings.TrimSpace(strings.ToLower(hl[:idx]))] = strings.TrimSpace(hl[idx+1:])
		}
	}
	if statusCode == 101 {
		if headers["sec-websocket-accept"] != expectedWebSocketAccept(wsKey) {
			rawConn.Close()
			return nil, fmt.Errorf("invalid Sec-WebSocket-Accept")
		}
		return &RawWebSocket{conn: rawConn, bufReader: bufReader}, nil
	}
	rawConn.Close()
	return nil, &WsHandshakeError{
		StatusCode: statusCode,
		StatusLine: firstLine,
		Headers:    headers,
		Location:   headers["location"],
	}
}

func cfConnectDomain(ctx context.Context, domain, path string, timeout float64) (*RawWebSocket, string, error) {
	if path == "" {
		path = "/apiws"
	}

	attemptTimeout := wsConnectTimeout(timeout)
	attemptCtx, cancel := context.WithTimeout(ctx, attemptTimeout)
	defer cancel()

	resolvedIP := resolveDoH(attemptCtx, domain)
	if resolvedIP != "" {
		logDebug.Printf(" CF DNS %s -> %s", domain, resolvedIP)
		ws, err := wsConnectOnce(
			attemptCtx,
			resolvedIP,
			domain,
			path,
			contextRemainingTimeout(attemptCtx, attemptTimeout),
		)
		if err == nil {
			return ws, resolvedIP, nil
		}
		if attemptCtx.Err() == nil {
			logError.Printf(" CF IP fail %s (%s): %s", domain, resolvedIP, compactConnError(err))
		}
	} else {
		logDebug.Printf(" CF DNS %s -> no result", domain)
	}

	ws, err := wsConnectOnce(
		attemptCtx,
		domain,
		domain,
		path,
		contextRemainingTimeout(attemptCtx, attemptTimeout),
	)
	if err == nil {
		return ws, resolvedIP, nil
	}
	return nil, resolvedIP, err
}

func wsConnect(ctx context.Context, ip, domain, path string, timeout float64) (*RawWebSocket, error) {
	if path == "" {
		path = "/apiws"
	}

	attemptTimeout := wsConnectTimeout(timeout)
	attemptCtx, cancel := context.WithTimeout(ctx, attemptTimeout)
	defer cancel()

	primaryAddr := strings.TrimSpace(ip)
	if primaryAddr == "" {
		primaryAddr = domain
	}

	ws, err := wsConnectOnce(attemptCtx, primaryAddr, domain, path, attemptTimeout)
	if err == nil {
		return ws, nil
	}

	if primaryAddr == domain && net.ParseIP(primaryAddr) == nil {
		if resolvedIP := resolveDoH(attemptCtx, domain); resolvedIP != "" && resolvedIP != primaryAddr {
			return wsConnectOnce(attemptCtx, resolvedIP, domain, path, attemptTimeout)
		}
	}

	return nil, err
}

func (ws *RawWebSocket) writeFrame(frame []byte, timeout time.Duration) error {
	ws.writeMu.Lock()
	defer ws.writeMu.Unlock()
	defer recycleFrame(frame)

	if timeout > 0 {
		_ = ws.conn.SetWriteDeadline(time.Now().Add(timeout))
		defer ws.conn.SetWriteDeadline(time.Time{})
	}

	_, err := ws.conn.Write(frame)
	if err != nil {
		ws.closed.Store(true)
	}
	return err
}

func (ws *RawWebSocket) Send(data []byte) error {
	if ws.closed.Load() {
		return fmt.Errorf("WebSocket closed")
	}
	frame := ws.buildFrame(opBinary, data, true)
	return ws.writeFrame(frame, wsWriteTimeout)
}

func (ws *RawWebSocket) SendBatch(parts [][]byte) error {
	if ws.closed.Load() {
		return fmt.Errorf("WebSocket closed")
	}
	ws.writeMu.Lock()
	defer ws.writeMu.Unlock()
	_ = ws.conn.SetWriteDeadline(time.Now().Add(wsWriteTimeout))
	defer ws.conn.SetWriteDeadline(time.Time{})
	for _, part := range parts {
		frame := ws.buildFrame(opBinary, part, true)
		if _, err := ws.conn.Write(frame); err != nil {
			recycleFrame(frame)
			ws.closed.Store(true)
			return err
		}
		recycleFrame(frame)
	}
	return nil
}

func (ws *RawWebSocket) SendPing() error {
	if ws.closed.Load() {
		return fmt.Errorf("WebSocket closed")
	}
	frame := ws.buildFrame(opPing, nil, true)
	return ws.writeFrame(frame, wsControlTimeout)
}

func (ws *RawWebSocket) probe(timeout time.Duration) error {
	if ws.closed.Load() {
		return fmt.Errorf("WebSocket closed")
	}
	if err := ws.SendPing(); err != nil {
		return err
	}
	_ = ws.conn.SetReadDeadline(time.Now().Add(timeout))
	defer ws.conn.SetReadDeadline(time.Time{})

	for !ws.closed.Load() {
		opcode, payload, err := ws.readFrame()
		if err != nil {
			ws.closed.Store(true)
			return err
		}
		switch opcode {
		case opPong:
			return nil
		case opPing:
			if err := ws.writeFrame(ws.buildFrame(opPong, payload, true), wsControlTimeout); err != nil {
				return err
			}
		case opClose:
			ws.closed.Store(true)
			return io.EOF
		default:
			return fmt.Errorf("unexpected frame %d during pool probe", opcode)
		}
	}
	return io.EOF
}

func (ws *RawWebSocket) Recv() ([]byte, error) {
	for !ws.closed.Load() {
		opcode, payload, err := ws.readFrame()
		if err != nil {
			ws.closed.Store(true)
			return nil, err
		}
		switch opcode {
		case opClose:
			ws.closed.Store(true)
			closePayload := payload
			if len(closePayload) > 2 {
				closePayload = closePayload[:2]
			}
			reply := ws.buildFrame(opClose, closePayload, true)
			_ = ws.writeFrame(reply, wsControlTimeout)
			return nil, io.EOF
		case opPing:
			pong := ws.buildFrame(opPong, payload, true)
			_ = ws.writeFrame(pong, wsControlTimeout)
			continue
		case opText, opBinary:
			return payload, nil
		}
	}
	return nil, io.EOF
}

func (ws *RawWebSocket) Close() {
	if ws.closed.Swap(true) {
		return
	}
	frame := ws.buildFrame(opClose, nil, true)
	_ = ws.writeFrame(frame, wsControlTimeout)
	_ = ws.conn.Close()
}

var framePool = sync.Pool{
	New: func() any { return make([]byte, 0, pooledFrameCap) },
}

func recycleFrame(frame []byte) {
	if cap(frame) == pooledFrameCap {
		framePool.Put(frame[:0])
	}
}

func (ws *RawWebSocket) buildFrame(opcode int, data []byte, mask bool) []byte {
	length := len(data)
	fb := byte(0x80 | opcode)

	headerSize := 2
	if mask {
		headerSize += 4
	}
	if length >= 126 && length < 65536 {
		headerSize += 2
	} else if length >= 65536 {
		headerSize += 8
	}

	totalSize := headerSize + length
	var result []byte
	if totalSize <= pooledFrameCap {
		result = framePool.Get().([]byte)[:0]
	} else {
		result = make([]byte, 0, totalSize)
	}
	result = result[:totalSize]

	pos := 0
	result[pos] = fb
	pos++

	var maskKey [4]byte
	if mask {
		_, _ = rand.Read(maskKey[:])
	}

	if length < 126 {
		lb := byte(length)
		if mask {
			lb |= 0x80
		}
		result[pos] = lb
		pos++
	} else if length < 65536 {
		lb := byte(126)
		if mask {
			lb |= 0x80
		}
		result[pos] = lb
		pos++
		binary.BigEndian.PutUint16(result[pos:], uint16(length))
		pos += 2
	} else {
		lb := byte(127)
		if mask {
			lb |= 0x80
		}
		result[pos] = lb
		pos++
		binary.BigEndian.PutUint64(result[pos:], uint64(length))
		pos += 8
	}

	if mask {
		copy(result[pos:], maskKey[:])
		pos += 4
		payloadStart := pos
		copy(result[payloadStart:], data)
		xorMaskInPlace(result[payloadStart:payloadStart+length], maskKey[:])
	} else {
		copy(result[pos:], data)
	}
	return result
}

func (ws *RawWebSocket) readFrame() (int, []byte, error) {
	var hdr [2]byte
	if _, err := io.ReadFull(ws.bufReader, hdr[:]); err != nil {
		return 0, nil, err
	}

	opcode := int(hdr[0] & 0x0F)
	length := uint64(hdr[1] & 0x7F)

	if length == 126 {
		var buf [2]byte
		if _, err := io.ReadFull(ws.bufReader, buf[:]); err != nil {
			return 0, nil, err
		}
		length = uint64(binary.BigEndian.Uint16(buf[:]))
	} else if length == 127 {
		var buf [8]byte
		if _, err := io.ReadFull(ws.bufReader, buf[:]); err != nil {
			return 0, nil, err
		}
		length = binary.BigEndian.Uint64(buf[:])
	}

	hasMask := (hdr[1] & 0x80) != 0
	var maskKey [4]byte
	if hasMask {
		if _, err := io.ReadFull(ws.bufReader, maskKey[:]); err != nil {
			return 0, nil, err
		}
	}

	const maxFramePayload = 16 * 1024 * 1024
	if length > maxFramePayload {
		return 0, nil, fmt.Errorf("frame too large: %d bytes", length)
	}
	payload := make([]byte, length)
	if length > 0 {
		if _, err := io.ReadFull(ws.bufReader, payload); err != nil {
			return 0, nil, err
		}
	}

	if hasMask {
		xorMaskInPlace(payload, maskKey[:])
	}

	return opcode, payload, nil
}

// ---------------------------------------------------------------------------
// Crypto & MTProto Splitter
// ---------------------------------------------------------------------------

type TrackedStream struct {
	key       []byte
	iv        []byte
	processed uint64
	stream    cipher.Stream
}

func newTrackedCTR(key, iv []byte) (*TrackedStream, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	return &TrackedStream{
		key:       append([]byte(nil), key...),
		iv:        append([]byte(nil), iv...),
		processed: 0,
		stream:    cipher.NewCTR(block, iv),
	}, nil
}

func (t *TrackedStream) XORKeyStream(dst, src []byte) {
	t.stream.XORKeyStream(dst, src)
	t.processed += uint64(len(src))
}

func (t *TrackedStream) Clone() cipher.Stream {
	block, _ := aes.NewCipher(t.key)
	cloneStream := cipher.NewCTR(block, t.iv)
	tClone := &TrackedStream{
		key:       t.key,
		iv:        t.iv,
		processed: t.processed,
		stream:    cloneStream,
	}
	var dummy [16384]byte
	rem := t.processed
	for rem > 0 {
		n := rem
		if n > 16384 {
			n = 16384
		}
		tClone.stream.XORKeyStream(dummy[:n], dummy[:n])
		rem -= n
	}
	return tClone
}

func newAESCTR(key, iv []byte) (cipher.Stream, error) {
	return newTrackedCTR(key, iv)
}

const (
	protoAbridged           = 0
	protoIntermediate       = 1
	protoPaddedIntermediate = 2
)

type MsgSplitter struct {
	stream    cipher.Stream
	protoType int
	cipherBuf []byte
	plainBuf  []byte
	disabled  bool
}

func protoTagToType(proto uint32) int {
	switch proto {
	case 0xEEEEEEEE:
		return protoIntermediate
	case 0xDDDDDDDD:
		return protoPaddedIntermediate
	default:
		return protoAbridged
	}
}

func newMsgSplitter(initData []byte, proto uint32) (*MsgSplitter, error) {
	if len(initData) < 56 {
		return nil, fmt.Errorf("init data too short")
	}
	stream, err := newAESCTR(initData[8:40], initData[40:56])
	if err != nil {
		return nil, err
	}
	skip := make([]byte, 64)
	stream.XORKeyStream(skip, zero64)

	return &MsgSplitter{
		stream:    stream,
		protoType: protoTagToType(proto),
	}, nil
}

func (s *MsgSplitter) Split(chunk []byte) [][]byte {
	if len(chunk) == 0 {
		return nil
	}
	if s.disabled {
		return [][]byte{chunk}
	}

	s.cipherBuf = append(s.cipherBuf, chunk...)
	decrypted := make([]byte, len(chunk))
	s.stream.XORKeyStream(decrypted, chunk)
	s.plainBuf = append(s.plainBuf, decrypted...)

	var parts [][]byte
	for len(s.cipherBuf) > 0 {
		pktLen := s.nextPacketLen()
		if pktLen < 0 {
			break
		}
		if pktLen == 0 {
			parts = append(parts, append([]byte(nil), s.cipherBuf...))
			s.cipherBuf = nil
			s.plainBuf = nil
			s.disabled = true
			break
		}
		if len(s.cipherBuf) < pktLen {
			break
		}
		parts = append(parts, append([]byte(nil), s.cipherBuf[:pktLen]...))
		s.cipherBuf = s.cipherBuf[pktLen:]
		s.plainBuf = s.plainBuf[pktLen:]
	}

	if len(s.cipherBuf) == 0 {
		s.cipherBuf = nil
		s.plainBuf = nil
	}
	if len(parts) == 0 {
		return nil
	}
	return parts
}

func (s *MsgSplitter) nextPacketLen() int {
	if len(s.plainBuf) == 0 {
		return -1
	}
	switch s.protoType {
	case protoAbridged:
		first := s.plainBuf[0] & 0x7F
		var headerLen, payloadLen int
		if first == 0x7F {
			if len(s.plainBuf) < 4 {
				return -1
			}
			payloadLen = int(uint32(s.plainBuf[1])|uint32(s.plainBuf[2])<<8|uint32(s.plainBuf[3])<<16) * 4
			headerLen = 4
		} else {
			payloadLen = int(first) * 4
			headerLen = 1
		}
		if payloadLen <= 0 {
			return 0
		}
		pktLen := headerLen + payloadLen
		if len(s.plainBuf) < pktLen {
			return -1
		}
		return pktLen

	case protoIntermediate, protoPaddedIntermediate:
		if len(s.plainBuf) < 4 {
			return -1
		}
		payloadLen := int(binary.LittleEndian.Uint32(s.plainBuf[:4]) & 0x7FFFFFFF)
		if payloadLen <= 0 {
			return 0
		}
		pktLen := 4 + payloadLen
		if len(s.plainBuf) < pktLen {
			return -1
		}
		return pktLen
	}
	return 0
}

// ---------------------------------------------------------------------------
// WsPool & Bridging
// ---------------------------------------------------------------------------

func wsDomains(dc int, isMedia bool) []string {
	effectiveDC := dc
	if override, ok := dcOverrides[dc]; ok {
		effectiveDC = override
	}

	if isMedia {
		return []string{
			fmt.Sprintf("kws%d-1.web.telegram.org", effectiveDC),
			fmt.Sprintf("kws%d.web.telegram.org", effectiveDC),
		}
	}
	return []string{
		fmt.Sprintf("kws%d.web.telegram.org", effectiveDC),
		fmt.Sprintf("kws%d-1.web.telegram.org", effectiveDC),
	}
}

type dcSlot struct {
	dc      int
	isMedia int
}

type poolEntry struct {
	ws        *RawWebSocket
	created   int64
	validated int64
}

type WsPool struct {
	queues sync.Map
	status sync.Map
}

func newWsPool() *WsPool { return &WsPool{} }

func (p *WsPool) getQueue(slot dcSlot) (chan *poolEntry, *atomic.Int32) {
	q, _ := p.queues.LoadOrStore(slot, make(chan *poolEntry, 16)) // Max size safely handled
	s, _ := p.status.LoadOrStore(slot, &atomic.Int32{})
	return q.(chan *poolEntry), s.(*atomic.Int32)
}

func isMediaInt(b bool) int {
	if b {
		return 1
	}
	return 0
}

func isPoolEntryUsable(e *poolEntry, now int64) bool {
	if e == nil || e.ws == nil || e.ws.closed.Load() {
		return false
	}
	if now-e.created > int64(wsPoolMaxAge) {
		return false
	}
	if e.validated == 0 || now-e.validated > int64(wsPoolProbeAfter) {
		if err := e.ws.probe(wsPoolProbeTimeout); err != nil {
			return false
		}
		e.validated = now
	}
	return true
}

func (p *WsPool) Get(ctx context.Context, dc int, isMedia bool, targetIP string, domains []string) *RawWebSocket {
	slot := dcSlot{dc, isMediaInt(isMedia)}
	q, s := p.getQueue(slot)
	now := time.Now().Unix()
	var ws *RawWebSocket

	for {
		select {
		case e := <-q:
			if !isPoolEntryUsable(e, now) {
				if e != nil && e.ws != nil {
					SafeClose(e.ws.conn)
				}
				continue
			}
			ws = e.ws
			stats.poolHits.Add(1)
		default:
			stats.poolMisses.Add(1)
		}
		break
	}

	if s.CompareAndSwap(0, 1) {
		go p.refill(ctx, slot, q, s, targetIP, domains)
	}
	return ws
}

func (p *WsPool) refill(ctx context.Context, slot dcSlot, q chan *poolEntry, s *atomic.Int32, targetIP string, domains []string) {
	defer s.Store(0)
	needed := int(poolSize.Load()) - len(q)
	if needed <= 0 {
		return
	}

	var wg sync.WaitGroup
	for i := 0; i < needed; i++ {
		select {
		case <-ctx.Done():
			return
		default:
		}
		wg.Add(1)
		go func() {
			defer wg.Done()
			if ws := connectOneWS(ctx, targetIP, domains); ws != nil {
				if err := ws.probe(wsPoolProbeTimeout); err != nil {
					SafeClose(ws.conn)
					return
				}
				now := time.Now().Unix()
				select {
				case q <- &poolEntry{ws: ws, created: now, validated: now}:
				case <-ctx.Done():
					SafeClose(ws.conn)
				default:
					SafeClose(ws.conn)
				}
			}
		}()
	}
	wg.Wait()
}

func (p *WsPool) Warmup(ctx context.Context, dcOptMap map[int]string) {
	for dc, targetIP := range dcOptMap {
		if targetIP == "" {
			continue
		}
		for _, isMedia := range []bool{false, true} {
			select {
			case <-ctx.Done():
				return
			default:
			}
			domains := wsDomains(dc, isMedia)
			slot := dcSlot{dc, isMediaInt(isMedia)}
			q, s := p.getQueue(slot)
			if s.CompareAndSwap(0, 1) {
				go p.refill(ctx, slot, q, s, targetIP, domains)
			}
		}
	}
}

func (p *WsPool) Maintain(ctx context.Context, dcOptMap map[int]string) {
	ticker := time.NewTicker(time.Duration(poolMaintainInterval) * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			p.queues.Range(func(key, val interface{}) bool {
				slot := key.(dcSlot)
				q := val.(chan *poolEntry)
				s, _ := p.status.Load(slot)

				sz := len(q)
				for i := 0; i < sz; i++ {
					select {
					case e := <-q:
						now := time.Now().Unix()
						if now-e.created > int64(wsPoolMaxAge) || e.ws.closed.Load() {
							SafeClose(e.ws.conn)
						} else {
							needsProbe := e.validated == 0 || now-e.validated > int64(wsPoolProbeAfter)
							if needsProbe {
								if err := e.ws.probe(wsPoolProbeTimeout); err != nil {
									SafeClose(e.ws.conn)
									continue
								}
								e.validated = now
							} else if err := e.ws.SendPing(); err != nil {
								SafeClose(e.ws.conn)
								continue
							}
							select {
							case q <- e:
							default:
								SafeClose(e.ws.conn)
							}
						}
					default:
					}
				}

				if len(q) < int(poolSize.Load()) && s.(*atomic.Int32).CompareAndSwap(0, 1) {
					isMediaBool := slot.isMedia == 1
					dms := wsDomains(slot.dc, isMediaBool)
					go p.refill(ctx, slot, q, s.(*atomic.Int32), dcOptMap[slot.dc], dms)
				}
				return true
			})
		}
	}
}
func (p *WsPool) IdleCount() int {
	count := 0
	p.queues.Range(func(_, val interface{}) bool {
		count += len(val.(chan *poolEntry))
		return true
	})
	return count
}

func (p *WsPool) CloseAll() {
	p.queues.Range(func(_, val interface{}) bool {
		q := val.(chan *poolEntry)
		for {
			select {
			case e := <-q:
				SafeClose(e.ws.conn)
			default:
				return true
			}
		}
	})
}

var wsPool = newWsPool()

func mediaTag(isMedia bool) string {
	if isMedia {
		return "m"
	}
	return ""
}

func isHTTPTransport(data []byte) bool {
	if len(data) < 4 {
		return false
	}
	return string(data[:4]) == "POST" || string(data[:3]) == "GET" ||
		string(data[:4]) == "HEAD" || string(data[:7]) == "OPTIONS"
}

func bridgeWS(ctx context.Context, conn net.Conn, ws *RawWebSocket,
	label string, dc int, dst string, port int, isMedia bool,
	splitter *MsgSplitter, cltDec, cltEnc, tgEnc, tgDec cipher.Stream) {

	ctx2, cancel := context.WithCancel(ctx)
	defer cancel()

	go func() {
		<-ctx2.Done()
		SafeClose(conn)
		ws.Close()
	}()

	var wg sync.WaitGroup
	wg.Add(2)

	// WS keepalive: periodic ping to detect dead connections
	lastActivity := time.Now()
	var activityMu sync.Mutex

	go func() {
		ticker := time.NewTicker(bridgePingInterval)
		defer ticker.Stop()
		for {
			select {
			case <-ctx2.Done():
				return
			case <-ticker.C:
				activityMu.Lock()
				idle := time.Since(lastActivity)
				activityMu.Unlock()
				if idle > bridgePingInterval {
					if err := ws.SendPing(); err != nil {
						cancel()
						return
					}
				}
			}
		}
	}()

	go func() {
		defer wg.Done()
		defer cancel()
		buf := bytesPool.Get().([]byte)
		defer bytesPool.Put(buf)
		readLimit := cap(buf)
		if readLimit > wsBridgeChunkSize {
			readLimit = wsBridgeChunkSize
		}
		for {
			_ = conn.SetReadDeadline(time.Now().Add(bridgeReadTimeout))
			n, err := conn.Read(buf[:readLimit])
			if n > 0 {
				chunk := buf[:n]
				stats.bytesUp.Add(int64(n))

				activityMu.Lock()
				lastActivity = time.Now()
				activityMu.Unlock()

				cltDec.XORKeyStream(chunk, chunk)
				tgEnc.XORKeyStream(chunk, chunk)

				var sendErr error
				if splitter != nil {
					parts := splitter.Split(chunk)
					if len(parts) > 1 {
						sendErr = ws.SendBatch(parts)
					} else if len(parts) == 1 {
						sendErr = ws.Send(parts[0])
					}
				} else {
					sendErr = ws.Send(chunk)
				}
				if sendErr != nil {
					return
				}
			}
			if err != nil {
				return
			}
		}
	}()

	go func() {
		defer wg.Done()
		defer cancel()
		for {
			_ = ws.conn.SetReadDeadline(time.Now().Add(bridgeReadTimeout))
			data, err := ws.Recv()
			if err != nil || data == nil {
				return
			}
			n := len(data)
			stats.bytesDown.Add(int64(n))

			activityMu.Lock()
			lastActivity = time.Now()
			activityMu.Unlock()

			tgDec.XORKeyStream(data, data)
			cltEnc.XORKeyStream(data, data)
			if _, werr := conn.Write(data); werr != nil {
				return
			}
		}
	}()

	wg.Wait()
}

func bridgeTCP(ctx context.Context, client, remote net.Conn,
	label string, dc int, dst string, port int, isMedia bool, cltDec, cltEnc, tgEnc, tgDec cipher.Stream) {

	ctx2, cancel := context.WithCancel(ctx)

	go func() {
		<-ctx2.Done()
		SafeClose(client)
		SafeClose(remote)
	}()

	var wg sync.WaitGroup
	wg.Add(2)

	forward := func(src, dstW net.Conn, isUp bool) {
		defer wg.Done()
		defer cancel()
		buf := bytesPool.Get().([]byte)
		defer bytesPool.Put(buf)
		for {
			_ = src.SetReadDeadline(time.Now().Add(bridgeReadTimeout))
			n, err := src.Read(buf[:cap(buf)])
			if n > 0 {
				chunk := buf[:n]
				if isUp {
					stats.bytesUp.Add(int64(n))
					cltDec.XORKeyStream(chunk, chunk)
					tgEnc.XORKeyStream(chunk, chunk)
				} else {
					stats.bytesDown.Add(int64(n))
					tgDec.XORKeyStream(chunk, chunk)
					cltEnc.XORKeyStream(chunk, chunk)
				}
				if _, werr := dstW.Write(chunk); werr != nil {
					return
				}
			}
			if err != nil {
				return
			}
		}
	}

	go forward(client, remote, true)
	go forward(remote, client, false)

	wg.Wait()
}

func tcpFallback(ctx context.Context, client net.Conn, dst string, port int,
	init []byte, label string, dc int, isMedia bool, cltDec, cltEnc, tgEnc, tgDec cipher.Stream) bool {

	dialer := &net.Dialer{
		Timeout:   10 * time.Second,
		KeepAlive: 60 * time.Second,
	}
	remote, err := dialer.DialContext(ctx, "tcp", net.JoinHostPort(dst, strconv.Itoa(port)))
	if err != nil {
		return false
	}

	stats.connectionsTcpFallback.Add(1)
	logInfo.Printf(" DC%d%s подключен по TCP", dc, mediaTag(isMedia))
	_, _ = remote.Write(init)
	bridgeTCP(ctx, client, remote, label, dc, dst, port, isMedia, cltDec, cltEnc, tgEnc, tgDec)
	return true
}

func cfproxyFallback(ctx context.Context, conn net.Conn, relayInit []byte, label string,
	dc int, isMedia bool, splitter *MsgSplitter,
	cltDec, cltEnc, tgEnc, tgDec cipher.Stream) bool {

	cfproxyMu.RLock()
	if !cfproxyEnabled || len(cfproxyDomains) == 0 {
		cfproxyMu.RUnlock()
		return false
	}
	active := activeCfDomain
	domains := make([]string, len(cfproxyDomains))
	copy(domains, cfproxyDomains)
	cfproxyMu.RUnlock()

	ordered := []string{active}
	for _, d := range domains {
		if d != active {
			ordered = append(ordered, d)
		}
	}

	mTag := mediaTag(isMedia)
	logDebug.Printf(" CF fallback DC%d%s: %d домен(ов)", dc, mTag, len(ordered))

	type wsResult struct {
		ws     *RawWebSocket
		domain string
	}
	attemptCtx, cancelAttempts := context.WithCancel(ctx)
	defer cancelAttempts()
	ch := make(chan wsResult, len(ordered))
	for _, baseDomain := range ordered {
		go func(bd string) {
			domain := fmt.Sprintf("kws%d.%s", dc, bd)
			logDebug.Printf(" CF try %s", domain)
			ws, resolvedIP, err := cfConnectDomain(attemptCtx, domain, "/apiws", 5)
			if err != nil {
				if attemptCtx.Err() == nil {
					if resolvedIP != "" {
						logError.Printf(" CF fail %s via %s: %s", domain, resolvedIP, compactConnError(err))
					} else {
						logError.Printf(" CF fail %s: %s", domain, compactConnError(err))
					}
				}
				ch <- wsResult{nil, ""}
				return
			}
			if resolvedIP != "" {
				logDebug.Printf(" CF ok %s via %s", domain, resolvedIP)
			} else {
				logDebug.Printf(" CF ok %s via hostname", domain)
			}
			ch <- wsResult{ws, bd}
		}(baseDomain)
	}

	var ws *RawWebSocket
	var chosenDomain string
	for i := 0; i < len(ordered); i++ {
		r := <-ch
		if r.ws != nil && ws == nil {
			ws = r.ws
			chosenDomain = r.domain
			cancelAttempts()
			remaining := len(ordered) - i - 1
			if remaining > 0 {
				go func(left int) {
					for j := 0; j < left; j++ {
						rr := <-ch
						if rr.ws != nil {
							go rr.ws.Close()
						}
					}
				}(remaining)
			}
			break
		} else if r.ws != nil {
			go r.ws.Close()
		}
	}

	if ws == nil {
		logWarn.Printf(" CF fallback DC%d%s: все CF домены недоступны", dc, mTag)
		return false
	}

	if chosenDomain != "" && chosenDomain != active {
		cfproxyMu.Lock()
		activeCfDomain = chosenDomain
		cfproxyMu.Unlock()
		logInfo.Printf(" CF домен  %s", chosenDomain)
	}

	stats.connectionsCfproxy.Add(1)
	logInfo.Printf(" DC%d%s подключен через CF", dc, mTag)

	if err := ws.Send(relayInit); err != nil {
		ws.Close()
		return false
	}

	bridgeWS(ctx, conn, ws, label, dc, chosenDomain, 443, isMedia, splitter, cltDec, cltEnc, tgEnc, tgDec)
	return true
}

func doFallback(ctx context.Context, conn net.Conn, relayInit []byte, label string,
	dc int, isMedia bool, splitter *MsgSplitter,
	cltDec, cltEnc, tgEnc, tgDec cipher.Stream) bool {

	if t, ok := cltDec.(interface{ Clone() cipher.Stream }); ok {
		cltDec = t.Clone()
	}
	if t, ok := cltEnc.(interface{ Clone() cipher.Stream }); ok {
		cltEnc = t.Clone()
	}
	if t, ok := tgEnc.(interface{ Clone() cipher.Stream }); ok {
		tgEnc = t.Clone()
	}
	if t, ok := tgDec.(interface{ Clone() cipher.Stream }); ok {
		tgDec = t.Clone()
	}

	var fallbackDst string
	dcOptMu.RLock()
	if ip, ok := dcOpt[dc]; ok && ip != "" {
		fallbackDst = ip
	}
	dcOptMu.RUnlock()
	if fallbackDst == "" {
		fallbackDst = dcDefaultIPs[dc]
	}

	cfproxyMu.RLock()
	useCf := cfproxyEnabled
	cfproxyMu.RUnlock()

	if useCf {
		if cfproxyFallback(ctx, conn, relayInit, label, dc, isMedia, splitter, cltDec, cltEnc, tgEnc, tgDec) {
			return true
		}
	}

	if fallbackDst != "" {
		if tcpFallback(ctx, conn, fallbackDst, 443, relayInit, label, dc, isMedia, cltDec, cltEnc, tgEnc, tgDec) {
			return true
		}
	}

	return false
}

// ---------------------------------------------------------------------------
// Fake TLS support (ee-secret)
// ---------------------------------------------------------------------------

const (
	tlsRecordHandshake = 0x16
	tlsRecordCCS       = 0x14
	tlsRecordAppData   = 0x17
	clientRandomOffset = 11
	clientRandomLen    = 32
	sessionIdOffset    = 44
	sessionIdLen       = 32
	timestampTolerance = 120
)

func verifyClientHello(data, secret []byte) ([]byte, []byte, bool) {
	n := len(data)
	if n < 43 {
		return nil, nil, false
	}
	if data[0] != tlsRecordHandshake || data[5] != 0x01 {
		return nil, nil, false
	}

	clientRandom := make([]byte, clientRandomLen)
	copy(clientRandom, data[clientRandomOffset:clientRandomOffset+clientRandomLen])

	zeroed := make([]byte, n)
	copy(zeroed, data)
	for i := 0; i < clientRandomLen; i++ {
		zeroed[clientRandomOffset+i] = 0
	}

	mac := hmacSHA256(secret, zeroed)

	for i := 0; i < 28; i++ {
		if mac[i] != clientRandom[i] {
			return nil, nil, false
		}
	}

	tsXor := make([]byte, 4)
	for i := 0; i < 4; i++ {
		tsXor[i] = clientRandom[28+i] ^ mac[28+i]
	}
	timestamp := binary.LittleEndian.Uint32(tsXor)
	now := uint32(time.Now().Unix())
	diff := int64(now) - int64(timestamp)
	if diff < 0 {
		diff = -diff
	}
	if diff > timestampTolerance {
		return nil, nil, false
	}

	sessionId := make([]byte, sessionIdLen)
	if n >= sessionIdOffset+sessionIdLen && data[43] == 0x20 {
		copy(sessionId, data[sessionIdOffset:sessionIdOffset+sessionIdLen])
	}

	return clientRandom, sessionId, true
}

func hmacSHA256(key, data []byte) []byte {
	h := hmac.New(sha256.New, key)
	h.Write(data)
	return h.Sum(nil)
}

var serverHelloTemplate = []byte{
	0x16, 0x03, 0x03, 0x00, 0x7a, 0x02, 0x00, 0x00, 0x76, 0x03, 0x03,
	0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	0x20,
	0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	0x13, 0x01, 0x00, 0x00, 0x2e, 0x00, 0x33, 0x00, 0x24, 0x00, 0x1d, 0x00, 0x20,
	0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	0x00, 0x2b, 0x00, 0x02, 0x03, 0x04,
}

func buildServerHello(secret, clientRandom, sessionId []byte) []byte {
	sh := make([]byte, len(serverHelloTemplate))
	copy(sh, serverHelloTemplate)
	copy(sh[44:44+32], sessionId)

	pubKey := make([]byte, 32)
	rand.Read(pubKey)
	copy(sh[89:89+32], pubKey)

	ccsFrame := []byte{0x14, 0x03, 0x03, 0x00, 0x01, 0x01}

	encSize := 1900 + int(time.Now().UnixNano()%200)
	encData := make([]byte, encSize)
	rand.Read(encData)
	appRecord := make([]byte, 5+encSize)
	appRecord[0] = 0x17
	appRecord[1] = 0x03
	appRecord[2] = 0x03
	binary.BigEndian.PutUint16(appRecord[3:5], uint16(encSize))
	copy(appRecord[5:], encData)

	response := make([]byte, 0, len(sh)+len(ccsFrame)+len(appRecord))
	response = append(response, sh...)
	response = append(response, ccsFrame...)
	response = append(response, appRecord...)

	hmacInput := make([]byte, 0, len(clientRandom)+len(response))
	hmacInput = append(hmacInput, clientRandom...)
	hmacInput = append(hmacInput, response...)
	serverRandom := hmacSHA256(secret, hmacInput)

	copy(response[11:11+32], serverRandom)

	return response
}

type FakeTlsConn struct {
	conn     net.Conn
	readLeft int
}

func newFakeTlsConn(conn net.Conn) *FakeTlsConn {
	return &FakeTlsConn{conn: conn}
}

func (f *FakeTlsConn) Read(p []byte) (int, error) {
	if f.readLeft > 0 {
		toRead := f.readLeft
		if toRead > len(p) {
			toRead = len(p)
		}
		n, err := f.conn.Read(p[:toRead])
		f.readLeft -= n
		return n, err
	}

	for {
		hdr := make([]byte, 5)
		if _, err := io.ReadFull(f.conn, hdr); err != nil {
			return 0, err
		}

		rtype := hdr[0]
		recLen := int(binary.BigEndian.Uint16(hdr[3:5]))

		if rtype == tlsRecordCCS {
			if recLen > 0 {
				discard := make([]byte, recLen)
				if _, err := io.ReadFull(f.conn, discard); err != nil {
					return 0, err
				}
			}
			continue
		}

		if rtype != tlsRecordAppData {
			return 0, fmt.Errorf("unexpected TLS record type 0x%02X", rtype)
		}

		toRead := recLen
		if toRead > len(p) {
			toRead = len(p)
		}
		n, err := f.conn.Read(p[:toRead])
		f.readLeft = recLen - n
		return n, err
	}
}

func (f *FakeTlsConn) Write(p []byte) (int, error) {
	var parts []byte
	offset := 0
	for offset < len(p) {
		end := offset + 16384
		if end > len(p) {
			end = len(p)
		}
		chunk := p[offset:end]
		hdr := []byte{0x17, 0x03, 0x03, 0, 0}
		binary.BigEndian.PutUint16(hdr[3:5], uint16(len(chunk)))
		parts = append(parts, hdr...)
		parts = append(parts, chunk...)
		offset = end
	}
	_, err := f.conn.Write(parts)
	return len(p), err
}

func (f *FakeTlsConn) Close() error                       { return f.conn.Close() }
func (f *FakeTlsConn) LocalAddr() net.Addr                { return f.conn.LocalAddr() }
func (f *FakeTlsConn) RemoteAddr() net.Addr               { return f.conn.RemoteAddr() }
func (f *FakeTlsConn) SetDeadline(t time.Time) error      { return f.conn.SetDeadline(t) }
func (f *FakeTlsConn) SetReadDeadline(t time.Time) error  { return f.conn.SetReadDeadline(t) }
func (f *FakeTlsConn) SetWriteDeadline(t time.Time) error { return f.conn.SetWriteDeadline(t) }

// PrefixConn solves the 1/256 disconnect bug gracefully
type PrefixConn struct {
	net.Conn
	prefix []byte
}

func (c *PrefixConn) Read(p []byte) (int, error) {
	if len(c.prefix) > 0 {
		n := copy(p, c.prefix)
		c.prefix = c.prefix[n:]
		return n, nil
	}
	return c.Conn.Read(p)
}

func handleClient(ctx context.Context, conn net.Conn) {
	stats.connectionsTotal.Add(1)
	stats.connectionsActive.Add(1)
	defer func() {
		if stats.connectionsActive.Load() > 0 {
			stats.connectionsActive.Add(-1)
		}
	}()
	peer := conn.RemoteAddr().String()
	label := peer

	setSockOpts(conn)

	defer conn.Close()

	proxySecretMu.RLock()
	currentSecret := proxySecret
	proxySecretMu.RUnlock()
	secretBytes, _ := hex.DecodeString(currentSecret)

	fakeTlsMu.RLock()
	useFakeTls := fakeTlsEnabled
	fakeTlsMu.RUnlock()

	firstByte := make([]byte, 1)
	_ = conn.SetReadDeadline(time.Now().Add(10 * time.Second))
	if _, err := io.ReadFull(conn, firstByte); err != nil {
		return
	}
	_ = conn.SetReadDeadline(time.Time{})

	var clientConn net.Conn = conn
	var handshake []byte

	if useFakeTls && firstByte[0] == tlsRecordHandshake {
		hdrRest := make([]byte, 4)
		if _, err := io.ReadFull(conn, hdrRest); err != nil {
			return
		}
		tlsHeader := append(firstByte, hdrRest...)
		recordLen := int(binary.BigEndian.Uint16(tlsHeader[3:5]))

		if recordLen > 16384 {
			// Not TLS, gracefully fallback
			clientConn = &PrefixConn{Conn: conn, prefix: tlsHeader}
		} else {
			recordBody := make([]byte, recordLen)
			if _, err := io.ReadFull(conn, recordBody); err != nil {
				return
			}
			clientHello := append(tlsHeader, recordBody...)
			clientRandom, sessionId, ok := verifyClientHello(clientHello, secretBytes)
			if !ok {
				// FakeTLS failed, fallback gracefully (fixes 1/256 disconnect bug)
				clientConn = &PrefixConn{Conn: conn, prefix: clientHello}
			} else {
				serverHello := buildServerHello(secretBytes, clientRandom, sessionId)
				if _, err := conn.Write(serverHello); err != nil {
					return
				}
				clientConn = newFakeTlsConn(conn)
			}
		}
	} else {
		clientConn = &PrefixConn{Conn: conn, prefix: firstByte}
	}

	handshake = make([]byte, 64)
	_ = clientConn.SetReadDeadline(time.Now().Add(5 * time.Second))
	if _, err := io.ReadFull(clientConn, handshake); err != nil {
		return
	}
	_ = clientConn.SetReadDeadline(time.Time{})

	if isHTTPTransport(handshake) {
		stats.connectionsHttpReject.Add(1)
		_, _ = conn.Write([]byte("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n"))
		return
	}

	cltDecPrekey := handshake[8:40]
	cltDecIv := handshake[40:56]
	hashDec := sha256.New()
	hashDec.Write(cltDecPrekey)
	hashDec.Write(secretBytes)
	cltDecryptor, _ := newAESCTR(hashDec.Sum(nil), cltDecIv)

	decrypted := make([]byte, 64)
	cltDecryptor.XORKeyStream(decrypted, handshake)

	protoTag := decrypted[56:60]
	proto := binary.LittleEndian.Uint32(protoTag)
	if !validProtos[proto] {
		stats.connectionsBad.Add(1)
		return
	}

	dcRaw := int16(binary.LittleEndian.Uint16(decrypted[60:62]))
	dc := int(dcRaw)
	if dc < 0 {
		dc = -dc
	}
	isMedia := dcRaw < 0
	mTag := mediaTag(isMedia)

	cltEncPrekeyAndIv := make([]byte, 48)
	for i := 0; i < 48; i++ {
		cltEncPrekeyAndIv[i] = handshake[8+47-i]
	}
	hashEnc := sha256.New()
	hashEnc.Write(cltEncPrekeyAndIv[:32])
	hashEnc.Write(secretBytes)
	cltEncryptor, _ := newAESCTR(hashEnc.Sum(nil), cltEncPrekeyAndIv[32:])

	relayInit := make([]byte, 64)
	for {
		rand.Read(relayInit)
		if relayInit[0] == 0xEF {
			continue
		}
		s := string(relayInit[:4])
		if s == "HEAD" || s == "POST" || s == "GET " || s == "\xee\xee\xee\xee" || s == "\xdd\xdd\xdd\xdd" {
			continue
		}
		if relayInit[0] == 0x16 && relayInit[1] == 0x03 && relayInit[2] == 0x01 && relayInit[3] == 0x02 {
			continue
		}
		if relayInit[4] == 0 && relayInit[5] == 0 && relayInit[6] == 0 && relayInit[7] == 0 {
			continue
		}
		break
	}

	tgDecPrekeyAndIv := make([]byte, 48)
	for i := 0; i < 48; i++ {
		tgDecPrekeyAndIv[i] = relayInit[8+47-i]
	}

	tgEncryptor, _ := newAESCTR(relayInit[8:40], relayInit[40:56])
	tgDecryptor, _ := newAESCTR(tgDecPrekeyAndIv[:32], tgDecPrekeyAndIv[32:])

	dcBytes := make([]byte, 2)
	dcIdx := dc
	if isMedia {
		dcIdx = -dc
	}
	binary.LittleEndian.PutUint16(dcBytes, uint16(dcIdx))

	tailPlain := make([]byte, 8)
	copy(tailPlain[0:4], protoTag)
	copy(tailPlain[4:6], dcBytes)
	rand.Read(tailPlain[6:8])

	encryptedFull := make([]byte, 64)
	tgEncryptor.XORKeyStream(encryptedFull, relayInit)

	keystreamTail := make([]byte, 8)
	for i := 0; i < 8; i++ {
		keystreamTail[i] = encryptedFull[56+i] ^ relayInit[56+i]
		relayInit[56+i] = tailPlain[i] ^ keystreamTail[i]
	}

	dcKey := [2]int{dc, isMediaInt(isMedia)}
	now := float64(time.Now().UnixNano()) / 1e9

	splitter, _ := newMsgSplitter(relayInit, proto)

	dcOptMu.RLock()
	target, dcConfigured := dcOpt[dc]
	if isMedia {
		if t, ok := dcOpt[-dc]; ok && t != "" {
			target = t
			dcConfigured = true
		}
	}
	dcOptMu.RUnlock()

	wsBlackMu.RLock()
	blacklisted := wsBlacklist[dcKey]
	wsBlackMu.RUnlock()

	if !dcConfigured || blacklisted {
		doFallback(ctx, clientConn, relayInit, label, dc, isMedia, splitter, cltDecryptor, cltEncryptor, tgEncryptor, tgDecryptor)
		return
	}

	dcFailMu.RLock()
	failUntil := dcFailUntil[dcKey]
	dcFailMu.RUnlock()

	wsTimeout := 10.0
	if now < failUntil {
		wsTimeout = wsFailTimeout
	}

	domains := wsDomains(dc, isMedia)
	ws := wsPool.Get(ctx, dc, isMedia, target, domains)

	wsFailedRedirect := false
	allRedirects := true

	if ws == nil {
		type wsRes struct {
			ws  *RawWebSocket
			err error
		}
		attemptCtx, cancelAttempts := context.WithCancel(ctx)
		defer cancelAttempts()
		resCh := make(chan wsRes, len(domains))
		for _, dom := range domains {
			go func(d string) {
				w, err := wsConnect(attemptCtx, target, d, "/apiws", wsTimeout)
				resCh <- wsRes{w, err}
			}(dom)
		}

		for i := 0; i < len(domains); i++ {
			r := <-resCh
			if r.err == nil {
				ws = r.ws
				allRedirects = false
				cancelAttempts()

				remaining := len(domains) - i - 1
				if remaining > 0 {
					go func(left int) {
						for j := 0; j < left; j++ {
							rr := <-resCh
							if rr.ws != nil {
								rr.ws.Close()
							}
						}
					}(remaining)
				}

				break
			}

			stats.wsErrors.Add(1)
			if wsErr, ok := r.err.(*WsHandshakeError); ok {
				if wsErr.IsRedirect() {
					wsFailedRedirect = true
				} else {
					allRedirects = false
				}
			} else {
				allRedirects = false
			}
		}
	}

	if ws == nil {
		logWarn.Printf(" DC%d%s: все попытки WS провалены (DPI/Интернет)", dc, mTag)
		if wsFailedRedirect && allRedirects {
			wsBlackMu.Lock()
			wsBlacklist[dcKey] = true
			wsBlackMu.Unlock()
			logWarn.Printf(" DC%d%s заблокирован (302)", dc, mTag)
		} else {
			dcFailMu.Lock()
			dcFailUntil[dcKey] = now + dcFailCooldown
			dcFailMu.Unlock()
		}

		splitterFb, _ := newMsgSplitter(relayInit, proto)
		doFallback(ctx, clientConn, relayInit, label, dc, isMedia, splitterFb, cltDecryptor, cltEncryptor, tgEncryptor, tgDecryptor)
		return
	}

	dcFailMu.Lock()
	delete(dcFailUntil, dcKey)
	dcFailMu.Unlock()

	stats.connectionsWs.Add(1)

	if err := ws.Send(relayInit); err != nil {
		ws.Close()
		tcpFallback(ctx, clientConn, target, 443, relayInit, label, dc, isMedia, cltDecryptor, cltEncryptor, tgEncryptor, tgDecryptor)
		return
	}

	bridgeWS(ctx, clientConn, ws, label, dc, target, 443, isMedia, splitter, cltDecryptor, cltEncryptor, tgEncryptor, tgDecryptor)
}

// ---------------------------------------------------------------------------
// Server
// ---------------------------------------------------------------------------

func runProxy(ctx context.Context, host string, port int, dcOptMap map[int]string, started chan<- error) error {
	dcOptMu.Lock()
	dcOpt = dcOptMap
	dcOptMu.Unlock()

	addr := net.JoinHostPort(host, strconv.Itoa(port))
	lc := net.ListenConfig{}

	listener, err := lc.Listen(ctx, "tcp", addr)
	if err != nil {
		signalProxyStart(started, fmt.Errorf("listen on %s: %w", addr, err))
		return fmt.Errorf("listen on %s: %w", addr, err)
	}
	signalProxyStart(started, nil)

	if tcpL, ok := listener.(*net.TCPListener); ok {
		raw, err := tcpL.SyscallConn()
		if err == nil {
			_ = raw.Control(func(fd uintptr) {
				_ = syscall.SetsockoptInt(int(fd), syscall.IPPROTO_TCP, syscall.TCP_NODELAY, 1)
			})
		}
	}

	srvCtx, srvCancel := context.WithCancel(ctx)
	defer srvCancel()

	startCfproxyRefresh(srvCtx)

	logInfo.Println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
	logInfo.Println("  TG WS Proxy запущен")
	logInfo.Printf("  Адрес: %s:%d", host, port)

	go func() {
		ticker := time.NewTicker(60 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-srvCtx.Done():
				return
			case <-ticker.C:
				idleCount := wsPool.IdleCount()
				logInfo.Printf(" %s | пул:%d", stats.SummaryRu(), idleCount)
			}
		}
	}()

	wsPool.Warmup(srvCtx, dcOptMap)
	go wsPool.Maintain(srvCtx, dcOptMap)

	var activeConns sync.WaitGroup

	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				select {
				case <-srvCtx.Done():
					return
				default:
					if ne, ok := err.(net.Error); ok && ne.Timeout() {
						continue
					}
					return
				}
			}
			activeConns.Add(1)
			go func() {
				defer activeConns.Done()
				handleClient(srvCtx, conn)
			}()
		}
	}()

	<-srvCtx.Done()
	_ = listener.Close()

	done := make(chan struct{})
	go func() {
		activeConns.Wait()
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(30 * time.Second):
	}

	wsPool.CloseAll()
	return nil
}

func parseCIDRPool(cidrsStr string) (map[int]string, error) {
	result := make(map[int]string)
	pairs := strings.Split(cidrsStr, ",")
	for _, pair := range pairs {
		parts := strings.Split(pair, ":")
		if len(parts) == 2 {
			dcRaw := strings.TrimSpace(parts[0])
			ipRaw := strings.TrimSpace(parts[1])
			if dc, err := strconv.Atoi(dcRaw); err == nil && ipRaw != "" {
				result[dc] = ipRaw
			}
		}
	}
	return result, nil
}

func signalProxyStart(started chan<- error, err error) {
	if started == nil {
		return
	}
	select {
	case started <- err:
	default:
	}
}

// ---------------------------------------------------------------------------
// CGO exports
// ---------------------------------------------------------------------------

var (
	globalCtx    context.Context
	globalCancel context.CancelFunc
	globalMu     sync.Mutex
)

//export StartProxy
func StartProxy(cHost *C.char, port C.int, cDcIps *C.char, cSecret *C.char, verbose C.int) C.int {
	globalMu.Lock()
	defer globalMu.Unlock()

	if globalCancel != nil {
		return -1
	}

	host := C.GoString(cHost)
	goPort := int(port)
	dcIpsStr := C.GoString(cDcIps)
	secretStr := C.GoString(cSecret)
	isVerbose := int(verbose) != 0

	initLogging(isVerbose)

	if len(secretStr) == 32 {
		if _, err := hex.DecodeString(secretStr); err == nil {
			proxySecretMu.Lock()
			proxySecret = secretStr
			proxySecretMu.Unlock()
		}
	}

	initCfproxyDomains()

	dcOptMap, err := parseCIDRPool(dcIpsStr)
	if err != nil {
		return -2
	}

	globalCtx, globalCancel = context.WithCancel(context.Background())
	started := make(chan error, 1)

	go func() {
		_ = runProxy(globalCtx, host, goPort, dcOptMap, started)
	}()

	if err := <-started; err != nil {
		globalCancel()
		globalCancel = nil
		globalCtx = nil
		return -3
	}

	return 0
}

//export StopProxy
func StopProxy() C.int {
	globalMu.Lock()
	defer globalMu.Unlock()

	if globalCancel == nil {
		return -1
	}

	globalCancel()
	globalCancel = nil
	globalCtx = nil

	stats.Reset()

	wsBlackMu.Lock()
	wsBlacklist = make(map[[2]int]bool)
	wsBlackMu.Unlock()

	dcFailMu.Lock()
	dcFailUntil = make(map[[2]int]float64)
	dcFailMu.Unlock()

	return 0
}

//export SetPoolSize
func SetPoolSize(size C.int) {
	n := int32(size)
	if n < 2 {
		n = 2
	}
	if n > 16 {
		n = 16
	}
	poolSize.Store(n)
}

//export SetCfProxyConfig
func SetCfProxyConfig(enabled C.int, priority C.int, cUserDomain *C.char) {
	cfproxyMu.Lock()
	defer cfproxyMu.Unlock()

	cfproxyEnabled = int(enabled) != 0

	userDomain := C.GoString(cUserDomain)
	cfproxyUserDomain = userDomain

	if userDomain != "" {
		cfproxyDomains = []string{userDomain}
		activeCfDomain = userDomain
	}
}

//export SetSecret
func SetSecret(cSecret *C.char) {
	s := C.GoString(cSecret)
	if len(s) != 32 {
		return
	}
	if _, err := hex.DecodeString(s); err != nil {
		return
	}
	proxySecretMu.Lock()
	proxySecret = s
	proxySecretMu.Unlock()
}

//export GetStats
func GetStats() *C.char {
	return C.CString(stats.Summary())
}

//export SetFakeTls
func SetFakeTls(enabled C.int, cDomain *C.char) {
	fakeTlsMu.Lock()
	defer fakeTlsMu.Unlock()

	fakeTlsEnabled = int(enabled) != 0
	fakeTlsDomain = C.GoString(cDomain)
}

//export GetSecretWithPrefix
func GetSecretWithPrefix() *C.char {
	proxySecretMu.RLock()
	sec := proxySecret
	proxySecretMu.RUnlock()

	fakeTlsMu.RLock()
	tlsOn := fakeTlsEnabled
	tlsDom := fakeTlsDomain
	fakeTlsMu.RUnlock()

	var result string
	if tlsOn && tlsDom != "" {
		domHex := hex.EncodeToString([]byte(tlsDom))
		result = "ee" + sec + domHex
	} else {
		result = "dd" + sec
	}
	return C.CString(result)
}

//export FreeString
func FreeString(p *C.char) {
	C.free(unsafe.Pointer(p))
}

func main() {
	runtime.LockOSThread()
	initLogging(true)
	initCfproxyDomains()

	dcOptMap := map[int]string{
		2: "149.154.167.220",
		4: "149.154.167.220",
	}

	ctx, cancel := context.WithCancel(context.Background())
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sigCh
		cancel()
	}()

	_ = runProxy(ctx, "127.0.0.1", defaultPort, dcOptMap, nil)
}
