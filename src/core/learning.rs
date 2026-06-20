use crate::core::KeyboardEngine;
use crate::storage::UserDataDelta;

impl KeyboardEngine {
    /// Insere uma palavra na Trie estática.
    pub fn insert_static(&mut self, word: &str, base_frequency: u32) {
        let mut node = &mut self.static_root;
        for ch in word.chars() {
            node = node.children.entry(ch).or_default();
        }
        node.is_terminal = true;
        node.base_frequency = base_frequency;
    }

    pub fn learn_word(&self, word: &str, context: &[String]) {
        self.learn_word_with_boost(word, context, 1);
    }

    /// Insere ou atualiza uma palavra na Trie do usuário e persiste com um multiplicador de frequência.
    pub fn learn_word_with_boost(&self, word: &str, context: &[String], boost: u32) {
        if self.privacy_manager.is_incognito() {
            log::trace!("Security: Ignorando aprendizado de '{}' devido ao Modo Incógnito.", word);
            return;
        }

        if boost > 1 {
            log::info!("Core: 🚀 Active Reinforcement Learning acionado! Palavra '{}' recebeu boost maciço de +{}", word, boost);
        } else {
            log::debug!("Core: Aprendendo palavra '{}' com contexto {:?} e boost {}", word, context, boost);
        }
        self.learn_word_internal(word, boost);

        // Atualiza a recência da palavra
        if let Ok(mut recency) = self.recency.write() {
            recency.use_word(word.to_string());
        }

        // Aprende e incrementa frases na memória
        if let Ok(mut phrases_map) = self.phrase_memory.write() {
            // Bigrama: Contexto: "anterior" -> Alvo: "word"
            if let Some(prev) = context.last() {
                let context_key = prev.clone();
                log::debug!("Core: Learning bigram (phrase) association: '{}' -> '{}'", context_key, word);
                let entry = phrases_map.entry(context_key).or_default();
                if let Some(pair) = entry.iter_mut().find(|p| p.0 == word) {
                    pair.1 += boost;
                } else {
                    entry.push((word.to_string(), boost));
                }
            }
            // Trigrama: Contexto: "antepenultimo anterior" -> Alvo: "word"
            if context.len() >= 2 {
                let context_key = format!("{} {}", context[context.len()-2], context[context.len()-1]);
                log::debug!("Core: Learning trigram (phrase) association: '{}' -> '{}'", context_key, word);
                let entry = phrases_map.entry(context_key).or_default();
                if let Some(pair) = entry.iter_mut().find(|p| p.0 == word) {
                    pair.1 += boost;
                } else {
                    entry.push((word.to_string(), boost));
                }
            }

            // Contexto de Domínio: Armazena frequência da palavra específica para o app atual
            if let Ok(domain) = self.current_domain.read() {
                if !domain.is_empty() {
                    let context_key = format!("__domain__:{}", domain);
                    log::debug!("Core: Learning domain association for '{}' in {}", word, domain);
                    let entry = phrases_map.entry(context_key).or_default();
                    if let Some(pair) = entry.iter_mut().find(|p| p.0 == word) {
                        pair.1 += boost;
                    } else {
                        entry.push((word.to_string(), boost));
                    }
                }
            }

            // Contexto Temporal: Armazena frequência da palavra para o momento do dia (ex: WEEKDAY_MORNING)
            if let Ok(temporal) = self.current_temporal_context.read() {
                if !temporal.is_empty() {
                    let context_key = format!("__time__:{}", temporal);
                    log::debug!("Core: Learning temporal association for '{}' in {}", word, temporal);
                    let entry = phrases_map.entry(context_key).or_default();
                    if let Some(pair) = entry.iter_mut().find(|p| p.0 == word) {
                        pair.1 += 1;
                    } else {
                        entry.push((word.to_string(), 1));
                    }
                }
            }

            // Contexto Semântico: Associa a palavra aos temas (tópicos) atuais da conversa
            if let Ok(topics) = self.current_semantic_topics.read() {
                for topic in topics.iter() {
                    if topic != word {
                        let context_key = format!("__topic__:{}", topic);
                        log::trace!("Core: Learning semantic topic association for '{}' in {}", word, topic);
                        let entry = phrases_map.entry(context_key).or_default();
                        if let Some(pair) = entry.iter_mut().find(|p| p.0 == word) {
                            pair.1 += boost;
                        } else {
                            entry.push((word.to_string(), boost));
                        }
                    }
                }
            }
        }

        if let Some(ref mgr) = self.storage {
            let delta = UserDataDelta {
                word: word.to_string(),
                frequency: boost, // Frequência acumulada será tratada no futuro ou simplificada aqui
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
        if self.privacy_manager.is_incognito() {
            return;
        }

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
}
