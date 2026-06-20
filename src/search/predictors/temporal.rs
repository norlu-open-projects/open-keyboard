use std::collections::HashMap;
use crate::core::KeyboardEngine;

/// Predicts next words purely based on the current temporal context
pub fn predict_temporal(
    engine: &KeyboardEngine,
    candidates: &mut HashMap<String, (usize, u32, usize)>,
) {
    if let Ok(temporal) = engine.current_temporal_context.read() {
        if !temporal.is_empty() {
            let temporal_key = format!("__time__:{}", temporal);
            if let Ok(user_phrases) = engine.phrase_memory.read() {
                if let Some(list) = user_phrases.get(&temporal_key) {
                    for (cand, freq) in list {
                        let entry = candidates.entry(cand.clone()).or_insert((0, 0, 0));
                        entry.1 = entry.1.max(*freq);
                        entry.2 = entry.2.max(3); // Level 3 match
                    }
                }
            }
        }
    }
}

/// Applies a score boost to existing candidates if they match the temporal context
pub fn apply_temporal_boost(
    engine: &KeyboardEngine,
    candidates: &mut HashMap<String, (usize, u32, usize)>,
) {
    if let Ok(temporal) = engine.current_temporal_context.read() {
        if !temporal.is_empty() {
            let temporal_key = format!("__time__:{}", temporal);
            if let Ok(user_phrases) = engine.phrase_memory.read() {
                if let Some(list) = user_phrases.get(&temporal_key) {
                    for (cand, freq) in list {
                        if let Some(entry) = candidates.get_mut(cand) {
                            entry.1 += *freq; // Increase base frequency
                            entry.2 = entry.2.max(3); // Level 3 match
                        }
                    }
                }
            }
        }
    }
}
