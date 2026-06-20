use std::sync::atomic::{AtomicU32, Ordering};
use crate::storage::StorageManager;

pub struct AnalyticsManager {
    pub keystrokes_saved: AtomicU32,
    pub methods_won_topic: AtomicU32,
    pub methods_won_temporal: AtomicU32,
    pub methods_won_fuzzy: AtomicU32,
}

impl Default for AnalyticsManager {
    fn default() -> Self {
        Self::new()
    }
}

impl AnalyticsManager {
    pub fn new() -> Self {
        Self {
            keystrokes_saved: AtomicU32::new(0),
            methods_won_topic: AtomicU32::new(0),
            methods_won_temporal: AtomicU32::new(0),
            methods_won_fuzzy: AtomicU32::new(0),
        }
    }

    pub fn load(&self, storage: &Option<StorageManager>) {
        if let Some(mgr) = storage {
            if let Ok(tree) = mgr.db.open_tree(b"analytics") {
                if let Ok(Some(val)) = tree.get(b"keystrokes_saved") {
                    let mut bytes = [0u8; 4];
                    bytes.copy_from_slice(&val);
                    self.keystrokes_saved.store(u32::from_be_bytes(bytes), Ordering::SeqCst);
                }
                if let Ok(Some(val)) = tree.get(b"methods_won_topic") {
                    let mut bytes = [0u8; 4];
                    bytes.copy_from_slice(&val);
                    self.methods_won_topic.store(u32::from_be_bytes(bytes), Ordering::SeqCst);
                }
                if let Ok(Some(val)) = tree.get(b"methods_won_temporal") {
                    let mut bytes = [0u8; 4];
                    bytes.copy_from_slice(&val);
                    self.methods_won_temporal.store(u32::from_be_bytes(bytes), Ordering::SeqCst);
                }
                if let Ok(Some(val)) = tree.get(b"methods_won_fuzzy") {
                    let mut bytes = [0u8; 4];
                    bytes.copy_from_slice(&val);
                    self.methods_won_fuzzy.store(u32::from_be_bytes(bytes), Ordering::SeqCst);
                }
            }
        }
    }

    pub fn flush(&self, storage: &Option<StorageManager>) {
        if let Some(mgr) = storage {
            if let Ok(tree) = mgr.db.open_tree(b"analytics") {
                let _ = tree.insert(b"keystrokes_saved", &self.keystrokes_saved.load(Ordering::SeqCst).to_be_bytes());
                let _ = tree.insert(b"methods_won_topic", &self.methods_won_topic.load(Ordering::SeqCst).to_be_bytes());
                let _ = tree.insert(b"methods_won_temporal", &self.methods_won_temporal.load(Ordering::SeqCst).to_be_bytes());
                let _ = tree.insert(b"methods_won_fuzzy", &self.methods_won_fuzzy.load(Ordering::SeqCst).to_be_bytes());
                let _ = tree.flush();
            }
        }
    }

    pub fn increment_keystrokes(&self, count: u32) {
        self.keystrokes_saved.fetch_add(count, Ordering::SeqCst);
    }

    pub fn increment_method(&self, method: &str) {
        match method {
            "topic" => { self.methods_won_topic.fetch_add(1, Ordering::SeqCst); }
            "temporal" => { self.methods_won_temporal.fetch_add(1, Ordering::SeqCst); }
            _ => { self.methods_won_fuzzy.fetch_add(1, Ordering::SeqCst); }
        };
    }
}
