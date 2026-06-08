package com.norlu.openkeyboard.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout

import com.norlu.openkeyboard.core.RustEngineAsync
import com.norlu.openkeyboard.security.SecureKeyManager
import com.norlu.openkeyboard.ui.keyboard.OptimisedKeyboardView
import com.norlu.openkeyboard.ui.settings.SettingsActivity
import com.norlu.openkeyboard.utils.InputContextUtils

class AdvancedKeyboardService : InputMethodService() {
    private lateinit var rustEngine: RustEngineAsync
    private lateinit var keyboardView: OptimisedKeyboardView
    private var isPredictionEnabled = true
    private var currentGhostText = ""
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("norlu_prefs", Context.MODE_PRIVATE)
        
        // Inicializa o motor Rust com a chave segura do Hardware (Keystore) via SecureKeyManager
        val secureKey = SecureKeyManager.getOrCreateSecureKey()
        rustEngine = RustEngineAsync(filesDir.absolutePath, secureKey)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        
        // Proteção contra prints baseada em configuração (default: true)
        val isSecureEnabled = prefs.getBoolean("flag_secure_enabled", true)
        if (isSecureEnabled) {
            window?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        val inputType = attribute?.inputType ?: 0
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
        
        isPredictionEnabled = !(variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD || 
                                variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                                variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)
    }

    override fun onCreateInputView(): View {
        val root = FrameLayout(this)
        keyboardView = OptimisedKeyboardView(this)
        
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        keyboardView.layoutParams = params

        keyboardView.setOnSuggestionClickListener { suggestion: String ->
            Log.d("NorluService", "Suggestion clicked: $suggestion")
            val textBefore = currentInputConnection.getTextBeforeCursor(100, 0)?.toString() ?: ""
            val lastWord = if (textBefore.endsWith(" ")) "" else textBefore.split(InputContextUtils.SPACE_REGEX).lastOrNull() ?: ""
            
            // Substitui a palavra parcial pela sugestão completa
            if (lastWord.isNotEmpty()) {
                currentInputConnection.deleteSurroundingText(lastWord.length, 0)
            }
            currentInputConnection.commitText("$suggestion ", 1)
            
            // Aprende a palavra selecionada
            rustEngine.learnWord(suggestion, InputContextUtils.getCurrentContext(textBefore, skipLast = lastWord.isNotEmpty()))

            // Limpa ghost text e dispara novas predições (NWP)
            currentGhostText = ""
            keyboardView.setGhostText("")
            updatePredictions()
        }

        keyboardView.setOnKeyClickListener { char: String ->
            Log.d("NorluService", "Key clicked: $char")
            if (char == "⚙️") {
                val intent = Intent(this, SettingsActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else if (char == "⌫") {
                currentInputConnection.deleteSurroundingText(1, 0)
            } else if (char == "\n") {
                val textBefore = currentInputConnection.getTextBeforeCursor(100, 0)?.toString() ?: ""
                val rawLastWord = textBefore.split(InputContextUtils.SPACE_REGEX).lastOrNull { it.isNotEmpty() } ?: ""
                val cleanLastWord = rawLastWord.replace(InputContextUtils.PUNCTUATION_REGEX, "")
                
                if (cleanLastWord.isNotEmpty()) {
                    rustEngine.learnWord(cleanLastWord, InputContextUtils.getCurrentContext(textBefore, skipLast = true))
                }

                val action = currentInputEditorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    currentInputConnection.performEditorAction(action)
                } else {
                    currentInputConnection.commitText("\n", 1)
                }
            } else if (char == " ") {
                val textBefore = currentInputConnection.getTextBeforeCursor(100, 0)?.toString() ?: ""
                val rawLastWord = textBefore.split(InputContextUtils.SPACE_REGEX).lastOrNull { it.isNotEmpty() } ?: ""
                val cleanLastWord = rawLastWord.replace(InputContextUtils.PUNCTUATION_REGEX, "")
                
                if (cleanLastWord.isNotEmpty()) {
                    rustEngine.learnWord(cleanLastWord, InputContextUtils.getCurrentContext(textBefore, skipLast = true))
                }

                // AUTOCORREÇÃO: Se houver ghost text, commita a sugestão ao apertar espaço
                if (isPredictionEnabled && currentGhostText.isNotEmpty()) {
                    currentInputConnection.commitText(currentGhostText + " ", 1)
                } else {
                    currentInputConnection.commitText(" ", 1)
                }
            } else {
                currentInputConnection.commitText(char, 1)
            }
            
            updatePredictions()
        }

        keyboardView.setOnGlobeClickListener {
            switchToNextInputMethod()
        }

        root.addView(keyboardView)
        keyboardView.requestFocus()
        keyboardView.invalidate() // Força o primeiro desenho
        return root
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

    override fun onFinishInput() {
        super.onFinishInput()
        // Aciona a manutenção de entropia ao fechar o teclado
        rustEngine.runMaintenance(0.5)
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
        Log.d("NorluService", "Triggering prediction for word: '$currentWord' with context: ${context.joinToString()}")
        
        // 1. Busca predições (Ghost Text e Candidate Bar)
        rustEngine.getPredictions(currentWord, context) { suggestions: Array<String> ->
            Log.d("NorluService", "Rust predictions: ${suggestions.joinToString()}")
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
            Log.d("NorluService", "Rust hitboxes: ${probs.size} chars")
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
