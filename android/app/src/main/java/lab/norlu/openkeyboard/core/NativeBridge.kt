package lab.norlu.openkeyboard.core

/**
 * NativeBridge: Interface de baixo nível (JNI) para o motor Rust.
 * Este objeto centraliza todas as assinaturas 'external' e o carregamento da biblioteca nativa.
 */
internal object NativeBridge {
    init {
        try {
            System.loadLibrary("keyboard_engine")
        } catch (e: Exception) {
            android.util.Log.e("NativeBridge", "Falha ao carregar a biblioteca 'keyboard_engine'", e)
        }
    }

    @JvmStatic external fun initEngine(storagePath: String, secureKey: ByteArray): Long
    @JvmStatic external fun destroyEngine(ptr: Long)
    
    @JvmStatic external fun setDomainContext(ptr: Long, domain: String)
    @JvmStatic external fun setTemporalContext(ptr: Long, context: String)
    @JvmStatic external fun updateSemanticContext(ptr: Long, text: String)
    @JvmStatic external fun setIncognitoMode(ptr: Long, isIncognito: Boolean)
    @JvmStatic external fun incrementAnalytics(ptr: Long, category: String, value: Int)
    @JvmStatic external fun getAnalyticsData(ptr: Long, page: Int, filter: String): String
    @JvmStatic external fun getSuggestions(ptr: Long, text: String, context: Array<String>, isFastTyping: Boolean): Array<String>
    @JvmStatic external fun learnWord(ptr: Long, word: String, context: Array<String>)
    @JvmStatic external fun learnWordWithBoost(ptr: Long, word: String, context: Array<String>, boost: Int)
    @JvmStatic external fun getNextCharProbabilities(ptr: Long, prefix: String): Array<String>
    @JvmStatic external fun runMaintenance(ptr: Long, survivalThreshold: Double)

    @JvmStatic external fun pushDeletedWord(ptr: Long, word: String)
    @JvmStatic external fun popDeletedWord(ptr: Long): String
    @JvmStatic external fun hasUndoItems(ptr: Long): Boolean
    @JvmStatic external fun clearUndo(ptr: Long)
}
