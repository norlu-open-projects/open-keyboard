use std::collections::HashMap;
use crate::core::KeyboardEngine;

/// Predição pura de tópicos (Next-Word Prediction para quando a palavra é vazia)
pub fn predict_topic(
    engine: &KeyboardEngine,
    candidates: &mut HashMap<String, (usize, u32, usize)>,
) {
    if let Ok(topics) = engine.current_semantic_topics.read() {
        if let Ok(memory) = engine.phrase_memory.read() {
            for topic in topics.iter() {
                let context_key = format!("__topic__:{}", topic);
                if let Some(topic_phrases) = memory.get(&context_key) {
                    for (cand, freq) in topic_phrases {
                        let entry = candidates.entry(cand.clone()).or_insert((0, 0, 0));
                        entry.1 = entry.1.max(*freq);
                        entry.2 = entry.2.max(2); // Level 2 match
                    }
                }
            }
        }
    }
}

/// Aplica bônus a candidatos se eles estiverem correlacionados com o tema semântico atual da conversa.
pub fn apply_topic_boost(
    engine: &KeyboardEngine,
    candidates: &mut HashMap<String, (usize, u32, usize)>
) {
    if candidates.is_empty() { return; }

    if let Ok(topics) = engine.current_semantic_topics.read() {
        if topics.is_empty() { return; }
        
        if let Ok(memory) = engine.phrase_memory.read() {
            for topic in topics.iter() {
                let context_key = format!("__topic__:{}", topic);
                if let Some(topic_phrases) = memory.get(&context_key) {
                    for (cand, freq) in topic_phrases {
                        if let Some(entry) = candidates.get_mut(cand) {
                            log::debug!("Search: Aplicando Match Level 2 (Topic Match '{}') para a palavra '{}'", topic, cand);
                            entry.1 += *freq; // Increase base frequency
                            entry.2 = entry.2.max(2); // Level 2 match
                        }
                    }
                }
            }
        }
    }
}
