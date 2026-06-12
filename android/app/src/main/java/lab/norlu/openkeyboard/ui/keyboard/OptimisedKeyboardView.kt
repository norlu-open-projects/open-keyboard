package lab.norlu.openkeyboard.ui.keyboard

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class OptimisedKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val themeManager = KeyboardThemeManager()
    private val touchHandler = KeyboardTouchHandler(this) { key -> handleKey(key) }
    private val renderer = KeyboardRenderer(this, themeManager, touchHandler)

    private var lastShiftTime = 0L

    // Callbacks com No-Op por padrão (Resiliência contra NullPointerException)
    private var onKeyClickListener: (String) -> Unit = {}
    private var onSuggestionClickListener: (String) -> Unit = {}
    private var onGlobeClickListener: () -> Unit = {}
    private var onThemeChangedListener: (Boolean) -> Unit = {}
    private var onUndoClickListener: () -> Unit = {}

    val isFastTyping: Boolean
        get() = touchHandler.isFastTyping

    init {
        isClickable = true
        isFocusable = true
        themeManager.updateTextSizes(resources.configuration.fontScale)
    }

    fun isLongPressActive(): Boolean = touchHandler.isLongPressActive

    // --- Public API for State Management ---

    fun setUndoVisible(visible: Boolean) {
        if (touchHandler.isUndoVisible != visible) {
            touchHandler.isUndoVisible = visible
            invalidate()
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        touchHandler.setHapticEnabled(enabled)
    }

    fun applyTheme(dark: Boolean) {
        themeManager.applyTheme(dark)
        invalidate()
    }

    fun setImeAction(action: Int) {
        if (touchHandler.imeAction != action) {
            touchHandler.imeAction = action
            invalidate()
        }
    }

    fun setKeyboardState(state: KeyboardState) {
        if (touchHandler.currentState != state) {
            touchHandler.currentState = state
            invalidate()
        }
    }

    fun setGhostText(text: String) {
        touchHandler.ghostText = text
        invalidate()
    }

    fun setSuggestions(list: Array<String>) {
        // Pre-parse para Zero-Allocation no onDraw
        val items = ArrayList<SuggestionItem>(list.size)
        for (i in 0 until list.size) {
            val rawSug = list[i]
            val parts = rawSug.split(":")
            val text = parts[0]
            val confidence = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
            items.add(SuggestionItem(text, confidence >= 3))
        }
        touchHandler.suggestions = items
        invalidate()
    }

    fun setNextCharProbabilities(probs: Map<Char, Double>) {
        touchHandler.nextCharProbabilities = probs
    }

    // --- Listeners ---

    fun setOnKeyClickListener(listener: (String) -> Unit) { onKeyClickListener = listener }
    fun setOnSuggestionClickListener(listener: (String) -> Unit) { onSuggestionClickListener = listener }
    fun setOnGlobeClickListener(listener: () -> Unit) { onGlobeClickListener = listener }
    fun setOnThemeChangedListener(listener: (Boolean) -> Unit) { onThemeChangedListener = listener }
    fun setOnUndoClickListener(listener: () -> Unit) { onUndoClickListener = listener }

    // --- View Overrides ---

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        touchHandler.topBuffer = 45 * density

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val baseHeightDp = if (isLandscape) 200f else 260f

        val keyboardHeight = baseHeightDp * density
        setMeasuredDimension(width, (keyboardHeight + touchHandler.topBuffer).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        renderer.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchHandler.onTouchEvent(event)
    }

    // --- Logic Orchestration ---

    private fun handleKey(key: KeyboardKey) {
        when (key) {
            is KeyboardKey.Suggestion -> {
                if (key.index < touchHandler.suggestions.size) {
                    val sugItem = touchHandler.suggestions[key.index]
                    onSuggestionClickListener(sugItem.text)
                }
            }
            is KeyboardKey.Undo -> onUndoClickListener()
            is KeyboardKey.Settings -> {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                touchHandler.isSettingsOpen = !touchHandler.isSettingsOpen
                invalidate()
            }
            is KeyboardKey.SettingsClose -> {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                touchHandler.isSettingsOpen = false
                invalidate()
            }
            is KeyboardKey.ThemeLight -> {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                applyTheme(false)
                onThemeChangedListener(false)
            }
            is KeyboardKey.ThemeDark -> {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                applyTheme(true)
                onThemeChangedListener(true)
            }
            is KeyboardKey.Globe -> onGlobeClickListener()
            is KeyboardKey.Symbols -> setKeyboardState(KeyboardState.SYMBOLS)
            is KeyboardKey.Numeric -> setKeyboardState(KeyboardState.NUMERIC_PAD)
            is KeyboardKey.Alpha -> setKeyboardState(KeyboardState.ALPHA)
            is KeyboardKey.Backspace -> onKeyClickListener("⌫")
            is KeyboardKey.Search -> onKeyClickListener("\n")
            is KeyboardKey.Space -> onKeyClickListener(" ")
            is KeyboardKey.Shift -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastShiftTime < 300) {
                    touchHandler.shiftState = ShiftState.CAPS_LOCK
                } else {
                    touchHandler.shiftState = when (touchHandler.shiftState) {
                        ShiftState.LOWER -> ShiftState.SHIFTED
                        else -> ShiftState.LOWER
                    }
                }
                lastShiftTime = currentTime
                invalidate()
            }
            is KeyboardKey.Text -> {
                val textToSubmit = if (touchHandler.shiftState != ShiftState.LOWER) key.upperChar else key.lowerChar
                onKeyClickListener(textToSubmit)
                if (touchHandler.shiftState == ShiftState.SHIFTED) {
                    touchHandler.shiftState = ShiftState.LOWER
                    invalidate()
                }
            }
            is KeyboardKey.None -> { /* Do nothing */ }
        }
    }
}
