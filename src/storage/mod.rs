//! Camada de Persistência Segura (Storage).
//! Gerencia o armazenamento criptografado do vocabulário do usuário
//! utilizando Sled (DB) e ChaCha20Poly1305 (Criptografia).

use sled::Db;
use serde::{Serialize, Deserialize};
use std::path::Path;
use chacha20poly1305::{
    aead::{Aead, KeyInit},
    ChaCha20Poly1305, Nonce
};
use zeroize::Zeroize;

#[derive(Serialize, Deserialize, Debug, Clone, Zeroize)]
#[zeroize(drop)]
pub struct UserDataDelta {
    pub word: String,
    pub frequency: u32,
    pub last_used: u64,
}

pub struct StorageManager {
    db: Db,
    // Chave de criptografia mantida em RAM (será zeroizada no drop)
    cipher_key: [u8; 32],
}

impl StorageManager {
    pub fn new<P: AsRef<Path>>(path: P, key: [u8; 32]) -> Result<Self, Box<dyn std::error::Error>> {
        let db = sled::Config::new()
            .path(path)
            .cache_capacity(16 * 1024 * 1024) // 16MB de cache para mobile
            .use_compression(true)
            .open()?;
        Ok(Self { db, cipher_key: key })
    }

    pub fn save_delta(&self, delta: &UserDataDelta) -> Result<(), Box<dyn std::error::Error>> {
        let encoded = bincode::serialize(delta)
            .map_err(|e| format!("Serialization error: {}", e))?;
            
        let compressed = zstd::encode_all(&encoded[..], 3)
            .map_err(|e| format!("Compression error: {}", e))?;
        
        // Criptografia ChaCha20Poly1305
        let cipher = ChaCha20Poly1305::new(&self.cipher_key.into());
        let nonce = Nonce::from_slice(b"unique_nonce"); 
        
        let ciphertext = cipher.encrypt(nonce, compressed.as_ref())
            .map_err(|e| format!("Encryption failure: {:?}", e))?;

        self.db.insert(&delta.word, ciphertext)
            .map_err(|e| format!("Database insert error: {}", e))?;
            
        Ok(())
    }

    pub fn load_all_deltas(&self) -> Result<Vec<UserDataDelta>, Box<dyn std::error::Error>> {
        let mut deltas = Vec::new();
        let cipher = ChaCha20Poly1305::new(&self.cipher_key.into());
        let nonce = Nonce::from_slice(b"unique_nonce");

        for item in self.db.iter() {
            let (_, value) = item?;
            
            // Descriptografia
            let decrypted = cipher.decrypt(nonce, value.as_ref())
                .map_err(|_| "Decryption failure")?;

            let decompressed = zstd::decode_all(&decrypted[..])?;
            let delta: UserDataDelta = bincode::deserialize(&decompressed)?;
            deltas.push(delta);
        }
        Ok(deltas)
    }

    pub fn remove(&self, word: &str) -> Result<(), Box<dyn std::error::Error>> {
        self.db.remove(word)?;
        Ok(())
    }

    pub fn flush(&self) -> Result<(), Box<dyn std::error::Error>> {
        self.db.flush()?;
        Ok(())
    }

    pub fn clear(&self) -> Result<(), Box<dyn std::error::Error>> {
        self.db.clear()?;
        Ok(())
    }
}

impl Drop for StorageManager {
    fn drop(&mut self) {
        self.cipher_key.zeroize();
    }
}
