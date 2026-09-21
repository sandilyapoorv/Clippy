package com.clippy.forever

import android.Manifest
import android.app.DatePickerDialog
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.clippy.forever.databinding.ActivityMainBinding
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var capture: ClipboardCapture
    private lateinit var adapter: ClipAdapter
    private var query: String = ""
    private var dayStart: Long? = null
    private var dayEnd: Long? = null

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        ClipboardWatchService.start(this)
        maybeAskBatteryExemption()
        refreshChrome()
    }

    private val backupSaver = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            ClipBackup.export(this, uri)
            Toast.makeText(this, R.string.backup_ok, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private val restoreOpener = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            ClipBackup.import(this, uri)
            Toast.makeText(this, R.string.restore_ok, Toast.LENGTH_SHORT).show()
            refresh(scrollToTop = true)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (hasWindowFocus()) {
            showStatus(capture.captureCurrent())
            refresh(scrollToTop = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        capture = ClipboardCapture(this)
        adapter = ClipAdapter(
            onCopy = { record ->
                capture.copyBack(record)
                Toast.makeText(this, R.string.copied_again, Toast.LENGTH_SHORT).show()
            },
            onEdit = { record -> showEdit(record) },
            onDelete = { record ->
                ClippyApp.instance.store.delete(record.id)
                Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show()
                refresh()
            },
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val first = (recyclerView.layoutManager as LinearLayoutManager)
                    .findFirstVisibleItemPosition()
                if (first > 0) {
                    binding.captureButton.hide()
                } else {
                    binding.captureButton.show()
                }
            }
        })
        binding.search.doAfterTextChanged {
            query = it?.toString().orEmpty()
            refresh()
        }
        binding.captureButton.setOnClickListener {
            showStatus(capture.captureCurrent())
            refresh(scrollToTop = true)
        }
        binding.workingSwitch.setOnCheckedChangeListener { _, checked ->
            WatchPrefs.setEnabled(this, checked)
            if (checked) ClipboardWatchService.start(this) else ClipboardWatchService.stop(this)
            refreshChrome()
        }
        binding.setupButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.backupButton.setOnClickListener {
            backupSaver.launch("clippy-by-apoorv-backup.json")
        }
        binding.restoreButton.setOnClickListener {
            restoreOpener.launch(arrayOf("application/json", "*/*"))
        }
        binding.dateButton.setOnClickListener { pickDate() }
        binding.dateButton.setOnLongClickListener {
            clearDateFilter()
            true
        }
        requestNotificationAccess()
        handleIncoming(intent)
        refresh(scrollToTop = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
        refresh(scrollToTop = true)
    }

    override fun onResume() {
        super.onResume()
        ClippyApp.mainVisible = true
        ClipInbox.drain(this, ClippyApp.instance.store)
        binding.workingSwitch.isChecked = WatchPrefs.isEnabled(this)
        refreshChrome()
        if (WatchPrefs.isEnabled(this)) {
            ClipboardWatchService.start(this)
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(clipListener)
        showStatus(capture.captureCurrent())
        refresh(scrollToTop = true)
        binding.captureButton.show()
    }

    override fun onPause() {
        ClippyApp.mainVisible = false
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.removePrimaryClipChangedListener(clipListener)
        super.onPause()
    }

    private fun showEdit(record: ClipRecord) {
        val input = EditText(this).apply {
            setText(record.text.orEmpty())
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_title)
            .setView(input)
            .setPositiveButton(R.string.save_edit) { _, _ ->
                val next = input.text?.toString().orEmpty()
                if (ClippyApp.instance.store.updateText(record.id, next)) {
                    Toast.makeText(this, R.string.edited, Toast.LENGTH_SHORT).show()
                    refresh(scrollToTop = true)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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

    private fun applySystemBarInsets() {
        val fabGap = resources.getDimensionPixelSize(R.dimen.fab_edge_gap)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contentRoot.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
            )
            val params = binding.captureButton.layoutParams as CoordinatorLayout.LayoutParams
            params.bottomMargin = fabGap + bars.bottom
            params.marginEnd = fabGap + bars.right
            binding.captureButton.layoutParams = params
            insets
        }
    }

    private fun pickDate() {
        val calendar = Calendar.getInstance()
        dayStart?.let { calendar.timeInMillis = it }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val start = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val end = start.clone() as Calendar
                end.add(Calendar.DAY_OF_MONTH, 1)
                dayStart = start.timeInMillis
                dayEnd = end.timeInMillis
                refresh(scrollToTop = true)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).apply {
            setButton(DatePickerDialog.BUTTON_NEUTRAL, getString(R.string.clear_date)) { _, _ ->
                clearDateFilter()
            }
        }.show()
    }

    private fun clearDateFilter() {
        if (dayStart == null && dayEnd == null) return
        dayStart = null
        dayEnd = null
        refresh(scrollToTop = true)
    }

    private fun refresh(scrollToTop: Boolean = false) {
        val items = ClippyApp.instance.store.all(query, dayStart, dayEnd)
        adapter.submitList(items) {
            if (scrollToTop && items.isNotEmpty()) {
                binding.list.scrollToPosition(0)
                binding.captureButton.show()
            }
        }
        binding.empty.isVisible = items.isEmpty()
        binding.emptyBody.setText(if (dayStart != null) R.string.empty_date_body else R.string.empty_body)
        val start = dayStart
        if (start != null) {
            val dateLabel = DateFormat.getMediumDateFormat(this).format(start)
            binding.dateButton.text = dateLabel
            binding.countLabel.text = getString(R.string.count_on_date, items.size, dateLabel)
        } else {
            binding.dateButton.text = getString(R.string.all_dates)
            binding.countLabel.text = getString(R.string.count_label, ClippyApp.instance.store.count())
        }
    }

    private fun refreshChrome() {
        val working = WatchPrefs.isEnabled(this)
        binding.workingLabel.text = getString(if (working) R.string.working_yes else R.string.working_no)
        val needsSetup = !ClipboardAccessibilityService.isEnabled(this)
        binding.setupButton.isVisible = needsSetup
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
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        ClipboardWatchService.start(this)
        maybeAskBatteryExemption()
    }

    private fun maybeAskBatteryExemption() {
        if (Build.VERSION.SDK_INT < 23) return
        val power = getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(packageName)) return
        if (!WatchPrefs.shouldAskBattery(this)) return
        WatchPrefs.markBatteryAsked(this)
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
