#!/bin/bash

# Abortar o script se qualquer comando falhar
set -e

# Configurações
LIB_NAME="libkeyboard_engine.so"
JNI_LIBS_DIR="android/app/src/main/jniLibs"

# Tenta encontrar o NDK automaticamente se a variável não estiver setada
if [ -z "$ANDROID_NDK_HOME" ]; then
    # Caminho informado pelo usuário
    POSSIBLE_NDK="/home/one/Android/Sdk/ndk/30.0.14904198"
    if [ -d "$POSSIBLE_NDK" ]; then
        export ANDROID_NDK_HOME="$POSSIBLE_NDK"
        echo "🔍 NDK encontrado em: $ANDROID_NDK_HOME"
    else
        echo "❌ Erro: A variável ANDROID_NDK_HOME não está configurada e o caminho padrão não existe."
        echo "Por favor, execute: export ANDROID_NDK_HOME=/caminho/para/seu/ndk antes de rodar o script."
        exit 1
    fi
fi

echo "🔨 Iniciando compilação cruzada para Android..."

# 1. Compilar para ARM64
echo "📦 Compilando para arm64-v8a..."
cargo ndk -t arm64-v8a build --release

# 2. Compilar para x86_64
echo "📦 Compilando para x86_64..."
cargo ndk -t x86_64 build --release

# 3. Preparar diretórios JNI
echo "📂 Organizando arquivos .so..."
mkdir -p "$JNI_LIBS_DIR/arm64-v8a"
mkdir -p "$JNI_LIBS_DIR/x86_64"

# 4. Mover binários e verificar existência
if [ -f "target/aarch64-linux-android/release/$LIB_NAME" ]; then
    cp target/aarch64-linux-android/release/$LIB_NAME "$JNI_LIBS_DIR/arm64-v8a/"
    echo "✅ ARM64 copiado."
else
    echo "❌ Erro: Binário ARM64 não encontrado."
    exit 1
fi

if [ -f "target/x86_64-linux-android/release/$LIB_NAME" ]; then
    cp target/x86_64-linux-android/release/$LIB_NAME "$JNI_LIBS_DIR/x86_64/"
    echo "✅ x86_64 copiado."
else
    echo "❌ Erro: Binário x86_64 não encontrado."
    exit 1
fi

echo "🚀 Tudo pronto! Agora você pode gerar o APK no Android Studio."
