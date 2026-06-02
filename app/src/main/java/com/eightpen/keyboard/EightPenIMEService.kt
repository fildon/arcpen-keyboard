package com.eightpen.keyboard

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo

class EightPenIMEService : InputMethodService() {

    private var keyboardView: EightPenView? = null
    private var lastSpaceTime = 0L
    private val DOUBLE_TAP_MS = 300L

    override fun onCreateInputView(): View {
        return EightPenView(this).also { view ->
            keyboardView = view
            view.keyListener = object : EightPenView.KeyListener {
                override fun onCharacter(ch: Char) {
                    currentInputConnection?.commitText(ch.toString(), 1)
                }
                override fun onBackspace() {
                    val ic = currentInputConnection ?: return
                    if (!ic.getSelectedText(0).isNullOrEmpty()) {
                        ic.commitText("", 1)
                    } else {
                        ic.deleteSurroundingText(1, 0)
                    }
                }
                override fun onSpace() {
                    val ic = currentInputConnection ?: return
                    val now = SystemClock.uptimeMillis()
                    if (now - lastSpaceTime < DOUBLE_TAP_MS &&
                        ic.getTextBeforeCursor(1, 0) == " ") {
                        // Double-tap: swap the trailing space for ". " and auto-capitalise
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(". ", 1)
                        lastSpaceTime = 0L
                        keyboardView?.shiftState = ShiftState.ONCE
                    } else {
                        ic.commitText(" ", 1)
                        lastSpaceTime = now
                    }
                }
                override fun onEnter() {
                    val ic  = currentInputConnection ?: return
                    val ei  = currentInputEditorInfo
                    val opts = ei?.imeOptions ?: 0
                    val action = opts and EditorInfo.IME_MASK_ACTION
                    val isMultiLine = (ei?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
                    // IME_FLAG_NO_ENTER_ACTION tells us the app wants Enter to mean newline
                    // even when an action is also configured.
                    val noEnterAction = opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0

                    if (isMultiLine || noEnterAction ||
                        action == EditorInfo.IME_ACTION_NONE ||
                        action == EditorInfo.IME_ACTION_UNSPECIFIED) {
                        ic.commitText("\n", 1)
                    } else {
                        ic.performEditorAction(action)
                    }
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val autoCap = info?.let {
            it.inputType and android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0
        } ?: false
        if (autoCap) keyboardView?.shiftState = ShiftState.ONCE
    }
}
