package lab.norlu.openkeyboard.utils

object InputContextUtils {

    /**
     * Extrai a última palavra do texto antes do cursor.
     * Retorna "" se terminar em espaço ou estiver vazio.
     * Zero-allocation (usa substrings apenas para o resultado).
     */
    fun getLastWord(text: CharSequence): String {
        val len = text.length
        if (len == 0 || text[len - 1].isWhitespace()) return ""
        
        var start = len - 1
        while (start >= 0 && !text[start].isWhitespace()) {
            start--
        }
        return text.substring(start + 1, len)
    }

    /**
     * Extrai a última palavra do texto removendo caracteres que não são letras ou números.
     */
    fun getCleanLastWord(text: CharSequence): String {
        val word = getLastWord(text)
        if (word.isEmpty()) return ""
        
        // Verifica se precisa de limpeza
        var needsCleaning = false
        for (i in 0 until word.length) {
            if (!word[i].isLetterOrDigit()) {
                needsCleaning = true
                break
            }
        }
        if (!needsCleaning) return word
        
        val sb = StringBuilder(word.length)
        for (i in 0 until word.length) {
            val c = word[i]
            if (c.isLetterOrDigit()) {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Extrai o contexto (até 2 palavras limpas) do texto antes do cursor.
     * @param textBefore O texto bruto.
     * @param skipLast Se true, ignora a última palavra (ex: se está sendo digitada agora).
     */
    fun getCurrentContext(textBefore: CharSequence, skipLast: Boolean = false): Array<String> {
        val contextList = ArrayList<String>(2)
        var i = textBefore.length - 1
        
        var skipCount = if (skipLast) 1 else 0

        while (i >= 0 && contextList.size < 2) {
            // Pula espaços
            while (i >= 0 && textBefore[i].isWhitespace()) {
                i--
            }
            if (i < 0) break
            
            // Encontra fim e início da palavra
            val end = i + 1
            while (i >= 0 && !textBefore[i].isWhitespace()) {
                i--
            }
            val start = i + 1
            
            if (skipCount > 0) {
                skipCount--
            } else {
                val cleanWord = cleanSubstring(textBefore, start, end)
                if (cleanWord.isNotEmpty()) {
                    contextList.add(cleanWord)
                }
            }
        }
        
        // Retorna em ordem cronológica (palavra n-1, palavra n)
        return when (contextList.size) {
            0 -> emptyArray()
            1 -> arrayOf(contextList[0])
            else -> arrayOf(contextList[1], contextList[0])
        }
    }
    
    private fun cleanSubstring(text: CharSequence, start: Int, end: Int): String {
        var needsCleaning = false
        for (i in start until end) {
            if (!text[i].isLetterOrDigit()) {
                needsCleaning = true
                break
            }
        }
        
        val sub = text.subSequence(start, end).toString()
        if (!needsCleaning) return sub
        
        val sb = StringBuilder(sub.length)
        for (i in 0 until sub.length) {
            val c = sub[i]
            if (c.isLetterOrDigit()) {
                sb.append(c)
            }
        }
        return sb.toString()
    }
}
