use std::sync::atomic::{AtomicBool, Ordering};

pub struct PrivacyManager {
    is_incognito: AtomicBool,
}

impl Default for PrivacyManager {
    fn default() -> Self {
        Self::new()
    }
}

impl PrivacyManager {
    pub fn new() -> Self {
        Self {
            is_incognito: AtomicBool::new(false),
        }
    }

    pub fn set_incognito(&self, incognito: bool) {
        self.is_incognito.store(incognito, Ordering::SeqCst);
        if incognito {
            log::info!("Security: 👻 Modo Incógnito ativado. Aprendizado congelado.");
        } else {
            log::info!("Security: 🟢 Modo Incógnito desativado. Aprendizado retomado.");
        }
    }

    pub fn is_incognito(&self) -> bool {
        self.is_incognito.load(Ordering::SeqCst)
    }
}
