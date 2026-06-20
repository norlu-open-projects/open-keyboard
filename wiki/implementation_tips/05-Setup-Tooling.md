# 05 - Setup Tooling (Como montar e compilar)

Construir essa sinfonia exige um Toolchain muito afiado. Se você for clonar ou criar um projeto inspirado nesta arquitetura, o fluxo é o seguinte.

## 1. O Script Único de Build (`build.sh`)
O Android Studio usa Gradle e o Rust usa o Cargo. Tentar misturar os dois na mesma configuração é pedir por problemas obscuros.
A decisão arquitetural é: **Desacoplar a compilação do Rust.**
- Temos um `./build.sh` que usa `cargo ndk` para compilar os `.so` de forma purista.
- O script compila para `arm64-v8a` e `x86_64` (emuladores rápidos) e move o arquivo final para as pastas nativas da JNI no Android (`android/app/src/main/jniLibs/`).
- O Android Studio vê esses arquivos `.so` apenas como "Binários Fechados". O Gradle não se estressa tentando invocar o CMake ou o Rust. 

## 2. Android Studio (Setup)
- É fundamental possuir o **NDK** e o **CMake** instalados via SDK Manager para garantir as bibliotecas header C/C++ corretas.
- O Gradle apenas precisa empacotar os `jniLibs`. Nenhuma linha absurda de C++ Ninja Build é inserida no `build.gradle`.

## 3. Depuração
- **Crash Nativos (SIGSEGV):** Se o Rust estourar um Null Pointer em *unsafe code*, o app crascha sem aviso no Android Studio. Para debugar isso, use `android:debuggable="true"` e a ferramenta "Attach Native Debugger" (LLDB) no AS.
- **Logcat Controlado:** Logar na Thread Principal do Kotlin atrasa o processamento (strings allocation). O Rust também possui um bridge nativo de Logs se preciso, mas evitamos logar dados sensíveis. A Gamificação Local ajudou a expor estatísticas cruciais via JNI e visualizá-las diretamente numa tela no app sem encher o logcat de ruído.

## Resumo Dourado
Trate o **Rust** como se ele fosse um micro-serviço robusto rodando dentro do aplicativo, enquanto o **Kotlin** funciona como o Frontend leve e burro. A ponte (JNI) é o cabo de rede; mantenha o pacote de rede (Payload) compacto e trafegue-o de forma assíncrona.
