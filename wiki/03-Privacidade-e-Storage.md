# Privacidade, Storage e Local-First

A privacidade não é um recurso adicional no Norlu Keyboard; ela é a fundação da arquitetura. O sistema foi desenhado para ser "Invisível à Nuvem".

## 1. Local-First por Design
Todo o aprendizado de vocabulário, correção de erros e hábitos de digitação ocorre estritamente dentro do dispositivo do usuário.
*   **Sem Telemetria**: Não existem chamadas de rede para modelos de linguagem externos.
*   **Processamento de Borda**: O motor Rust processa 100% das predições localmente.

## 2. Isolamento de Storage (Sandbox Android)
A persistência utiliza o diretório privado `context.filesDir`.
*   **Permissões**: O teclado opera com **zero permissões de armazenamento externo** (sem `READ/WRITE_EXTERNAL_STORAGE`).
*   **Acesso Restrito**: Devido ao modelo de segurança do Android, nenhum outro aplicativo (exceto o próprio teclado) pode acessar os arquivos de banco de dados do motor.

## 3. Tecnologia de Persistência (Sled & Zstd)
Para garantir que o armazenamento seja rápido e ocupe o mínimo de espaço:
*   **Sled DB**: Um banco de dados Key-Value embarcado de alta performance escrito em Rust, otimizado para flash storage.
*   **Compressão Zstd**: Os deltas de aprendizado do usuário são comprimidos usando o algoritmo Zstd antes de serem gravados, reduzindo o footprint de memória em até 70%.
*   **Serialização Bincode**: Formato binário ultra-eficiente que minimiza o tempo de CPU gasto em I/O.

## 4. Proteção de Dados Sensíveis (Privacidade Ativa)
O sistema detecta automaticamente contextos de segurança:
*   **Modo Incógnito de Senhas**: Ao detectar campos de senha (`EditorInfo`), o teclado desativa instantaneamente:
    1.  O motor de predição (Ghost Text).
    2.  O aprendizado de novas palavras (User Trie).
    3.  O registro de logs de digitação.
*   **Zero Leakage**: O ponteiro do motor Rust é destruído e recriado conforme o ciclo de vida do serviço Android, garantindo que não haja resíduos de memória em transições de campos de texto.

## 5. Entropia Controlada e Direito ao Esquecimento
O motor implementa um algoritmo de manutenção automática para lidar com o acúmulo de dados ao longo de anos:
*   **Decaimento Temporal**: O score de cada palavra diminui exponencialmente se não for utilizada.
*   **Poda Automática (Pruning)**: Periodicamente, o motor identifica e remove fisicamente palavras cujos scores caíram abaixo de um limiar de sobrevivência.
*   **Evolução Linguística**: Este mecanismo garante que o teclado "esqueça" erros de digitação antigos ou mudanças de vocabulário do usuário, funcionando como um sistema homeostático que se mantém enxuto e performático mesmo após 5 anos de uso contínuo.
