# 02 - A Ponte JNI e Otimização Extrema (Zero-Allocation)

A chave para misturar Kotlin e Rust em um aplicativo Android moderno (onde cada frame visual tem 16ms ou 8ms para ser desenhado) reside na forma como a comunicação JNI (Java Native Interface) é configurada.

## 1. O Padrão `NativeBridge` e `Pointer`
Em vez de instanciar objetos globais que a JNI tenta gerenciar (o que gera memory leaks fatais), nós usamos **Value Classes** e **Ponteiros Brutos**.

```kotlin
// NativePointer.kt
@JvmInline
value class NativePointer(val raw: Long) {
    val isValid: Boolean get() = raw != 0L
}
```
O Rust cria a Engine, aloca na memória `heap` e devolve apenas um número `Long` (o endereço de memória) para o Kotlin. Toda vez que o Kotlin precisa do Rust, ele repassa esse `Long`.
- **Por quê?** O Garbage Collector do Android ignora *Value Classes* repassadas assim, ou seja, **Zero alocação de objetos Java**. O ponteiro não "pesa".

## 2. Assincronismo: Coroutines `Dispatchers.IO` e `Dispatchers.Default`
O C-ABI bloqueia a Thread onde é chamado. Sendo assim, **NENHUMA** chamada JNI (nem a mais rápida do mundo) deve ocorrer na Main Thread (Thread de UI) do Android.
- Em `RustEngineAsync.kt`, criamos um encapsulamento total com *Kotlin Coroutines*.
- Funções pesadas como predição vão para `Dispatchers.Default` (otimizada para cálculo CPU-bound).
- Funções de gravação (Analytics, Learn) vão para `Dispatchers.IO` (otimizada para disco).
- **Vantagem:** O teclado nunca "engasga", mesmo num celular antigo gravando centenas de palavras no banco de dados.

## 3. Zero-Allocation em Arrays (Passagem de Dados)
A JNI sofre muito para converter Strings do Rust para o Kotlin.
- **A Solução:** Quando o Rust precisa devolver 3 palavras sugeridas, ele devolve um array nativo `jobjectArray` que o Kotlin lê diretamente como `Array<String>`.
- Mais além: No Kotlin, esse `Array<String>` não é iterado usando `forEach` ou transformado em objetos complexos durante a renderização. Nós pré-processamos e mandamos direto pro Canvas desenhar.

## 4. Retorno Estruturado Flexível (JSON)
Quando lidamos com dados dinâmicos como os do Analytics (Paginação da Gamificação), montar objetos JNI intrincados (arrays de arrays) é doloroso e inseguro.
- **Tática Ninja:** O Rust usa o `serde_json` para montar todo o payload estruturado como uma única gigantesca `String` JSON. 
- O Kotlin pega essa `String` pura e a injeta diretamente na tela (ou desserializa rapidamente usando bibliotecas nativas de JSON se precisar de lógica). Isso corta o código JNI em 90% e evita crashes de segmentação (`SIGSEGV`).
