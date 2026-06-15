use crate::config::ZERO64;
use aes::cipher::{KeyIvInit, StreamCipher};
use byteorder::{BigEndian, ByteOrder, LittleEndian};

type Aes256Ctr = ctr::Ctr64BE<aes::Aes256>;

// ---------------------------------------------------------------------------
// TrackedStream — эмулирует Go cipher.Stream с поддержкой Clone() через
// "промотку" keystream на processed байт.
// ---------------------------------------------------------------------------

pub struct TrackedStream {
    key: Vec<u8>,
    iv: Vec<u8>,
    processed: u64,
    stream: Aes256Ctr,
}

impl TrackedStream {
    pub fn new(key: &[u8], iv: &[u8]) -> TrackedStream {
        let stream = Aes256Ctr::new(key.into(), iv.into());
        TrackedStream {
            key: key.to_vec(),
            iv: iv.to_vec(),
            processed: 0,
            stream,
        }
    }

    // XOR in place (dst == src в нашем использовании)
    pub fn xor(&mut self, data: &mut [u8]) {
        self.stream.apply_keystream(data);
        self.processed += data.len() as u64;
    }

    pub fn clone_state(&self) -> TrackedStream {
        let mut clone_stream = Aes256Ctr::new(self.key.as_slice().into(), self.iv.as_slice().into());
        let mut dummy = [0u8; 16384];
        let mut rem = self.processed;
        while rem > 0 {
            let n = if rem > 16384 { 16384 } else { rem as usize };
            clone_stream.apply_keystream(&mut dummy[..n]);
            rem -= n as u64;
        }
        TrackedStream {
            key: self.key.clone(),
            iv: self.iv.clone(),
            processed: self.processed,
            stream: clone_stream,
        }
    }
}

pub fn new_aes_ctr(key: &[u8], iv: &[u8]) -> TrackedStream {
    TrackedStream::new(key, iv)
}

// ---------------------------------------------------------------------------
// MTProto Splitter
// ---------------------------------------------------------------------------

pub const PROTO_ABRIDGED: i32 = 0;
pub const PROTO_INTERMEDIATE: i32 = 1;
pub const PROTO_PADDED_INTERMEDIATE: i32 = 2;

pub fn proto_tag_to_type(proto: u32) -> i32 {
    match proto {
        0xEEEEEEEE => PROTO_INTERMEDIATE,
        0xDDDDDDDD => PROTO_PADDED_INTERMEDIATE,
        _ => PROTO_ABRIDGED,
    }
}

pub struct MsgSplitter {
    stream: Aes256Ctr,
    proto_type: i32,
    cipher_buf: Vec<u8>,
    plain_buf: Vec<u8>,
    disabled: bool,
}

impl MsgSplitter {
    pub fn new(init_data: &[u8], proto: u32) -> Option<MsgSplitter> {
        if init_data.len() < 56 {
            return None;
        }
        let mut stream = Aes256Ctr::new(init_data[8..40].into(), init_data[40..56].into());
        let mut skip = [0u8; 64];
        skip.copy_from_slice(&ZERO64);
        stream.apply_keystream(&mut skip);
        Some(MsgSplitter {
            stream,
            proto_type: proto_tag_to_type(proto),
            cipher_buf: Vec::new(),
            plain_buf: Vec::new(),
            disabled: false,
        })
    }

    pub fn split(&mut self, chunk: &[u8]) -> Vec<Vec<u8>> {
        if chunk.is_empty() {
            return Vec::new();
        }
        if self.disabled {
            return vec![chunk.to_vec()];
        }

        self.cipher_buf.extend_from_slice(chunk);
        let mut decrypted = chunk.to_vec();
        self.stream.apply_keystream(&mut decrypted);
        self.plain_buf.extend_from_slice(&decrypted);

        let mut parts: Vec<Vec<u8>> = Vec::new();
        while !self.cipher_buf.is_empty() {
            let pkt_len = self.next_packet_len();
            if pkt_len < 0 {
                break;
            }
            if pkt_len == 0 {
                parts.push(self.cipher_buf.clone());
                self.cipher_buf.clear();
                self.plain_buf.clear();
                self.disabled = true;
                break;
            }
            let pkt_len = pkt_len as usize;
            if self.cipher_buf.len() < pkt_len {
                break;
            }
            parts.push(self.cipher_buf[..pkt_len].to_vec());
            self.cipher_buf.drain(..pkt_len);
            self.plain_buf.drain(..pkt_len);
        }

        if self.cipher_buf.is_empty() {
            self.cipher_buf.clear();
            self.plain_buf.clear();
        }
        parts
    }

    pub fn flush(&mut self) -> Vec<Vec<u8>> {
        if self.cipher_buf.is_empty() {
            return Vec::new();
        }
        let tail = self.cipher_buf.clone();
        self.cipher_buf.clear();
        self.plain_buf.clear();
        vec![tail]
    }

    fn next_packet_len(&self) -> i64 {
        if self.plain_buf.is_empty() {
            return -1;
        }
        match self.proto_type {
            PROTO_ABRIDGED => {
                let first = self.plain_buf[0] & 0x7F;
                let header_len;
                let payload_len;
                if first == 0x7F {
                    if self.plain_buf.len() < 4 {
                        return -1;
                    }
                    payload_len = ((self.plain_buf[1] as i64)
                        | ((self.plain_buf[2] as i64) << 8)
                        | ((self.plain_buf[3] as i64) << 16))
                        * 4;
                    header_len = 4;
                } else {
                    payload_len = (first as i64) * 4;
                    header_len = 1;
                }
                if payload_len <= 0 {
                    return 0;
                }
                let pkt_len = header_len + payload_len;
                if (self.plain_buf.len() as i64) < pkt_len {
                    return -1;
                }
                pkt_len
            }
            PROTO_INTERMEDIATE | PROTO_PADDED_INTERMEDIATE => {
                if self.plain_buf.len() < 4 {
                    return -1;
                }
                let payload_len =
                    (LittleEndian::read_u32(&self.plain_buf[..4]) & 0x7FFFFFFF) as i64;
                if payload_len <= 0 {
                    return 0;
                }
                let pkt_len = 4 + payload_len;
                if (self.plain_buf.len() as i64) < pkt_len {
                    return -1;
                }
                pkt_len
            }
            _ => 0,
        }
    }
}

// ---------------------------------------------------------------------------
// XOR mask (websocket frame masking) — оптимизированный вариант
// ---------------------------------------------------------------------------

pub fn xor_mask_in_place(data: &mut [u8], mask: &[u8]) {
    let n = data.len();
    if n == 0 {
        return;
    }
    let mask8: u64 = (mask[0] as u64)
        | ((mask[1] as u64) << 8)
        | ((mask[2] as u64) << 16)
        | ((mask[3] as u64) << 24)
        | ((mask[0] as u64) << 32)
        | ((mask[1] as u64) << 40)
        | ((mask[2] as u64) << 48)
        | ((mask[3] as u64) << 56);

    let mut i = 0;
    while i + 8 <= n {
        let v = LittleEndian::read_u64(&data[i..]);
        LittleEndian::write_u64(&mut data[i..], v ^ mask8);
        i += 8;
    }
    while i < n {
        data[i] ^= mask[i & 3];
        i += 1;
    }
}

#[allow(dead_code)]
pub fn write_be_u16(buf: &mut [u8], v: u16) {
    BigEndian::write_u16(buf, v);
}
#[allow(dead_code)]
pub fn write_be_u64(buf: &mut [u8], v: u64) {
    BigEndian::write_u64(buf, v);
}