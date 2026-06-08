use keyboard_engine::core::KeyboardEngine;

#[test]
fn test_trie_insertion_and_search() {
    let mut engine = KeyboardEngine::new();
    engine.insert_static("casa", 100);
    engine.insert_static("carro", 50);
    engine.insert_static("caso", 80);

    let results = engine.search_smart("cas", &[], 1);
    
    // Filtramos apenas as palavras para facilitar o assert
    let words: Vec<String> = results.iter().map(|r| r.0.clone()).collect();
    
    assert!(words.contains(&"casa".to_string()));
    assert!(words.contains(&"caso".to_string()));
    assert!(!words.contains(&"carro".to_string()));
}

#[test]
fn test_recency_influence() {
    let mut engine = KeyboardEngine::new();
    engine.insert_static("casa", 100);
    engine.insert_static("caso", 101);

    // Sem recência, "caso" ganha por ter freq 101
    let r1 = engine.search_smart("cas", &[], 1);
    assert_eq!(r1[0].0, "caso");

    // Usa "casa"
    engine.recency.use_word("casa".to_string());

    // Agora "casa" deve ganhar pelo bônus de recência
    let r2 = engine.search_smart("cas", &[], 1);
    assert_eq!(r2[0].0, "casa");
}

#[test]
fn test_nwp_fallbacks() {
    let engine = KeyboardEngine::new();
    let results = engine.search_smart("", &[], 0);
    
    // Deve sugerir iniciadores comuns
    let words: Vec<String> = results.iter().map(|r| r.0.clone()).collect();
    assert!(words.contains(&"o".to_string()));
    assert!(words.contains(&"eu".to_string()));
}

#[test]
fn test_trigram_matching_and_learning() {
    let engine = KeyboardEngine::new();
    
    // Simula aprendizado de uma frase: "eu gosto de" -> "teclado"
    let context = vec!["eu".to_string(), "gosto".to_string(), "de".to_string()];
    engine.learn_word("teclado", &context);

    // Quando o usuário digita "gosto de", o motor deve sugerir "teclado" (Trigrama)
    let context_search = vec!["gosto".to_string(), "de".to_string()];
    let results = engine.search_smart("", &context_search, 0);

    let top_suggestion = &results[0];
    assert_eq!(top_suggestion.0, "teclado");
    assert_eq!(top_suggestion.3, 3); // Match level 3 (Trigrama)
}

#[test]
fn test_fuzzy_priority_over_strong_context() {
    let mut engine = KeyboardEngine::new();
    engine.insert_static("casa", 10);
    engine.insert_static("carro", 100);
    
    // Contexto favorece "casa"
    engine.static_phrases.insert("na".to_string(), vec![("casa".to_string(), 50)]);
    
    // Usuário digita "carr"
    let context = vec!["na".to_string()];
    let results = engine.search_smart("carr", &context, 1);
    
    // "carro" deve ganhar por ser completion (2x bonus) mesmo que "casa" tenha contexto
    assert_eq!(results[0].0, "carro");
}
