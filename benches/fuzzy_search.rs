use criterion::{black_box, criterion_group, criterion_main, Criterion};
use rustkey::engine::KeyboardEngine;

fn bench_fuzzy_search(c: &mut Criterion) {
    let mut engine = KeyboardEngine::new();
    
    // Simula um dicionário pequeno/médio
    for i in 0..1000 {
        engine.insert(&format!("palavra_{}", i), i as u32);
    }
    engine.insert("abacaxi", 1000);
    engine.insert("abacate", 900);
    engine.insert("aba", 800);

    c.bench_function("fuzzy_search_k2", |b| {
        b.iter(|| {
            engine.search_fuzzy(black_box("abacax"), black_box(2))
        })
    });
}

criterion_group!(benches, bench_fuzzy_search);
criterion_main!(benches);
