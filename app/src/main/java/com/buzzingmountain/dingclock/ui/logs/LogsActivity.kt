package com.buzzingmountain.dingclock.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.buzzingmountain.dingclock.R
import com.buzzingmountain.dingclock.databinding.ActivityLogsBinding
import com.buzzingmountain.dingclock.log.LogRepository
import timber.log.Timber

class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding
    private lateinit var repo: LogRepository

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refresh(autoScroll = false)
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = LogRepository(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> { refresh(autoScroll = true); true }
                R.id.action_copy -> { copyToClipboard(); true }
                R.id.action_share -> { shareLogFiles(); true }
                R.id.action_clear -> { confirmClear(); true }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh(autoScroll = true)
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun refresh(autoScroll: Boolean) {
        val files = repo.listFiles()
        binding.metaText.text = getString(
            R.string.logs_meta,
            files.size,
            repo.totalBytes() / 1024.0,
        )
        val text = repo.tail(MAX_LINES)
        if (text != binding.logText.text.toString()) {
            binding.logText.text = text.ifEmpty { getString(R.string.logs_empty) }
            if (autoScroll) {
                binding.scrollView.post { binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
            }
        }
    }

    private fun copyToClipboard() {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("DingClock log", binding.logText.text))
        Toast.makeText(this, R.string.logs_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareLogFiles() {
        val files = repo.listFiles()
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.logs_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val authority = "${packageName}.fileprovider"
        val uris = ArrayList(files.map { FileProvider.getUriForFile(this, authority, it) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.logs_action_share))) }
            .onFailure {
                Timber.e(it, "share failed")
                Toast.makeText(this, R.string.logs_share_failed, Toast.LENGTH_LONG).show()
            }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logs_clear_confirm_title)
            .setMessage(R.string.logs_clear_confirm_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.logs_action_clear) { _, _ ->
                repo.clearAll()
                Timber.i("All logs cleared by user")
                refresh(autoScroll = true)
            }
            .show()
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 2_000L
        private const val MAX_LINES = 4_000
    }
}
