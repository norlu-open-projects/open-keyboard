package com.norlu.openkeyboard.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.LinearLayout
import android.view.Gravity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        
        val title = TextView(this).apply {
            text = "Configurações do Norlu Keyboard"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        
        val description = TextView(this).apply {
            val status = try {
                System.loadLibrary("keyboard_engine")
                "✅ Motor Rust: CARREGADO E VINCULADO"
            } catch (e: Exception) {
                "❌ Erro ao carregar motor: ${e.message}"
            }
            text = "\n$status\n\nPróximos passos:\n1. Vá em Configurações > Idiomas > Teclado na Tela.\n2. Gerenciar Teclados > Ative o 'Norlu Keyboard'.\n3. Abra uma barra de busca e mude o método de entrada."
            textSize = 16f
            gravity = Gravity.CENTER
        }
        
        layout.addView(title)
        layout.addView(description)
        setContentView(layout)
    }
}
