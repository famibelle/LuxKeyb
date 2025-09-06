package com.example.kreyolkeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.util.Log

class TestInputMethodService : InputMethodService() {
    
    private val TAG = "TestIME"
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TEST IME SERVICE CRÉÉ !!!")
    }
    
    override fun onCreateInputView(): View? {
        Log.d(TAG, "TEST IME onCreateInputView !!!")
        
        val textView = TextView(this).apply {
            text = "TEST CLAVIER MINIMAL"
            textSize = 24f
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            setPadding(50, 50, 50, 50)
            gravity = Gravity.CENTER
            height = 300
        }
        
        Log.d(TAG, "TEST IME Vue créée !!!")
        return textView
    }
    
    override fun onStartInput(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        Log.d(TAG, "TEST IME onStartInput !!!")
    }
    
    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "TEST IME onStartInputView !!!")
    }
}
