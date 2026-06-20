# 01 - Filosofia Local-First & Arquitetura Base

O **Norlu Keyboard** (e qualquer projeto derivado dessa base arquitetural) foi construído sob uma premissa inegociável: **Privacidade Total e Alta Performance**.

## 1. Local-First como Regra de Ouro
A arquitetura repudia a nuvem para o processamento de Machine Learning e Inteligência Artificial. Todos os cálculos linguísticos, predições, e estatísticas comportamentais (Analytics) ocorrem **no próprio dispositivo** usando Rust.
- **Sem requisições de rede:** O app sequer possui a permissão `android.permission.INTERNET`.
- **Privacidade por padrão:** Modelos locais garantem que senhas, fofocas, e-mails de trabalho ou pesquisas íntimas nunca saiam da RAM local e, quando precisam persistir, vão para o disco cifrados (`ChaCha20Poly1305`).

## 2. A Divisão Clara de Responsabilidades
Para atingir performance extrema, foi necessário criar um "Muro de Berlim" lógico entre as tecnologias:

### Kotlin (Android Native)
É responsável exclusivamente por **Visão e Interação**.
- **Hardware & SO:** Ouve o Android (Keystore, InputConnection, SharedPreferences).
- **Tela:** Desenha pixels na tela usando `Canvas` nativo a 60-120fps.
- **Toque:** Processa onde o dedo do usuário encostou fisicamente e calcula heurísticas visuais (ex: *Fat Finger Euclidiano*).
- **NÃO FAZ:** Kotlin não guarda vocabulário, não faz fuzzy search, não processa linguística e não salva dados sensíveis no disco.

### Rust (Core Engine)
É o **Cérebro Algorítmico**.
- **Memória:** Usa uma `Dual-Trie` em RAM para buscas sub-milissegundo.
- **Lógica:** Calcula probabilidade de próxima letra, sugere palavras com *Levenshtein Distance*.
- **Segurança e Armazenamento:** Gerencia a Engine Sled de BD embutido e criptografa os *buffers* usando a chave que o Kotlin extraiu do Keystore do Hardware.
- **NÃO FAZ:** Rust não sabe a resolução da tela, não toca em Canvas, não cria Threads do SO sozinhos (tudo flui pela JNI).

## 3. Benefícios Práticos dessa Separação
1. **Crash Isolation:** Um "Out of Memory" na UI não corrompe o BD do Rust.
2. **Reuso Total (Cross-Platform):** Todo o código contido em `/src` (Rust) pode ser reaproveitado em iOS, Windows, Linux ou macOS com 0% de alteração, pois não acopla bibliotecas de UI.
3. **Economia de Bateria:** Kotlin consome muita bateria se ficar operando matrizes com Garbage Collector. Rust roda bare-metal com alocação determinística.
