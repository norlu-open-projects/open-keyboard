//! Orquestração do Motor de Teclado (Core).
//! Gerencia o ciclo de vida, persistência e integração entre as Tries e o Storage.

pub mod undo;
pub mod learning;
pub mod maintenance;
pub mod semantic;
pub mod security;
pub mod analytics;

use std::collections::HashMap;
use std::sync::RwLock;
use std::path::Path;
use crate::search::scoring::{ScoringEngine, RecencyCache};
use crate::trie::TrieNode;
use crate::dictionary::load_default_dictionary;
use crate::storage::StorageManager;
use crate::core::undo::UndoManager;
use crate::core::security::PrivacyManager;
use crate::core::analytics::AnalyticsManager;

pub struct KeyboardEngine {
    pub static_root: TrieNode,
    pub user_root: RwLock<TrieNode>,
    pub scoring: ScoringEngine,
    pub recency: RwLock<RecencyCache>,
    /// Mapa de frases estáticas: Contexto (ex: "a dona") -> Lista de (Próxima Palavra, Frequência)
    pub static_phrases: HashMap<Box<str>, Vec<(Box<str>, u32)>>,
    /// Memória de frases do usuário: Mesma estrutura que static_phrases
    pub phrase_memory: RwLock<HashMap<String, Vec<(String, u32)>>>,
    pub current_domain: RwLock<String>,
    pub current_temporal_context: RwLock<String>,
    pub current_semantic_topics: RwLock<Vec<String>>,
    pub storage: Option<StorageManager>,
    pub undo: UndoManager,
    pub privacy_manager: PrivacyManager,
    pub analytics: AnalyticsManager,
}

impl KeyboardEngine {
    pub fn new() -> Self {
        log::debug!("Core: Inicializando motor básico (sem storage)");
        let mut engine = Self {
            static_root: TrieNode::default(),
            user_root: RwLock::new(TrieNode::default()),
            scoring: ScoringEngine::default(),
            recency: RwLock::new(RecencyCache::new(1000)),
            static_phrases: HashMap::new(),
            phrase_memory: RwLock::new(HashMap::new()),
            current_domain: RwLock::new(String::new()),
            current_temporal_context: RwLock::new(String::new()),
            current_semantic_topics: RwLock::new(Vec::new()),
            storage: None,
            undo: UndoManager::new([0u8; 32]),
            privacy_manager: PrivacyManager::new(),
            analytics: AnalyticsManager::new(),
        };
        
        load_default_dictionary(&mut engine);
        engine
    }

    pub fn new_empty() -> Self {
        log::debug!("Core: Inicializando motor vazio para testes");
        Self {
            static_root: TrieNode::default(),
            user_root: RwLock::new(TrieNode::default()),
            scoring: ScoringEngine::default(),
            recency: RwLock::new(RecencyCache::new(1000)),
            static_phrases: HashMap::new(),
            phrase_memory: RwLock::new(HashMap::new()),
            current_domain: RwLock::new(String::new()),
            current_temporal_context: RwLock::new(String::new()),
            current_semantic_topics: RwLock::new(Vec::new()),
            storage: None,
            undo: UndoManager::new([0u8; 32]),
            privacy_manager: PrivacyManager::new(),
            analytics: AnalyticsManager::new(),
        }
    }

    pub fn new_with_storage<P: AsRef<Path>>(path: P, key: [u8; 32]) -> Self {
        log::debug!("Core: Inicializando motor com storage em {:?}", path.as_ref());

        let mut undo_key = key;
        for i in 0..32 { undo_key[i] ^= 0xAA; }

        let mut engine = Self {
            static_root: TrieNode::default(),
            user_root: RwLock::new(TrieNode::default()),
            scoring: ScoringEngine::default(),
            recency: RwLock::new(RecencyCache::new(1000)),
            static_phrases: HashMap::new(),
            phrase_memory: RwLock::new(HashMap::new()),
            current_domain: RwLock::new(String::new()),
            current_temporal_context: RwLock::new(String::new()),
            current_semantic_topics: RwLock::new(Vec::new()),
            storage: StorageManager::new(path, key).ok(),
            undo: UndoManager::new(undo_key),
            privacy_manager: PrivacyManager::new(),
            analytics: AnalyticsManager::new(),
        };

        engine.analytics.load(&engine.storage);

        // Carrega o dicionário base (PT-BR)
        load_default_dictionary(&mut engine);

        // Carrega deltas salvos se o storage estiver disponível
        if let Some(ref mgr) = engine.storage {
            if let Ok(deltas) = mgr.load_all_deltas() {
                log::debug!("Core: Carregando {} palavras do storage", deltas.len());
                for delta in deltas {
                    engine.learn_word_internal(&delta.word, delta.frequency);
                }
            }
        }

        engine
    }

    pub fn set_domain(&self, domain: &str) {
        if let Ok(mut current) = self.current_domain.write() {
            *current = domain.to_string();
            log::debug!("Core: Domínio definido para: {}", domain);
        }
        // Força flush do storage para evitar handles abertos por muito tempo
        self.flush();
    }

    pub fn set_temporal_context(&self, context: &str) {
        if let Ok(mut current) = self.current_temporal_context.write() {
            *current = context.to_string();
            log::debug!("Core: Contexto temporal definido para: {}", context);
        }
    }

    /// Garante que todos os dados pendentes sejam gravados no disco.
    pub fn flush(&self) {
        if let Some(ref mgr) = self.storage {
            log::info!("Core: Sincronizando banco de dados com o disco (Flush)...");
            self.analytics.flush(&self.storage);
            let _ = mgr.flush();
        }
    }
}
