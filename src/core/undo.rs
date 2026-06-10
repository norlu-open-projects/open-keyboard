use std::collections::VecDeque;
use std::sync::RwLock;
use std::sync::atomic::{AtomicU64, Ordering};
use chacha20poly1305::{aead::{Aead, KeyInit}, ChaCha20Poly1305, Nonce};
use zeroize::Zeroize;

static NONCE_COUNTER: AtomicU64 = AtomicU64::new(0);

pub struct UndoManager {
    /// Pilha LIFO de palavras criptografadas. Cada item é Nonce(12 bytes) + Ciphertext
    stack: RwLock<VecDeque<Vec<u8>>>,
    /// Chave de sessão para criptografia em RAM (efêmera)
    session_key: [u8; 32],
}

impl UndoManager {
    pub fn new(key: [u8; 32]) -> Self {
        Self {
            stack: RwLock::new(VecDeque::with_capacity(50)),
            session_key: key,
        }
    }

    /// Encripta e adiciona uma palavra à pilha.
    pub fn push(&self, word: &str) {
        if word.is_empty() { return; }
        log::debug!("Undo: Pushing word '{}' to session stack", word);

        let cipher = ChaCha20Poly1305::new(&self.session_key.into());
        
        // Gera nonce incremental
        let counter = NONCE_COUNTER.fetch_add(1, Ordering::SeqCst);
        let mut n_bytes = [0u8; 12];
        n_bytes[0..8].copy_from_slice(&counter.to_le_bytes());
        let nonce = Nonce::from_slice(&n_bytes);

        if let Ok(ciphertext) = cipher.encrypt(nonce, word.as_bytes()) {
            let mut stored = Vec::with_capacity(12 + ciphertext.len());
            stored.extend_from_slice(&n_bytes);
            stored.extend_from_slice(&ciphertext);

            if let Ok(mut stack) = self.stack.write() {
                if stack.len() >= 50 {
                    if let Some(mut old) = stack.pop_front() {
                        old.zeroize();
                    }
                }
                stack.push_back(stored);
            }
        }
    }

    /// Remove e desencripta a última palavra da pilha.
    pub fn pop(&self) -> Option<String> {
        let mut stored = {
            let mut stack = self.stack.write().ok()?;
            stack.pop_back()?
        };

        if stored.len() < 12 { return None; }

        let n_bytes = &stored[0..12];
        let ciphertext = &stored[12..];
        let nonce = Nonce::from_slice(n_bytes);
        let cipher = ChaCha20Poly1305::new(&self.session_key.into());

        let result = if let Ok(plaintext) = cipher.decrypt(nonce, ciphertext) {
            String::from_utf8(plaintext).ok()
        } else {
            None
        };

        if let Some(ref w) = result {
            log::debug!("Undo: Popped word '{}' from session stack", w);
        }

        stored.zeroize();
        result
    }

    /// Verifica se há itens na pilha.
    pub fn has_items(&self) -> bool {
        self.stack.read().map(|s| !s.is_empty()).unwrap_or(false)
    }

    /// Limpa toda a pilha e aplica Zeroize.
    pub fn clear(&self) {
        if let Ok(mut stack) = self.stack.write() {
            for mut item in stack.drain(..) {
                item.zeroize();
            }
        }
    }
}

impl Drop for UndoManager {
    fn drop(&mut self) {
        self.clear();
        self.session_key.zeroize();
    }
}
