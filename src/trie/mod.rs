//! Estrutura de dados principal (Trie).
//! Contém a definição do nó da Trie e operações fundamentais de árvore.
//! Focado em otimização de memória e destruição segura (Zeroize).

use std::collections::BTreeMap;
use zeroize::Zeroize;

#[derive(Default, Debug)]
pub struct TrieNode {
    pub children: BTreeMap<char, TrieNode>,
    pub is_terminal: bool,
    pub base_frequency: u32,
}

impl Zeroize for TrieNode {
    fn zeroize(&mut self) {
        // Limpa as frequências e terminais
        self.is_terminal.zeroize();
        self.base_frequency.zeroize();
        // Limpa os filhos recursivamente
        for node in self.children.values_mut() {
            node.zeroize();
        }
        self.children.clear();
    }
}

// Garante a zeroização física ao sair do escopo
impl Drop for TrieNode {
    fn drop(&mut self) {
        // children.clear() já vai disparar o drop recursivo.
        // O zeroize aqui era redundante e aumentava a profundidade de recursão.
        self.children.clear();
    }
}
