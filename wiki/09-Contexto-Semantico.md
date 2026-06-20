# Contexto Semântico de Longo Alcance (Topic Modeling)

O teclado entende ativamente sobre o que o usuário está conversando em um nível de parágrafo. As sugestões tornam-se incrivelmente temáticas, mitigando a "amnésia" que geralmente afeta modelos baseados apenas em N-Gramas de vizinhança.

## ✅ IMPLEMENTADO (V1.1)

### Arquitetura Zero-Allocation e NLP Otimizado
* **Kotlin (Debounce Engine):** Para respeitar totalmente a regra de "Zero-Allocation" durante a digitação ativa, o `KeyboardPredictionHandler` possui um debounce fixado em 3 segundos. Apenas quando o usuário pausa a digitação, o texto pré-cursor (últimos 2000 caracteres) é extraído. Ele é enviado de forma assíncrona (via `Dispatchers.Default` e JNI) ao motor, evitando *frame-drops*.
* **Rust (Filtro Passivo e Bag of Words):** Dentro de `semantic.rs`, a string maciça recebida é rapidamente "tokenizada" pelas pontuações. Um filtro estático de dezenas de *stop-words* do idioma elimina preposições e artigos. O motor então retém em RAM, na variável `current_semantic_topics`, **apenas as 5 palavras mais frequentes**. O resto é instantaneamente reciclado pelo _Garbage Collector_ intrínseco do Rust.
* **Rust (Aderência Relacional no Sled):** A engenharia brilha em `learning.rs`. Ao aprender uma nova palavra digitada, ela é vinculada a todos os 5 tópicos vigentes da conversa, sob a sintaxe `__topic__:<tema>`. Como isso reutiliza a excelente arquitetura em árvore do banco de dados `sled`, não há impacto volumétrico nem de performance para manter um "Grafo de Conhecimento" do usuário.
* **Rust (Injeção na Predição):** O recém-criado preditor `topic.rs` itera de forma seletiva sobre a lista de candidatas a _Autocomplete_. Ao detectar que uma candidata sugerida já conversou estatisticamente com um dos temas ativos na tela, ela ganha um prêmio de **Match Level 2** (fortíssimo), furando a fila e subindo imediatamente para o display central do teclado.
