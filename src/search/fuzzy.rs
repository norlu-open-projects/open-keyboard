//! Algoritmos Puros de Busca Fuzzy (Levenshtein).
//! Contém a lógica recursiva para navegação em Tries com tolerância a erros.
//! Independente de estado, focado em performance computacional.

use crate::trie::TrieNode;

/// Executa a busca recursiva na TrieNode usando a distância de Levenshtein.
pub fn search_recursive(
    node: &TrieNode,
    ch: char,
    target_word: &str,
    previous_row: &[usize],
    current_word: &mut String,
    max_distance: usize,
    results: &mut Vec<(String, usize, u32)>,
) {
    current_word.push(ch);
    let columns = target_word.len() + 1;
    let mut current_row = vec![0; columns];
    current_row[0] = previous_row[0] + 1;

    for column in 1..columns {
        let insert_cost = current_row[column - 1] + 1;
        let delete_cost = previous_row[column] + 1;
        let replace_cost = if target_word.chars().nth(column - 1) == Some(ch) {
            previous_row[column - 1]
        } else {
            previous_row[column - 1] + 1
        };

        current_row[column] = insert_cost.min(delete_cost).min(replace_cost);
    }

    // Se atingimos uma palavra válida dentro da distância máxima
    if current_row[target_word.len()] <= max_distance && node.is_terminal {
        results.push((
            current_word.clone(),
            current_row[target_word.len()],
            node.base_frequency,
        ));
    }

    // Poda (Pruning): Só continua se houver chance de encontrar algo válido
    if *current_row.iter().min().unwrap_or(&usize::MAX) <= max_distance {
        for (next_ch, child_node) in &node.children {
            search_recursive(
                child_node,
                *next_ch,
                target_word,
                &current_row,
                current_word,
                max_distance,
                results,
            );
        }
    }

    current_word.pop();
}

/// Busca todas as palavras que começam com o prefixo fornecido.
pub fn search_prefix(
    node: &TrieNode,
    prefix: &str,
    results: &mut Vec<(String, usize, u32)>,
) {
    let mut current_node = node;
    
    // 1. Navega até o nó do prefixo
    for ch in prefix.chars() {
        if let Some(next) = current_node.children.get(&ch) {
            current_node = next;
        } else {
            return; // Prefixo não existe
        }
    }

    // 2. Coleta todos os terminais a partir deste nó
    let mut word_buffer = prefix.to_string();
    collect_all_terminals(current_node, &mut word_buffer, results);
}

fn collect_all_terminals(
    node: &TrieNode,
    current_word: &mut String,
    results: &mut Vec<(String, usize, u32)>,
) {
    if node.is_terminal {
        results.push((current_word.clone(), 0, node.base_frequency));
    }

    // Limite de segurança para não explodir em dicionários gigantes
    if results.len() > 500 {
        return;
    }

    for (ch, child) in &node.children {
        current_word.push(*ch);
        collect_all_terminals(child, current_word, results);
        current_word.pop();
    }
}
