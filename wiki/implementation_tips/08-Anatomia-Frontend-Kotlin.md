# 08 - Anatomia do Frontend Nativo em Kotlin

A arquitetura Android do Norlu Keyboard no diretório `android/app/src/main/java/lab/norlu/openkeyboard/` desafia o desenvolvimento convencional. Abandonamos inflar Layouts (XML) e ViewGroups e optamos por Controle Total e Composição Pura para assegurar performance de 60 FPS+ com Zero-Allocation.

## 1. Desconstruindo o God Object (`AdvancedKeyboardService`)
Geralmente, classes baseadas no `InputMethodService` do Android acabam se tornando "God Objects" (Objetos Divinos com milhares de linhas gerenciando tudo). Para evitar isso, quebramos a orquestração em composições focadas:

- **`AdvancedKeyboardService.kt`**: Atua apenas como a ponte com o Sistema Operacional Android. Ele escuta o ciclo de vida (`onStartInput`, `onCreateInputView`) e repassa o controle de forma delegada.
- **`KeyboardInputHandler.kt`**: Lida exclusivamente com digitação, toques de Backspace rápido, Enter e regras de Undo (apagar a última palavra autocompletada).
- **`KeyboardPredictionHandler.kt`**: O cérebro de interface para as Coroutines do Rust. Ele lida com debounces de extração semântica e exibe o "Ghost Text" na tela.

## 2. Estratégia de Debounce (Semântica Assíncrona)
Em `KeyboardPredictionHandler.kt`, há um detalhe refinado de inteligência artificial na extração de tópicos da frase:
```kotlin
if (now - lastSemanticExtractionTime > 3000) {
    lastSemanticExtractionTime = now
    val longText = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
    rustEngine.updateSemanticContext(longText)
}
```
A cada 3 segundos, se o usuário pausar a digitação, recolhemos os últimos 2000 caracteres e enviamos silenciosamente pro Rust mapear o "Assunto" do texto (ex: Futebol, Programação). Enviar isso a cada tecla mataria a bateria. O uso de **Debounce Temporal** é crucial em sistemas Local-First.

## 3. UI Direta com Canvas (`OptimisedKeyboardView` e `KeyboardRenderer`)
A interface gráfica é desenhada do zero usando chamadas diretas C/C++ na placa de vídeo (via `Canvas` nativo).
- Não há botões inflados. O teclado inteiro é uma única imagem sendo desenhada e redesenhada (paint loop).
- A tela de Configurações, Gamificação e Analytics são simplesmente retângulos geométricos desenhados por cima do teclado no `KeyboardRenderer.kt`, utilizando a flag `isSettingsOpen`. Ao não usar Fragments ou Activities para as Configurações, garantimos a **velocidade da luz** ao interagir.

## 4. Substituindo Click Listeners por Matemática Pura
O Android costuma gerenciar os toques atribuindo um `.setOnClickListener` para cada Botão. Nós abolimos isso.
- O `KeyboardTouchHandler.kt` assina apenas a interceptação global de matriz (`onTouchEvent`).
- Ele mapeia o "X e Y" fornecido pela tela contra um grid matemático para descobrir em qual região o dedo caiu (incluindo as setas `<` e `>` da nossa gamificação temporal). 
- Graças a isso, conseguimos interceptar a Distância Euclidiana (Fat Finger) livremente, antes que qualquer "Botão Falso" consumisse o toque de forma errada.
