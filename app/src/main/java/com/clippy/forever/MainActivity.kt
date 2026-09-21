package com.clippy.forever

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.clippy.forever.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var capture: ClipboardCapture
    private lateinit var adapter: ClipAdapter
    private var query: String = ""

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) SaveNotifier.show(this)
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (hasWindowFocus()) {
            showStatus(capture.captureCurrent())
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        capture = ClipboardCapture(this)
        adapter = ClipAdapter(
            onCopy = { record ->
                capture.copyBack(record)
                Toast.makeText(this, R.string.copied_again, Toast.LENGTH_SHORT).show()
            },
            onDelete = { record ->
                ClippyApp.instance.store.delete(record.id)
                Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show()
                refresh()
            },
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.search.doAfterTextChanged {
            query = it?.toString().orEmpty()
            refresh()
        }
        binding.captureButton.setOnClickListener {
            showStatus(capture.captureCurrent())
            refresh()
        }
        requestNotificationAccess()
        handleIncoming(intent)
        refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(clipListener)
        showStatus(capture.captureCurrent())
        refresh()
    }

    override fun onPause() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.removePrimaryClipChangedListener(clipListener)
        super.onPause()
    }

    private fun handleIncoming(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val uri = extraUri(intent)
                val status = when {
                    uri != null -> capture.captureSharedUri(uri, intent.type)
                    !text.isNullOrBlank() -> capture.captureSharedText(text)
                    else -> CaptureStatus.EMPTY
                }
                showStatus(status)
            }
        }
    }

    private fun extraUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun refresh() {
        val items = ClippyApp.instance.store.all(query)
        adapter.submitList(items)
        binding.empty.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.countLabel.text = getString(R.string.count_label, ClippyApp.instance.store.count())
    }

    private fun showStatus(status: CaptureStatus) {
        val message = when (status) {
            CaptureStatus.SAVED -> getString(R.string.saved)
            CaptureStatus.DUPLICATE -> null
            CaptureStatus.EMPTY -> null
        }
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationAccess() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        SaveNotifier.show(this)
    }
}
