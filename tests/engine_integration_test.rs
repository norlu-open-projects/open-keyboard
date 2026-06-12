use keyboard_engine::core::KeyboardEngine;

#[test]
fn test_trie_insertion_and_search() {
    let mut engine = KeyboardEngine::new_empty();
    engine.insert_static("casa", 100);
    engine.insert_static("carro", 50);
    engine.insert_static("caso", 80);

    let results = engine.search_smart("cas", &[], 1, false);
    
    // Filtramos apenas as palavras para facilitar o assert
    let words: Vec<String> = results.iter().map(|r| r.0.clone()).collect();
    
    assert!(words.contains(&"casa".to_string()));
    assert!(words.contains(&"caso".to_string()));
    assert!(!words.contains(&"carro".to_string()));
}

#[test]
fn test_recency_influence() {
    let mut engine = KeyboardEngine::new_empty();
    engine.insert_static("casa", 100);
    engine.insert_static("caso", 101);

    // Sem recência, "caso" ganha por ter freq 101
    let r1 = engine.search_smart("cas", &[], 1, false);
    assert_eq!(r1[0].0, "caso");

    // Usa "casa"
    engine.recency.write().unwrap().use_word("casa".to_string());

    // Agora "casa" deve ganhar pelo bônus de recência
    let r2 = engine.search_smart("cas", &[], 1, false);
    assert_eq!(r2[0].0, "casa");
}

#[test]
fn test_nwp_fallbacks() {
    let engine = KeyboardEngine::new();
    let results = engine.search_smart("", &[], 0, false);
    
    // Deve sugerir iniciadores comuns
    let words: Vec<String> = results.iter().map(|r| r.0.clone()).collect();
    assert!(words.contains(&"o".to_string()));
    assert!(words.contains(&"eu".to_string()));
}

#[test]
fn test_trigram_matching_and_learning() {
    let engine = KeyboardEngine::new_empty();
    
    // Simula aprendizado de uma frase: "eu gosto de" -> "teclado"
    let context = vec!["eu".to_string(), "gosto".to_string(), "de".to_string()];
    engine.learn_word("teclado", &context);

    // Quando o usuário digita "gosto de", o motor deve sugerir "teclado" (Trigrama)
    let context_search = vec!["gosto".to_string(), "de".to_string()];
    let results = engine.search_smart("", &context_search, 0, false);

    let top_suggestion = &results[0];
    assert_eq!(top_suggestion.0, "teclado");
    assert_eq!(top_suggestion.3, 3); // Match level 3 (Trigrama)
}

#[test]
fn test_fuzzy_priority_over_strong_context() {
    let mut engine = KeyboardEngine::new_empty();
    engine.insert_static("casa", 10);
    engine.insert_static("carro", 100);
    
    // Contexto favorece "casa"
    engine.static_phrases.insert("na".into(), vec![("casa".into(), 50)]);
    
    // Usuário digita "carr"
    let context = vec!["na".to_string()];
    let results = engine.search_smart("carr", &context, 1, false);
    
    // "carro" deve ganhar por ser completion (2x bonus) mesmo que "casa" tenha contexto
    assert_eq!(results[0].0, "carro");
}

#[test]
fn test_domain_context_learning_and_boosting() {
    let engine = KeyboardEngine::new_empty();
    
    // Define o domínio como 'whatsapp'
    engine.set_domain("whatsapp");
    engine.learn_word("zap", &[]);
    
    // Troca para domínio 'email' e aprende outra palavra
    engine.set_domain("email");
    engine.learn_word("atenciosamente", &[]);
    
    // Volta para 'whatsapp'
    engine.set_domain("whatsapp");
    
    // Quando digitamos "z", "zap" deve ter match_level 4 (Domain Match)
    let results = engine.search_smart("z", &[], 1, false);
    let zap_match = results.iter().find(|r| r.0 == "zap").unwrap();
    assert_eq!(zap_match.3, 4); // Match Level 4: Domínio

    // Em 'email', "zap" não deve ter match_level 4
    engine.set_domain("email");
    let results_email = engine.search_smart("z", &[], 1, false);
    if let Some(zap_match_email) = results_email.iter().find(|r| r.0 == "zap") {
        assert_ne!(zap_match_email.3, 4);
    }
}

#[test]
fn test_cadence_context_bypasses_nwp() {
    let engine = KeyboardEngine::new();
    
    // Em digitação normal, NWP deve retornar algo (ex: 'o', 'eu')
    let normal_results = engine.search_smart("", &[], 0, false);
    assert!(!normal_results.is_empty());
    
    // Em digitação rápida (fast=true), o NWP deve ser ignorado para economizar CPU
    let fast_results = engine.search_smart("", &[], 0, true);
    assert!(fast_results.is_empty());
}
