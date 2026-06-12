package lab.norlu.openkeyboard.core

/**
 * Representa um endereço de memória (ponteiro) da engine nativa em Rust.
 * O uso de 'value class' garante tipagem forte sem o overhead de alocação de objetos.
 */
@JvmInline
value class NativePointer(val raw: Long) {
    val isValid: Boolean get() = raw != 0L

    companion object {
        val NULL = NativePointer(0L)
    }
}
