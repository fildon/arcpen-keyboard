package com.eightpen.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

class EightPenIMEService : InputMethodService() {

    private var keyboardView: EightPenView? = null

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
                    currentInputConnection?.commitText(" ", 1)
                }
                override fun onEnter() {
                    sendDefaultEditorAction(true)
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
