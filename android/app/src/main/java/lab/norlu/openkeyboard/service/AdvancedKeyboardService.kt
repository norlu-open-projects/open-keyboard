package lab.norlu.openkeyboard.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout

import lab.norlu.openkeyboard.core.RustEngineAsync
import lab.norlu.openkeyboard.security.SecureKeyManager
import lab.norlu.openkeyboard.ui.keyboard.OptimisedKeyboardView
import lab.norlu.openkeyboard.ui.keyboard.KeyboardState
import lab.norlu.openkeyboard.ui.settings.SettingsActivity
import lab.norlu.openkeyboard.utils.InputContextUtils

class AdvancedKeyboardService : InputMethodService() {
    private lateinit var rustEngine: RustEngineAsync
    private lateinit var keyboardView: OptimisedKeyboardView
    private var isPredictionEnabled = true
    private var currentGhostText = ""
    private lateinit var prefs: SharedPreferences
    private var isDeletingSequence = false

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("norlu_prefs", Context.MODE_PRIVATE)
        
        // Inicializa o motor Rust com a chave segura do Hardware (Keystore) via SecureKeyManager
        val secureKey = SecureKeyManager.getOrCreateSecureKey()
        rustEngine = RustEngineAsync(filesDir.absolutePath, secureKey)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        // Proteção Anti-Screenshot Permanente (Opinião do App)
        window?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        Log.i("OpenKeyboard", "FLAG_SECURE enforced for this input session.")

        val inputType = attribute?.inputType ?: 0
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
        val isMultiLine = (inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        isPredictionEnabled = !(variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                                variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                                variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)

        // Define o estado inicial do layout baseado no tipo de input
        if (::keyboardView.isInitialized) {
            val imeOptions = attribute?.imeOptions ?: 0
            val action = imeOptions and EditorInfo.IME_MASK_ACTION

            // Se for multi-linha, forçamos o ícone de Enter (None) para indicar quebra de linha
            if (isMultiLine) {
                keyboardView.setImeAction(EditorInfo.IME_ACTION_NONE)
            } else {
                keyboardView.setImeAction(action)
            }

            val inputClass = inputType and EditorInfo.TYPE_MASK_CLASS

            if (inputClass == EditorInfo.TYPE_CLASS_NUMBER || inputClass == EditorInfo.TYPE_CLASS_PHONE) {
                keyboardView.setKeyboardState(KeyboardState.NUMERIC_PAD)
            } else {
                keyboardView.setKeyboardState(KeyboardState.ALPHA)
            }
            
            // Atualiza preferência de vibração
            keyboardView.setHapticEnabled(prefs.getBoolean("norlu_haptic_enabled", true))
        }

    }

    override fun onEvaluateFullscreenMode(): Boolean {
        // Nunca entra em modo tela cheia, mantendo o app original visível atrás do teclado
        return false
    }

    override fun onEvaluateInputViewShown(): Boolean {
        return true
    }

    override fun onCreateInputView(): View {
        val root = FrameLayout(this)
        keyboardView = OptimisedKeyboardView(this)
        
        // Aplica o tema salvo (Default: Escuro)
        val isDark = prefs.getBoolean("norlu_theme_dark", true)
        keyboardView.applyTheme(isDark)
        
        // Sincroniza preferência de vibração
        keyboardView.setHapticEnabled(prefs.getBoolean("norlu_haptic_enabled", true))

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        keyboardView.layoutParams = params

        // Aplica o inset da barra de navegação para que o teclado não seja engolido
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bottomInset = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            view.setPadding(0, 0, 0, bottomInset)
            insets
        }

        keyboardView.setOnThemeChangedListener { dark ->
            prefs.edit().putBoolean("norlu_theme_dark", dark).apply()
            Log.d("OpenKeyboard", "Theme persisted: dark=$dark")
        }

        keyboardView.setOnSuggestionClickListener { suggestion: String ->
            triggerVibration()
            Log.d("OpenKeyboard", "Suggestion clicked: $suggestion")
            val textBefore = currentInputConnection.getTextBeforeCursor(100, 0)?.toString() ?: ""
            val lastWord = if (textBefore.endsWith(" ")) "" else textBefore.split(InputContextUtils.SPACE_REGEX).lastOrNull() ?: ""
            
            // UNDO: Salva a palavra atual antes de ser substituída pela sugestão
            if (lastWord.isNotEmpty()) {
                rustEngine.pushUndo(lastWord)
                keyboardView.setUndoVisible(true)
                currentInputConnection?.deleteSurroundingText(lastWord.length, 0)
            }
            isDeletingSequence = false
            currentInputConnection?.commitText("$suggestion ", 1)
            
            // Aprende a palavra selecionada
            rustEngine.learnWord(suggestion, InputContextUtils.getCurrentContext(textBefore, skipLast = lastWord.isNotEmpty()))

            // Limpa ghost text e dispara novas predições (NWP)
            currentGhostText = ""
            keyboardView.setGhostText("")
            updatePredictions()
        }

