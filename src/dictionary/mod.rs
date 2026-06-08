//! Gerenciamento do Dicionário Estático Base.
//! Contém a lógica para carregar o vocabulário básico a partir de um binário comprimido.

use crate::core::KeyboardEngine;
use bincode;
use zstd;

/// Dicionário binário embutido.
/// Inclui ~260.000 palavras (Dicionário completo USP + Top 2700 prioritárias).
const BINARY_DICT: &[u8] = include_bytes!("../../assets/pt-BR/dictionary.bin");
const BINARY_PHRASES: &[u8] = include_bytes!("../../assets/pt-BR/phrases.bin");

/// Carrega o vocabulário inicial na Trie estática do motor.
pub fn load_default_dictionary(engine: &mut KeyboardEngine) {
    log::debug!("Dictionary: Iniciando carregamento do dicionário binário ({} bytes)", BINARY_DICT.len());

    match decompress_and_load_dict(engine) {
        Ok(count) => log::info!("Dictionary: Sucesso! {} palavras carregadas.", count),
        Err(e) => {
            log::error!("Dictionary: Falha no dicionário binário: {:?}. Usando fallback.", e);
            load_fallback_dictionary(engine);
        }
    }

    log::debug!("Dictionary: Iniciando carregamento de frases estáticas ({} bytes)", BINARY_PHRASES.len());
    match decompress_and_load_phrases(engine) {
        Ok(count) => log::info!("Dictionary: Sucesso! {} frases carregadas.", count),
        Err(e) => log::warn!("Dictionary: Nenhuma frase estática carregada: {:?}", e),
    }
}

fn decompress_and_load_dict(engine: &mut KeyboardEngine) -> Result<usize, Box<dyn std::error::Error>> {
    let decompressed = zstd::decode_all(BINARY_DICT)?;
    if decompressed.is_empty() { return Ok(0); }
    let mut words: Vec<(String, u32)> = bincode::deserialize(&decompressed)?;
    
    // Limite rígido para evitar OOM em dispositivos com menos RAM
    if words.len() > 200_000 {
        words.truncate(200_000);
    }

    let count = words.len();
    for (word, freq) in words {
        engine.insert_static(&word, freq);
    }
    Ok(count)
}

fn decompress_and_load_phrases(engine: &mut KeyboardEngine) -> Result<usize, Box<dyn std::error::Error>> {
    let decompressed = zstd::decode_all(BINARY_PHRASES)?;
    if decompressed.is_empty() { return Ok(0); }

    let mut count = 0;
    let mut cursor = 0;
    
    // Limite de 500.000 frases para manter o heap do Android saudável (Scudo)
    const MAX_PHRASES: usize = 500_000;

    while cursor < decompressed.len() && count < MAX_PHRASES {
        let len = decompressed[cursor] as usize;
        cursor += 1;
        if cursor + len + 4 > decompressed.len() { break; }
        
        let phrase_bytes = &decompressed[cursor..cursor + len];
        let phrase_str = std::str::from_utf8(phrase_bytes).unwrap_or("");
        cursor += len;
        
        let mut freq_bytes = [0u8; 4];
        freq_bytes.copy_from_slice(&decompressed[cursor..cursor + 4]);
        let freq = u32::from_le_bytes(freq_bytes);
        cursor += 4;

        // Indexa a frase por contexto
        let mut parts = phrase_str.split_whitespace();
        let p1 = parts.next();
        let p2 = parts.next();
        let p3 = parts.next();

        match (p1, p2, p3) {
            (Some(c1), Some(target), None) => {
                // Bigrama: "c1" -> "target"
                engine.static_phrases
                    .entry(Box::from(c1))
                    .or_default()
                    .push((Box::from(target), freq));
            }
            (Some(c1), Some(c2), Some(target)) => {
                // Trigrama: "c1 c2" -> "target"
                let context = format!("{} {}", c1, c2).into_boxed_str();
                engine.static_phrases
                    .entry(context)
                    .or_default()
                    .push((Box::from(target), freq));
            }
            _ => {}
        }
        
        count += 1;
    }
    Ok(count)
}

/// Fallback caso o binário falhe ou para desenvolvimento rápido.
fn load_fallback_dictionary(engine: &mut KeyboardEngine) {
    let fallback_words = ["o", "a", "e", "de", "que", "do", "da", "em", "um", "para"];
    for word in fallback_words {
        engine.insert_static(word, 100);
    }
}
