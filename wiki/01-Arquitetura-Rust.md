# Arquitetura do Motor Core (Rust Engine)

O coração do Norlu Keyboard é um motor escrito em Rust, projetado para latência sub-milisegundo e segurança de memória. Esta decisão foi tomada para garantir que o processamento linguístico não compita com a Main Thread da UI do Android.

## 1. Estruturas de Dados
A peça central é uma **Trie (Árvore de Prefixos)** otimizada.
*   **Dual-Trie Design**: O sistema opera com duas árvores simultâneas:
    *   **Static Trie**: Contém o dicionário base da língua (imutável, carregado no binário).
    *   **User Trie**: Uma estrutura mutável protegida por `RwLock` que aprende com o vocabulário único do usuário.

## 2. Algoritmo de Busca Fuzzy (Levenshtein)
A busca por correções utiliza a **Distância de Levenshtein** com uma otimização crítica: **Poda de Matriz Iterativa**.
*   Em vez de calcular a distância contra todas as palavras do dicionário, o motor percorre a Trie e mantém uma linha da matriz de programação dinâmica.
*   **Poda (Pruning)**: Se o menor valor na linha atual da matriz exceder o limite `k` (distância máxima), o ramo inteiro da árvore é descartado instantaneamente.
*   **Performance**: Este método permite realizar buscas em dicionários de 100k+ palavras em aproximadamente **500 nanosegundos**.

## 3. Scoring Multissinal
A classificação das sugestões não se baseia apenas na distância de edição, mas em um modelo probabilístico:
*   **Frequência Base**: Popularidade intrínseca da palavra.
*   **Recência (Decaimento Exponencial)**: Palavras usadas recentemente ganham um boost temporário que decai conforme a fórmula $e^{-\lambda \Delta t}$.
*   **Contexto (N-Gramas)**: Probabilidade condicional baseada na palavra anterior $P(W_n | W_{n-1})$.

## 4. Próximas Probabilidades (Dynamic Hitboxes)
O motor expõe uma API que calcula a probabilidade dos próximos caracteres possíveis. Isso permite que a UI "preveja" onde o usuário provavelmente tocará, permitindo a implementação de áreas de toque dinâmicas que compensam erros físicos de paralaxe.
