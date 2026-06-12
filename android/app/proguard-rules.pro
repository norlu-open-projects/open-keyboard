# Impede a ofuscação das classes que fazem a ponte com o Rust (JNI)
-keep class lab.norlu.openkeyboard.core.NativeBridge { *; }
-keepclassmembers class lab.norlu.openkeyboard.core.NativeBridge {
    native <methods>;
}
-keep class lab.norlu.openkeyboard.RustEngine { *; }

# Mantém todos os métodos nativos (external) intocados em qualquer classe
-keepclasseswithmembernames class * {
    native <methods>;
}

# Mantém os nomes dos campos se eles forem usados para persistência de estado nativo
-keepclassmembers class * {
    private long nativeEnginePtr;
}
