package lab.norlu.openkeyboard.ui.keyboard

import android.graphics.Paint
import android.graphics.Typeface

class KeyboardThemeManager {
    var isDark: Boolean = true
    var colorBackground: Int = 0xFF121212.toInt()
    var colorKey: Int = 0xFF3A3A3C.toInt()
    var colorFunctionKey: Int = 0xFF2C2C2E.toInt()
    var colorKeyPushed: Int = 0xFF5A5A5C.toInt()
    var colorText: Int = 0xFFFFFFFF.toInt()
    var colorHighlight: Int = 0xFF50C878.toInt()
    var colorGhost: Int = 0x66FFFFFF.toInt()
    var colorAccent: Int = 0xFF444444.toInt()
    var colorShadow: Int = 0xFF000000.toInt()

    var textOffset: Float = 0f

    val backgroundPaint = Paint().apply { color = colorBackground }
    val shadowPaint = Paint().apply { color = colorShadow; style = Paint.Style.FILL }
    val keyPaint = Paint().apply { style = Paint.Style.FILL }
    val textPaint = Paint().apply {
        color = colorText
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val ghostPaint = Paint().apply {
        color = colorGhost
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun applyTheme(dark: Boolean) {
        this.isDark = dark
        if (dark) {
            colorBackground = 0xFF121212.toInt()
            colorKey = 0xFF3A3A3C.toInt()
            colorFunctionKey = 0xFF2C2C2E.toInt()
            colorKeyPushed = 0xFF5A5A5C.toInt()
            colorText = 0xFFFFFFFF.toInt()
            colorHighlight = 0xFF50C878.toInt()
            colorGhost = 0x66FFFFFF.toInt()
            colorAccent = 0xFF444444.toInt()
            colorShadow = 0xFF000000.toInt()
        } else {
            colorBackground = 0xFFE5E7EB.toInt()
            colorKey = 0xFFFFFFFF.toInt()
            colorFunctionKey = 0xFFD1D5DB.toInt()
            colorKeyPushed = 0xFF9CA3AF.toInt()
            colorText = 0xFF111827.toInt()
            colorHighlight = 0xFF059669.toInt()
            colorGhost = 0x66111827.toInt()
            colorAccent = 0xFFD1D5DB.toInt()
            colorShadow = 0x33000000.toInt()
        }

        backgroundPaint.color = colorBackground
        shadowPaint.color = colorShadow
        textPaint.color = colorText
        ghostPaint.color = colorGhost
    }

    fun updateTextSizes(fontScale: Float) {
        textPaint.textSize = 64f * fontScale
        textOffset = (textPaint.descent() + textPaint.ascent()) / 2f
    }

    fun updateForSize(size: Float) {
        textPaint.textSize = size
        textOffset = (textPaint.descent() + textPaint.ascent()) / 2f
    }
}
