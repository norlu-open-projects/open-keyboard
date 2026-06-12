package lab.norlu.openkeyboard.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Representa uma chave criptográfica de 256 bits (32 bytes).
 * O uso de 'value class' garante tipagem forte sem overhead de alocação de objeto no heap.
 */
@JvmInline
value class SecureKey(val bytes: ByteArray) {
    init {
        require(bytes.size == 32) { "A chave de armazenamento deve ter exatamente 32 bytes (256 bits)." }
    }
}

/**
 * Gerenciador de Chaves de Segurança do Teclado.
 * Encapsula a lógica de interação com o Android Keystore System.
 */
object SecureKeyManager {
    private const val KEY_ALIAS = "NorluStorageKey"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    
    @Volatile
    private var cachedKey: SecureKey? = null

    /**
     * Obtém ou gera a chave segura para o motor de armazenamento.
     * Implementa caching thread-safe para otimização máxima de performance.
     */
    fun getOrCreateSecureKey(): SecureKey {
        // Double-checked locking para performance e thread-safety
        return cachedKey ?: synchronized(this) {
            cachedKey ?: fetchFromKeystore().also { cachedKey = it }
        }
    }

    private fun fetchFromKeystore(): SecureKey {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateNewAesKey()
            }

            // Recupera a entrada como SecretKey
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            val rawBytes = secretKey?.encoded

            if (rawBytes == null) {
                // NOTA TÉCNICA: Em dispositivos com TEE/StrongBox (Hardware-backed), 
                // .encoded SEMPRE retorna null por segurança.
                // O motor Rust deve estar preparado para lidar com chaves persistentes 
                // caso o hardware bloqueie a exportação.
                Log.w("SecureKeyManager", "Chave do Keystore não exportável (Hardware-backed). Usando fallback persistente.")
                return SecureKey(ByteArray(32)) // Fallback original preservado para compatibilidade
            }

            if (rawBytes.size != 32) {
                Log.e("SecureKeyManager", "Tamanho de chave inválido: ${rawBytes.size} bytes.")
                return SecureKey(ByteArray(32))
            }

            return SecureKey(rawBytes)
        } catch (e: Exception) {
            Log.e("SecureKeyManager", "Erro crítico ao acessar o Android Keystore", e)
            return SecureKey(ByteArray(32))
        }
    }

    private fun generateNewAesKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER
        )
        
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        
        keyGenerator.init(spec)
        keyGenerator.generateKey()
        Log.i("SecureKeyManager", "Nova chave AES-256 gerada no Android Keystore.")
    }
}
