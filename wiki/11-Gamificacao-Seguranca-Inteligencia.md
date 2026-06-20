# 11 - Gamificação, Segurança e Inteligência Espacial

## 1. Fat Finger Euclidiano (Inteligência Espacial)
**Motivação:** Usuários digitam rapidamente em telas de toque e frequentemente atingem a margem das teclas adjacentes, gerando *typos* (ex: acertar `S` no lugar de `A`).

**Implementação:** Em `KeyboardTouchHandler.kt`, a verificação estática de "margem de 20%" foi substituída por um cálculo Matemático/Geométrico contínuo. Ao detectar um toque:
- Calculamos a **Distância Euclidiana** (hipotenusa em pixels) entre o ponto de toque físico e o centro da tecla atingida, bem como o centro das teclas vizinhas.
- Multiplicamos o inverso da distância pelo peso estatístico fornecido pelo motor em Rust (`nextCharProbabilities`).
- A tecla resultante é aquela que obtiver o melhor "Score Combinado" (`Probabilidade * (1 / Distância)`), absorvendo "erros de dedo" baseados no que o usuário provavelmente tentou digitar.

## 2. Modo Incógnito Integrado (Privacidade e Segurança)
**Motivação:** Em campos sensíveis, o teclado jamais pode memorizar palavras ou armazenar frequências para evitar o vazamento de senhas ou chaves.

**Implementação:** 
- O Android detecta campos tipo Senha ou marcados com `IME_FLAG_NO_PERSONALIZED_LEARNING` em `AdvancedKeyboardService.kt` e sinaliza o Rust via JNI (`setIncognitoMode`).
- No core Rust, o arquivo `src/core/security.rs` gerencia uma flag `AtomicBool` (`PrivacyManager`). Quando verdadeira, todas as operações de aprendizado (inserção na Trie ou Contexto de Frases) invocam um *early return*, ignorando o aprendizado no momento.
- **UX:** Uma camada visual do `KeyboardRenderer.kt` desenha de forma discreta o emoji de Fantasma ("👻") no botão de espaço sempre que esse modo estiver ativado.

## 3. Gamificação de Analytics Local
**Motivação:** Demonstrar para o usuário o poder e impacto do motor nativo no seu dia a dia sem enviar nenhum dado para a nuvem.

**Implementação:**
- **Coleta (Rust):** O motor Rust armazena em `src/core/analytics.rs` (salvo via `sled` no disco) contadores atômicos (toques salvos pelo autocompletar e predições por categoria algorítmica).
- **Interface (Kotlin):** O painel de Configurações do teclado evoluiu para suportar paginação:
    - **Página 0:** Troca de Temas (Claro/Escuro).
    - **Página 1:** Estatísticas Gerais (exibição de toques economizados via JNI JSON).
    - **Página 2:** Estatísticas Temporais detalhadas. O usuário clica em turnos (Manhã/Tarde/Noite) e dias (Úteis/Fim de semana) para visualizar dinamicamente (pela JNI `getAnalyticsData`) o top 3 das palavras mais frequentes associadas ao seu contexto na árvore de contexto temporal armazenada localmente.
