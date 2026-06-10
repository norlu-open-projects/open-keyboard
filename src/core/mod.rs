//! Orquestração do Motor de Teclado (Core).
//! Gerencia o ciclo de vida, persistência e integração entre as Tries e o Storage.

pub mod undo;

use std::collections::HashMap;
use std::sync::RwLock;
use std::path::Path;
use crate::search::scoring::{ScoringEngine, RecencyCache};
use crate::trie::TrieNode;
use crate::dictionary::load_default_dictionary;
use crate::storage::{StorageManager, UserDataDelta};
use crate::core::undo::UndoManager;

pub struct KeyboardEngine {
    pub static_root: TrieNode,
    pub user_root: RwLock<TrieNode>,
    pub scoring: ScoringEngine,
    pub recency: RecencyCache,
    /// Mapa de frases estáticas: Contexto (ex: "a dona") -> Lista de (Próxima Palavra, Frequência)
    pub static_phrases: HashMap<Box<str>, Vec<(Box<str>, u32)>>,
    /// Memória de frases do usuário: Mesma estrutura que static_phrases
    pub phrase_memory: RwLock<HashMap<String, Vec<(String, u32)>>>,
    pub storage: Option<StorageManager>,
    pub undo: UndoManager,
}

impl KeyboardEngine {
    pub fn new() -> Self {
        log::debug!("Core: Inicializando motor básico (sem storage)");
        Self {
            static_root: TrieNode::default(),
            user_root: RwLock::new(TrieNode::default()),
            scoring: ScoringEngine::default(),
            recency: RecencyCache::new(1000),
            static_phrases: HashMap::new(),
            phrase_memory: RwLock::new(HashMap::new()),
            storage: None,
            undo: UndoManager::new([0u8; 32]),
        }
    }

