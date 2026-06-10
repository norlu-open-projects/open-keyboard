use jni::JNIEnv;
use jni::objects::{JClass, JString, JByteArray, JObjectArray};
use jni::sys::{jlong, jobjectArray, jstring, jboolean};
use crate::core::KeyboardEngine;
use android_logger::Config;
use log::LevelFilter;

/// Inicializa o motor e retorna o ponteiro para o Kotlin/Java (persistência de estado).
/// Recebe storage_path do context.filesDir e uma chave de 32 bytes do Android Keystore.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_lab_norlu_openkeyboard_core_RustEngineAsync_initEngine(
    mut env: JNIEnv,
    _class: JClass,
    storage_path: JString,
    key: JByteArray,
) -> jlong {
    // Inicializa o logger para o Android Logcat
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Debug)
            .with_tag("OpenKeyboardEngine"),
    );
    log::debug!("Rust Engine initializing...");

    let path_rust: String = env.get_string(&storage_path)
        .map(|s| s.into())
        .unwrap_or_else(|_| ".keyboard_storage".to_string());

    let mut key_bytes = [0u8; 32];
    let j_bytes = env.convert_byte_array(&key).unwrap_or_default();
    if j_bytes.len() == 32 {
        key_bytes.copy_from_slice(&j_bytes);
    }

    let engine = Box::new(KeyboardEngine::new_with_storage(path_rust, key_bytes));
    Box::into_raw(engine) as jlong
}

/// Executa a busca inteligente usando o ponteiro persistido e contexto de palavras.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_lab_norlu_openkeyboard_core_RustEngineAsync_getSuggestions(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    input: JString,
    context: JObjectArray,
) -> jobjectArray {
    let engine = unsafe { &*(engine_ptr as *const KeyboardEngine) };
    
    let input_rust: String = env.get_string(&input)
        .map(|s| s.into())
        .unwrap_or_default();

    // Converte context (String[]) para Vec<String>
    let mut context_rust = Vec::new();
    if !context.is_null() {
        if let Ok(len) = env.get_array_length(&context) {
            for i in 0..len {
                if let Ok(obj) = env.get_object_array_element(&context, i) {
                    let j_str: JString = obj.into();
                    if let Ok(s) = env.get_string(&j_str) {
                        context_rust.push(s.into());
                    }
                }
            }
        }
    }

    // Lógica de DISTÂNCIA DINÂMICA
    let max_distance = match input_rust.len() {
        0..=1 => 0, // Precisão total para 1 letra
        2..=4 => 1, // 1 erro para palavras curtas
        _ => 2,     // 2 erros para palavras longas
    };

    log::debug!("Rust getSuggestions for: '{}' (context={:?}, dyn_dist={})", input_rust, context_rust, max_distance);

    let results = engine.search_smart(&input_rust, &context_rust, max_distance);
    
    // Converte o Vec de resultados para um String[] do Java (Limitado a 10)
    let suggestions: Vec<String> = results.into_iter()
        .take(10)
        .map(|(s, _, _, match_level)| format!("{}:{}", s, match_level))
        .collect();

    let fallback_str = env.new_string("").unwrap_or_else(|_| env.new_string("ERR").unwrap());
    let array = match env.new_object_array(
        suggestions.len() as i32,
        "java/lang/String",
        &fallback_str,
    ) {
        Ok(a) => a,
        Err(e) => {
            log::error!("Failed to create object array: {:?}", e);
            // Retorna array vazio em caso de falha crítica na JNI em vez de null
            return env.new_object_array(0, "java/lang/String", &fallback_str).unwrap().into_raw();
        }
    };

    for (i, val) in suggestions.iter().enumerate() {
        if let Ok(j_str) = env.new_string(val) {
            let _ = env.set_object_array_element(&array, i as i32, j_str);
        }
    }

    array.into_raw()
}

/// Permite que o teclado aprenda uma nova palavra e sua frase de contexto.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_lab_norlu_openkeyboard_core_RustEngineAsync_learnWord(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    word: JString,
    context: JObjectArray,
) {
    if engine_ptr == 0 { return; }
    let engine = unsafe { &*(engine_ptr as *const KeyboardEngine) };
    let word_rust: String = env.get_string(&word).map(|s| s.into()).unwrap_or_default();
    
    let mut context_rust = Vec::new();
    let len = env.get_array_length(&context).unwrap_or(0);
    for i in 0..len {
        if let Ok(obj) = env.get_object_array_element(&context, i) {
            let j_str: JString = obj.into();
            if let Ok(s) = env.get_string(&j_str) {
                context_rust.push(s.into());
            }
        }
    }

    if !word_rust.is_empty() {
        engine.learn_word(&word_rust, &context_rust);
    }
}

