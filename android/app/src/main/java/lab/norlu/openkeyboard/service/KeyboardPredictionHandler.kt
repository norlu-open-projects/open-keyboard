package lab.norlu.openkeyboard.service

import android.view.inputmethod.InputConnection
import lab.norlu.openkeyboard.core.RustEngineAsync
import lab.norlu.openkeyboard.ui.keyboard.OptimisedKeyboardView
import lab.norlu.openkeyboard.utils.InputContextUtils

class KeyboardPredictionHandler(
    private val rustEngine: RustEngineAsync,
    private val keyboardView: OptimisedKeyboardView
) {
    var currentGhostText = ""
    var isPredictionEnabled = true

    /**
     * Atualiza as sugestões e as hitboxes dinâmicas baseadas no texto atual.
     * Otimizado para evitar alocações pesadas.
     */
    fun updatePredictions(ic: InputConnection?) {
        if (!isPredictionEnabled || ic == null) return

        val textBefore = ic.getTextBeforeCursor(100, 0) ?: ""
        val currentWord = InputContextUtils.getLastWord(textBefore)
        val context = InputContextUtils.getCurrentContext(textBefore, skipLast = currentWord.isNotEmpty())
        val isFastTyping = keyboardView.isFastTyping

        // 1. Busca predições (Candidate Bar e Ghost Text)
        rustEngine.getPredictions(currentWord.toString(), context, isFastTyping) { suggestions: Array<String> ->
            keyboardView.setSuggestions(suggestions)
            
            val rawTopSuggestion = suggestions.getOrNull(0) ?: ""
            val colonIndex = rawTopSuggestion.indexOf(':')
            val topSuggestionWord = if (colonIndex != -1) rawTopSuggestion.substring(0, colonIndex) else rawTopSuggestion
            
            // Verifica se a sugestão principal é apenas um sufixo do que está sendo digitado
            val isExactMatch = suggestions.any { sug ->
                val word = if (sug.contains(':')) sug.split(':')[0] else sug
                word.equals(currentWord.toString(), ignoreCase = true)
            }
            
            if (topSuggestionWord.startsWith(currentWord, ignoreCase = true) && 
                currentWord.length >= 2 && 
                !isExactMatch) {
                
                currentGhostText = topSuggestionWord.substring(currentWord.length)
                keyboardView.setGhostText(currentGhostText)
            } else {
                currentGhostText = ""
                keyboardView.setGhostText("")
            }
        }

        // 2. Busca probabilidades para Dynamic Hitboxes (WCAG AAA)
        rustEngine.getNextCharProbabilities(currentWord.toString()) { probs: Array<String> ->
            val probMap = mutableMapOf<Char, Double>()
            for (item in probs) {
                val colonIdx = item.indexOf(':')
                if (colonIdx != -1) {
                    val charKey = item.getOrNull(0) ?: ' '
                    val probValue = item.substring(colonIdx + 1).toDoubleOrNull() ?: 0.0
                    probMap[charKey] = probValue
                }
            }
            keyboardView.setNextCharProbabilities(probMap)
        }
    }
}
