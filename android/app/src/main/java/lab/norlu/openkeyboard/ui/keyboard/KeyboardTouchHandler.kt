package lab.norlu.openkeyboard.ui.keyboard

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class KeyboardTouchHandler(
    private val view: View,
    private val onKeyAction: (KeyboardKey) -> Unit
) {
    private val longPressHandler = Handler(Looper.getMainLooper())
    
    var pressedKey: KeyboardKey = KeyboardKey.None
    var isLongPressActive = false
    var longPressedKey: KeyboardKey = KeyboardKey.None
    var alternateOptions: List<String> = emptyList()
    var selectedAlternateIndex: Int? = null // null means base key
    
    var pressedKeyRect = RectF()
    
    private var backspaceInterval = 100L
    private var hapticEnabled: Boolean = true

    // State dependencies (to be updated by View)
    var currentState = KeyboardState.ALPHA
    var shiftState = ShiftState.LOWER
    var imeAction: Int = EditorInfo.IME_ACTION_NONE
    var isUndoVisible = false
    var isSettingsOpen = false
    var settingsPage = 0
    var temporalShift = "MORNING"
    var temporalDay = "WEEKDAY"
    var isIncognito = false
    var analyticsData: String = "Carregando..."
    var onSettingsPageChanged: ((Int, String, String) -> Unit)? = null
    var ghostText: String = ""
    var nextCharProbabilities: Map<Char, Double> = emptyMap()
    var suggestions: List<SuggestionItem> = emptyList()
    var topBuffer: Float = 0f
    
    private var lastTouchTime = 0L
    val isFastTyping: Boolean
        get() = (System.currentTimeMillis() - lastTouchTime) < 150L

    private val backspaceRunnable = object : Runnable {
        override fun run() {
            if (pressedKey is KeyboardKey.Backspace) {
                onKeyAction(KeyboardKey.Backspace)
                if (backspaceInterval > 25L) {
                    backspaceInterval = (backspaceInterval * 0.9f).toLong().coerceAtLeast(25L)
                }
                longPressHandler.postDelayed(this, backspaceInterval)
            }
        }
    }

    private val longPressRunnable = Runnable {
        val key = pressedKey
        if (key is KeyboardKey.Backspace) {
            isLongPressActive = true
            backspaceInterval = 100L
            longPressHandler.post(backspaceRunnable)
        } else if (key is KeyboardKey.Text) {
            val alternates = KeyboardLayouts.alternatesMap[key.char.uppercase()]
            if (alternates != null) {
                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                isLongPressActive = true
                longPressedKey = key
                alternateOptions = alternates
                selectedAlternateIndex = -1 // -1 is the base key in the popup
                view.invalidate()
            }
        } else if (key is KeyboardKey.Symbols || key is KeyboardKey.Alpha || key is KeyboardKey.Numeric) {
            if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            isLongPressActive = true
            longPressedKey = KeyboardKey.None // ACTION_UP não fará nada
            
            if (currentState == KeyboardState.NUMERIC_PAD) {
                onKeyAction(KeyboardKey.Alpha)
            } else {
                onKeyAction(KeyboardKey.Numeric)
            }
        }
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isSettingsOpen) {
                    val totalHeight = view.height.toFloat() - topBuffer
                    val suggestionBarHeight = totalHeight * 0.15f
                    val top = suggestionBarHeight + topBuffer
                    
                    if (event.y > top) {
                        val localY = event.y - top
                        
                        if (settingsPage > 0 && event.x < view.width * 0.2f) {
                            settingsPage--
                            onSettingsPageChanged?.invoke(settingsPage, temporalDay, temporalShift)
                            view.invalidate()
                            return true
                        }
                        if (settingsPage < 2 && event.x > view.width * 0.8f) {
                            settingsPage++
                            onSettingsPageChanged?.invoke(settingsPage, temporalDay, temporalShift)
                            view.invalidate()
                            return true
                        }

                        if (settingsPage == 2) {
                            val h = totalHeight * 0.15f
                            val dayBtnY = totalHeight * 0.2f
                            val shiftBtnY = totalHeight * 0.4f
                            
                            if (localY in dayBtnY..(dayBtnY + h)) {
                                temporalDay = if (event.x < view.width / 2f) "WEEKDAY" else "WEEKEND"
                                onSettingsPageChanged?.invoke(settingsPage, temporalDay, temporalShift)
                                view.invalidate()
                                return true
                            }
                            
                            if (localY in shiftBtnY..(shiftBtnY + h)) {
                                temporalShift = if (event.x < view.width * 0.33f) "MORNING" 
                                    else if (event.x < view.width * 0.66f) "AFTERNOON" 
                                    else "NIGHT"
                                onSettingsPageChanged?.invoke(settingsPage, temporalDay, temporalShift)
                                view.invalidate()
                                return true
                            }
                        }
                    }
                }

                val key = resolveKeyAt(event.x, event.y)
                pressedKey = key
                isLongPressActive = false
                selectedAlternateIndex = null
                longPressedKey = KeyboardKey.None
                alternateOptions = emptyList()

                if (key != KeyboardKey.None) {
                    if (key is KeyboardKey.Backspace || (key is KeyboardKey.Text && KeyboardLayouts.alternatesMap.containsKey(key.char.uppercase()))) {
                        longPressHandler.postDelayed(longPressRunnable, 150)
                    } else if (key is KeyboardKey.Symbols || key is KeyboardKey.Alpha || key is KeyboardKey.Numeric) {
                        longPressHandler.postDelayed(longPressRunnable, 300)
                    }
                }
                view.invalidate()
                lastTouchTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isLongPressActive) {
                    val popupWidth = pressedKeyRect.width() * 1.5f
                    val totalOptions = alternateOptions.size + 1
                    val fullPopupWidth = popupWidth * totalOptions
                    var popupLeft = pressedKeyRect.centerX() - (fullPopupWidth / 2)
                    if (popupLeft < 10f) popupLeft = 10f
                    if (popupLeft + fullPopupWidth > view.width - 10f) popupLeft = view.width - fullPopupWidth - 10f

                    val relativeX = event.x - popupLeft
                    val index = (relativeX / popupWidth).toInt().coerceIn(0, totalOptions - 1)

                    val newIndex = index - 1
                    if (newIndex != selectedAlternateIndex) {
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        selectedAlternateIndex = newIndex
                        view.invalidate()
                    }
                } else {
                    val key = resolveKeyAt(event.x, event.y)
                    if (key != pressedKey) {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        pressedKey = key
                        view.invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                longPressHandler.removeCallbacks(backspaceRunnable)
                if (isLongPressActive) {
                    val finalKey = if (selectedAlternateIndex == null || selectedAlternateIndex == -1) {
                        longPressedKey
                    } else {
                        val altChar = alternateOptions.getOrNull(selectedAlternateIndex!!)
                        if (altChar != null) KeyboardKey.Text(altChar) else longPressedKey
                    }
                    if (finalKey != KeyboardKey.None) {
                        onKeyAction(finalKey)
                    }
                } else {
                    if (pressedKey != KeyboardKey.None) onKeyAction(pressedKey)
                }
                resetTouchState()
                view.invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                longPressHandler.removeCallbacks(backspaceRunnable)
                resetTouchState()
                view.invalidate()
            }
        }
        return true
    }

    private fun resetTouchState() {
        pressedKey = KeyboardKey.None
        isLongPressActive = false
        longPressedKey = KeyboardKey.None
        alternateOptions = emptyList()
        selectedAlternateIndex = null
    }

    fun resolveKeyAt(x: Float, rawY: Float): KeyboardKey {
        val y = rawY - topBuffer
        if (y < 0) return KeyboardKey.None

        val totalHeight = view.height.toFloat() - topBuffer
        val suggestionBarHeight = totalHeight * 0.15f

        if (y < suggestionBarHeight) {
            return when {
                x < view.width * 0.15f -> KeyboardKey.Settings
                isUndoVisible && x > view.width * 0.85f -> KeyboardKey.Undo
                else -> {
                    val undoButtonWidth = if (isUndoVisible) view.width * 0.15f else 0f
                    val contentAreaWidth = (view.width * 0.8f) - undoButtonWidth
                    val startX = view.width * 0.15f
                    if (x >= startX && x <= startX + contentAreaWidth) {
                        val count = suggestions.size.coerceAtMost(3)
                        if (count > 0) {
                            val sugWidth = contentAreaWidth / count
                            val index = ((x - startX) / sugWidth).toInt().coerceIn(0, count - 1)
                            KeyboardKey.Suggestion(index)
                        } else KeyboardKey.None
                    } else KeyboardKey.None
                }
            }
        }

        if (isSettingsOpen) {
            val top = suggestionBarHeight
            val btnWidth = view.width * 0.8f
            val btnHeight = 120f
            val btnX = (view.width - btnWidth) / 2f

            return when {
                y in (top + 150f)..(top + 150f + btnHeight) && x in btnX..(btnX + btnWidth) -> KeyboardKey.ThemeLight
                y in (top + 150f + btnHeight + 30f)..(top + 150f + (btnHeight * 2) + 30f) && x in btnX..(btnX + btnWidth) -> KeyboardKey.ThemeDark
                y in (totalHeight - 100f)..(totalHeight - 20f) && x in btnX..(btnX + btnWidth) -> KeyboardKey.SettingsClose
                else -> KeyboardKey.None
            }
        }
        val keyboardAreaHeight = totalHeight - suggestionBarHeight
        val rowIndex = ((y - suggestionBarHeight) / (keyboardAreaHeight / 4f)).toInt().coerceIn(0, 3)

        val keys = when (currentState) {
            KeyboardState.ALPHA -> listOf(KeyboardLayouts.alphaRow1, KeyboardLayouts.alphaRow2, KeyboardLayouts.alphaRow3, KeyboardLayouts.alphaRow4)[rowIndex]
            KeyboardState.SYMBOLS -> listOf(KeyboardLayouts.symRow1, KeyboardLayouts.symRow2, KeyboardLayouts.symRow3, KeyboardLayouts.symRow4)[rowIndex]
            KeyboardState.NUMERIC_PAD -> listOf(KeyboardLayouts.padRow1, KeyboardLayouts.padRow2, KeyboardLayouts.padRow3, KeyboardLayouts.padRow4)[rowIndex]
        }

        val margin = if (currentState == KeyboardState.ALPHA) {
            when (rowIndex) {
                1 -> view.width * 0.05f
                2 -> view.width * 0.02f
                else -> 0f
            }
        } else 0f

        val availableWidth = view.width - (margin * 2)
        var totalWeight = 0f
        for (i in 0 until keys.size) {
            totalWeight += KeyboardLayouts.getKeyWeight(keys[i], currentState)
        }

        var currentX = margin
        for (i in 0 until keys.size) {
            val key = keys[i]
            val keyWeight = KeyboardLayouts.getKeyWeight(key, currentState)
            val keyWidth = (availableWidth / totalWeight) * keyWeight

            if (x >= currentX && x <= currentX + keyWidth) {
                val padding = 11f
                val topRow = topBuffer + suggestionBarHeight + (rowIndex * (keyboardAreaHeight / 4f))
                pressedKeyRect.set(currentX + padding, topRow + padding, currentX + keyWidth - padding, topRow + (keyboardAreaHeight / 4f) - padding)

                if (currentState != KeyboardState.ALPHA || key !is KeyboardKey.Text) return key

                val keyCenterY = topRow + (keyboardAreaHeight / 8f)
                val keyCenterX = currentX + (keyWidth / 2f)
                val baseProb = nextCharProbabilities[key.char.lowercase()[0]] ?: 0.0
                
                // Cálculo Euclidiano: Distância Física combinada com Probabilidade Preditiva
                val distanceToBase = Math.hypot((x - keyCenterX).toDouble(), (y - keyCenterY).toDouble())
                var bestKey = key
                var bestScore = (baseProb + 0.01) * (100.0 / (distanceToBase + 1.0)) // 0.01 baseline para lidar com probs zeradas

                if (i > 0) {
                    val leftKey = keys[i - 1]
                    if (leftKey is KeyboardKey.Text) {
                        val leftWeight = KeyboardLayouts.getKeyWeight(leftKey, currentState)
                        val leftWidth = (availableWidth / totalWeight) * leftWeight
                        val leftCenterX = currentX - (leftWidth / 2f)
                        val leftProb = nextCharProbabilities[leftKey.char.lowercase()[0]] ?: 0.0
                        
                        val distToLeft = Math.hypot((x - leftCenterX).toDouble(), (y - keyCenterY).toDouble())
                        val leftScore = (leftProb + 0.01) * (100.0 / (distToLeft + 1.0))
                        
                        // Exige um mínimo de probabilidade real (> 0.05) para roubar a colisão da vizinhança
                        if (leftScore > bestScore && leftProb > 0.05) {
                            bestScore = leftScore
                            bestKey = leftKey
                        }
                    }
                }

                if (i < keys.size - 1) {
                    val rightKey = keys[i + 1]
                    if (rightKey is KeyboardKey.Text) {
                        val rightWeight = KeyboardLayouts.getKeyWeight(rightKey, currentState)
                        val rightWidth = (availableWidth / totalWeight) * rightWeight
                        val rightCenterX = currentX + keyWidth + (rightWidth / 2f)
                        val rightProb = nextCharProbabilities[rightKey.char.lowercase()[0]] ?: 0.0
                        
                        val distToRight = Math.hypot((x - rightCenterX).toDouble(), (y - keyCenterY).toDouble())
                        val rightScore = (rightProb + 0.01) * (100.0 / (distToRight + 1.0))
                        
                        if (rightScore > bestScore && rightProb > 0.05) {
                            bestScore = rightScore
                            bestKey = rightKey
                        }
                    }
                }
                
                return bestKey
            }
            currentX += keyWidth
        }
        return KeyboardKey.None
    }

    fun setHapticEnabled(enabled: Boolean) {
        this.hapticEnabled = enabled
    }
}