        keyboardView.setOnKeyClickListener { char: String ->
            triggerVibration()
            Log.d("OpenKeyboard", "Key clicked: $char")
            if (char == "⌫") {
                val textBefore = currentInputConnection.getTextBeforeCursor(100, 0)?.toString() ?: ""
                val currentWord = textBefore.split(InputContextUtils.SPACE_REGEX).lastOrNull() ?: ""
                
                // Se estamos iniciando a deleção de uma palavra, salvamos a versão cheia dela
                if (currentWord.isNotEmpty()) {
                    if (!isDeletingSequence) {
                        Log.d("OpenKeyboard", "Backspace: Starting deletion sequence for '$currentWord'")
                        rustEngine.pushUndo(currentWord)
                        isDeletingSequence = true
                        keyboardView.setUndoVisible(true)
                    }
                } else {
                    // Se o campo está vazio ou acabamos de apagar um espaço, resetamos a sequência
                    isDeletingSequence = false
                }

                currentInputConnection.deleteSurroundingText(1, 0)
                keyboardView.setUndoVisible(rustEngine.hasUndo())
            } else if (char == "\n") {
                isDeletingSequence = false
                val textBefore = currentInputConnection?.getTextBeforeCursor(100, 0)?.toString() ?: ""
                val rawLastWord = textBefore.split(InputContextUtils.SPACE_REGEX).lastOrNull { it.isNotEmpty() } ?: ""
                val cleanLastWord = rawLastWord.replace(InputContextUtils.PUNCTUATION_REGEX, "")
                
                if (cleanLastWord.isNotEmpty()) {
                    rustEngine.learnWord(cleanLastWord, InputContextUtils.getCurrentContext(textBefore, skipLast = true))
                }

                val editorInfo = currentInputEditorInfo
                val inputType = editorInfo?.inputType ?: 0
                val isMultiLine = (inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0
                val action = if (editorInfo != null) editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION else EditorInfo.IME_ACTION_NONE

                if (!isMultiLine && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    currentInputConnection?.performEditorAction(action)
                } else {
                    currentInputConnection?.commitText("\n", 1)
                }

            } else if (char == " ") {
                isDeletingSequence = false
                val textBefore = currentInputConnection?.getTextBeforeCursor(100, 0)?.toString() ?: ""
                val rawLastWord = textBefore.split(InputContextUtils.SPACE_REGEX).lastOrNull { it.isNotEmpty() } ?: ""
                val cleanLastWord = rawLastWord.replace(InputContextUtils.PUNCTUATION_REGEX, "")
                
                if (cleanLastWord.isNotEmpty()) {
                    rustEngine.learnWord(cleanLastWord, InputContextUtils.getCurrentContext(textBefore, skipLast = true))
                }

                // AUTOCORREÇÃO: Se houver ghost text, commita a sugestão ao apertar espaço
                if (isPredictionEnabled && currentGhostText.isNotEmpty()) {
                    val currentWord = textBefore.split(InputContextUtils.SPACE_REGEX).lastOrNull() ?: ""
                    if (currentWord.isNotEmpty()) {
                        rustEngine.pushUndo(currentWord)
                    }
                    currentInputConnection.commitText(currentGhostText + " ", 1)
                } else {
                    currentInputConnection.commitText(" ", 1)
                }
            } else {
                isDeletingSequence = false
                currentInputConnection.commitText(char, 1)
            }
            
            updatePredictions()
        }

        keyboardView.setOnGlobeClickListener {
            triggerVibration()
            switchToNextInputMethod()
        }

        keyboardView.setOnUndoClickListener {
            triggerVibration()
            performUndo()
        }

        root.clipChildren = false
        root.clipToPadding = false
        
        root.addView(keyboardView)
        keyboardView.requestFocus()
        keyboardView.invalidate() // Força o primeiro desenho
        return root
    }

