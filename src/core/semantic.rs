use std::collections::HashMap;
use crate::core::KeyboardEngine;

impl KeyboardEngine {
    /// Processa o texto extraído do input, removendo stop-words e gerando o modelo de tópicos (Bag of Words)
    pub fn update_semantic_context(&self, text: &str) {
        let stop_words = ["o", "a", "os", "as", "um", "uma", "uns", "umas", 
            "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas", 
            "por", "para", "com", "sem", "que", "se", "como", "mas", "ou", "e",
            "não", "sim", "eu", "tu", "ele", "ela", "nós", "vós", "eles", "elas",
            "me", "te", "se", "nos", "vos", "lhe", "lhes",
            "este", "esta", "esse", "essa", "isso", "isto", "aquilo", "aquele", "aquela",
            "ser", "estar", "ter", "haver", "é", "são", "foi", "foram",
            "já", "só", "muito", "mais", "bem", "mal", "quando", "onde", "qual", "quais"];
            
        let mut frequencies: HashMap<String, usize> = HashMap::new();
        
        // Simples tokenização: divide por espaço ou pontuação, converte pra minúsculo
        for word in text.split(|c: char| !c.is_alphanumeric()).filter(|w| !w.is_empty()) {
            let lower = word.to_lowercase();
            if lower.len() > 2 && !stop_words.contains(&lower.as_str()) {
                *frequencies.entry(lower).or_insert(0) += 1;
            }
        }
        
        let mut sorted_words: Vec<(String, usize)> = frequencies.into_iter().collect();
        sorted_words.sort_by(|a, b| b.1.cmp(&a.1));
        
        // Pega as 5 palavras mais frequentes como "temas"
        let top_topics: Vec<String> = sorted_words.into_iter()
            .take(5)
            .map(|(w, _)| w)
            .collect();
            
        if let Ok(mut topics) = self.current_semantic_topics.write() {
            log::debug!("Core: Semantic topics updated: {:?}", top_topics);
            *topics = top_topics;
        }
    }
}
