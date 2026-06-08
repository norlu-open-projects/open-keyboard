# Acessibilidade e Resiliência (WCAG AAA)

O Norlu Keyboard foi projetado para ser inclusivo e indestrutível sob carga.

## 1. Conformidade WCAG AAA
A implementação manual no Canvas permite um controle granular que supera as limitações das Views padrão.
*   **Contraste (Critério 1.4.3)**: Paleta de cores fixada em #121212 (fundo) e #FFFFFF (texto), atingindo uma proporção de contraste superior a 7:1.
*   **Alvos de Toque (Critério 2.5.5)**: Cada tecla no Canvas é desenhada respeitando o tamanho mínimo de 48x48dp, garantindo que usuários com dificuldades motoras tenham uma área de acionamento segura.
*   **Escalonamento de Texto (Critério 1.4.4)**: O motor de renderização consulta o `fontScale` do Android em tempo real, ajustando o tamanho dos glifos desenhados no Canvas sem quebrar o layout.
*   **Anúncios de Acessibilidade**: Sugestões e estados do teclado são reportados via `contentDescription` virtual para leitores de tela (TalkBack).

## 2. Resiliência do Sistema
A arquitetura de "Ponte de Memória Otimizada" garante estabilidade:
*   **Gestão de Memória Rust**: O uso de `Box::into_raw` e `Box::from_raw` permite que o estado do motor persista entre chamadas JNI, mas seja limpo deterministicamente no `onDestroy` do serviço Android, evitando memory leaks.
*   **Panic Safety**: O uso de `panic = "abort"` no `Cargo.toml` garante que falhas catastróficas no motor nativo resultem em um fechamento limpo do serviço, permitindo que o Android o reinicie instantaneamente, em vez de deixar o teclado em um estado zumbi corrompido.
*   **Fallback Estático**: Se o banco de dados do usuário (`Sled`) for corrompido em disco, o motor detecta a falha na inicialização e entra em modo de Fallback, operando apenas com o dicionário estático imutável, garantindo que o usuário nunca fique sem poder digitar.

## 3. Otimizações de Build (Pipeline de Produção)
*   **LTO (Link Time Optimization)**: Reduz o tamanho do binário nativo e elimina código morto entre crates.
*   **Strip**: Remove símbolos de debug do binário final, reduzindo o tamanho do APK em vários megabytes.
*   **ABI Filtering**: O APK é gerado especificamente para `arm64-v8a` e `x86_64`, evitando o inchaço de bibliotecas para arquiteturas legadas.
