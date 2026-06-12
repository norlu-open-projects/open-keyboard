package lab.norlu.openkeyboard.ui.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.inputmethod.EditorInfo

class KeyboardRenderer(
    private val view: View,
    private val theme: KeyboardThemeManager,
    private val touchHandler: KeyboardTouchHandler
) {
    private val iconPath = Path()
    private val searchPath = Path()
    private val keyRect = RectF()

    fun onDraw(canvas: Canvas) {
        theme.updateTextSizes(view.resources.configuration.fontScale)
        
        // 1. Fundo do Teclado
        canvas.drawRect(0f, touchHandler.topBuffer, view.width.toFloat(), view.height.toFloat(), theme.backgroundPaint)

        val totalHeight = view.height.toFloat() - touchHandler.topBuffer
        val suggestionBarHeight = totalHeight * 0.15f
        val keyboardAreaHeight = totalHeight - suggestionBarHeight
        val rowHeight = keyboardAreaHeight / 4f
        val padding = 11f

        // 2. Barra de Sugestões
        canvas.save()
        canvas.translate(0f, touchHandler.topBuffer)
        drawSuggestionBar(canvas, suggestionBarHeight)
        canvas.restore()

        if (touchHandler.isSettingsOpen) {
            canvas.save()
            canvas.translate(0f, touchHandler.topBuffer)
            drawSettingsModal(canvas, suggestionBarHeight, keyboardAreaHeight)
            canvas.restore()
            return
        }

        // 3. Desenho das Linhas do Teclado
        val currentRows = when (touchHandler.currentState) {
            KeyboardState.ALPHA -> listOf(KeyboardLayouts.alphaRow1, KeyboardLayouts.alphaRow2, KeyboardLayouts.alphaRow3, KeyboardLayouts.alphaRow4)
            KeyboardState.SYMBOLS -> listOf(KeyboardLayouts.symRow1, KeyboardLayouts.symRow2, KeyboardLayouts.symRow3, KeyboardLayouts.symRow4)
            KeyboardState.NUMERIC_PAD -> listOf(KeyboardLayouts.padRow1, KeyboardLayouts.padRow2, KeyboardLayouts.padRow3, KeyboardLayouts.padRow4)
        }

        for (i in 0 until currentRows.size) {
            val keys = currentRows[i]
            val top = touchHandler.topBuffer + suggestionBarHeight + (i * rowHeight)
            val rowMargin = if (touchHandler.currentState == KeyboardState.ALPHA) {
                when (i) {
                    1 -> view.width * 0.05f
                    2 -> view.width * 0.02f
                    else -> 0f
                }
            } else 0f
            drawRow(canvas, keys, top, rowHeight, padding, rowMargin)
        }

        // 4. Preview Pop-up
        drawPopup(canvas)
    }

    private fun drawSuggestionBar(canvas: Canvas, barHeight: Float) {
        theme.keyPaint.color = theme.colorAccent
        canvas.drawRect(0f, 0f, view.width.toFloat(), barHeight, theme.keyPaint)

        // Ícone Chevron
        val iconSize = barHeight * 0.4f
        val startX = view.width * 0.05f
        val centerY = barHeight * 0.5f

        theme.backgroundPaint.style = Paint.Style.STROKE
        theme.backgroundPaint.strokeWidth = 4f
        theme.backgroundPaint.color = theme.colorText

        iconPath.reset()
        iconPath.moveTo(startX, centerY + (iconSize * 0.2f))
        iconPath.lineTo(startX + (iconSize / 2), centerY - (iconSize * 0.2f))
        iconPath.lineTo(startX + iconSize, centerY + (iconSize * 0.2f))
        iconPath.moveTo(startX, centerY - (iconSize * 0.1f))
        iconPath.lineTo(startX + (iconSize / 2), centerY - (iconSize * 0.5f))
        iconPath.lineTo(startX + iconSize, centerY - (iconSize * 0.1f))

        canvas.drawPath(iconPath, theme.backgroundPaint)
        theme.backgroundPaint.style = Paint.Style.FILL
        theme.backgroundPaint.color = theme.colorBackground

        val undoButtonWidth = if (touchHandler.isUndoVisible) view.width * 0.15f else 0f
        val contentAreaWidth = (view.width * 0.8f) - undoButtonWidth
        val sugStartX = view.width * 0.15f

        if (touchHandler.suggestions.isNotEmpty()) {
            val count = touchHandler.suggestions.size.coerceAtMost(3)
            val sugWidth = contentAreaWidth / count

            for (i in 0 until count) {
                val sugItem = touchHandler.suggestions[i]
                val s = sugItem.text

                if (sugItem.isHighConfidence) {
                    theme.textPaint.color = theme.colorHighlight
                    theme.textPaint.isFakeBoldText = true
                } else {
                    theme.textPaint.color = theme.colorText
                    theme.textPaint.isFakeBoldText = false
                }

                var size = barHeight * 0.45f
                theme.updateForSize(size)
                while (theme.textPaint.measureText(s) > sugWidth * 0.9f && size > 24f) {
                    size -= 2f
                    theme.updateForSize(size)
                }
                // Reposiciona o texto com o textOffset cacheado
                canvas.drawText(s, sugStartX + (i * sugWidth) + (sugWidth / 2), barHeight * 0.5f - theme.textOffset, theme.textPaint)
            }
        }

        if (touchHandler.isUndoVisible) {
            drawUndoButton(canvas, barHeight)
        }

        theme.textPaint.color = theme.colorText
        theme.textPaint.isFakeBoldText = false
    }

    private fun drawUndoButton(canvas: Canvas, barHeight: Float) {
        val radius = barHeight * 0.2f
        val centerX = view.width * 0.90f
        val undoCenterY = barHeight * 0.5f

        theme.backgroundPaint.style = Paint.Style.STROKE
        theme.backgroundPaint.strokeWidth = 4f
        theme.backgroundPaint.color = theme.colorText

        iconPath.reset()
        val rect = RectF(centerX - radius, undoCenterY - radius, centerX + radius, undoCenterY + radius)
        iconPath.arcTo(rect, 90f, -180f)
        iconPath.lineTo(centerX - (radius * 1.5f), undoCenterY - radius)

        val arrowX = centerX - (radius * 1.5f)
        val arrowY = undoCenterY - radius
        iconPath.moveTo(arrowX + (radius * 0.4f), arrowY - (radius * 0.4f))
        iconPath.lineTo(arrowX, arrowY)
        iconPath.lineTo(arrowX + (radius * 0.4f), arrowY + (radius * 0.4f))

        canvas.drawPath(iconPath, theme.backgroundPaint)
        theme.backgroundPaint.style = Paint.Style.FILL
        theme.backgroundPaint.color = theme.colorBackground
    }

    private fun drawSettingsModal(canvas: Canvas, top: Float, totalHeight: Float) {
        val centerX = view.width / 2f
        theme.textPaint.textSize = 42f
        theme.textPaint.isFakeBoldText = true
        canvas.drawText("OPÇÕES DO TECLADO", centerX, top + 70f, theme.textPaint)

        val btnWidth = view.width * 0.8f
        val btnHeight = 120f
        val btnX = (view.width - btnWidth) / 2f

        // Botão Tema Claro
        keyRect.set(btnX, top + 150f, btnX + btnWidth, top + 150f + btnHeight)
        theme.keyPaint.color = if (!theme.isDark) theme.colorHighlight else theme.colorFunctionKey
        canvas.drawRoundRect(keyRect, 16f, 16f, theme.keyPaint)
        theme.textPaint.color = if (!theme.isDark) 0xFFFFFFFF.toInt() else theme.colorText
        theme.textPaint.textSize = 36f
        canvas.drawText("TEMA CLARO (AAA)", centerX, keyRect.centerY() + 12f, theme.textPaint)

        // Botão Tema Escuro
        keyRect.set(btnX, top + 150f + btnHeight + 30f, btnX + btnWidth, top + 150f + (btnHeight * 2) + 30f)
        theme.keyPaint.color = if (theme.isDark) theme.colorHighlight else theme.colorFunctionKey
        canvas.drawRoundRect(keyRect, 16f, 16f, theme.keyPaint)
        theme.textPaint.color = if (theme.isDark) 0xFFFFFFFF.toInt() else theme.colorText
        canvas.drawText("TEMA ESCURO (AAA)", centerX, keyRect.centerY() + 12f, theme.textPaint)

        // Botão Fechar
        keyRect.set(btnX, top + totalHeight - 100f, btnX + btnWidth, top + totalHeight - 20f)
        theme.keyPaint.color = theme.colorAccent
        canvas.drawRoundRect(keyRect, 16f, 16f, theme.keyPaint)
        theme.textPaint.color = theme.colorText
        theme.textPaint.textSize = 36f
        canvas.drawText("VOLTAR AO TECLADO", centerX, keyRect.centerY() + 12f, theme.textPaint)

        theme.textPaint.isFakeBoldText = false
    }

    /**
     * Renderiza uma linha de teclas.
     * PERFORMANCE NOTE: Loop baseado em índice para evitar alocação de Iterator.
     * Cálculos de peso e dimensões são feitos sem alocações temporárias.
     */
    private fun drawRow(canvas: Canvas, keys: List<KeyboardKey>, top: Float, rowHeight: Float, padding: Float, margin: Float) {
        val availableWidth = view.width - (margin * 2)
        var totalWeight = 0f
        for (i in 0 until keys.size) {
            totalWeight += KeyboardLayouts.getKeyWeight(keys[i], touchHandler.currentState)
        }

        var currentX = margin
        for (i in 0 until keys.size) {
            val key = keys[i]
            val keyWeight = KeyboardLayouts.getKeyWeight(key, touchHandler.currentState)
            val keyWidth = (availableWidth / totalWeight) * keyWeight

            keyRect.set(currentX + padding, top + padding, currentX + keyWidth - padding, top + rowHeight - padding)

            val shadowOffset = 2f
            canvas.drawRoundRect(keyRect.left, keyRect.top + shadowOffset, keyRect.right, keyRect.bottom + shadowOffset, 12f, 12f, theme.shadowPaint)

            val isFunction = key !is KeyboardKey.Text && key !is KeyboardKey.Space

            theme.keyPaint.color = when {
                touchHandler.pressedKey == key -> theme.colorKeyPushed
                key is KeyboardKey.Shift && touchHandler.shiftState != ShiftState.LOWER -> theme.colorAccent
                isFunction -> theme.colorFunctionKey
                else -> theme.colorKey
            }
            canvas.drawRoundRect(keyRect, 12f, 12f, theme.keyPaint)

            if (isFunction) {
                theme.updateForSize(if (key is KeyboardKey.Symbols || key is KeyboardKey.Alpha || key is KeyboardKey.Numeric) rowHeight * 0.4f else rowHeight * 0.5f)
                theme.textPaint.isFakeBoldText = true
            } else {
                theme.updateForSize(rowHeight * 0.45f)
            }

            val textCenterY = keyRect.centerY() - theme.textOffset

            when (key) {
                is KeyboardKey.Search -> drawSearchIcon(canvas, keyRect, rowHeight)
                is KeyboardKey.Text -> {
                    val displayText = if (touchHandler.shiftState != ShiftState.LOWER) key.upperChar else key.lowerChar
                    canvas.drawText(displayText, keyRect.centerX(), textCenterY, theme.textPaint)

                    if (touchHandler.currentState == KeyboardState.ALPHA) {
                        KeyboardLayouts.hintMap[key.upperChar]?.let { hint ->
                            drawHint(canvas, keyRect, hint, rowHeight)
                        }
                    }
                }
                is KeyboardKey.Shift -> {
                    val displayText = if (touchHandler.shiftState == ShiftState.CAPS_LOCK) "⇪" else "⇧"
                    canvas.drawText(displayText, keyRect.centerX(), textCenterY, theme.textPaint)
                }
                is KeyboardKey.Backspace -> {
                    canvas.drawText("⌫", keyRect.centerX(), textCenterY, theme.textPaint)
                }
                is KeyboardKey.Symbols -> {
                    canvas.drawText("?123", keyRect.centerX(), textCenterY, theme.textPaint)
                }
                is KeyboardKey.Numeric -> {
                    canvas.drawText("NUM", keyRect.centerX(), textCenterY, theme.textPaint)
                }
                is KeyboardKey.Alpha -> {
                    canvas.drawText("ABC", keyRect.centerX(), textCenterY, theme.textPaint)
                }
                is KeyboardKey.Space -> { /* Space is empty background */ }
                else -> { /* Other keys */ }
            }

            if (isFunction) {
                theme.textPaint.isFakeBoldText = false
            }
            currentX += keyWidth
        }
    }

    private fun drawHint(canvas: Canvas, rect: RectF, hint: String, rowHeight: Float) {
        val hintSize = rowHeight * 0.22f
        theme.textPaint.textSize = hintSize
        theme.textPaint.color = theme.colorGhost
        theme.textPaint.isFakeBoldText = false
        theme.textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(hint, rect.right - 10f, rect.top + hintSize + 4f, theme.textPaint)
        theme.textPaint.textAlign = Paint.Align.CENTER
        theme.textPaint.color = theme.colorText
    }

    private fun drawSearchIcon(canvas: Canvas, rect: RectF, rowHeight: Float) {
        val iconSize = rowHeight * 0.45f
        val scx = rect.centerX()
        val scy = rect.centerY()

        theme.textPaint.style = Paint.Style.STROKE
        theme.textPaint.strokeWidth = 6f
        theme.textPaint.color = theme.colorText
        searchPath.reset()

        val isSearch = (touchHandler.imeAction == EditorInfo.IME_ACTION_SEARCH)

        if (isSearch) {
            searchPath.addCircle(scx - (iconSize * 0.15f), scy - (iconSize * 0.15f), iconSize * 0.42f, Path.Direction.CW)
            searchPath.moveTo(scx + (iconSize * 0.12f), scy + (iconSize * 0.12f))
            searchPath.lineTo(scx + (iconSize * 0.55f), scy + (iconSize * 0.55f))
        } else {
            val offset = iconSize * 0.5f
            searchPath.moveTo(scx + offset, scy - offset)
            searchPath.lineTo(scx + offset, scy + offset * 0.5f)
            searchPath.quadTo(scx + offset, scy + offset, scx, scy + offset)
            searchPath.lineTo(scx - offset, scy + offset)
            val arrowHeadSize = iconSize * 0.3f
            searchPath.moveTo(scx - offset + arrowHeadSize, scy + offset - arrowHeadSize)
            searchPath.lineTo(scx - offset, scy + offset)
            searchPath.lineTo(scx - offset + arrowHeadSize, scy + offset + arrowHeadSize)
        }

        canvas.drawPath(searchPath, theme.textPaint)
        theme.textPaint.style = Paint.Style.FILL
        theme.textPaint.strokeWidth = 0f
    }

    private fun drawPopup(canvas: Canvas) {
        val key = if (touchHandler.isLongPressActive) touchHandler.longPressedKey else touchHandler.pressedKey
        if (key !is KeyboardKey.Text) return

        val popupWidth = touchHandler.pressedKeyRect.width() * 1.5f
        val popupHeight = touchHandler.pressedKeyRect.height() * 1.6f
        val popupTop = touchHandler.pressedKeyRect.top - popupHeight - 20f

        if (touchHandler.isLongPressActive) {
            val totalOptions = touchHandler.alternateOptions.size + 1
            val fullPopupWidth = popupWidth * totalOptions
            var popupLeft = touchHandler.pressedKeyRect.centerX() - (fullPopupWidth / 2)
            if (popupLeft < 10f) popupLeft = 10f
            if (popupLeft + fullPopupWidth > view.width - 10f) popupLeft = view.width - fullPopupWidth - 10f

            val popupRect = RectF(popupLeft, popupTop, popupLeft + fullPopupWidth, popupTop + popupHeight)
            canvas.drawRoundRect(popupRect.left, popupRect.top + 8f, popupRect.right, popupRect.bottom + 8f, 20f, 20f, theme.shadowPaint)
            theme.keyPaint.color = theme.colorKeyPushed
            canvas.drawRoundRect(popupRect, 20f, 20f, theme.keyPaint)

            val originalSize = theme.textPaint.textSize
            theme.textPaint.textSize = popupHeight * 0.6f
            val textCenterY = popupRect.centerY() - (theme.textPaint.descent() + theme.textPaint.ascent()) / 2f

            for (i in 0 until totalOptions) {
                val optionX = popupLeft + (i * popupWidth) + (popupWidth / 2)
                val isSelected = (i - 1 == touchHandler.selectedAlternateIndex)

                if (isSelected) {
                    theme.keyPaint.color = theme.colorHighlight
                    val selRect = RectF(popupLeft + (i * popupWidth), popupTop, popupLeft + ((i + 1) * popupWidth), popupTop + popupHeight)
                    canvas.drawRoundRect(selRect, 20f, 20f, theme.keyPaint)
                    theme.textPaint.color = 0xFFFFFFFF.toInt()
                } else {
                    theme.textPaint.color = theme.colorText
                }

                val text = if (i == 0) {
                    if (touchHandler.shiftState != ShiftState.LOWER) key.upperChar else key.lowerChar
                } else {
                    val alt = touchHandler.alternateOptions[i - 1]
                    // Manual upper/lower for alternates as they are dynamic Strings from the map
                    if (touchHandler.shiftState != ShiftState.LOWER) alt.uppercase() else alt.lowercase()
                }
                canvas.drawText(text, optionX, textCenterY, theme.textPaint)
            }
            theme.textPaint.textSize = originalSize
            theme.textPaint.color = theme.colorText
        } else {
            val popupLeft = touchHandler.pressedKeyRect.centerX() - (popupWidth / 2)
            val popupRect = RectF(popupLeft, popupTop, popupLeft + popupWidth, popupTop + popupHeight)
            canvas.drawRoundRect(popupRect.left, popupRect.top + 8f, popupRect.right, popupRect.bottom + 8f, 20f, 20f, theme.shadowPaint)
            theme.keyPaint.color = theme.colorKeyPushed
            canvas.drawRoundRect(popupRect, 20f, 20f, theme.keyPaint)

            val originalSize = theme.textPaint.textSize
            theme.textPaint.textSize = popupHeight * 0.6f
            val displayText = if (touchHandler.shiftState != ShiftState.LOWER) key.upperChar else key.lowerChar
            val textCenterY = popupRect.centerY() - (theme.textPaint.descent() + theme.textPaint.ascent()) / 2f
            canvas.drawText(displayText, popupRect.centerX(), textCenterY, theme.textPaint)
            theme.textPaint.textSize = originalSize
        }
    }
}