    pub fn new_with_storage<P: AsRef<Path>>(path: P, key: [u8; 32]) -> Self {
        log::debug!("Core: Inicializando motor com storage em {:?}", path.as_ref());
        
        // Deriva uma chave de sessão para o UndoManager a partir da chave mestra (SHA256 simplificado aqui ou apenas XOR)
        // Para este projeto, usaremos a chave direta para encriptação em RAM, já que ela é segura do Keystore.
        let mut undo_key = key;
        for i in 0..32 { undo_key[i] ^= 0xAA; } // Ofuscação simples para diferenciar do storage

        let storage = StorageManager::new(path, key).ok();
        let engine = Self {
            static_root: TrieNode::default(),
            user_root: RwLock::new(TrieNode::default()),
            scoring: ScoringEngine::default(),
            recency: RecencyCache::new(1000),
            static_phrases: HashMap::new(),
            phrase_memory: RwLock::new(HashMap::new()),
            storage,
            undo: UndoManager::new(undo_key),
        };

        let mut engine = engine;
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

    /// Insere uma palavra na Trie estática.
    pub fn insert_static(&mut self, word: &str, base_frequency: u32) {
        let mut node = &mut self.static_root;
        for ch in word.chars() {
            node = node.children.entry(ch).or_default();
        }
        node.is_terminal = true;
        node.base_frequency = base_frequency;
    }

    /// Insere ou atualiza uma palavra na Trie do usuário e persiste se o storage estiver ativo.
    pub fn learn_word(&self, word: &str, context: &[String]) {
        log::debug!("Core: Aprendendo palavra '{}' com contexto {:?}", word, context);
        self.learn_word_internal(word, 1);

        // Aprende e incrementa frases na memória
        if let Ok(mut phrases_map) = self.phrase_memory.write() {
            // Bigrama: Contexto: "anterior" -> Alvo: "word"
            if let Some(prev) = context.last() {
                let context_key = prev.clone();
                let entry = phrases_map.entry(context_key).or_default();
                if let Some(pair) = entry.iter_mut().find(|p| p.0 == word) {
                    pair.1 += 1;
                } else {
                    entry.push((word.to_string(), 1));
                }
            }
            // Trigrama: Contexto: "antepenultimo anterior" -> Alvo: "word"
            if context.len() >= 2 {
                let context_key = format!("{} {}", context[context.len()-2], context[context.len()-1]);
                let entry = phrases_map.entry(context_key).or_default();
                if let Some(pair) = entry.iter_mut().find(|p| p.0 == word) {
                    pair.1 += 1;
                } else {
                    entry.push((word.to_string(), 1));
                }
            }
        }

        if let Some(ref mgr) = self.storage {
            let delta = UserDataDelta {
                word: word.to_string(),
                frequency: 1, // Frequência acumulada será tratada no futuro ou simplificada aqui
                last_used: std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_secs(),
            };
            if let Err(e) = mgr.save_delta(&delta) {
                log::error!("Core: Falha ao persistir palavra '{}': {:?}", word, e);
            }
        }
    }

    pub fn learn_word_internal(&self, word: &str, frequency: u32) {
        if let Ok(mut node) = self.user_root.write() {
            let mut current = &mut *node;
            for ch in word.chars() {
                current = current.children.entry(ch).or_default();
            }
            current.is_terminal = true;
            current.base_frequency = frequency;
        } else {
            log::error!("Core: Falha ao obter lock de escrita na user_trie para aprender '{}'", word);
        }
    }

    /// Realiza a manutenção da User Trie, removendo palavras com baixa relevância (Entropia Controlada).
    pub fn cleanup_user_trie(&self, survival_threshold: f64) {
        log::debug!("Core: Iniciando manutenção da User Trie (threshold={})", survival_threshold);
        let mut words_to_remove = Vec::new();

        // 1. Identifica palavras obsoletas baseado no score atual
        {
            if let Ok(user_root) = self.user_root.read() {
                self.identify_obsolete_words(&user_root, &mut String::new(), survival_threshold, &mut words_to_remove);
            }
        }

        // 2. Remove as palavras identificadas
        if !words_to_remove.is_empty() {
            log::debug!("Core: Removendo {} palavras obsoletas", words_to_remove.len());
            if let Ok(mut user_root) = self.user_root.write() {
                for word in words_to_remove {
                    self.remove_word_from_trie(&mut user_root, &word);
                    if let Some(ref mgr) = self.storage {
                        let _ = mgr.remove(&word);
                    }
                }
            }
        }
    }

    fn identify_obsolete_words(
        &self,
        node: &TrieNode,
        current_word: &mut String,
        threshold: f64,
        to_remove: &mut Vec<String>,
    ) {
        if node.is_terminal {
            let last_used = self.recency.get_last_used(current_word);
            let score = self.scoring.calculate_score(node.base_frequency, last_used, 0);
            
            if score < threshold {
                to_remove.push(current_word.clone());
            }
        }

        for (ch, child) in &node.children {
            current_word.push(*ch);
            self.identify_obsolete_words(child, current_word, threshold, to_remove);
            current_word.pop();
        }
    }

    fn remove_word_from_trie(&self, node: &mut TrieNode, word: &str) -> bool {
        let mut chars = word.chars();
        if let Some(ch) = chars.next() {
            let remaining = chars.as_str();
            let should_remove_child = if let Some(child) = node.children.get_mut(&ch) {
                if self.remove_word_from_trie(child, remaining) {
                    node.children.remove(&ch);
                    true
                } else {
                    false
                }
            } else {
                false
            };

            // Se o filho foi removido, verificamos se este nó também ficou órfão e inútil
            if should_remove_child {
                return !node.is_terminal && node.children.is_empty();
            }
        } else {
            // Chegamos ao final da palavra
            node.is_terminal = false;
            node.base_frequency = 0;
            // Se não tem filhos, pode ser removido pelo pai
            return node.children.is_empty();
        }
        false
    }
}
