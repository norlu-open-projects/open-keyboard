# 06 - Próxima Fronteira: Guia de Arquitetura para uma Câmera Segura e de Alta Performance

Inspirado no sucesso arquitetural do Norlu Keyboard, este documento serve como um blueprint técnico para a construção do seu próximo projeto: **Um aplicativo de Câmera Local-First**, com foco insano em assincronismo, processamento pesado em hardware e privacidade extrema.

## 1. O Pipeline Visual: Kotlin + CameraX
Para a camada de visualização, a escolha óbvia é a API nativa **CameraX** do Android, que simplifica a compatibilidade de hardware entre milhares de dispositivos.

### A Responsabilidade do Kotlin
- **Lifecycle & Preview:** Controlar a tela e desenhar os frames a 60 FPS diretamente em um `PreviewView` (via `SurfaceProvider`).
- **Input Tátil:** Gerenciar gestos como "Pinch to Zoom", "Tap to Focus" e controle de Exposição nativamente.
- **ImageAnalysis:** É aqui que a mágica acontece. O CameraX permite anexar um `ImageAnalysis.Analyzer` que cospe frames em tempo real (formato YUV_420_888).

## 2. A Ponte JNI e Zero-Allocation de Frames
Transportar imagens de 12 Megapixels ou frames YUV a 30 FPS do Kotlin para o Rust pode derreter a memória se feito errado. 

### A Estratégia de ByteBuffer (Direct Allocation)
- O `ImageProxy` do CameraX nos fornece acesso a um `ByteBuffer` interno mapeado via hardware.
- **A Regra de Ouro:** NUNCA copie um array de bytes no Java (`ByteArray`). Em vez disso, repasse o ponteiro do `ByteBuffer` nativo (`GetDirectBufferAddress` na JNI) diretamente para o Rust.
- O Rust lê essa memória *in-place* e opera matrizes sem alocar um único byte extra no Garbage Collector do Kotlin.

## 3. O Cérebro em Rust: Processamento C/C++ FFI
Se no teclado o Rust fazia Fuzzy Search, na Câmera ele será o mestre da Visão Computacional.

### Pipeline Assíncrono para Frames
Como frames chegam a 30 FPS (33ms por frame), a engine Rust precisa processá-los de forma não bloqueante.
- O Kotlin envia o frame numa coroutine (`Dispatchers.Default`).
- Se o Rust estiver ocupado renderizando ou analisando o frame anterior, o Kotlin aplica uma política `STRATEGY_KEEP_ONLY_LATEST` e *dropa* o frame atual silenciosamente. Nunca se enfileira frames na RAM!
- **Utilitários de Hardware (Rust):** O Rust pode aplicar filtros avançados localmente: Equalização de Histograma, HDR computacional, detecção de bordas (Sobel) ou processamento ONNX para ML Local (como detectar rostos e ofuscar *blur* antes de salvar), tudo usando bibliotecas como `image-rs` ou `opencv-rust`.

## 4. Criptografia Nativa e Privacidade (O Vault)
Em uma "Câmera Paranoica", a galeria do Android (MediaStore) é considerada território hostil (pois qualquer app pode ter acesso).

### File System Bypass
1. **O Gatilho:** Quando o Kotlin emite a ordem de "Tirar Foto" e captura o Jpeg ou RAW.
2. **Interceptação:** Em vez de chamar a API padrão que salva em `/DCIM/Camera`, o fluxo é desviado inteiramente para o Rust.
3. **Criptografia On-the-Fly:** Usando o mesmo `SecureKeyManager` e `ChaCha20Poly1305`, o Rust criptografa os bytes da imagem *em streaming* enquanto os escreve diretamente numa pasta oculta do app no disco (O "Vault").
4. **Remoção de EXIF e Rastreabilidade:** O Rust expurga metadados invisíveis (GPS, marca do celular, timestamp) de forma impiedosa *antes* de salvar, garantindo zero Rastreamento (Fingerprint).
5. **Decriptação na Visualização:** Quando o usuário for visualizar a foto, a Galeria interna pedirá a imagem e o Rust fará a decriptação On-The-Fly em chunks diretos para o Canvas renderizar.

## 5. Resumo da Implementação Futura
- **UI & Lifecycle:** Kotlin + CameraX (Camera, Preview, Gestures).
- **Tráfego de Imagem:** `ByteBuffer` nativos repassados via ponteiro JNI puro (Zero Java allocations).
- **Motor Gráfico e Utilitário:** Rust processando YUV para RGB, aplicando filtros de CV ou ML (On-Device Artificial Intelligence).
- **Storage Privado:** Rust criptografando bytes em ChaCha20Poly1305 antes de tocar o disco SSD, banindo o MediaStore e erradicando dados EXIF por padrão.
