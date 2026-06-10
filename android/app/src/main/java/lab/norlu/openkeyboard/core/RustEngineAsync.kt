package lab.norlu.openkeyboard.core

import android.util.Log
import kotlinx.coroutines.*

class RustEngineAsync(private val storagePath: String, private val secureKey: ByteArray) {
    private var nativeEnginePtr: Long = 0
    private var isReady = false
    
    // Escopo estruturado para gerenciar o ciclo de vida das operações nativas
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // Inicializa o core nativo sem travar a Main Thread da UI
        scope.launch(Dispatchers.IO) {
            try {
                System.loadLibrary("keyboard_engine")
                nativeEnginePtr = initEngine(storagePath, secureKey)
                isReady = true
                Log.d("RustEngineAsync", "Native engine ready. Pointer: $nativeEnginePtr")
            } catch (e: Exception) {
                Log.e("RustEngineAsync", "Failed to initialize native engine", e)
            }
        }
    }

    fun getPredictions(text: String, context: Array<String>, callback: (Array<String>) -> Unit) {
        if (!isReady || nativeEnginePtr == 0L) {
            callback(emptyArray())
            return
        }
        
        scope.launch {
            val results = withContext(Dispatchers.Default) {
                getSuggestions(nativeEnginePtr, text, context)
            }
            callback(results)
        }
    }

    fun learnWord(word: String, context: Array<String>) {
        if (!isReady || nativeEnginePtr == 0L || word.isEmpty()) return
        scope.launch(Dispatchers.Default) {
            learnWord(nativeEnginePtr, word, context)
        }
    }

    fun close() {
        // Cancela todas as corotinas pendentes (buscas, aprendizado, etc)
        scope.cancel()
        
        if (nativeEnginePtr != 0L) {
            destroyEngine(nativeEnginePtr)
            nativeEnginePtr = 0L
            isReady = false
        }
    }

    fun getNextCharProbabilities(prefix: String, callback: (Array<String>) -> Unit) {
        if (!isReady || nativeEnginePtr == 0L) {
            callback(emptyArray())
            return
        }
        scope.launch {
            val results = withContext(Dispatchers.Default) {
                getNextCharProbabilities(nativeEnginePtr, prefix)
            }
            callback(results)
        }
    }

    fun runMaintenance(survivalThreshold: Double) {
        if (!isReady || nativeEnginePtr == 0L) return
        scope.launch(Dispatchers.IO) {
            runMaintenance(nativeEnginePtr, survivalThreshold)
        }
    }

    fun pushUndo(word: String) {
        if (!isReady || nativeEnginePtr == 0L || word.isEmpty()) return
        pushDeletedWord(nativeEnginePtr, word)
    }

    fun popUndo(): String {
        if (!isReady || nativeEnginePtr == 0L) return ""
        return popDeletedWord(nativeEnginePtr)
    }

    fun hasUndo(): Boolean {
        if (!isReady || nativeEnginePtr == 0L) return false
        return hasUndoItems(nativeEnginePtr)
    }

    fun clearUndo() {
        if (!isReady || nativeEnginePtr == 0L) return
        clearUndo(nativeEnginePtr)
    }

    private external fun initEngine(storagePath: String, secureKey: ByteArray): Long
    private external fun getSuggestions(ptr: Long, text: String, context: Array<String>): Array<String>
    private external fun learnWord(ptr: Long, word: String, context: Array<String>)
    private external fun getNextCharProbabilities(ptr: Long, prefix: String): Array<String>
    private external fun runMaintenance(ptr: Long, survivalThreshold: Double)
    private external fun destroyEngine(ptr: Long)

    private external fun pushDeletedWord(ptr: Long, word: String)
    private external fun popDeletedWord(ptr: Long): String
    private external fun hasUndoItems(ptr: Long): Boolean
    private external fun clearUndo(ptr: Long)
    }

