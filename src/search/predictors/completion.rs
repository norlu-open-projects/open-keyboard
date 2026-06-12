use std::collections::HashMap;
use crate::core::KeyboardEngine;
use crate::search::fuzzy::{search_prefix, search_recursive};

/// Predicts the completion of a word being currently typed.
/// Evaluates fuzzy search and maps context matches.
pub fn predict_completion(
    engine: &KeyboardEngine,
    word: &str,
    context: &[String],
    max_distance: usize,
    candidates: &mut HashMap<String, (usize, u32, usize)>,
) {
    let mut raw_candidates = Vec::new();
    let current_row: Vec<usize> = (0..=word.len()).collect();

    // 1. Busca por Prefixo (Completar) - Alta prioridade
    search_prefix(&engine.static_root, word, &mut raw_candidates);
    if let Ok(user_trie) = engine.user_root.read() {
        search_prefix(&user_trie, word, &mut raw_candidates);
    }

    // 2. Busca Fuzzy (Correção)
    for (ch, child) in &engine.static_root.children {
        search_recursive(child, *ch, word, &current_row, &mut String::new(), max_distance, &mut raw_candidates);
    }
    if let Ok(user_trie) = engine.user_root.read() {
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
            let user_phrases = engine.phrase_memory.read().ok();

            if context.len() >= 2 {
                let trigram_key = format!("{} {}", context[context.len()-2], context[context.len()-1]);
                let match_static = engine.static_phrases.get(trigram_key.as_str()).map_or(false, |l| l.iter().any(|p| p.0.as_ref() == cand));
                let match_user = user_phrases.as_ref().map_or(false, |up| up.get(&trigram_key).map_or(false, |l| l.iter().any(|p| p.0 == cand)));
                
                if match_static || match_user {
                    entry.2 = entry.2.max(3);
                }
            }
            if entry.2 < 2 {
                let bigram_key = context[context.len()-1].clone();
                let match_static = engine.static_phrases.get(bigram_key.as_str()).map_or(false, |l| l.iter().any(|p| p.0.as_ref() == cand));
                let match_user = user_phrases.as_ref().map_or(false, |up| up.get(&bigram_key).map_or(false, |l| l.iter().any(|p| p.0 == cand)));
                
                if match_static || match_user {
                    entry.2 = entry.2.max(2);
                }
            }
        }

        // Domain Match: Bônus máximo se a palavra é frequente no app atual
        if let Ok(domain) = engine.current_domain.read() {
            if !domain.is_empty() {
                let user_phrases = engine.phrase_memory.read().ok();
                let domain_key = format!("__domain__:{}", domain);
                let match_domain = user_phrases.as_ref().map_or(false, |up| 
                    up.get(&domain_key).map_or(false, |l| l.iter().any(|p| p.0 == cand))
                );
                if match_domain {
                    log::trace!("Predictor: Domain boost (Level 4) applied to '{}'", cand);
                    entry.2 = entry.2.max(4); // Domain match > Trigram match
                }
            }
        }
    }
}
