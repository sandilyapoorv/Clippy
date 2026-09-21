package com.clippy.forever

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CaptureActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = ClipboardCapture(this).captureCurrent()
        val message = when (status) {
            CaptureStatus.SAVED -> getString(R.string.saved)
            CaptureStatus.DUPLICATE -> getString(R.string.already_saved)
            CaptureStatus.EMPTY -> getString(R.string.nothing_on_clipboard)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
