package lab.norlu.openkeyboard.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Switch
import android.view.Gravity
import android.content.Context
import android.graphics.Color

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            setBackgroundColor(Color.parseColor("#F3F4F6"))
        }
        
        val title = TextView(this).apply {
            text = "Norlu Keyboard"
            textSize = 28f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 48)
        }
        
        // Seção de Privacidade (Opinião do App)
        val privacyHeader = TextView(this).apply {
            text = "PRIVACIDADE E SEGURANÇA"
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 32, 0, 16)
        }

        val secureDesc = TextView(this).apply {
            text = "🔒 Proteção Anti-Screenshot: ATIVADA POR PADRÃO.\n\nO Norlu Keyboard é seguro por design. A captura de tela (FLAG_SECURE) está permanentemente bloqueada a nível de sistema para impedir que malwares, keyloggers ou gravações de tela capturem o que você digita. Esta não é uma opção, é a nossa opinião técnica sobre privacidade."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 32)
        }

        // Seção de Status do Motor
        val motorHeader = TextView(this).apply {
            text = "SISTEMA"
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 32, 0, 16)
        }

        val statusText = try {
            System.loadLibrary("keyboard_engine")
            "✅ Motor Rust: Ativo (V7-Core)"
        } catch (e: Exception) {
            "❌ Motor Rust: Offline"
        }

        val motorStatus = TextView(this).apply {
            text = statusText
            textSize = 16f
            setPadding(0, 0, 0, 32)
        }

        // Opções Adicionais
        val optionsHeader = TextView(this).apply {
            text = "PREFERÊNCIAS"
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 32, 0, 16)
        }

        val prefs = getSharedPreferences("norlu_prefs", Context.MODE_PRIVATE)
        val hapticSwitch = Switch(this).apply {
            text = "Feedback Tátil ao Digitar"
            textSize = 16f
            isChecked = prefs.getBoolean("norlu_haptic_enabled", true)
            setPadding(0, 16, 0, 16)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("norlu_haptic_enabled", isChecked).apply()
            }
        }
        
        layout.addView(title)
        layout.addView(privacyHeader)
        layout.addView(secureDesc)
        layout.addView(motorHeader)
        layout.addView(motorStatus)
        layout.addView(optionsHeader)
        layout.addView(hapticSwitch)
        
        setContentView(layout)
    }
}
