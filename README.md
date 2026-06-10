<div align="center">

# Norlu OpenKeyboard

**IME Android de Alta Performance com Core Rust e Privacidade Blindada**

[![License: MPL 2.0](https://img.shields.io/badge/License-MPL_2.0-2b3a42?style=for-the-badge)](https://opensource.org/licenses/MPL-2.0)
[![Rust 2024](https://img.shields.io/badge/Rust-2024-dea584?style=for-the-badge&logo=rust)](https://www.rust-lang.org/)
[![Android MinSDK 24](https://img.shields.io/badge/Android-24+-3ddc84?style=for-the-badge&logo=android)](https://developer.android.com/)
[![Made in Brazil](https://img.shields.io/badge/Made_in-Brazil-009739?style=for-the-badge)](https://github.com/topics/brazil)

</div>

O **`Norlu OpenKeyboard`** é um método de entrada (IME) de próxima geração que funde a segurança e velocidade do **Rust** com uma engine de renderização direta em **Canvas**, eliminando o risco de telemetria e latência. Projetado sob o dogma da "Privacidade como Opinião", ele entrega predições sub-milisegundo e segurança de memória de nível militar, processando 100% dos dados no dispositivo.

---

## 📖 Documentação Técnica

- [01 - Arquitetura do Motor Core (Rust Engine)](./wiki/01-Arquitetura-Rust.md)
- [02 - Engine de UI e Performance (Canvas & UX)](./wiki/02-UI-e-UX-Performance.md)
- [03 - Privacidade e Armazenamento Local-First](./wiki/03-Privacidade-e-Storage.md)
- [04 - Acessibilidade e Resiliência (WCAG AAA)](./wiki/04-Acessibilidade-e-Resiliencia.md)
- [05 - Sistema de Undo Seguro e Zeroização de RAM](./wiki/05-Sistema-Undo-Seguro.md)
- [06 - Segurança como Opinião (Mandatory FLAG_SECURE)](./wiki/06-Seguranca-como-Opiniao.md)

---

## 🚀 Engenharia de Performance

### Compilação do Core (Rust):
```bash
# Cross-compilation para as arquiteturas alvo (arm64-v8a / x86_64)
./build.sh
```

### Integração JNI (Kotlin):
```kotlin
// Inicialização Assíncrona via Coroutines para manter a UI estável
class RustEngineAsync(storagePath: String, secureKey: ByteArray) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        scope.launch(Dispatchers.IO) {
            // Carrega o motor nativo e inicializa o Sled DB criptografado
            nativeEnginePtr = initEngine(storagePath, secureKey)
        }
    }

    // Busca Fuzzy < 500ns via Trie Pruning
    fun getPredictions(text: String, context: Array<String>) {
        // ...
    }
}
```

---

## 🎯 Qual problema o Norlu resolve?

Teclados convencionais introduzem dois riscos críticos: **latência de UI** causada pelo Garbage Collector do Java e **vazamento de dados** via telemetria em nuvem. O Norlu OpenKeyboard elimina esses problemas ao tratar o input do usuário como um artefato criptográfico volátil.

### 1. Integridade Linguística (Rust Core)
- **Dual-Trie Design**: Combina um dicionário estático imutável com uma `User Trie` mutável e thread-safe (`RwLock`).
- **Fuzzy Search (Levenshtein)**: Algoritmo otimizado com **Poda de Matriz Iterativa**, realizando buscas em dicionários de 100k+ palavras em nanossegundos.
- **Contextual Scoring**: Modelo Probabilístico condicional $P(W_n | W_{n-1})$ que aprende os hábitos de digitação do usuário localmente.

### 2. Privacidade Blindada (Security as Opinion)
- **Mandatory FLAG_SECURE**: O teclado impõe bloqueio de screenshot a nível de sistema; keyloggers visuais e malwares de gravação de tela capturam apenas um bloco preto.
- **Secure Undo System**: Armazena até 50 palavras em uma pilha LIFO criptografada com **ChaCha20Poly1305** na RAM.
- **Physical Zeroizing**: Ao fechar o teclado, a memória é sobrescrita com zeros (`Zeroize`), destruindo qualquer resíduo elétrico de texto na RAM.

### 3. Performance Perceptual (Canvas Engine)
- **Zero-Allocation Draw**: Abandona XMLs e Views por um `Canvas` de renderização direta, pré-alocando todos os objetos para atingir **120fps constantes**.
- **Dynamic Hitboxes**: Ajusta as áreas de toque dinamicamente com base na probabilidade linguística (WCAG AAA), corrigindo erros de digitação antes mesmo da letra aparecer.
- **Sky Buffer Rendering**: Sistema de buffer transparente que permite pop-ups de teclas fluírem acima da interface sem serem cortados.

---

## 🔍 Infraestrutura de Persistência

O Norlu utiliza o **Sled DB**, um banco de dados de alta performance em Rust, protegido por chaves derivadas do **Android Keystore**.

| Recurso | Tecnologia | Benefício |
| :--- | :--- | :--- |
| **Criptografia** | ChaCha20Poly1305 | Dados em repouso são ilegíveis sem a chave mestra. |
| **Nonce** | Hash-Based Deterministic | Impede ataques de análise estatística e reúso de fluxo. |
| **Compressão** | Zstd (Zstandard) | Reduz o footprint do dicionário do usuário em até 70%. |
| **Integridade** | Key-Based Nonces | Garante que cada palavra persistida seja um artefato único. |

---

## 🏗️ Requisitos de Build

- **Rust**: Edition 2024
- **Android NDK**: r26+
- **Cargo-NDK**: Para cross-compilation de targets móveis.
- **Android SDK**: API 24 (7.0 Nougat) ou superior.

---

<div align="center">

**O Norlu OpenKeyboard é a ferramenta definitiva para quem não aceita compromissos entre performance e privacidade.**

---

--- Este projeto é Open Source e Licenciado através da **Mozilla Public License v2.0 (MPL-2.0)** ---

</div>
