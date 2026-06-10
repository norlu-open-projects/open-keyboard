package lab.norlu.openkeyboard.utils

object InputContextUtils {
    val SPACE_REGEX = Regex("\\s+")
    val PUNCTUATION_REGEX = Regex("[^\\p{L}\\p{N}]")

    /**
     * Extrai o contexto (últimas 2 palavras) do texto antes do cursor.
     * @param textBefore O texto bruto obtido do InputConnection.
     * @param skipLast Se true, remove a última palavra (útil se estiver sendo digitada agora).
     */
    fun getCurrentContext(textBefore: String, skipLast: Boolean = false): Array<String> {
        // Split por espaços, mantendo entradas vazias para detectar se termina em espaço
        val rawWords = textBefore.split(SPACE_REGEX)
        
        // Limpa pontuação e remove palavras vazias
        val cleanWords = rawWords.map { it.replace(PUNCTUATION_REGEX, "") }
            .filter { it.isNotEmpty() }

        val contextWords = if (skipLast && cleanWords.isNotEmpty()) {
            cleanWords.dropLast(1)
        } else {
            cleanWords
        }
        
        return contextWords.takeLast(2).toTypedArray()
    }
}
