# Contextos Avançados de Predição

Este documento descreve as abordagens estratégicas para o motor de predição do Norlu Keyboard, capturando o estado do usuário e o contexto ambiental para predições "telepáticas", mantendo o processamento 100% offline (Rust) e "Zero-Allocation" (Kotlin).

## ✅ IMPLEMENTADO (V1.1)

### 1. Contexto de Domínio (App/InputType)
* **Status:** Concluído.
* **Implementação Técnica:**
    * **Kotlin:** O `AdvancedKeyboardService` captura `packageName` e `inputType` via `EditorInfo`. O `RustEngineAsync` gerencia o binding tardio (late-binding) para garantir que o contexto seja aplicado mesmo que o motor ainda esteja inicializando.
    * **Rust:** O `KeyboardEngine` armazena o `current_domain`. No aprendizado (`learning.rs`), associa-se a palavra à chave `__domain__:<contexto>` na `phrase_memory`.
    * **Impacto no Scoring:** Palavras detectadas no domínio atual recebem **Match Level 4**, sobrepondo-se até mesmo aos Trigramas clássicos, priorizando o vocabulário específico do aplicativo em uso.

### 2. Contexto de Cadência (Velocidade de Digitação)
* **Status:** Concluído.
* **Implementação Técnica:**
    * **Kotlin:** O `KeyboardTouchHandler` calcula o delta de tempo entre eventos de toque. Se o intervalo for `< 150ms`, a flag `isFastTyping` é ativada.
    * **Rust:** O orquestrador de busca (`search/mod.rs`) intercepta a flag. Se ativada, o motor ignora o `predict_next_word` (N-Gramas profundos) e foca exclusivamente na completude e autocorreção espacial (`completion.rs`).
    * **Benefício:** Redução drástica de uso de CPU durante rajadas de digitação e UX mais "perdoadora" para erros de precisão em alta velocidade.

### 3. Contexto Temporal e de Rotina
* **Documentação Detalhada:** Vide [08-Contexto-Temporal.md](./08-Contexto-Temporal.md)

### 4. Contexto Semântico de Longo Alcance (Topic Modeling)
* **Documentação Detalhada:** Vide [09-Contexto-Semantico.md](./09-Contexto-Semantico.md)

### 5. Active Reinforcement Learning (Correção Automática)
* **Documentação Detalhada:** Vide [10-Active-Reinforcement-Learning.md](./10-Active-Reinforcement-Learning.md)

---

## 🚀 PRÓXIMAS RODADAS (Visão de Longo Prazo)

