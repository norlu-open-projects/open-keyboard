use dashmap::DashMap;
use rayon::prelude::*;
use std::fs::File;
use std::io::{BufRead, BufReader, Write};
use std::env;
use zstd::stream::encode_all;
use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};
use bincode;

fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() < 3 {
        println!("Uso: gen_phrases <input_txt> <output_phrases_bin>");
        return;
    }

    let input_path = &args[1];
    let output_phrases_path = &args[2];
    let output_dict_path = "assets/pt-BR/dictionary.bin";

    println!("🚀 Processando 6GB+ da Wiki com limite estendido (100M frases) usando 12GB+ RAM...");
    let file = File::open(input_path).expect("Falha ao abrir input");
    let reader = BufReader::with_capacity(1024 * 1024, file);

    // DashMap pré-alocado para reduzir realocações custosas
    let phrase_counts = Arc::new(DashMap::with_capacity(30_000_000));
    let word_counts = Arc::new(DashMap::with_capacity(10_000_000));
    let line_counter = Arc::new(AtomicUsize::new(0));

    // Processamento em LOTES para não estourar a RAM
    let mut batch = Vec::with_capacity(500_000);
    
    for line in reader.lines() {
        if let Ok(text) = line {
            batch.push(text);
            
            if batch.len() >= 500_000 {
                process_batch(&batch, &phrase_counts, &word_counts, &line_counter);
                batch.clear();
                
                // Monitoramento de memória relaxado para 100M frases
                if phrase_counts.len() > 100_000_000 {
                    println!("🧹 RAM atingindo limite (100M frases), limpando ruído...");
                    phrase_counts.retain(|_, freq| *freq > 1);
                }
            }
        }
    }

    // Processa o resto
    if !batch.is_empty() {
        process_batch(&batch, &phrase_counts, &word_counts, &line_counter);
    }

    // ==========================================
    // 1. GERAR PHRASES.BIN
    // ==========================================
    println!("🧪 Filtragem final de Frases (Top 500.000) para o APK...");
    let mut final_phrases: Vec<(String, u32)> = Vec::new();
    
    phrase_counts.iter().for_each(|r| {
        if *r.value() > 50 { // Aumentamos um pouco o filtro de frequência para economizar memória
            final_phrases.push((r.key().clone(), *r.value()));
        }
    });

    // Ordena por frequência e pega apenas o top 500.000
    final_phrases.sort_by(|a, b| b.1.cmp(&a.1));
    if final_phrases.len() > 500_000 {
        println!("✂️ Limitando de {} para 500.000 frases mais frequentes.", final_phrases.len());
        final_phrases.truncate(500_000);
    }

    println!("📦 Gerando binário de {} frases selecionadas...", final_phrases.len());

    let mut buffer = Vec::new();
    for (phrase, freq) in final_phrases {
        let bytes = phrase.as_bytes();
        if bytes.len() > 255 { continue; }
        buffer.push(bytes.len() as u8);
        buffer.extend_from_slice(bytes);
        buffer.extend_from_slice(&freq.to_le_bytes());
    }

    println!("🌀 Comprimindo Frases (ZSTD 21)...");
    let compressed_phrases = encode_all(&buffer[..], 21).expect("Falha na compressão das frases");

    let mut out_file = File::create(output_phrases_path).expect("Falha ao criar output frases");
    out_file.write_all(&compressed_phrases).expect("Falha ao escrever frases");
    println!("✅ Concluído! Frases: {} ({:.2} MB)", output_phrases_path, compressed_phrases.len() as f64 / 1024.0 / 1024.0);

    // ==========================================
    // 2. GERAR DICTIONARY.BIN
    // ==========================================
    println!("🧪 Processando Dicionário de Palavras Únicas...");
    let mut all_words: Vec<(String, u32)> = Vec::new();
    word_counts.iter().for_each(|r| {
        if *r.value() > 5 { // Filtra ruído extremo
            all_words.push((r.key().clone(), *r.value()));
        }
    });
    
    // Ordena por frequência para identificar o top 10.000
    all_words.sort_by(|a, b| b.1.cmp(&a.1));

    // Limita o dicionário para as 200k palavras mais frequentes
    if all_words.len() > 200_000 {
        println!("✂️ Limitando dicionário de {} para 200.000 palavras.", all_words.len());
        all_words.truncate(200_000);
    }

    let mut dictionary_entries = Vec::with_capacity(all_words.len());
    for (i, (word, _freq)) in all_words.into_iter().enumerate() {
        let score = if i < 10_000 { 200 } else { 10 };
        dictionary_entries.push((word, score));
    }
    
    // Ordenar alfabeticamente para compressão
    dictionary_entries.sort_by(|a, b| a.0.cmp(&b.0));

    println!("📦 Gerando binário de {} palavras para o Dicionário...", dictionary_entries.len());
    let dict_encoded: Vec<u8> = bincode::serialize(&dictionary_entries).expect("Falha ao serializar dicionário");
    
    println!("🌀 Comprimindo Dicionário (ZSTD 21)...");
    let dict_compressed = zstd::encode_all(&dict_encoded[..], 21).expect("Falha ao comprimir dicionário");
    
    let mut dict_out = File::create(output_dict_path).expect("Falha ao criar dicionário");
    dict_out.write_all(&dict_compressed).expect("Falha ao escrever");
    
    println!("✅ Concluído! Dicionário: {} ({:.2} MB, {} palavras)", output_dict_path, dict_compressed.len() as f64 / 1024.0 / 1024.0, dictionary_entries.len());
}

fn process_batch(
    batch: &[String], 
    phrase_counts: &Arc<DashMap<String, u32>>, 
    word_counts: &Arc<DashMap<String, u32>>, 
    counter: &Arc<AtomicUsize>
) {
    batch.par_iter().for_each(|text| {
        let current = counter.fetch_add(1, Ordering::Relaxed);
        if current % 1_000_000 == 0 && current > 0 {
            println!("⏳ {}M linhas processadas...", current / 1_000_000);
        }

        let words: Vec<&str> = text
            .split(|c: char| !c.is_alphabetic() && c != '\'' && c != '-')
            .filter(|s| s.len() > 1 || matches!(*s, "o" | "a" | "e" | "é" | "O" | "A" | "E" | "É"))
            .collect();

        if !words.is_empty() {
            for i in 0..words.len() {
                let w1 = words[i].to_lowercase();
                *word_counts.entry(w1.clone()).or_insert(0) += 1;

                // Bigrama
                if i + 1 < words.len() {
                    let w2 = words[i+1].to_lowercase();
                    let bigram = format!("{} {}", w1, w2);
                    *phrase_counts.entry(bigram).or_insert(0) += 1;
                    
                    // Trigrama
                    if i + 2 < words.len() {
                        let w3 = words[i+2].to_lowercase();
                        let trigram = format!("{} {} {}", w1, w2, w3);
                        *phrase_counts.entry(trigram).or_insert(0) += 1;
                    }
                }
            }
        }
    });
}