    private fun performUndo() {
        val wordToRestore = rustEngine.popUndo()
        Log.d("OpenKeyboard", "performUndo: Popped word to restore: '$wordToRestore'")
        if (wordToRestore.isNotEmpty()) {
            val textBefore = currentInputConnection.getTextBeforeCursor(100, 0)?.toString() ?: ""
            
            // Detecta o "token" atual (palavra parcial ou palavra completa + espaço)
            // Se termina em espaço, o token inclui o espaço.
            val trimmed = textBefore.trimEnd()
            val lastSpace = trimmed.lastIndexOf(' ')
            val tokenLength = if (lastSpace == -1) textBefore.length else textBefore.length - lastSpace - 1
            
            Log.d("OpenKeyboard", "performUndo: Deleting token of length $tokenLength from '$textBefore'")
            
            if (tokenLength > 0) {
                currentInputConnection.deleteSurroundingText(tokenLength, 0)
            }
            
            // Restaura a palavra original
            currentInputConnection.commitText(wordToRestore, 1)
            isDeletingSequence = false
            
            keyboardView.setUndoVisible(rustEngine.hasUndo())
            updatePredictions()
        }
    }

    private fun switchToNextInputMethod() {
        try {
            switchToNextInputMethod(false)
        } catch (e: Exception) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val token = window?.window?.attributes?.token
            if (token != null) {
                imm.switchToNextInputMethod(token, true)
            }
        }
    }

    private fun triggerVibration() {
        if (prefs.getBoolean("norlu_haptic_enabled", true)) {
            keyboardView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // Aciona a manutenção de entropia ao fechar o teclado
        rustEngine.runMaintenance(0.5)
        rustEngine.clearUndo()
        isDeletingSequence = false
        if (::keyboardView.isInitialized) {
            keyboardView.setUndoVisible(false)
            keyboardView.setSuggestions(emptyArray())
            keyboardView.setGhostText("")
        }
    }

    override fun onDestroy() {
        rustEngine.close()
        super.onDestroy()
    }

    private fun updatePredictions() {
        if (!isPredictionEnabled) return

        val textBefore = currentInputConnection?.getTextBeforeCursor(100, 0)?.toString() ?: ""
        
        // Se terminar em espaço, a palavra atual é vazia (dispara NWP)
        val currentWord = if (textBefore.endsWith(" ")) "" else textBefore.split(InputContextUtils.SPACE_REGEX).lastOrNull() ?: ""
        
        // O contexto são as palavras ANTERIORES ao que está sendo digitado
        val context = InputContextUtils.getCurrentContext(textBefore, skipLast = currentWord.isNotEmpty())
        Log.d("OpenKeyboard", "Triggering prediction for word: '$currentWord' with context: ${context.joinToString()}")
        
        // 1. Busca predições (Ghost Text e Candidate Bar)
        rustEngine.getPredictions(currentWord, context) { suggestions: Array<String> ->
            Log.d("OpenKeyboard", "Rust predictions: ${suggestions.joinToString()}")
            keyboardView.setSuggestions(suggestions)
            
            // O motor retorna "palavra:confiança", precisamos apenas da palavra para o Ghost Text
            val rawTopSuggestion = suggestions.firstOrNull() ?: ""
            val topSuggestion = rawTopSuggestion.split(":")[0]
            
            // LÓGICA DE GHOST TEXT (Auto-correção):
            val isExactMatch = suggestions.any { it.split(":")[0].equals(currentWord, ignoreCase = true) }
            
            if (topSuggestion.startsWith(currentWord, ignoreCase = true) && 
                currentWord.length >= 2 && 
                !isExactMatch) {
                
                currentGhostText = topSuggestion.substring(currentWord.length)
                keyboardView.setGhostText(currentGhostText)
            } else {
                currentGhostText = ""
                keyboardView.setGhostText("")
            }
        }

        // 2. Busca probabilidades para Dynamic Hitboxes
        rustEngine.getNextCharProbabilities(currentWord) { probs: Array<String> ->
            Log.d("OpenKeyboard", "Rust hitboxes: ${probs.size} chars")
            val probMap = probs.associate { item: String ->
                val parts = item.split(":")
                val charKey = parts[0].getOrNull(0) ?: ' '
                val probValue = parts.getOrNull(1)?.toDouble() ?: 0.0
                charKey to probValue
            }
            keyboardView.setNextCharProbabilities(probMap)
        }
    }
}
