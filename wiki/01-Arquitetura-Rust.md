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

## 3. Scoring e Recência
A classificação das sugestões baseia-se em um modelo de múltiplos sinais:
*   **Frequência Base**: Popularidade intrínseca da palavra.
*   **RecencyCache**: Um cache LRU protegido por `RwLock` que rastreia o uso recente.
*   **Decaimento Exponencial**: Palavras recentes ganham um boost que decai conforme $e^{-\lambda \Delta t}$.
*   **Contexto (N-Gramas)**: Probabilidade condicional baseada na palavra anterior $P(W_n | W_{n-1})$.

## 4. Gerenciamento de Concorrência
Para garantir a responsividade, o motor é inerentemente thread-safe:
*   **RwLock em Componentes Críticos**: Tanto a `User Trie` quanto o `RecencyCache` e a memória de frases utilizam travas de leitura/escrita. Isso permite que predições (leituras) ocorram em paralelo, enquanto manutenções e aprendizados (escritas) são sincronizados.

## 5. Armazenamento Seguro (Storage Layer)
O vocabulário do usuário é persistido usando `sled` com encriptação de ponta:
*   **ChaCha20Poly1305**: Criptografia autenticada para cada entrada.
*   **Nonce Determinístico Único**: Cada palavra gera seu próprio nonce derivado de um hash (Key-Based Nonce), impedindo a reutilização de fluxo de bytes (keystream reuse) e ataques de análise estatística.
*   **Direct Boot Awareness**: O motor é capaz de iniciar em modo de leitura apenas (Static Trie) antes do desbloqueio da chave mestra do usuário.

## 6. Sistema de Undo Seguro (Encrypted Session Stack)
O motor implementa um sistema de "Undo" focado em segurança máxima:
*   **Encrypted LIFO Stack**: Armazena até 50 palavras em RAM.
*   **Zeroize**: A memória é explicitamente preenchida com zeros ao final de cada sessão, garantindo que nenhum resíduo de texto persista na memória volátil.
