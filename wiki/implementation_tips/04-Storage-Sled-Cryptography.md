# 04 - Armazenamento Local, Sled e Criptografia Militar

Num ecossistema "Local-First", dados persistem no disco do dispositivo. Tratar dicionários de teclado e dados contextuais em disco é um desafio gigante de I/O e Segurança.

## 1. Motor de Banco de Dados: `Sled`
Por que usar Sled e não SQLite (Room do Android)?
- **Sled é um DB Key-Value nativo em Rust.**
- Desenhado para Concorrência lock-free moderna e SSDs.
- Ele permite salvar chaves de bytes e não faz "overhead" de SQL.
- A indexação e os prefixos para o Contexto Temporal ficam assustadoramente rápidos, e podemos empacotar tudo no `.so` C++, ignorando bibliotecas Java.

## 2. Segurança e Privacidade: ChaCha20Poly1305
Nunca gravamos as palavras do usuário (suas conversas) em plain text no disco.
Se o dispositivo tiver root (ou houver um backup desprotegido do Android ADB), as palavras aprendidas estarão expostas.
- **Solução Técnica:** 
  1. O Android possui o **Hardware Keystore** (TEE/TrustZone). O Kotlin (`SecureKeyManager.kt`) pede ao Hardware uma chave AES forte (256-bit). Essa chave nunca pode ser lida fora do kernel isolado em aparelhos modernos.
  2. O Kotlin passa essa chave nativamente via JNI no momento em que a Engine Rust desperta.
  3. Toda vez que o `Sled` vai gravar um vocabulário, o Rust roda uma camada de encriptação com **XChaCha20Poly1305**. É criptografia autenticada de nível militar rápida.

## 3. Modo Incógnito Integrado
A privacidade do teclado também é comportamental.
- O Kotlin detecta passivamente campos "Sensíveis" (Senhas de app de banco, ou atributos `IME_FLAG_NO_PERSONALIZED_LEARNING` em navegadores em aba anônima).
- Manda um sinal ao Rust: `setIncognitoMode(true)`.
- A engine aciona um `AtomicBool` de travamento global.
- Qualquer requisição `learn_word` que tente ensinar o teclado sofre um "early return". Nenhuma palavra ou frase entra no disco e nada da predição local do dispositivo contamina aquele campo.
