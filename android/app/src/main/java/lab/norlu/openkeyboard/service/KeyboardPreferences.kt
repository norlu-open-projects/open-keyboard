package lab.norlu.openkeyboard.service

import android.content.Context
import android.content.SharedPreferences

class KeyboardPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("norlu_prefs", Context.MODE_PRIVATE)

    var isHapticEnabled: Boolean
        get() = prefs.getBoolean("norlu_haptic_enabled", true)
        set(value) = prefs.edit().putBoolean("norlu_haptic_enabled", value).apply()

    var isDarkTheme: Boolean
        get() = prefs.getBoolean("norlu_theme_dark", true)
        set(value) = prefs.edit().putBoolean("norlu_theme_dark", value).apply()
}
