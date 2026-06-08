package com.norlu.openkeyboard.ui.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

enum class KeyboardState { ALPHA, NUMERIC }
enum class ShiftState { LOWER, SHIFTED, CAPS_LOCK }

class OptimisedKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paleta de Cores WCAG AAA
    private val colorBackground = 0xFF121212.toInt()
    private val colorKey = 0xFF3A3A3C.toInt()
    private val colorFunctionKey = 0xFF2C2C2E.toInt()
    private val colorKeyPushed = 0xFF5A5A5C.toInt()
    private val colorText = 0xFFFFFFFF.toInt()
    private val colorHighlight = 0xFF50C878.toInt() // Emerald Green para alta confiança
    private val colorGhost = 0x66FFFFFF.toInt()
    private val colorAccent = 0xFF444444.toInt()
    private val colorShadow = 0xFF000000.toInt()

    private val backgroundPaint = Paint().apply { color = colorBackground }
    private val shadowPaint = Paint().apply { color = colorShadow; style = Paint.Style.FILL }
    private val keyPaint = Paint().apply { style = Paint.Style.FILL }
    private val textPaint = Paint().apply {
        color = colorText
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val ghostPaint = Paint().apply {
        color = colorGhost
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    
    private val keyRect = RectF()
    private val pressedKeyRect = RectF()
    
    // Definição de Layouts
    private val row0 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
    private val row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
    private val row3 = listOf("⇧", "Z", "X", "C", "V", "B", "N", "M", "⌫")
    private val rowBottom = listOf("123", ",", " ", ".", "↵")
    
    private val numRow0 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val numRow1 = listOf("@", "#", "$", "&", "*", "(", ")", "-", "_")
    private val numRow2 = listOf("+", "-", "*", "/", "=", "%", "<", ">", "|")
    private val numRow3 = listOf("(", ")", "{", "}", "[", "]", "!", "?", "⌫")
    private val numRowBottom = listOf("ABC", ",", " ", ".", "↵")

    // Estados
    private var currentState = KeyboardState.ALPHA
    private var shiftState = ShiftState.LOWER
    private var lastShiftTime = 0L
    private var pressedKey: String? = null
    private var ghostText: String = ""
    private var suggestions: Array<String> = emptyArray()
    private var nextCharProbabilities: Map<Char, Double> = emptyMap()
    
    // Callbacks
    private var onKeyClickListener: ((String) -> Unit)? = null
    private var onSuggestionClickListener: ((String) -> Unit)? = null
    private var onGlobeClickListener: (() -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        updateTextSizes()
    }

    private fun getKeyWeight(key: String): Float {
        return when (key) {
            " " -> 4.0f
            "⇧", "⌫", "123", "ABC", "↵" -> 1.5f
            else -> 1.0f
        }
    }

    fun setGhostText(text: String) {
        this.ghostText = text
        invalidate()
    }

    fun setSuggestions(list: Array<String>) {
        this.suggestions = list
        invalidate()
    }

    fun setNextCharProbabilities(probs: Map<Char, Double>) {
        this.nextCharProbabilities = probs
    }

    fun setOnKeyClickListener(listener: (String) -> Unit) {
        onKeyClickListener = listener
    }

    fun setOnSuggestionClickListener(listener: (String) -> Unit) {
        onSuggestionClickListener = listener
    }

    fun setOnGlobeClickListener(listener: () -> Unit) {
        onGlobeClickListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (300 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        // 1. Fundo Total
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val totalHeight = height.toFloat()
        val suggestionBarHeight = totalHeight * 0.15f
        val keyboardAreaHeight = totalHeight - suggestionBarHeight
        val rowHeight = keyboardAreaHeight / 5f
        val padding = 8f

        // 2. Barra de Sugestões
        drawSuggestionBar(canvas, suggestionBarHeight)

        // 3. Desenho das Linhas do Teclado
        val currentRows = if (currentState == KeyboardState.ALPHA) {
            listOf(row0, row1, row2, row3, rowBottom)
        } else {
            listOf(numRow0, numRow1, numRow2, numRow3, numRowBottom)
        }

        currentRows.forEachIndexed { index, keys ->
            val top = suggestionBarHeight + (index * rowHeight)
            val rowMargin = when (index) {
                2 -> width * 0.05f // Recuo da linha do 'A'
                3 -> width * 0.02f // Recuo leve para a linha do 'Z' para centralizar
                else -> 0f
            }
            drawRow(canvas, keys, top, rowHeight, padding, rowMargin)
        }

        // 4. Preview Pop-up
        drawPopup(canvas)
    }

    private fun drawSuggestionBar(canvas: Canvas, barHeight: Float) {
        keyPaint.color = colorAccent
        canvas.drawRect(0f, 0f, width.toFloat(), barHeight, keyPaint)
        
        // Desenha os botões fixos na barra superior
        textPaint.textSize = barHeight * 0.5f
        canvas.drawText("🌐", width * 0.075f, barHeight * 0.65f, textPaint)
        canvas.drawText("⚙️", width * 0.925f, barHeight * 0.65f, textPaint)

        val contentAreaWidth = width * 0.7f
        val startX = width * 0.15f
        
        if (suggestions.isNotEmpty()) {
            val count = suggestions.size.coerceAtMost(3)
            val sugWidth = contentAreaWidth / count
            
            suggestions.take(3).forEachIndexed { i, rawSug ->
                // Formato esperado: "palavra:confiança"
                val parts = rawSug.split(":")
                val s = parts[0]
                val confidence = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
                
                // Estilo baseado na confiança
                if (confidence >= 3) {
                    textPaint.color = colorHighlight
                    textPaint.isFakeBoldText = true
                } else {
                    textPaint.color = colorText
                    textPaint.isFakeBoldText = false
                }

                // Ajuste dinâmico de texto para não cortar
                var size = barHeight * 0.45f
                textPaint.textSize = size
                while (textPaint.measureText(s) > sugWidth * 0.9f && size > 24f) {
                    size -= 2f
                    textPaint.textSize = size
                }
                canvas.drawText(s, startX + (i * sugWidth) + (sugWidth / 2), barHeight * 0.65f, textPaint)
            }
        }
        textPaint.color = colorText // Reseta cor
        textPaint.isFakeBoldText = false
        updateTextSizes() // Restaura tamanho original
    }

    private fun drawRow(canvas: Canvas, keys: List<String>, top: Float, rowHeight: Float, padding: Float, margin: Float) {
        val availableWidth = width - (margin * 2)
        val totalWeight = keys.sumOf { getKeyWeight(it).toDouble() }.toFloat()
        
        var currentX = margin
        keys.forEachIndexed { i, key ->
            val keyWeight = getKeyWeight(key)
            val keyWidth = (availableWidth / totalWeight) * keyWeight

            keyRect.set(currentX + padding, top + padding, currentX + keyWidth - padding, top + rowHeight - padding)
            
            // 1. Desenha Sombra (Elevação)
            val shadowOffset = 2f
            canvas.drawRoundRect(
                keyRect.left, keyRect.top + shadowOffset, keyRect.right, keyRect.bottom + shadowOffset,
                12f, 12f, shadowPaint
            )

            val isShiftKey = key == "⇧"
            val isFunction = isShiftKey || key == "↵" || key == "⌫" || key == "123" || key == "ABC" || key == "🌐" || key == "⚙️"
            
            // 2. Define Cor do Fundo
            keyPaint.color = when {
                pressedKey == key -> {
                    pressedKeyRect.set(keyRect) // Salva para o pop-up
                    colorKeyPushed
                }
                isShiftKey && shiftState != ShiftState.LOWER -> colorAccent
                isFunction -> colorFunctionKey
                else -> colorKey
            }
            canvas.drawRoundRect(keyRect, 12f, 12f, keyPaint)
            
            val cx = keyRect.centerX()
            val cy = keyRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
            
            if (isFunction) {
                textPaint.textSize = if (key == "⌫") rowHeight * 0.4f else rowHeight * 0.5f
                textPaint.isFakeBoldText = true
            }

            val displayText = when {
                isShiftKey -> if (shiftState == ShiftState.CAPS_LOCK) "⇪" else "⇧"
                key.length == 1 && key[0].isLetter() -> if (shiftState != ShiftState.LOWER) key.uppercase() else key.lowercase()
                else -> key
            }
            canvas.drawText(displayText, cx, cy, textPaint)
            
            if (isFunction) {
                updateTextSizes()
                textPaint.isFakeBoldText = false
            }
            
            currentX += keyWidth
        }
    }

    private fun drawPopup(canvas: Canvas) {
        val key = pressedKey ?: return
        if (key.length != 1 || !key[0].isLetter()) return // Apenas para letras

        val popupWidth = pressedKeyRect.width() * 1.5f
        val popupHeight = pressedKeyRect.height() * 1.6f
        val popupLeft = pressedKeyRect.centerX() - (popupWidth / 2)
        val popupTop = pressedKeyRect.top - popupHeight - 10f
        
        val popupRect = RectF(popupLeft, popupTop, popupLeft + popupWidth, popupTop + popupHeight)
        
        // Sombra do Pop-up
        canvas.drawRoundRect(popupRect.left, popupRect.top + 8f, popupRect.right, popupRect.bottom + 8f, 20f, 20f, shadowPaint)
        
        // Fundo do Pop-up
        keyPaint.color = colorKeyPushed
        canvas.drawRoundRect(popupRect, 20f, 20f, keyPaint)
        
        // Texto do Pop-up
        val originalSize = textPaint.textSize
        textPaint.textSize = popupHeight * 0.6f
        val displayText = if (shiftState != ShiftState.LOWER) key.uppercase() else key.lowercase()
        val cy = popupRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(displayText, popupRect.centerX(), cy, textPaint)
        
        textPaint.textSize = originalSize
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val key = resolveKeyAt(event.x, event.y)
                pressedKey = key
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                pressedKey?.let { handleKey(it) }
                pressedKey = null
                invalidate()
            }
        }
        return true
    }

    private fun resolveKeyAt(x: Float, y: Float): String? {
        val totalHeight = height.toFloat()
        val suggestionBarHeight = totalHeight * 0.15f
        if (y < suggestionBarHeight) {
            return when {
                x < width * 0.15f -> "🌐"
                x > width * 0.85f -> "⚙️"
                else -> {
                    // Detecta qual sugestão foi clicada
                    val contentAreaWidth = width * 0.7f
                    val startX = width * 0.15f
                    if (x >= startX && x <= startX + contentAreaWidth) {
                        val count = suggestions.size.coerceAtMost(3)
                        if (count > 0) {
                            val sugWidth = contentAreaWidth / count
                            val index = ((x - startX) / sugWidth).toInt().coerceIn(0, count - 1)
                            "__SUG_${index}__"
                        } else null
                    } else null
                }
            }
        }

        val keyboardAreaHeight = totalHeight - suggestionBarHeight
        val rowIndex = ((y - suggestionBarHeight) / (keyboardAreaHeight / 5f)).toInt().coerceIn(0, 4)
        
        val keys = if (currentState == KeyboardState.ALPHA) {
            listOf(row0, row1, row2, row3, rowBottom)[rowIndex]
        } else {
            listOf(numRow0, numRow1, numRow2, numRow3, numRowBottom)[rowIndex]
        }
        
        val margin = if (rowIndex == 2) width * 0.05f else 0f
        val availableWidth = width - (margin * 2)
        val totalWeight = keys.sumOf { getKeyWeight(it).toDouble() }.toFloat()
        
        var currentX = margin
        keys.forEach { key ->
            val keyWeight = getKeyWeight(key)
            val keyWidth = (availableWidth / totalWeight) * keyWeight
            if (x >= currentX && x <= currentX + keyWidth) return key
            currentX += keyWidth
        }
        return null
    }

    private fun handleKey(key: String) {
        if (key.startsWith("__SUG_")) {
            val index = key.substring(6, key.length - 2).toIntOrNull()
            if (index != null && index < suggestions.size) {
                // Remove o sufixo de confiança antes de enviar
                val word = suggestions[index].split(":")[0]
                onSuggestionClickListener?.invoke(word)
            }
            return
        }

        when (key) {
            "🌐" -> onGlobeClickListener?.invoke()
            "⚙️" -> onKeyClickListener?.invoke("⚙️")
            "123" -> { currentState = KeyboardState.NUMERIC; invalidate() }
            "ABC" -> { currentState = KeyboardState.ALPHA; invalidate() }
            "⌫" -> onKeyClickListener?.invoke("⌫")
            "↵" -> onKeyClickListener?.invoke("\n")
            "⇧" -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastShiftTime < 300) {
                    shiftState = ShiftState.CAPS_LOCK
                } else {
                    shiftState = when (shiftState) {
                        ShiftState.LOWER -> ShiftState.SHIFTED
                        else -> ShiftState.LOWER
                    }
                }
                lastShiftTime = currentTime
                invalidate()
            }
            else -> {
                val textToSubmit = if (shiftState != ShiftState.LOWER) key.uppercase() else key.lowercase()
                onKeyClickListener?.invoke(textToSubmit)
                if (shiftState == ShiftState.SHIFTED) {
                    shiftState = ShiftState.LOWER
                    invalidate()
                }
            }
        }
    }

    private fun updateTextSizes() {
        textPaint.textSize = 56f * resources.configuration.fontScale
    }
}
