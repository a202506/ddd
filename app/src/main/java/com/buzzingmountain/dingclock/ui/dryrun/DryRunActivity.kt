package com.buzzingmountain.dingclock.ui.dryrun

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.buzzingmountain.dingclock.R
import com.buzzingmountain.dingclock.accessibility.AccessibilityHelper
import com.buzzingmountain.dingclock.accessibility.DingAccessibilityService
import com.buzzingmountain.dingclock.core.Constants
import com.buzzingmountain.dingclock.core.StepResult
import com.buzzingmountain.dingclock.data.ConfigRepository
import com.buzzingmountain.dingclock.data.PunchType
import com.buzzingmountain.dingclock.databinding.ActivityDryrunBinding
import com.buzzingmountain.dingclock.dingtalk.DingTalkLauncher
import com.buzzingmountain.dingclock.service.NotificationHelper
import com.buzzingmountain.dingclock.service.PunchForegroundService
import com.buzzingmountain.dingclock.ui.logs.LogsActivity
import com.buzzingmountain.dingclock.wifi.WifiWatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DryRunActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDryrunBinding

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Timber.i("POST_NOTIFICATIONS granted=%s", granted)
            updateStatus()
        }

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDryrunBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.openAccessibilityBtn.setOnClickListener {
            tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.requestNotifBtn.setOnClickListener { requestNotifications() }
        binding.openDingTalkBtn.setOnClickListener { launchDingTalk() }
        binding.openWifiSettingsBtn.setOnClickListener {
            tryStart(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        binding.awaitWifiBtn.setOnClickListener { runAwaitWifi() }
        binding.launchDingBtn.setOnClickListener { runLaunchDingTalk() }
        binding.fullPunchBtn.setOnClickListener { runFullPunch() }
        binding.dumpHereBtn.setOnClickListener {
            val ok = DingAccessibilityService.dumpRootIfActive()
            val msgId = if (ok) R.string.dryrun_dump_done else R.string.dryrun_dump_service_off
            Toast.makeText(this, msgId, Toast.LENGTH_SHORT).show()
        }
        binding.openLogsBtn.setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
    }

    private fun runAwaitWifi() {
        binding.phase4ResultText.text = getString(R.string.dryrun_wifi_waiting_any)
        binding.awaitWifiBtn.isEnabled = false
        lifecycleScope.launch {
            val r = WifiWatcher(applicationContext).awaitConnected("", timeoutMs = 60_000)
            val timeStr = timeFmt.format(Date())
            binding.phase4ResultText.text = when (r) {
                StepResult.Success -> getString(R.string.dryrun_wifi_ok_any, timeStr)
                is StepResult.Failure -> getString(R.string.dryrun_wifi_fail, timeStr, r.reason)
            }
            binding.awaitWifiBtn.isEnabled = true
        }
    }

    private fun runLaunchDingTalk() {
        binding.phase4ResultText.text = getString(R.string.dryrun_ding_launching)
        binding.launchDingBtn.isEnabled = false
        lifecycleScope.launch {
            val r = DingTalkLauncher(applicationContext).launchAndAwaitForeground(timeoutMs = 15_000)
            val timeStr = timeFmt.format(Date())
            binding.phase4ResultText.text = when (r) {
                StepResult.Success -> getString(R.string.dryrun_ding_ok, timeStr)
                is StepResult.Failure -> getString(R.string.dryrun_ding_fail, timeStr, r.reason)
            }
            binding.launchDingBtn.isEnabled = true
        }
    }

    private fun runFullPunch() {
        val cfg = ConfigRepository(this).load()
        if (cfg == null || !cfg.isComplete()) {
            Toast.makeText(this, R.string.dryrun_full_needs_config, Toast.LENGTH_LONG).show()
            return
        }
        binding.punchStateText.text = getString(R.string.dryrun_full_started)
        binding.fullPunchBtn.isEnabled = false
        Timber.i("DryRun: full punch")
        // Service will run independently; we just kick it off and let the user watch logs.
        PunchForegroundService.startWith(this, type = PunchType.DRY_RUN)
        // Re-enable button after a short cooldown — user can re-run if needed.
        binding.fullPunchBtn.postDelayed({
            binding.fullPunchBtn.isEnabled = true
            binding.punchStateText.text = getString(R.string.dryrun_full_check_logs)
        }, 5_000)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val accOn = AccessibilityHelper.isServiceEnabled(this, DingAccessibilityService::class.java)
        binding.accessibilityStatusText.text = getString(
            if (accOn) R.string.dryrun_accessibility_on else R.string.dryrun_accessibility_off,
        )
        val notifOn = NotificationHelper.canPostNotifications(this)
        binding.notificationStatusText.text = getString(
            if (notifOn) R.string.dryrun_notif_on else R.string.dryrun_notif_off,
        )
        binding.requestNotifBtn.isEnabled =
            !notifOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            tryStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            })
            return
        }
        requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun launchDingTalk() {
        val intent = packageManager.getLaunchIntentForPackage(Constants.DINGTALK_PACKAGE)
        if (intent == null) {
            Toast.makeText(this, R.string.dryrun_dingtalk_not_installed, Toast.LENGTH_LONG).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure { Timber.e(it, "launch dingtalk failed") }
    }

    private fun tryStart(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure {
                Timber.e(it, "intent failed: %s", intent)
                Toast.makeText(this, R.string.intent_unavailable, Toast.LENGTH_LONG).show()
            }
    }
}
