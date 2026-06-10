# Decisão Técnica: Segurança como Opinião (Mandatory FLAG_SECURE)

No Norlu Keyboard, a privacidade não é tratada como uma preferência configurável pelo usuário, mas como uma fundação arquitetural inegociável. Esta decisão técnica é materializada pela imposição da `FLAG_SECURE` em todas as sessões de entrada.

## 1. O Conceito de "Segurança como Opinião"
Software tradicional muitas vezes delega decisões críticas de segurança ao usuário (ex: "Deseja ativar a proteção?"). No Norlu Keyboard, adotamos a postura de que o aplicativo é o guardião dos dados de digitação e, portanto, deve aplicar as melhores práticas por padrão e de forma obrigatória.

## 2. Implementação Técnica
A proteção é aplicada diretamente na janela do serviço de entrada (`AdvancedKeyboardService.kt`):

```kotlin
override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
    super.onStartInput(attribute, restarting)
    // Proteção Anti-Screenshot Permanente
    window?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
}
```

### Por que não é opcional?
1.  **Imunidade a Keylogging Visual**: Malwares com permissão de gravação de tela ou serviços de acessibilidade com OCR poderiam capturar as teclas pressionadas se a flag fosse desativada.
2.  **Prevenção de Exposição Acidental**: Impede que o usuário capture senhas ou dados sensíveis em prints de tela por descuido.
3.  **Higiene de Multitarefa**: Garante que o conteúdo do teclado apareça oculto (bloco preto) na tela de aplicativos recentes, protegendo a privacidade visual.

## 3. Consequências e Limitações Técnicas
*   **Prints de Tela**: O Android bloqueará qualquer tentativa de captura de tela enquanto o teclado estiver visível.
*   **Gravação e Espelhamento**: Aplicativos de gravação de tela resultarão em um vídeo com uma tarja preta na área ocupada pelo teclado.
*   **Estética do Bloqueio**: A cor do bloqueio (preto) é determinada pelo kernel do Android (`SurfaceFlinger`) ao detectar a flag. Como o sistema fica "cego" para o conteúdo da janela, ele preenche o espaço vazio com a cor de ausência de sinal.

## 4. Auditoria de Sessão
Para garantir a integridade da proteção, cada início de sessão gera um log de auditoria no sistema:
`Log.i("OpenKeyboard", "FLAG_SECURE enforced for this input session.")`

Esta abordagem garante que o Norlu Keyboard permaneça um ambiente estéril e seguro, independentemente da expertise técnica do usuário final.
