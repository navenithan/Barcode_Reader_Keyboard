package com.codetropic.barcodescanner

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.barcodereaderkeyboard.R

class ScannerKeyboardService : InputMethodService() {

    companion object {
        var pendingScanText: String? = null
        private const val TAG = "MainActivity"
    }

    override fun onCreateInputView(): View {
        val layout = layoutInflater.inflate(R.layout.input_view, null)
        val scanButton = layout.findViewById<Button>(R.id.button_scan_from_ime)
        scanButton.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            startActivity(intent)
        }
        return layout
    }

    override fun onWindowShown() {
        super.onWindowShown()
        pendingScanText?.let {
            if (it.isNotEmpty()) {
                commitScan(it)
                pendingScanText = null // Clear the text after committing
            }
        }
    }

    private fun commitScan(text: String) {
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            Log.d(TAG, "Committing text: $text")
            val success = inputConnection.commitText(text, 1)
            if (success) {
                Log.d(TAG, "Text committed successfully")
            } else {
                Log.e(TAG, "Failed to commit text")
            }
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        } else {
            Log.e(TAG, "Input connection is null, could not commit text: $text")
        }
    }
}