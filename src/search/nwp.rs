use std::collections::HashMap;
use crate::core::KeyboardEngine;

/// Executa a lógica de Next-Word Prediction (NWP).
/// Retorna candidatos baseados no contexto (Bigramas e Trigramas).
pub fn predict_next_word(
    engine: &KeyboardEngine,
    context: &[String],
    candidates: &mut HashMap<String, (usize, u32, usize)>,
) {
    let mut context_keys = Vec::new();
    if context.len() >= 2 {
        context_keys.push(format!("{} {}", context[context.len()-2], context[context.len()-1])); // Trigrama
    }
    if !context.is_empty() {
        context_keys.push(context[context.len()-1].clone()); // Bigrama
    }

    let user_phrases = engine.phrase_memory.read().ok();
    let last_word = context.last().map(|s| s.as_str()).unwrap_or("");

    if context_keys.is_empty() {
        // Fallback para início de frase
        let common_starters = ["o", "a", "eu", "não", "como", "se", "para", "que", "você", "um"];
        for (i, target) in common_starters.iter().enumerate() {
            let entry = candidates.entry(target.to_string()).or_insert((0, 0, 0));
            entry.1 = (10 - i) as u32 * 20;
            entry.2 = 1;
        }
    } else {
        for key in context_keys {
            let match_level = if key.contains(' ') { 3 } else { 2 };
            
            // Frases estáticas
            if let Some(list) = engine.static_phrases.get(key.as_str()) {
                for (target, freq) in list {
                    if target.as_ref() != last_word {
                        let entry = candidates.entry(target.to_string()).or_insert((0, 0, 0));
                        entry.1 = entry.1.max(*freq);
                        entry.2 = entry.2.max(match_level);
                    }
                }
            }
            // Frases do usuário
            if let Some(ref up) = user_phrases {
                if let Some(list) = up.get(&key) {
                    for (target, freq) in list {
                        if target != last_word {
                            let entry = candidates.entry(target.clone()).or_insert((0, 0, 0));
                            entry.1 = entry.1.max(*freq + 100);
                            entry.2 = entry.2.max(match_level);
                        }
                    }
                }
            }
        }
    }
}
