package lab.norlu.openkeyboard.ui.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

enum class KeyboardState { ALPHA, SYMBOLS, NUMERIC_PAD }
enum class ShiftState { LOWER, SHIFTED, CAPS_LOCK }

class OptimisedKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paleta de Cores WCAG AAA (Estado Inicial: Escuro)
    private var colorBackground = 0xFF121212.toInt()
    private var colorKey = 0xFF3A3A3C.toInt()
    private var colorFunctionKey = 0xFF2C2C2E.toInt()
    private var colorKeyPushed = 0xFF5A5A5C.toInt()
    private var colorText = 0xFFFFFFFF.toInt()
    private var colorHighlight = 0xFF50C878.toInt()
    private var colorGhost = 0x66FFFFFF.toInt()
    private var colorAccent = 0xFF444444.toInt()
    private var colorShadow = 0xFF000000.toInt()

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

    private val iconPath = android.graphics.Path()
    private val searchPath = android.graphics.Path()

    private val keyRect = RectF()
    private val pressedKeyRect = RectF()

    // Definição de Layouts (4 Linhas)
    private val alphaRow1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
    private val alphaRow2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
    private val alphaRow3 = listOf("⇧", "Z", "X", "C", "V", "B", "N", "M", "⌫")
    private val alphaRow4 = listOf("?123", ",", " ", ".", "__SEARCH__")

    private val symRow1 = listOf("[", "]", "{", "}", "/", "\\", "|", "^", "<", ">")
    private val symRow2 = listOf("@", "#", "$", "%", "&", "*", "-", "+", "=", "~")
    private val symRow3 = listOf("(", ")", "\"", "'", ":", ";", "!", "?", "⌫")
    private val symRow4 = listOf("NUM", ",", " ", ".", "__SEARCH__")

    private val padRow1 = listOf("1", "2", "3", "⌫")
    private val padRow2 = listOf("4", "5", "6", ",")
    private val padRow3 = listOf("7", "8", "9", ".")
    private val padRow4 = listOf("ABC", "0", " ", "__SEARCH__")

    // Estados
    private var isSettingsOpen = false
    private var isDarkTheme = true
    private var currentState = KeyboardState.ALPHA
    private var shiftState = ShiftState.LOWER
    private var imeAction: Int = android.view.inputmethod.EditorInfo.IME_ACTION_NONE
    private var lastShiftTime = 0L
    private var pressedKey: String? = null
    private var ghostText: String = ""
    private var suggestions: Array<String> = emptyArray()
    private var nextCharProbabilities: Map<Char, Double> = emptyMap()
    private var isUndoVisible: Boolean = false
    private var topBuffer: Float = 0f
    private var hapticEnabled: Boolean = true

    // Long Press & Backspace Repeat
    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isLongPressActive = false
    private var longPressedKey: String? = null
    private var alternateOptions: List<String> = emptyList()
    private var selectedAlternateIndex: Int = -1 // -1 é a tecla base
    private val alternatesMap = mapOf(
        "Q" to listOf("1"), "W" to listOf("2"), "E" to listOf("3", "É", "Ê"),
        "R" to listOf("4"), "T" to listOf("5"), "Y" to listOf("6"),
        "U" to listOf("7", "Ú"), "I" to listOf("8", "Í"), "O" to listOf("9", "Ó", "Õ", "Ô"),
        "P" to listOf("0"), "A" to listOf("Á", "À", "Ã", "Â"), "S" to listOf("@"),
        "N" to listOf("!"), "G" to listOf("%"), "Z" to listOf("_"), "M" to listOf("?")
    )
    private val hintMap = mapOf(
        "Q" to "1", "W" to "2", "E" to "3", "R" to "4", "T" to "5",
        "Y" to "6", "U" to "7", "I" to "8", "O" to "9", "P" to "0",
        "S" to "@", "N" to "!", "G" to "%", "Z" to "_", "M" to "?"
    )

    private var backspaceInterval = 200L
    private val backspaceRunnable = object : Runnable {
        override fun run() {
            if (pressedKey == "⌫") {
                handleKey("⌫")
                // Acelera a cada 200ms de execução acumulada (indiretamente via redução do intervalo)
                if (backspaceInterval > 50L) {
                    backspaceInterval = (backspaceInterval * 0.9f).toLong().coerceAtLeast(50L)
                }
                longPressHandler.postDelayed(this, backspaceInterval)
            }
        }
    }

    private val longPressRunnable = Runnable {
        pressedKey?.let { key ->
            if (key == "⌫") {
                isLongPressActive = true
                backspaceInterval = 200L
                longPressHandler.post(backspaceRunnable)
            } else {
                val alternates = alternatesMap[key.uppercase()]
                if (alternates != null) {
                    if (hapticEnabled) performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    isLongPressActive = true
                    longPressedKey = key
                    alternateOptions = alternates
                    selectedAlternateIndex = 0 // Seleciona a tecla especial primeiro
                    invalidate()
                }
            }
        }
    }

    // Callbacks
    private var onKeyClickListener: ((String) -> Unit)? = null
    private var onSuggestionClickListener: ((String) -> Unit)? = null
    private var onGlobeClickListener: (() -> Unit)? = null
    private var onThemeChangedListener: ((Boolean) -> Unit)? = null
    private var onUndoClickListener: (() -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        updateTextSizes()
    }

    fun isLongPressActive(): Boolean = isLongPressActive

    fun setUndoVisible(visible: Boolean) {
        if (this.isUndoVisible != visible) {
            this.isUndoVisible = visible
            invalidate()
        }
    }

    fun setOnUndoClickListener(listener: () -> Unit) {
        onUndoClickListener = listener
    }

    fun setHapticEnabled(enabled: Boolean) {
        this.hapticEnabled = enabled
    }

    fun applyTheme(dark: Boolean) {
        this.isDarkTheme = dark
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
            colorBackground = 0xFFE5E7EB.toInt() // Cinza mais escuro para contraste com as teclas brancas
            colorKey = 0xFFFFFFFF.toInt()
            colorFunctionKey = 0xFFD1D5DB.toInt() // Cinza intermediário para botões de função
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
        invalidate()
    }

    fun setOnThemeChangedListener(listener: (Boolean) -> Unit) {
        onThemeChangedListener = listener
    }

    private fun getKeyWeight(key: String): Float {
        if (currentState == KeyboardState.NUMERIC_PAD) return 1.0f
        return when (key) {
            " " -> 4.0f
            "⇧", "⌫", "123", "ABC", "?123", "NUM", "__SEARCH__" -> 1.5f
            else -> 1.0f
        }
    }

    fun setImeAction(action: Int) {
        if (this.imeAction != action) {
            this.imeAction = action
            invalidate()
        }
    }

    fun setKeyboardState(state: KeyboardState) {
        this.currentState = state
        invalidate()
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
        val density = resources.displayMetrics.density
        // Reduzido de 100 para 40dp para evitar roubar espaço desnecessário da tela
        topBuffer = 45 * density

        // Reduz a altura base em landscape para não ocupar a tela toda
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val baseHeightDp = if (isLandscape) 200f else 260f

        val keyboardHeight = baseHeightDp * density
        setMeasuredDimension(width, (keyboardHeight + topBuffer).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        // 1. Fundo do Teclado (Começa após o buffer transparente)
        canvas.drawRect(0f, topBuffer, width.toFloat(), height.toFloat(), backgroundPaint)

        val totalHeight = height.toFloat() - topBuffer
        val suggestionBarHeight = totalHeight * 0.15f
        val keyboardAreaHeight = totalHeight - suggestionBarHeight
        val rowHeight = keyboardAreaHeight / 4f
        val padding = 8f

        // 2. Barra de Sugestões (Deslocada pelo topBuffer)
        canvas.save()
        canvas.translate(0f, topBuffer)
        drawSuggestionBar(canvas, suggestionBarHeight)
        canvas.restore()

        if (isSettingsOpen) {
            canvas.save()
            canvas.translate(0f, topBuffer)
            drawSettingsModal(canvas, suggestionBarHeight, keyboardAreaHeight)
            canvas.restore()
            return
        }

        // 3. Desenho das Linhas do Teclado
        val currentRows = when (currentState) {
            KeyboardState.ALPHA -> listOf(alphaRow1, alphaRow2, alphaRow3, alphaRow4)
            KeyboardState.SYMBOLS -> listOf(symRow1, symRow2, symRow3, symRow4)
            KeyboardState.NUMERIC_PAD -> listOf(padRow1, padRow2, padRow3, padRow4)
        }

        currentRows.forEachIndexed { index, keys ->
            val top = topBuffer + suggestionBarHeight + (index * rowHeight)
            val rowMargin = if (currentState == KeyboardState.ALPHA) {
                when (index) {
                    1 -> width * 0.05f // Recuo da linha do 'A'
                    2 -> width * 0.02f // Recuo leve para a linha do 'Z' para centralizar
                    else -> 0f
                }
            } else 0f
            drawRow(canvas, keys, top, rowHeight, padding, rowMargin)
        }

        // 4. Preview Pop-up (Desenhado livremente no Canvas, pode usar a área do buffer)
        drawPopup(canvas)
    }

    private fun drawSuggestionBar(canvas: Canvas, barHeight: Float) {
        keyPaint.color = colorAccent
        canvas.drawRect(0f, 0f, width.toFloat(), barHeight, keyPaint)

        // Desenha o ícone minimalista à esquerda (Chevron Up Duplo "^")
        val iconSize = barHeight * 0.4f
        val startX = width * 0.05f
        val centerY = barHeight * 0.5f

        backgroundPaint.style = Paint.Style.STROKE
        backgroundPaint.strokeWidth = 4f
        backgroundPaint.color = colorText

        iconPath.reset()
        // Primeira seta
        iconPath.moveTo(startX, centerY + (iconSize * 0.2f))
        iconPath.lineTo(startX + (iconSize / 2), centerY - (iconSize * 0.2f))
        iconPath.lineTo(startX + iconSize, centerY + (iconSize * 0.2f))
        // Segunda seta acima
        iconPath.moveTo(startX, centerY - (iconSize * 0.1f))
        iconPath.lineTo(startX + (iconSize / 2), centerY - (iconSize * 0.5f))
        iconPath.lineTo(startX + iconSize, centerY - (iconSize * 0.1f))

        canvas.drawPath(iconPath, backgroundPaint)
        backgroundPaint.style = Paint.Style.FILL // Reseta
        backgroundPaint.color = colorBackground

        // Se houver Undo disponível, reservamos 15% na direita.
        val undoButtonWidth = if (isUndoVisible) width * 0.15f else 0f
        val contentAreaWidth = (width * 0.8f) - undoButtonWidth
        val sugStartX = width * 0.15f

        if (suggestions.isNotEmpty()) {
            val count = suggestions.size.coerceAtMost(3)
            val sugWidth = contentAreaWidth / count

            suggestions.take(3).forEachIndexed { i, rawSug ->
                val parts = rawSug.split(":")
                val s = parts[0]
                val confidence = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0

                if (confidence >= 3) {
                    textPaint.color = colorHighlight
                    textPaint.isFakeBoldText = true
                } else {
                    textPaint.color = colorText
                    textPaint.isFakeBoldText = false
                }

                var size = barHeight * 0.45f
                textPaint.textSize = size
                while (textPaint.measureText(s) > sugWidth * 0.9f && size > 24f) {
                    size -= 2f
                    textPaint.textSize = size
                }
                canvas.drawText(s, sugStartX + (i * sugWidth) + (sugWidth / 2), barHeight * 0.65f, textPaint)
            }
        }

        // Desenha botão de Undo (180 deg arrow) se visível
        if (isUndoVisible) {
            val radius = barHeight * 0.2f
            val centerX = width * 0.90f // Mais para a esquerda (de 0.93f para 0.90f)
            val undoCenterY = barHeight * 0.5f

            backgroundPaint.style = Paint.Style.STROKE
            backgroundPaint.strokeWidth = 4f
            backgroundPaint.color = colorText

            iconPath.reset()
            // Seta curvada 180 graus (arco na direita)
            val rect = RectF(centerX - radius, undoCenterY - radius, centerX + radius, undoCenterY + radius)
            iconPath.arcTo(rect, 90f, -180f) // Baixo -> Direita -> Cima

            // Haste superior da seta voltando para a esquerda
            iconPath.lineTo(centerX - (radius * 1.5f), undoCenterY - radius)

            // Ponta da seta na extremidade esquerda (haste superior)
            val arrowX = centerX - (radius * 1.5f)
            val arrowY = undoCenterY - radius
            iconPath.moveTo(arrowX + (radius * 0.4f), arrowY - (radius * 0.4f))
            iconPath.lineTo(arrowX, arrowY)
            iconPath.lineTo(arrowX + (radius * 0.4f), arrowY + (radius * 0.4f))

            canvas.drawPath(iconPath, backgroundPaint)
            backgroundPaint.style = Paint.Style.FILL
            backgroundPaint.color = colorBackground
        }

        textPaint.color = colorText
        textPaint.isFakeBoldText = false
        updateTextSizes()
    }

    private fun drawSettingsModal(canvas: Canvas, top: Float, totalHeight: Float) {
        val centerX = width / 2f

        // Título
        textPaint.textSize = 42f
        textPaint.isFakeBoldText = true
        canvas.drawText("OPÇÕES DO TECLADO", centerX, top + 70f, textPaint)

        // Botões de Tema
        val btnWidth = width * 0.8f
        val btnHeight = 120f
        val btnX = (width - btnWidth) / 2f

        // Botão Tema Claro
        keyRect.set(btnX, top + 150f, btnX + btnWidth, top + 150f + btnHeight)
        keyPaint.color = if (!isDarkTheme) colorHighlight else colorFunctionKey
        canvas.drawRoundRect(keyRect, 16f, 16f, keyPaint)
        textPaint.color = if (!isDarkTheme) 0xFFFFFFFF.toInt() else colorText
        textPaint.textSize = 36f
        canvas.drawText("TEMA CLARO (AAA)", centerX, keyRect.centerY() + 12f, textPaint)

        // Botão Tema Escuro
        keyRect.set(btnX, top + 150f + btnHeight + 30f, btnX + btnWidth, top + 150f + (btnHeight * 2) + 30f)
        keyPaint.color = if (isDarkTheme) colorHighlight else colorFunctionKey
        canvas.drawRoundRect(keyRect, 16f, 16f, keyPaint)
        textPaint.color = if (isDarkTheme) 0xFFFFFFFF.toInt() else colorText
        canvas.drawText("TEMA ESCURO (AAA)", centerX, keyRect.centerY() + 12f, textPaint)

        // Botão Fechar
        keyRect.set(btnX, top + totalHeight - 100f, btnX + btnWidth, top + totalHeight - 20f)
        keyPaint.color = colorAccent
        canvas.drawRoundRect(keyRect, 16f, 16f, keyPaint)
        textPaint.color = colorText
        textPaint.textSize = 36f
        canvas.drawText("VOLTAR AO TECLADO", centerX, keyRect.centerY() + 12f, textPaint)

        textPaint.isFakeBoldText = false
        updateTextSizes()
    }
    private fun drawRow(canvas: Canvas, keys: List<String>, top: Float, rowHeight: Float, padding: Float, margin: Float) {
        val availableWidth = width - (margin * 2)
        val totalWeight = keys.sumOf { getKeyWeight(it).toDouble() }.toFloat()

        var currentX = margin
        keys.forEachIndexed { _, key ->
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
            val isSearchKey = key == "__SEARCH__"
            val isFunction = isShiftKey || isSearchKey || key == "⌫" || key == "123" || key == "ABC" || key == "🌐" || key == "⚙️"

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
                textPaint.textSize = if (key == "123" || key == "ABC") rowHeight * 0.4f else rowHeight * 0.5f
                textPaint.isFakeBoldText = true
            }

            if (isSearchKey) {
                val iconSize = rowHeight * 0.45f
                val scx = keyRect.centerX()
                val scy = keyRect.centerY()

                textPaint.style = Paint.Style.STROKE
                textPaint.strokeWidth = 6f
                textPaint.color = colorText
                searchPath.reset()

                val isSearch = (imeAction == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH)

                if (isSearch) {
                    // Círculo da lupa
                    searchPath.addCircle(scx - (iconSize * 0.15f), scy - (iconSize * 0.15f), iconSize * 0.42f, android.graphics.Path.Direction.CW)
                    // Cabo da lupa
                    searchPath.moveTo(scx + (iconSize * 0.12f), scy + (iconSize * 0.12f))
                    searchPath.lineTo(scx + (iconSize * 0.55f), scy + (iconSize * 0.55f))
                } else {
                    // Ícone de Enter (Seta curvada)
                    val offset = iconSize * 0.5f
                    searchPath.moveTo(scx + offset, scy - offset)
                    searchPath.lineTo(scx + offset, scy + offset * 0.5f)
                    searchPath.quadTo(scx + offset, scy + offset, scx, scy + offset)
                    searchPath.lineTo(scx - offset, scy + offset)
                    // Ponta da seta (relativa ao tamanho do ícone)
                    val arrowHeadSize = iconSize * 0.3f
                    searchPath.moveTo(scx - offset + arrowHeadSize, scy + offset - arrowHeadSize)
                    searchPath.lineTo(scx - offset, scy + offset)
                    searchPath.lineTo(scx - offset + arrowHeadSize, scy + offset + arrowHeadSize)
                }

                canvas.drawPath(searchPath, textPaint)
                textPaint.style = Paint.Style.FILL
                textPaint.strokeWidth = 0f
            } else {
                val displayText = when {
                    isShiftKey -> if (shiftState == ShiftState.CAPS_LOCK) "⇪" else "⇧"
                    key.length == 1 && key[0].isLetter() -> if (shiftState != ShiftState.LOWER) key.uppercase() else key.lowercase()
                    else -> key
                }
                canvas.drawText(displayText, cx, cy, textPaint)
            }

            // 3. Desenha Hint (Símbolo/Número no canto)
            if (currentState == KeyboardState.ALPHA && key.length == 1) {
                hintMap[key.uppercase()]?.let { hint ->
                    val hintSize = rowHeight * 0.22f
                    textPaint.textSize = hintSize
                    textPaint.color = colorGhost
                    textPaint.isFakeBoldText = false

                    val hx = keyRect.right - 10f
                    val hy = keyRect.top + hintSize + 4f

                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(hint, hx, hy, textPaint)

                    // Reseta paint para a próxima tecla
                    textPaint.textAlign = Paint.Align.CENTER
                    textPaint.color = colorText
                    updateTextSizes()
                }
            }

            if (isFunction) {
                updateTextSizes()
                textPaint.isFakeBoldText = false
            }

            currentX += keyWidth
        }
    }

    private fun drawPopup(canvas: Canvas) {
        val key = if (isLongPressActive) longPressedKey else pressedKey
        if (key == null || key.length != 1 || !key[0].isLetter()) return

        // Dimensões unificadas para ambos os tipos de popup
        val popupWidth = pressedKeyRect.width() * 1.5f
        val popupHeight = pressedKeyRect.height() * 1.6f
        val popupTop = pressedKeyRect.top - popupHeight - 20f // Posição fixa acima da tecla

        if (isLongPressActive) {
            val totalOptions = alternateOptions.size + 1
            val fullPopupWidth = popupWidth * totalOptions

            var popupLeft = pressedKeyRect.centerX() - (fullPopupWidth / 2)
            // Impede que o popup saia da tela lateralmente
            if (popupLeft < 10f) popupLeft = 10f
            if (popupLeft + fullPopupWidth > width - 10f) popupLeft = width - fullPopupWidth - 10f

            val popupRect = RectF(popupLeft, popupTop, popupLeft + fullPopupWidth, popupTop + popupHeight)

            // Sombra
            canvas.drawRoundRect(popupRect.left, popupRect.top + 8f, popupRect.right, popupRect.bottom + 8f, 20f, 20f, shadowPaint)
            // Fundo
            keyPaint.color = colorKeyPushed
            canvas.drawRoundRect(popupRect, 20f, 20f, keyPaint)

            val originalSize = textPaint.textSize
            textPaint.textSize = popupHeight * 0.6f
            val cy = popupRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)

            for (i in 0 until totalOptions) {
                val optionX = popupLeft + (i * popupWidth) + (popupWidth / 2)
                val isSelected = (i - 1 == selectedAlternateIndex)

                if (isSelected) {
                    keyPaint.color = colorHighlight
                    val selRect = RectF(popupLeft + (i * popupWidth), popupTop, popupLeft + ((i + 1) * popupWidth), popupTop + popupHeight)
                    canvas.drawRoundRect(selRect, 20f, 20f, keyPaint)
                    textPaint.color = 0xFFFFFFFF.toInt()
                } else {
                    textPaint.color = colorText
                }

                val text = if (i == 0) {
                    if (shiftState != ShiftState.LOWER) key.uppercase() else key.lowercase()
                } else {
                    val alt = alternateOptions[i - 1]
                    if (shiftState != ShiftState.LOWER) alt.uppercase() else alt.lowercase()
                }

                canvas.drawText(text, optionX, cy, textPaint)
            }
            textPaint.textSize = originalSize
            textPaint.color = colorText
        } else {
            val popupLeft = pressedKeyRect.centerX() - (popupWidth / 2)
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
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val key = resolveKeyAt(event.x, event.y)
                pressedKey = key
                isLongPressActive = false
                selectedAlternateIndex = -1

                if (key != null && (key == "⌫" || (key.length == 1 && alternatesMap.containsKey(key.uppercase())))) {
                    longPressHandler.postDelayed(longPressRunnable, 300)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isLongPressActive) {
                    val popupWidth = pressedKeyRect.width() * 1.5f
                    val totalOptions = alternateOptions.size + 1
                    val fullPopupWidth = popupWidth * totalOptions
                    val popupLeft = pressedKeyRect.centerX() - (fullPopupWidth / 2)

                    val relativeX = event.x - popupLeft
                    val index = (relativeX / popupWidth).toInt().coerceIn(0, totalOptions - 1)

                    // Ajuste de índice: 0 é a base, 1..N são os alternativos
                    val newIndex = index - 1
                    if (newIndex != selectedAlternateIndex) {
                        if (hapticEnabled) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        selectedAlternateIndex = newIndex
                        invalidate()
                    }
                } else {
                    // Se mover muito antes do long press, cancela o timer
                    val key = resolveKeyAt(event.x, event.y)
                    if (key != pressedKey) {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        pressedKey = key
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                longPressHandler.removeCallbacks(backspaceRunnable)
                if (isLongPressActive) {
                    val finalKey = if (selectedAlternateIndex == -1) {
                        longPressedKey ?: ""
                    } else {
                        alternateOptions.getOrNull(selectedAlternateIndex) ?: (longPressedKey ?: "")
                    }
                    if (finalKey.isNotEmpty()) {
                        handleKey(finalKey)
                    }
                } else {
                    pressedKey?.let { handleKey(it) }
                }
                pressedKey = null
                isLongPressActive = false
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                pressedKey = null
                isLongPressActive = false
                invalidate()
            }
        }
        return true
    }

    private fun resolveKeyAt(x: Float, rawY: Float): String? {
        val y = rawY - topBuffer
        if (y < 0) return null // Toque na área transparente do buffer

        val totalHeight = height.toFloat() - topBuffer
        val suggestionBarHeight = totalHeight * 0.15f

        if (y < suggestionBarHeight) {
            return when {
                x < width * 0.15f -> "__SETTINGS_TOGGLE__"
                isUndoVisible && x > width * 0.85f -> "__UNDO__"
                else -> {
                    val undoButtonWidth = if (isUndoVisible) width * 0.15f else 0f
                    val contentAreaWidth = (width * 0.8f) - undoButtonWidth
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

        if (isSettingsOpen) {
            val top = suggestionBarHeight
            val btnWidth = width * 0.8f
            val btnHeight = 120f
            val btnX = (width - btnWidth) / 2f

            return when {
                y in (top + 150f)..(top + 150f + btnHeight) && x in btnX..(btnX + btnWidth) -> "__THEME_LIGHT__"
                y in (top + 150f + btnHeight + 30f)..(top + 150f + (btnHeight * 2) + 30f) && x in btnX..(btnX + btnWidth) -> "__THEME_DARK__"
                y in (totalHeight - 100f)..(totalHeight - 20f) && x in btnX..(btnX + btnWidth) -> "__SETTINGS_CLOSE__"
                else -> null
            }
        }
        val keyboardAreaHeight = totalHeight - suggestionBarHeight
        val rowIndex = ((y - suggestionBarHeight) / (keyboardAreaHeight / 4f)).toInt().coerceIn(0, 3)

        val keys = when (currentState) {
            KeyboardState.ALPHA -> listOf(alphaRow1, alphaRow2, alphaRow3, alphaRow4)[rowIndex]
            KeyboardState.SYMBOLS -> listOf(symRow1, symRow2, symRow3, symRow4)[rowIndex]
            KeyboardState.NUMERIC_PAD -> listOf(padRow1, padRow2, padRow3, padRow4)[rowIndex]
        }

        val margin = if (currentState == KeyboardState.ALPHA) {
            when (rowIndex) {
                1 -> width * 0.05f
                2 -> width * 0.02f
                else -> 0f
            }
        } else 0f

        val availableWidth = width - (margin * 2)
        val totalWeight = keys.sumOf { getKeyWeight(it).toDouble() }.toFloat()

        var currentX = margin
        for (i in keys.indices) {
            val key = keys[i]
            val keyWeight = getKeyWeight(key)
            val keyWidth = (availableWidth / totalWeight) * keyWeight

            // Verifica se o toque caiu dentro da dimensão física desta tecla
            if (x >= currentX && x <= currentX + keyWidth) {
                // Se não for modo ALPHA ou tecla especial, retorna a tecla física
                if (currentState != KeyboardState.ALPHA || key.length != 1) return key

                // DYNAMIC HITBOXES: Mitigação de Erros (WCAG AAA)
                val baseProb = nextCharProbabilities[key.lowercase()[0]] ?: 0.0

                // Analisa "Zona de Dúvida" (25% das bordas)
                val hitZoneLeft = currentX + (keyWidth * 0.25f)
                val hitZoneRight = currentX + (keyWidth * 0.75f)

                if (x < hitZoneLeft && i > 0) {
                    val leftKey = keys[i - 1]
                    if (leftKey.length == 1) {
                        val leftProb = nextCharProbabilities[leftKey.lowercase()[0]] ?: 0.0
                        // Se vizinho for 3x mais provável, redireciona o toque
                        if (leftProb > baseProb * 3.0 && leftProb > 0.05) {
                            android.util.Log.d("OpenKeyboard", "Hitbox Bias: $key -> $leftKey")
                            return leftKey
                        }
                    }
                } else if (x > hitZoneRight && i < keys.size - 1) {
                    val rightKey = keys[i + 1]
                    if (rightKey.length == 1) {
                        val rightProb = nextCharProbabilities[rightKey.lowercase()[0]] ?: 0.0
                        if (rightProb > baseProb * 3.0 && rightProb > 0.05) {
                            android.util.Log.d("OpenKeyboard", "Hitbox Bias: $key -> $rightKey")
                            return rightKey
                        }
                    }
                }
                return key
            }
            currentX += keyWidth
        }
        return null
    }

    private fun handleKey(key: String) {
        if (key.startsWith("__SUG_")) {
            val indexStr = key.substring(6, key.length - 2)
            val index = indexStr.toIntOrNull()
            if (index != null && index < suggestions.size) {
                val word = suggestions[index].split(":")[0]
                onSuggestionClickListener?.invoke(word)
            }
            return
        }

        when (key) {
            "__UNDO__" -> onUndoClickListener?.invoke()
            "__SETTINGS_TOGGLE__" -> {
                if (hapticEnabled) performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                isSettingsOpen = !isSettingsOpen
                invalidate()
            }
            "__SETTINGS_CLOSE__" -> {
                if (hapticEnabled) performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                isSettingsOpen = false
                invalidate()
            }
            "__THEME_LIGHT__" -> {
                if (hapticEnabled) performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                applyTheme(false)
                onThemeChangedListener?.invoke(false)
            }
            "__THEME_DARK__" -> {
                if (hapticEnabled) performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                applyTheme(true)
                onThemeChangedListener?.invoke(true)
            }
            "🌐" -> onGlobeClickListener?.invoke()
            "⚙️" -> onKeyClickListener?.invoke("⚙️")
            "123", "?123" -> { currentState = KeyboardState.SYMBOLS; invalidate() }
            "NUM" -> { currentState = KeyboardState.NUMERIC_PAD; invalidate() }
            "ABC" -> { currentState = KeyboardState.ALPHA; invalidate() }
            "⌫" -> onKeyClickListener?.invoke("⌫")
            "__SEARCH__" -> onKeyClickListener?.invoke("\n")
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
                // Bloqueia qualquer comando interno que comece com __ de cair no commitText
                if (!key.startsWith("__")) {
                    val textToSubmit = if (shiftState != ShiftState.LOWER) key.uppercase() else key.lowercase()
                    onKeyClickListener?.invoke(textToSubmit)
                    if (shiftState == ShiftState.SHIFTED) {
                        shiftState = ShiftState.LOWER
                        invalidate()
                    }
                }
            }
        }
    }

    private fun updateTextSizes() {
        textPaint.textSize = 64f * resources.configuration.fontScale
    }
}
