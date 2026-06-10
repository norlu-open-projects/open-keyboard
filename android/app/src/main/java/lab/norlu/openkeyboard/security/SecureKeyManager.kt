package lab.norlu.openkeyboard.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object SecureKeyManager {
    private const val KEY_ALIAS = "NorluStorageKey"

    /**
     * Obtém ou gera uma chave AES de 256 bits no Android Keystore.
     * Retorna os bytes brutos para uso no motor Rust (ChaCha20).
     */
    fun getOrCreateSecureKey(): ByteArray {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            
            keyGenerator.init(builder)
            keyGenerator.generateKey()
        }
        
        val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        // Para o ChaCha20 no Rust, precisamos dos bytes brutos da chave de 32 bytes.
        return secretKey?.encoded ?: ByteArray(32) 
    }
}
