# Contexto Temporal e de Rotina

O teclado detecta implicitamente padrões temporais de uso (hora do dia, dia útil vs. final de semana) e associa as palavras usadas a esses contextos locais.

## ✅ IMPLEMENTADO (V1.1)

### Implementação Técnica
* **Kotlin (Captura e Gateway):** O `AdvancedKeyboardService` calcula de forma passiva o turno (manhã, tarde, noite) e se é um dia útil ou final de semana (`WEEKDAY_MORNING`, `WEEKEND_AFTERNOON`, etc.) usando `java.util.Calendar`. A _string_ representativa do tempo atual é enviada de forma assíncrona para o motor nativo via JNI.
* **Rust (Aprendizado):** O `KeyboardEngine` armazena o estado atual em `current_temporal_context`. Na camada de aprendizado (`learning.rs`), qualquer palavra confirmada pelo usuário é atrelada ao rótulo `__time__:<contexto>` dentro do banco de dados `sled` em sua estrutura preexistente de grafos, mantendo o armazenamento veloz e sem criar tabelas adicionais pesadas.
* **Rust (Predição Orquestrada):** O módulo específico `temporal.rs` atua em dois cenários durante o `search_smart`:
    1. **Next-Word Prediction (NWP):** Ao apertar espaço no começo de uma nova frase, ele injeta saudações ou palavras comumente usadas no horário (ex: "Bom dia" de manhã, "Sextou" de sexta à noite).
    2. **Fuzzy Search Boost:** Durante a digitação, ele concede um **Match Level 3** dinâmico para candidatas que, embora estejam com a distância de edição imperfeita, são fortemente atreladas ao horário atual na memória local.
