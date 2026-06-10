# Sistema de Undo Seguro (Secure Undo System)

O Norlu Keyboard implementa uma funcionalidade de "Undo" (Desfazer) que permite recuperar as últimas 50 palavras deletadas ou auto-corrigidas. Diferente de implementações convencionais, o nosso sistema foi desenhado sob o princípio de **"Zero Persistence"** e **"RAM Confidentiality"**.

## 1. O Problema da Segurança em Buffers de Undo
A maioria dos teclados mantém um buffer de texto simples na memória. Isso apresenta dois riscos críticos:
1.  **Memory Dumps**: Se o processo do teclado sofrer um crash e um dump de memória for gerado, palavras sensíveis deletadas estariam em texto claro.
2.  **Side-Channel Attacks**: Outros processos com vulnerabilidades de leitura de RAM (como Rowhammer ou falhas especulativas) poderiam, teoricamente, ler resíduos de texto.

## 2. A Solução: Arquitetura de 3 Camadas

### A. Criptografia de Sessão (ChaCha20Poly1305)
Cada palavra enviada para a pilha de Undo (`pushUndo`) não é armazenada como string. 
- **Chave Efêmera**: No momento em que o teclado abre, uma chave de 256 bits é gerada na RAM. Ela nunca é escrita em disco.
- **Nonce Incremental**: Cada palavra na pilha usa um Nonce exclusivo de 96 bits, garantindo que mesmo se o usuário deletar a mesma palavra várias vezes, o resultado cifrado na RAM será completamente diferente.
- **AEAD (Authenticated Encryption)**: Usamos ChaCha20Poly1305 para garantir que os dados não apenas sejam privados, mas que não possam ser manipulados na memória.

### B. Isolamento de Sessão e Ciclo de Vida
A pilha de Undo é estritamente **volátil**. 
- O motor Rust reside no espaço de memória nativo do Android Service. 
- Quando o método `onFinishInput()` é chamado (o usuário fecha o teclado ou troca de aplicativo), o comando `clearUndo()` é disparado imediatamente.
- Isso garante que o histórico de "Desfazer" não vaze entre diferentes aplicativos (ex: o que você apagou no WhatsApp não estará disponível ao abrir o Banco).

### C. Zeroização Física (Zeroize Trait)
Limpar uma lista ou deletar um objeto em linguagens comuns (como Java ou Python) não remove os dados da memória física imediatamente; o sistema apenas marca aquele espaço como "livre".
- No Norlu Keyboard, utilizamos a trait `Zeroize` do Rust.
- Quando uma palavra é removida da pilha (`pop`) ou quando a pilha é limpa (`clear`), o motor sobrescreve fisicamente os bytes de RAM com zeros (`0x00`).
- Isso destrói o dado no nível elétrico da memória, impedindo qualquer recuperação posterior via análise forense de RAM.

## 3. Fluxo de Dados Seguro
1.  **Input**: Usuário pressiona Backspace ou aceita uma Auto-correção.
2.  **Rust Core**: A palavra é capturada, criptografada com a chave de sessão e armazenada como um `Vec<u8>` binário.
3.  **UI**: O botão de Undo aparece.
4.  **Restore**: Ao clicar em Undo, a palavra é descriptografada em tempo real, enviada ao campo de texto, e o buffer binário é imediatamente **zeroizado**.
5.  **Terminação**: Ao fechar o teclado, a chave de sessão e toda a pilha são destruídas e zeroizadas.

## 4. O que garante a segurança?
O que torna este sistema único é a combinação de **Criptografia Simétrica de Grau Militar** com **Gestão Determinística de Memória**. Enquanto outros teclados confiam no Garbage Collector do sistema operacional, o Norlu Keyboard assume a responsabilidade ativa de destruir cada byte de informação sensível assim que ele não é mais necessário.
