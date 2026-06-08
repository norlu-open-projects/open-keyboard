//! Orquestração de Busca de Alto Nível.
//! Atua como o ponto de entrada para previsões e hitboxes, 
//! coordenando o uso de algoritmos fuzzy e modelos de scoring.

use std::collections::HashMap;
use crate::core::KeyboardEngine;

pub mod fuzzy;
pub mod scoring;
pub mod nwp;

impl KeyboardEngine {
    /// Busca candidatos em ambas as Tries (estática e usuário) e calcula o score final.
    pub fn search_smart(
        &self,
        word: &str,
        context: &[String],
        max_distance: usize,
    ) -> Vec<(String, f64, usize, usize)> {
        log::debug!("Search: Orquestrando busca para '{}' (contexto={:?}, dist={})", word, context, max_distance);
        
        // Candidatos mapeados: Palavra -> (Distância, Frequência Base, Nível de Match Contextual)
        let mut candidates: HashMap<String, (usize, u32, usize)> = HashMap::new();

        if word.is_empty() {
            nwp::predict_next_word(self, context, &mut candidates);
        } else {
            // --- WORD COMPLETION & FUZZY SEARCH ---
            use crate::search::fuzzy::{search_prefix, search_recursive};
            
            let mut raw_candidates = Vec::new();
            let current_row: Vec<usize> = (0..=word.len()).collect();

            // 1. Busca por Prefixo (Completar) - Alta prioridade
            search_prefix(&self.static_root, word, &mut raw_candidates);
            if let Ok(user_trie) = self.user_root.read() {
                search_prefix(&user_trie, word, &mut raw_candidates);
            }

            // 2. Busca Fuzzy (Correção)
            for (ch, child) in &self.static_root.children {
                search_recursive(child, *ch, word, &current_row, &mut String::new(), max_distance, &mut raw_candidates);
            }
            if let Ok(user_trie) = self.user_root.read() {
                for (ch, child) in &user_trie.children {
                    search_recursive(child, *ch, word, &current_row, &mut String::new(), max_distance, &mut raw_candidates);
                }
            }

            for (cand, dist, freq) in raw_candidates {
                let entry = candidates.entry(cand.clone()).or_insert((dist, 0, 0));
                entry.1 = entry.1.max(freq);
                entry.0 = entry.0.min(dist);

                // Check context match for this candidate (Static + User Memory)
                if !context.is_empty() {
                    let user_phrases = self.phrase_memory.read().ok();

                    if context.len() >= 2 {
                        let trigram_key = format!("{} {}", context[context.len()-2], context[context.len()-1]);
                        let match_static = self.static_phrases.get(trigram_key.as_str()).map_or(false, |l| l.iter().any(|p| p.0.as_ref() == cand));
                        let match_user = user_phrases.as_ref().map_or(false, |up| up.get(&trigram_key).map_or(false, |l| l.iter().any(|p| p.0 == cand)));
                        
                        if match_static || match_user {
                            entry.2 = entry.2.max(3);
                        }
                    }
                    if entry.2 < 2 {
                        let bigram_key = context[context.len()-1].clone();
                        let match_static = self.static_phrases.get(bigram_key.as_str()).map_or(false, |l| l.iter().any(|p| p.0.as_ref() == cand));
                        let match_user = user_phrases.as_ref().map_or(false, |up| up.get(&bigram_key).map_or(false, |l| l.iter().any(|p| p.0 == cand)));
                        
                        if match_static || match_user {
                            entry.2 = entry.2.max(2);
                        }
                    }
                }
            }
        }

        // --- SCORING & FINAL RANKING ---
        let mut results = Vec::new();
        for (candidate, (distance, base_freq, match_level)) in candidates {
            let last_used = self.recency.get_last_used(&candidate);
            let score = self.scoring.calculate_score(base_freq, last_used, match_level);
            
            // Penaliza distância no score final para predição de palavras (não NWP)
            // BÔNUS DE COMPLETION: Se a distância é 0, damos 2x mais relevância
            let adjusted_score = if !word.is_empty() {
                if distance == 0 {
                    score * 2.0
                } else {
                    score / (distance + 1) as f64
                }
            } else {
                score
            };

            results.push((candidate, adjusted_score, distance, match_level));
        }

        // Ordenação e limite para evitar OOM e latência na JNI
        results.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
        results.truncate(20); 

        log::debug!("Search: Retornando {} resultados", results.len());
        results
    }

    /// Calcula a probabilidade dos próximos caracteres possíveis dado um prefixo.
    /// Útil para 'Dynamic Hitboxes' (WCAG AAA).
    pub fn get_next_char_probabilities(&self, prefix: &str) -> HashMap<char, f64> {
        log::debug!("Search: Calculando probabilidades de hitboxes para '{}'", prefix);
        let mut char_counts = HashMap::new();

        // 1. Busca na Trie estática
        let mut node = &self.static_root;
        let mut found_static = true;
        for ch in prefix.chars() {
            if let Some(child) = node.children.get(&ch) {
                node = child;
            } else {
                found_static = false;
                break;
            }
        }
        if found_static {
            for (ch, child) in &node.children {
                *char_counts.entry(*ch).or_insert(0u32) += child.base_frequency.max(1);
            }
        }

        // 2. Busca na Trie do usuário
        if let Ok(user_trie) = self.user_root.read() {
            let mut u_node = &*user_trie;
            let mut found_user = true;
            for ch in prefix.chars() {
                if let Some(child) = u_node.children.get(&ch) {
                    u_node = child;
                } else {
                    found_user = false;
                    break;
                }
            }
            if found_user {
                for (ch, child) in &u_node.children {
                    *char_counts.entry(*ch).or_insert(0u32) += child.base_frequency.max(1);
                }
            }
        }

        // Calcula probabilidades finais
        let total_freq: u32 = char_counts.values().sum();
        let mut probs = HashMap::new();
        if total_freq > 0 {
            for (ch, freq) in char_counts {
                probs.insert(ch, (freq as f64) / (total_freq as f64));
            }
        }

        probs
    }
}
