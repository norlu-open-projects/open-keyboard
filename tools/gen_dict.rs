use std::fs::File;
use std::io::{BufRead, BufReader, Write};
use std::collections::HashMap;
use bincode;
use zstd;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut word_map: HashMap<String, u32> = HashMap::new();

    // 1. Carregar palavras de ALTA PRIORIDADE (Top ~2700)
    if let Ok(file) = File::open("assets/pt-BR/consolidated_raw.txt") {
        let reader = BufReader::new(file);
        for line in reader.lines() {
            let word = line?.trim().to_string().to_lowercase();
            if !word.is_empty() {
                word_map.insert(word, 200); // Alta prioridade
            }
        }
        println!("Palavras de alta prioridade carregadas: {}", word_map.len());
    }

    // 2. Carregar TODAS as palavras (Baixa prioridade)
    let full_dict_path = "assets/pt-BR/todas_palavras_portugues.txt";
    let file = File::open(full_dict_path)?;
    let reader = BufReader::new(file);
    let mut added_full = 0;
    
    for line in reader.lines() {
        let word = line?.trim().to_string().to_lowercase();
        if !word.is_empty() {
            // Só insere se não existir ou se a frequência atual for menor
            // (Nesse caso, se já existir no top 2700, mantemos a freq 200)
            word_map.entry(word).or_insert_with(|| {
                added_full += 1;
                10 // Baixa prioridade para o resto do dicionário
            });
        }
    }
    
    let total_words = word_map.len();
    println!("Dicionário completo consolidado. Total: {} palavras ({} novas da lista USP)", total_words, added_full);

    // Converte para Vec para serialização
    let mut final_words: Vec<(String, u32)> = word_map.into_iter().collect();
    // Ordenar ajuda na taxa de compressão
    final_words.sort_by(|a, b| a.0.cmp(&b.0));

    // 3. Serializar e Comprimir
    let encoded: Vec<u8> = bincode::serialize(&final_words)?;
    let compressed = zstd::encode_all(&encoded[..], 19)?; // Nível 19 para menor tamanho final
    
    let mut out = File::create("assets/pt-BR/dictionary.bin")?;
    out.write_all(&compressed)?;
    
    println!("✅ Sucesso!");
    println!("Arquivo gerado: assets/pt-BR/dictionary.bin");
    println!("Tamanho final: {:.2} KB (Redução massiva)", compressed.len() as f64 / 1024.0);
    
    Ok(())
}
