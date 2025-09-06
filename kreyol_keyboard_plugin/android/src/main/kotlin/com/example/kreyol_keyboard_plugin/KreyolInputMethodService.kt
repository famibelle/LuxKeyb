package com.example.kreyol_keyboard_plugin

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.InputConnection
import android.util.Log

class KreyolInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard
    
    companion object {
        private const val TAG = "KreyolIME"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KreyolInputMethodService onCreate")
    }

    override fun onCreateInputView(): View? {
        Log.d(TAG, "onCreateInputView called")
        return try {
            val rootView = layoutInflater.inflate(R.layout.keyboard, null)
            keyboardView = rootView.findViewById(R.id.keyboard)
            keyboard = Keyboard(this, R.xml.kreyol_keyboard)
            keyboardView.keyboard = keyboard
            keyboardView.setOnKeyboardActionListener(this)
            Log.d(TAG, "Input view created successfully")
            rootView
        } catch (e: Exception) {
            Log.e(TAG, "Error creating input view: ${e.message}", e)
            null
        }
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        Log.d(TAG, "onKey called with primaryCode: $primaryCode")
        val inputConnection: InputConnection = currentInputConnection ?: return

        when (primaryCode) {
            -5 -> inputConnection.deleteSurroundingText(1, 0) // Delete
            -4 -> inputConnection.commitText(" ", 1) // Space
            -1 -> inputConnection.commitText("\n", 1) // Enter
            else -> {
                val char = primaryCode.toChar().toString()
                inputConnection.commitText(char, 1)
            }
        }
    }

    override fun onPress(primaryCode: Int) {
        Log.d(TAG, "onPress: $primaryCode")
    }
    
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeDown() {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeUp() {}
}
