package lab.norlu.openkeyboard.service

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout

import lab.norlu.openkeyboard.core.RustEngineAsync
import lab.norlu.openkeyboard.security.SecureKeyManager
import lab.norlu.openkeyboard.ui.keyboard.OptimisedKeyboardView
import lab.norlu.openkeyboard.ui.keyboard.KeyboardState
import lab.norlu.openkeyboard.utils.InputContextUtils

/**
 * AdvancedKeyboardService: O orquestrador central do Norlu Keyboard.
 * Esta classe agora delega as responsabilidades de Input e Predição para handlers especializados,
 * mantendo o ciclo de vida e a integração com o sistema Android.
 */
class AdvancedKeyboardService : InputMethodService() {
    private lateinit var rustEngine: RustEngineAsync
    private lateinit var keyboardView: OptimisedKeyboardView
    private lateinit var preferences: KeyboardPreferences
    
    private lateinit var inputHandler: KeyboardInputHandler
    private lateinit var predictionHandler: KeyboardPredictionHandler

    override fun onCreate() {
        super.onCreate()
        preferences = KeyboardPreferences(this)
        
        // Inicializa o motor Rust com a chave segura do Hardware (Keystore)
        val secureKey = SecureKeyManager.getOrCreateSecureKey()
        rustEngine = RustEngineAsync(filesDir.absolutePath, secureKey)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        // Proteção Anti-Screenshot Permanente
        window?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val inputType = attribute?.inputType ?: 0
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
        
        val isPassword = variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                        variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD

        if (::predictionHandler.isInitialized) {
            predictionHandler.isPredictionEnabled = !isPassword
        }

        // Contexto de Domínio: App + Tipo de Entrada
        val domain = "${attribute?.packageName ?: "unknown"}:${attribute?.inputType ?: 0}"
        rustEngine.setDomain(domain)

        if (::keyboardView.isInitialized) {
            configureKeyboardView(attribute)
        }
    }

    private fun configureKeyboardView(attribute: EditorInfo?) {
        val inputType = attribute?.inputType ?: 0
        val isMultiLine = (inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val imeOptions = attribute?.imeOptions ?: 0
        val action = imeOptions and EditorInfo.IME_MASK_ACTION

        keyboardView.setImeAction(if (isMultiLine) EditorInfo.IME_ACTION_NONE else action)

        val inputClass = inputType and EditorInfo.TYPE_MASK_CLASS
        if (inputClass == EditorInfo.TYPE_CLASS_NUMBER || inputClass == EditorInfo.TYPE_CLASS_PHONE) {
            keyboardView.setKeyboardState(KeyboardState.NUMERIC_PAD)
        } else {
            keyboardView.setKeyboardState(KeyboardState.ALPHA)
        }
        
        keyboardView.setHapticEnabled(preferences.isHapticEnabled)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false
    override fun onEvaluateInputViewShown(): Boolean = true

    override fun onCreateInputView(): View {
        val root = FrameLayout(this)
        keyboardView = OptimisedKeyboardView(this)
        
        // Inicializa os handlers com a view criada
        inputHandler = KeyboardInputHandler(rustEngine, keyboardView)
        predictionHandler = KeyboardPredictionHandler(rustEngine, keyboardView)
        
        setupKeyboardView()

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        keyboardView.layoutParams = params

        root.setOnApplyWindowInsetsListener { view, insets ->
            val bottomInset = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            view.setPadding(0, 0, 0, bottomInset)
            insets
        }

        root.clipChildren = false
        root.clipToPadding = false
        root.addView(keyboardView)
        
        keyboardView.requestFocus()
        return root
    }

    private fun setupKeyboardView() {
        keyboardView.applyTheme(preferences.isDarkTheme)
        keyboardView.setHapticEnabled(preferences.isHapticEnabled)

        keyboardView.setOnThemeChangedListener { dark ->
            preferences.isDarkTheme = dark
        }

        keyboardView.setOnSuggestionClickListener { suggestion ->
            triggerVibration()
            inputHandler.isDeletingSequence = false
            currentInputConnection?.let { ic ->
                val textBefore = ic.getTextBeforeCursor(100, 0) ?: ""
                val lastWord = InputContextUtils.getLastWord(textBefore)
                
                if (lastWord.isNotEmpty()) {
                    rustEngine.pushUndo(lastWord.toString())
                    keyboardView.setUndoVisible(true)
                    ic.deleteSurroundingText(lastWord.length, 0)
                }
                ic.commitText("$suggestion ", 1)
                
                rustEngine.learnWord(suggestion, InputContextUtils.getCurrentContext(textBefore, skipLast = lastWord.isNotEmpty()))
                
                predictionHandler.currentGhostText = ""
                keyboardView.setGhostText("")
                predictionHandler.updatePredictions(ic)
            }
        }

        keyboardView.setOnKeyClickListener { char ->
            triggerVibration()
            currentInputConnection?.let { ic ->
                when (char) {
                    "⌫" -> inputHandler.handleBackspace(ic)
                    "\n" -> inputHandler.handleEnter(ic, currentInputEditorInfo)
                    " " -> inputHandler.handleSpace(ic, predictionHandler.isPredictionEnabled, predictionHandler.currentGhostText)
                    else -> inputHandler.handleText(ic, char)
                }
                predictionHandler.updatePredictions(ic)
            }
        }

        keyboardView.setOnGlobeClickListener {
            triggerVibration()
            switchToNextInputMethod()
        }

        keyboardView.setOnUndoClickListener {
            triggerVibration()
            currentInputConnection?.let { ic ->
                inputHandler.performUndo(ic)
                predictionHandler.updatePredictions(ic)
            }
        }
    }

    private fun switchToNextInputMethod() {
        try {
            @Suppress("DEPRECATION")
            switchToNextInputMethod(false)
        } catch (e: Exception) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val token = window?.window?.attributes?.token
            if (token != null) {
                @Suppress("DEPRECATION")
                imm.switchToNextInputMethod(token, true)
            }
        }
    }

    private fun triggerVibration() {
        if (preferences.isHapticEnabled) {
            keyboardView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        rustEngine.runMaintenance(0.5)
        rustEngine.clearUndo()
        if (::inputHandler.isInitialized) inputHandler.isDeletingSequence = false
        if (::keyboardView.isInitialized) {
            keyboardView.setUndoVisible(false)
            keyboardView.setSuggestions(emptyArray())
            keyboardView.setGhostText("")
        }
    }

    override fun onDestroy() {
        rustEngine.close()
        super.onDestroy()
    }
}
