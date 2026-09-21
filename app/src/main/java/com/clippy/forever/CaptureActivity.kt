package com.clippy.forever

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CaptureActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        handler.postDelayed({ captureAndFinish() }, 300)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            handler.post { captureAndFinish() }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun captureAndFinish() {
        if (finished || isFinishing) return
        finished = true
        val status = ClipboardCapture(this).captureCurrent()
        val silent = intent.getBooleanExtra(EXTRA_SILENT, false)
        if (!silent) {
            val message = when (status) {
                CaptureStatus.SAVED -> getString(R.string.saved)
                CaptureStatus.DUPLICATE -> getString(R.string.already_saved)
                CaptureStatus.EMPTY -> getString(R.string.nothing_on_clipboard)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_SILENT = "silent"
    }
}
