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
    var isAutocompleteSuppressed = false
    private var lastSemanticExtractionTime = 0L

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

        // Topic Modeling (Semantic Context) Debounce Logic
        if (currentWord.isEmpty()) {
            val now = System.currentTimeMillis()
            if (now - lastSemanticExtractionTime > 3000) {
                lastSemanticExtractionTime = now
                val longText = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
                rustEngine.updateSemanticContext(longText)
            }
        }

        // 1. Busca predições (Candidate Bar e Ghost Text)
        rustEngine.getPredictions(currentWord.toString(), context, isFastTyping) { suggestions: Array<String> ->
            val finalSuggestions = if (isAutocompleteSuppressed && currentWord.isNotEmpty()) {
                val list = suggestions.toMutableList()
                // Remove a palavra exata caso já exista
                list.removeAll { it.substringBefore(':').equals(currentWord.toString(), ignoreCase = true) }
                // Garante que existam pelo menos 2 itens para ocupar o 3º slot (índice 2)
                while (list.size < 2) { list.add("") }
                if (list.size > 2) {
                    list.add(2, currentWord.toString())
                } else {
                    list.add(currentWord.toString())
                }
                list.toTypedArray()
            } else {
                suggestions
            }
            keyboardView.setSuggestions(finalSuggestions)
            
            val rawTopSuggestion = finalSuggestions.getOrNull(0) ?: ""
            val colonIndex = rawTopSuggestion.indexOf(':')
            val topSuggestionWord = if (colonIndex != -1) rawTopSuggestion.substring(0, colonIndex) else rawTopSuggestion
            
            // Verifica se a sugestão principal é apenas um sufixo do que está sendo digitado
            val isExactMatch = finalSuggestions.any { sug ->
                val word = if (sug.contains(':')) sug.split(':')[0] else sug
                word.equals(currentWord.toString(), ignoreCase = true)
            }
            
            if (!isAutocompleteSuppressed && topSuggestionWord.startsWith(currentWord.toString(), ignoreCase = true) && 
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
