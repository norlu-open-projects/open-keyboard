# Impede a ofuscação das classes que fazem a ponte com o Rust (JNI)
-keep class com.norlu.openkeyboard.RustEngineAsync { *; }
-keep class com.norlu.openkeyboard.RustEngine { *; }

# Mantém todos os métodos nativos (external) intocados em qualquer classe
-keepclasseswithmembernames class * {
    native <methods>;
}

# Mantém os nomes dos campos se eles forem usados para persistência de estado nativo
-keepclassmembers class * {
    private long nativeEnginePtr;
}
