# 03 - Interface Nativa com Custom View (Canvas)

Não usamos XML para os botões do teclado, nem Jetpack Compose. Por quê? **Latência Absoluta**.
O Norlu Keyboard usa uma View nativa Customizada (`OptimisedKeyboardView`) e desenha cada botão literalmente "pintando a tela" via `android.graphics.Canvas`.

## 1. A Estrutura de Rendering
- **`KeyboardRenderer.kt`**: Uma classe utilitária puramente matemática. Ela recebe o `Canvas` do Android e itera sobre uma lista pré-compilada de Teclas.
- **Evitando `Iterator` e Objetos**: Durante o método `onDraw`, é estritamente **PROIBIDO** instanciar qualquer objeto (ex: `Rect`, `Paint`, `Path`). Qualquer `new` no `onDraw` acorda o Garbage Collector, que pausa a tela e gera o temido "Jank" (engasgo).
- O `KeyboardRenderer` aloca todos os `Paint` e `RectF` em sua inicialização e apenas altera seus valores dinamicamente. Os loops usam índice `for (i in 0 until size)` para não criar instâncias de Iteradores.

## 2. A Lógica de Toque (Touch Handler)
- **`KeyboardTouchHandler.kt`**: Intercepta os `MotionEvent` crus do sistema.
- Ao receber as coordenadas `(x, y)`, o sistema calcula matematicamente em qual região o dedo está.

### Fat Finger Euclidiano Inteligente
Esta é a maior proeza do Handing nativo vs UI tradicional:
1. Recebemos o `(x, y)` do toque.
2. Em vez de assumirmos a tecla puramente pela colisão da "caixa", olhamos os *vizinhos*.
3. O Rust previamente nos injetou um dicionário leve com a probabilidade das próximas letras (ex: `A: 95%, S: 5%`).
4. Calculamos a **Distância Euclidiana** `Math.hypot(x - centerX, y - centerY)`.
5. Cruzamos a Probabilidade do Rust vs a Distância Física para "Puxar o dedo" magicamente para a tecla certa, mesmo que o usuário tenha errado o botão por alguns milímetros.

## 3. Modais e Gamificação Renderizada
Como não há XML, componentes de UI complexos como as telas de Configuração e Estatísticas (Gamificação) também são pintadas!
- O handler capta o toque fora da área das letras e aciona a flag `isSettingsOpen`.
- O `Renderer` paralisa a pintura das letras e usa primitivas geométricas (`drawRoundRect`, `drawText`) para criar cards, botões de paginação (`<` e `>`) e títulos.
- **Resultado:** A tela de configurações do teclado abre em 0.0 milissegundos, sentindo como se fizesse parte da mesma lâmina de vidro.
