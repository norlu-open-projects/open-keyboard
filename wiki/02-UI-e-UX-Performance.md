# Engine de UI e Experiência do Usuário (Canvas & UX)

Para atingir a fluidez de 120fps, o Norlu Keyboard abandona o sistema de Views tradicional do Android em favor de uma engine de renderização direta.

## 1. Renderização Direta via Canvas
O uso de layouts XML e `Button`/`TextView` introduz um overhead de alocação de objetos que causa "jank" (engasgos) devido ao Garbage Collector.
*   **Custom View**: Implementamos a `OptimisedKeyboardView` que desenha tudo em um único `Canvas`.
*   **Zero-Allocation Draw Loop**: Todos os objetos `Paint`, `RectF` e `Path` são pré-alocados. O método `onDraw` realiza apenas cálculos matemáticos e chamadas de desenho primitivas.
*   **Performance**: Inicialização < 5ms e framerate estável mesmo em dispositivos de entrada.

## 2. Dynamic Hitboxes (Mitigação de Erro)
Esta é a funcionalidade de UX mais avançada do projeto.
*   **O Problema**: Usuários frequentemente tocam na borda entre duas teclas.
*   **A Solução**: Quando um toque ocorre em uma "zona de dúvida" (25% das bordas da tecla), a View consulta as probabilidades do motor Rust.
*   **Bias de Probabilidade**: Se a letra vizinha é 5x mais provável de ser a próxima na sequência linguística, o sistema redireciona o clique para ela, corrigindo o erro antes mesmo da letra aparecer na tela.

## 3. Ghost Text (Autossugestão Visual)
Inspirado no `zsh-autosuggestions`, o teclado projeta a predição mais provável diretamente no campo de texto (simulado na tecla de espaço).
*   **Visual**: Renderizado com baixo contraste (Alpha 0.5) para não distrair, mas fornecer um caminho de "menor esforço".
*   **Interação**: O pressionamento da tecla de Espaço atua como um "Tab", aceitando a sugestão e completando a palavra instantaneamente.

## 4. Máquina de Estados de Layout
A alternância entre layouts Alpha e Numeric é feita via mudança de ponteiros de arrays de glifos, sem `Layout Inflation`. Isso garante que a troca de modo seja tão rápida quanto a taxa de atualização da tela.