/// Aciona a manutenção de entropia do motor (Poda profunda).
#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_lab_norlu_openkeyboard_core_RustEngineAsync_runMaintenance(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    survival_threshold: f64,
) {
    let engine = unsafe { &*(engine_ptr as *const KeyboardEngine) };
    engine.cleanup_user_trie(survival_threshold);
}

/// Libera a memória do motor quando o serviço for destruído no Android.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_lab_norlu_openkeyboard_core_RustEngineAsync_destroyEngine(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr != 0 {
        unsafe {
            log::info!("FFI: Iniciando destruição ordenada do motor nativo...");
            let engine = Box::from_raw(engine_ptr as *mut KeyboardEngine);
            engine.flush();
            log::info!("FFI: Motor nativo destruído e memória liberada.");
        }
    }
}

/// Adição para suporte a Dynamic Hitboxes via JNI (WCAG AAA)
#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_lab_norlu_openkeyboard_core_RustEngineAsync_getNextCharProbabilities(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    prefix: JString,
) -> jobjectArray {
    let engine = unsafe { &*(engine_ptr as *const KeyboardEngine) };
    let prefix_rust: String = env.get_string(&prefix).map(|s| s.into()).unwrap_or_default();
    
    log::debug!("Rust getNextCharProbabilities for: '{}'", prefix_rust);

    let probs = engine.get_next_char_probabilities(&prefix_rust);
    
    // Retorna como um array de strings formatado "c:prob" (Limitado a 25 caracteres mais prováveis)
    let prob_strings: Vec<String> = probs.into_iter()
        .take(25)
        .map(|(c, p)| format!("{}:{}", c, p))
        .collect();

    let fallback_str = env.new_string("").unwrap_or_else(|_| env.new_string("ERR").unwrap());
    let array = match env.new_object_array(
        prob_strings.len() as i32,
        "java/lang/String",
        &fallback_str,
    ) {
        Ok(a) => a,
        Err(e) => {
            log::error!("Failed to create probabilities array: {:?}", e);
            return env.new_object_array(0, "java/lang/String", &fallback_str).unwrap().into_raw();
        }
    };

    for (i, val) in prob_strings.iter().enumerate() {
        if let Ok(j_str) = env.new_string(val) {
            let _ = env.set_object_array_element(&array, i as i32, j_str);
        }
    }

    array.into_raw()
}

/// Pushes a word into the session undo stack.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_lab_norlu_openkeyboard_core_RustEngineAsync_pushDeletedWord(
    mut env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
    word: JString,
) {
    if engine_ptr == 0 { return; }
    let engine = unsafe { &*(engine_ptr as *const KeyboardEngine) };
    let word_rust: String = env.get_string(&word).map(|s| s.into()).unwrap_or_default();
    if !word_rust.is_empty() {
        engine.undo.push(&word_rust);
    }
}

/// Pops the last word from the session undo stack.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_lab_norlu_openkeyboard_core_RustEngineAsync_popDeletedWord(
    env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) -> jstring {
    if engine_ptr == 0 { 
        return env.new_string("").unwrap().into_raw(); 
    }
    let engine = unsafe { &*(engine_ptr as *const KeyboardEngine) };
    
    let result = if let Some(word) = engine.undo.pop() {
        env.new_string(word).unwrap_or_else(|_| env.new_string("").unwrap())
    } else {
        env.new_string("").unwrap()
    };

    result.into_raw()
}

/// Checks if there are any words in the undo stack.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_lab_norlu_openkeyboard_core_RustEngineAsync_hasUndoItems(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) -> jboolean {
    if engine_ptr == 0 { return 0; }
    let engine = unsafe { &*(engine_ptr as *const KeyboardEngine) };
    if engine.undo.has_items() { 1 } else { 0 }
}

/// Clears the undo stack and zeroes the memory.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_lab_norlu_openkeyboard_core_RustEngineAsync_clearUndo(
    _env: JNIEnv,
    _class: JClass,
    engine_ptr: jlong,
) {
    if engine_ptr == 0 { return; }
    let engine = unsafe { &*(engine_ptr as *const KeyboardEngine) };
    engine.undo.clear();
}
