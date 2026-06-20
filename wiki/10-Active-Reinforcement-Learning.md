# Active Reinforcement Learning (Aprendizado de Alta Prioridade por Correção)

Uma feature avançada de Experiência de Usuário (UX) focada em adaptar a inteligência do teclado instantaneamente aos desejos forçados do usuário, corrigindo falsos-positivos na IA.

## ✅ IMPLEMENTADO (V1.1)

### O Problema Base
Muitas vezes, a engine de Autocomplete substitui uma palavra propositalmente incompleta do usuário ("se", por "seria"). O comportamento instintivo de correção dita que o usuário apertará o botão `Backspace` ou `Desfazer` para regredir à palavra original e então finalizá-la novamente. Em teclados ingênuos, o Autocomplete erraria em um loop infinito.

### A Solução Otimizada

* **Supressão de Estado (Kotlin):** O `KeyboardPredictionHandler` e o `AdvancedKeyboardService` agora gerenciam a *flag* `isAutocompleteSuppressed`. Ao detectar o uso de `Backspace` (ou do botão `Undo`) imediatamente sobre uma palavra em predição, a *flag* é ativada.
    * Consequência 1: O *Ghost Text* (autocompletar visual na tecla) some temporariamente.
    * Consequência 2: Se o usuário apertar *espaço*, a predição ignora a inserção forçada e insere a palavra crua.
    * Consequência 3: A *flag* desarma automaticamente assim que o usuário digita uma nova letra diferente ou inicia a próxima palavra, garantindo fluidez.

* **Readequação da Fila de Candidatos:** Quando a supressão está ativa, o Kotlin intercepta os resultados da predição local do Rust e injeta a palavra exata (`currentWord`) no 3º slot da barra de sugestões, garantindo que o usuário tenha um controle manual óbvio para consolidá-la.

* **Aprendizado Turbinado (Rust + JNI):** Quando uma correção forçada dessas ocorre — ou seja, o usuário sobrepõe o sistema de IA com a sua própria vontade crua usando o botão de *space* limpo, ou clica na barra de sugestão sob o cenário suprimido —, o teclado envia a ordem especial via JNI invocando `learnWordWithBoost` ao invés do aprendizado tradicional.
    * Em `learning.rs`, a variante `learn_word_with_boost` recebe a palavra acompanhada de um bônus numérico maciço (`boost = 20`).
    * O N-Grama do usuário imediatamente recebe **+20 de frequência persistente** num único golpe.
    * Essa "punição educativa" ao modelo garante que falsos-positivos irritantes desapareçam do caminho do usuário já no uso subsequente.
