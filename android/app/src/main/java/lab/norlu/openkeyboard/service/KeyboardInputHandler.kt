package lab.norlu.openkeyboard.service

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.view.inputmethod.EditorInfo
import lab.norlu.openkeyboard.core.RustEngineAsync
import lab.norlu.openkeyboard.ui.keyboard.OptimisedKeyboardView
import lab.norlu.openkeyboard.utils.InputContextUtils

class KeyboardInputHandler(
    private val rustEngine: RustEngineAsync,
    private val keyboardView: OptimisedKeyboardView
) {
    var isDeletingSequence = false

    fun handleBackspace(ic: InputConnection) {
        // 1. Envia o evento nativo de deleção (KeyDown + KeyUp)
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))

        // 2. Lógica de Undo: Salva a palavra sendo apagada se for o início da deleção
        if (!keyboardView.isLongPressActive()) {
            val textBefore = ic.getTextBeforeCursor(100, 0) ?: ""
            val currentWord = InputContextUtils.getLastWord(textBefore)
            
            if (currentWord.isNotEmpty() && !isDeletingSequence) {
                rustEngine.pushUndo(currentWord.toString())
                isDeletingSequence = true
                keyboardView.setUndoVisible(true)
            } else if (currentWord.isEmpty()) {
                isDeletingSequence = false
            }
        }
        keyboardView.setUndoVisible(rustEngine.hasUndo())
    }

    fun handleEnter(ic: InputConnection, editorInfo: EditorInfo?) {
        isDeletingSequence = false
        val textBefore = ic.getTextBeforeCursor(100, 0) ?: ""
        val cleanLastWord = InputContextUtils.getCleanLastWord(textBefore)
        
        if (cleanLastWord.isNotEmpty()) {
            rustEngine.learnWord(cleanLastWord, InputContextUtils.getCurrentContext(textBefore, skipLast = true))
        }

        val inputType = editorInfo?.inputType ?: 0
        val isMultiLine = (inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val action = editorInfo?.imeOptions?.let { it and EditorInfo.IME_MASK_ACTION } ?: EditorInfo.IME_ACTION_NONE

        if (!isMultiLine && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    fun handleSpace(ic: InputConnection, isPredictionEnabled: Boolean, currentGhostText: String) {
        isDeletingSequence = false
        val textBefore = ic.getTextBeforeCursor(100, 0) ?: ""
        val cleanLastWord = InputContextUtils.getCleanLastWord(textBefore)
        
        if (cleanLastWord.isNotEmpty()) {
            rustEngine.learnWord(cleanLastWord, InputContextUtils.getCurrentContext(textBefore, skipLast = true))
        }

        // Lógica de Autocorreção: Se houver Ghost Text, aceita a sugestão
        if (isPredictionEnabled && currentGhostText.isNotEmpty()) {
            val currentWord = InputContextUtils.getLastWord(textBefore)
            if (currentWord.isNotEmpty()) {
                rustEngine.pushUndo(currentWord.toString())
            }
            ic.commitText(currentGhostText, 1) // O Ghost Text é apenas o sufixo
            ic.commitText(" ", 1)
        } else {
            ic.commitText(" ", 1)
        }
    }

    fun handleText(ic: InputConnection, text: String) {
        isDeletingSequence = false
        ic.commitText(text, 1)
    }

    fun performUndo(ic: InputConnection) {
        val wordToRestore = rustEngine.popUndo()
        if (wordToRestore.isNotEmpty()) {
            val textBefore = ic.getTextBeforeCursor(100, 0) ?: ""
            
            // Determina o tamanho do "token" atual para apagar (palavra parcial ou espaço)
            val trimmed = textBefore.trimEnd()
            val lastSpace = trimmed.lastIndexOf(' ')
            val tokenLength = if (lastSpace == -1) textBefore.length else textBefore.length - lastSpace - 1
            
            if (tokenLength > 0) {
                ic.deleteSurroundingText(tokenLength, 0)
            }
            
            ic.commitText(wordToRestore, 1)
            isDeletingSequence = false
            keyboardView.setUndoVisible(rustEngine.hasUndo())
        }
    }
}
