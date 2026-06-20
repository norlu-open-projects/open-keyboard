# 07 - Anatomia do Core em Rust: Deep Dive no `src/`

Para complementar o conhecimento arquitetural, este guia disseca a engenharia por trás do diretório `src/`, detalhando algoritmos, estratégias de memória e o porquê de cada decisão técnica nas entranhas do motor C/C++ FFI.

## 1. `src/core/mod.rs`: O Coração (`KeyboardEngine`)
A struct `KeyboardEngine` é o orquestrador global alocado em Heap (via `Box::new`), mantido vivo através do ponteiro `jlong` no lado do Kotlin.

### Concorrência e Zero-Locking Paths
```rust
pub struct KeyboardEngine {
    pub static_root: TrieNode, // Acesso imutável e lock-free (Leitura Infinita)
    pub user_root: RwLock<TrieNode>, // Acesso protegido
    pub phrase_memory: RwLock<HashMap<String, Vec<(String, u32)>>>,
    // ...
}
```
- **O Dicionário Base:** A `static_root` (ex: 20.000 palavras em pt-BR) é construída em tempo de compilação ou na inicialização. Ela não possui `RwLock` ou `Mutex`. A leitura é atômica, lock-free e instantânea.
- **O Dicionário Dinâmico:** A `user_root` (palavras aprendidas) exige o `RwLock`. O Rust permite N leitores simultâneos (buscando predições) ou 1 único escritor (quando o teclado `learn_word` na Thread IO). 

## 2. `src/search/fuzzy.rs`: Levenshtein com Pruning
A funcionalidade mais invocada é a busca por tolerância de erros (Fat Finger/Fuzzy Search). Calcular Distância de Levenshtein (matriz de custo de deleção, inserção, substituição) em uma Trie inteira seria O(N).

### Otimização Extrema (Alocação Dinâmica Suspensa)
1. **Array de 1 Dimensão:** Em vez de alocar uma Matriz (Linhas x Colunas) para a métrica de distância, o algoritmo passa apenas o `previous_row` no parâmetro recursivo e inicializa localmente no Stack o `current_row`.
2. **Pruning (Poda da Árvore):**
```rust
if *current_row.iter().min().unwrap_or(&usize::MAX) <= max_distance {
    // Só entra nos nós filhos se o custo mínimo desta linha ainda for menor ou igual à distância máxima permitida
}
```
Isso significa que, se o usuário digitar "CAR" e a tolerância for `1` erro, a ramificação inteira da Trie que começa com "B..." é abortada instantaneamente no primeiro nó.

## 3. `src/ffi/mod.rs`: Desmistificando a JNI Nativa
A JNI no Android não é amigável. Se você não cuidar dos `JNIEnv`, o app explode com SIGSEGV.

### O Padrão de Recuperação Segura (Safe-Fail)
Quando precisamos devolver 10 sugestões, temos de gerar um `jobjectArray` no C para retornar um `String[]` no Java.
```rust
let fallback_str = env.new_string("").unwrap_or_else(|_| env.new_string("ERR").unwrap());
let array = match env.new_object_array(
    suggestions.len() as i32, "java/lang/String", &fallback_str,
) {
    Ok(a) => a,
    Err(e) => {
        log::error!("Falha crítica JNI: {:?}", e);
        return env.new_object_array(0, "java/lang/String", &fallback_str).unwrap().into_raw();
    }
};
```
Em vez de usar `.unwrap()` fatal (que fecha o app), sempre recuamos para um array vazio no `Err()`. O Kotlin processará um array vazio e esconderá a barra de sugestões sem *crashar*.

## 4. Retornos Estruturados: `getAnalyticsData`
A gamificação e Analytics requeriam que dados de *Top Palavras no turno da Manhã* fossem enviados pro Kotlin. 
Fazer o Rust instanciar Objetos JSON nativos Java seria suicídio de performance. A solução no FFI:
```rust
let json_result = if page == 1 {
    format!(r#"{{"keystrokes":{},"topic":{}}}"#, ks, top)
} else if page == 2 {
    format!(r#"[{}]"#, top_words.join(",")) // Concatena JSON puro de top palavras
};
env.new_string(json_result)
```
O Kotlin apenas executa `Json.decodeFromString(rustData)`. Simples, desacoplado e à prova de bugs de memória em C++.

## Resumo da Anatomia
Se for interagir com `src/`, tenha em mente:
- Modificações em Tries devem respeitar o algoritmo recursivo sem alocar `String` ou `Vec` gigante dentro do loop (Use sempre referências e `char` nativo).
- Funções em `ffi/mod.rs` precisam proteger a vida útil do `engine_ptr`.
- O uso de `RwLock` no Rust mapeia perfeitamente para as predições bloqueantes de "Read" na CPU contra as gravações assíncronas de "Write" na Thread de IO do Kotlin.
