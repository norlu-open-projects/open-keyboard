//! Sistema de Pontuação e Ranqueamento (Scoring).
//! Responsável por calcular a relevância de cada palavra baseada em:
//! 1. Frequência base (Dicionário).
//! 2. Recência (Último uso pelo usuário).
//! 3. Contexto (Probabilidade de N-gramas).

use std::collections::HashMap;
use std::time::{SystemTime, UNIX_EPOCH};

pub struct ScoringEngine {
    pub weight_freq: f64,
    pub weight_recency: f64,
    pub bonus_bigram: f64,
    pub bonus_trigram: f64,
    pub lambda: f64,
}

impl Default for ScoringEngine {
    fn default() -> Self {
        Self {
            weight_freq: 0.4,
            weight_recency: 0.4,
            bonus_bigram: 15.0,  // Bônus para bigrama exato
            bonus_trigram: 30.0, // Bônus para trigrama exato
            lambda: 0.0001,
        }
    }
}

impl ScoringEngine {
    pub fn calculate_score(
        &self,
        base_freq: u32,
        last_used_timestamp: Option<u64>,
        phrase_matches: usize, // 0 = nada, 2 = bigrama, 3 = trigrama
    ) -> f64 {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        // Sinal de Frequência: ln(F+1)
        let freq_signal = ((base_freq as f64) + 1.0).ln();

        // Sinal de Recência: e^(-lambda * delta_t)
        let recency_signal = if let Some(last_ts) = last_used_timestamp {
            let delta_t = (now.saturating_sub(last_ts)) as f64;
            (-self.lambda * delta_t).exp()
        } else {
            0.0
        };

        // Sinal de Contexto
        let context_bonus = match phrase_matches {
            3 => self.bonus_trigram,
            2 => self.bonus_bigram,
            _ => 0.0,
        };

        (self.weight_freq * freq_signal)
            + (self.weight_recency * recency_signal)
            + context_bonus
    }
}

/// Cache LRU simples para rastrear o uso recente de palavras
pub struct RecencyCache {
    capacity: usize,
    // Mapeia a palavra para o timestamp do último uso
    usage: HashMap<String, u64>,
}

impl RecencyCache {
    pub fn new(capacity: usize) -> Self {
        Self {
            capacity,
            usage: HashMap::with_capacity(capacity),
        }
    }

    pub fn use_word(&mut self, word: String) {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        if self.usage.len() >= self.capacity && !self.usage.contains_key(&word) {
            // Remove a entrada mais antiga (simplificado: remove uma qualquer se atingir capacidade)
            // Para um LRU real, precisaríamos de uma lista duplamente ligada ou algo similar.
            // Mas para este motor, um HashMap com timestamps já ajuda no cálculo do score.
            if let Some(key_to_remove) = self.usage.keys().next().cloned() {
                self.usage.remove(&key_to_remove);
            }
        }
        self.usage.insert(word, now);
    }

    pub fn get_last_used(&self, word: &str) -> Option<u64> {
        self.usage.get(word).copied()
    }
}
