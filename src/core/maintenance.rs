use crate::core::KeyboardEngine;
use crate::trie::TrieNode;

impl KeyboardEngine {
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
            let last_used = if let Ok(recency) = self.recency.read() {
                recency.get_last_used(current_word)
            } else {
                None
            };
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
