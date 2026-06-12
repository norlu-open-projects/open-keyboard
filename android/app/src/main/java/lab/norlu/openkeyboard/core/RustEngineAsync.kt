package lab.norlu.openkeyboard.core

import android.util.Log
import kotlinx.coroutines.*
import lab.norlu.openkeyboard.security.SecureKey

/**
 * RustEngineAsync: Orquestrador de alto nível para o motor nativo.
 * Gerencia a concorrência, o ciclo de vida do ponteiro e a segurança de tipos.
 */
class RustEngineAsync(private val storagePath: String, private val secureKey: SecureKey) {
    
    @Volatile
    private var ptr: NativePointer = NativePointer.NULL
    private var pendingDomain: String? = null
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch(Dispatchers.IO) {
            try {
                val rawPtr = NativeBridge.initEngine(storagePath, secureKey.bytes)
                ptr = NativePointer(rawPtr)
                Log.d("RustEngineAsync", "Motor nativo pronto. Pointer: ${ptr.raw}")
                
                // Aplica domínio pendente caso tenha sido definido antes do motor estar pronto
                pendingDomain?.let { domain ->
                    NativeBridge.setDomainContext(rawPtr, domain)
                    pendingDomain = null
                }
            } catch (e: Exception) {
                Log.e("RustEngineAsync", "Falha ao inicializar o motor nativo", e)
            }
        }
    }

    private inline fun <T> withEngine(crossinline block: (Long) -> T): T? {
        val currentPtr = ptr
        return if (currentPtr.isValid) {
            block(currentPtr.raw)
        } else {
            null
        }
    }

    fun setDomain(domain: String) {
        val currentPtr = ptr
        if (currentPtr.isValid) {
            scope.launch(Dispatchers.IO) {
                NativeBridge.setDomainContext(currentPtr.raw, domain)
            }
        } else {
            pendingDomain = domain
        }
    }

    fun getPredictions(text: String, context: Array<String>, isFastTyping: Boolean, callback: (Array<String>) -> Unit) {
        scope.launch {
            val results = withContext(Dispatchers.Default) {
                withEngine { NativeBridge.getSuggestions(it, text, context, isFastTyping) } ?: emptyArray()
            }
            callback(results)
        }
    }

    fun learnWord(word: String, context: Array<String>) {
        if (word.isEmpty()) return
        scope.launch(Dispatchers.Default) {
            withEngine { NativeBridge.learnWord(it, word, context) }
        }
    }

    fun getNextCharProbabilities(prefix: String, callback: (Array<String>) -> Unit) {
        scope.launch {
            val results = withContext(Dispatchers.Default) {
                withEngine { NativeBridge.getNextCharProbabilities(it, prefix) } ?: emptyArray()
            }
            callback(results)
        }
    }

    fun runMaintenance(survivalThreshold: Double) {
        scope.launch(Dispatchers.IO) {
            withEngine { NativeBridge.runMaintenance(it, survivalThreshold) }
        }
    }

    // --- Operações de Undo (Síncronas para garantir ordem correta no InputHandler) ---

    fun pushUndo(word: String) {
        if (word.isEmpty()) return
        withEngine { NativeBridge.pushDeletedWord(it, word) }
    }

    fun popUndo(): String {
        return withEngine { NativeBridge.popDeletedWord(it) } ?: ""
    }

    fun hasUndo(): Boolean {
        return withEngine { NativeBridge.hasUndoItems(it) } ?: false
    }

    fun clearUndo() {
        withEngine { NativeBridge.clearUndo(it) }
    }

    fun close() {
        scope.cancel()
        val currentPtr = ptr
        if (currentPtr.isValid) {
            ptr = NativePointer.NULL
            Log.i("RustEngineAsync", "Encerrando motor nativo...")
            runBlocking {
                withContext(Dispatchers.IO) {
                    try {
                        NativeBridge.destroyEngine(currentPtr.raw)
                        Log.i("RustEngineAsync", "Motor nativo encerrado com sucesso.")
                    } catch (e: Exception) {
                        Log.e("RustEngineAsync", "Erro ao encerrar motor nativo", e)
                    }
                }
            }
        }
    }
}
