package com.buzzingmountain.dingclock.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.buzzingmountain.dingclock.BuildConfig
import com.buzzingmountain.dingclock.R
import com.buzzingmountain.dingclock.accessibility.AccessibilityBridge
import com.buzzingmountain.dingclock.accessibility.AccessibilityHelper
import com.buzzingmountain.dingclock.accessibility.DingAccessibilityService
import com.buzzingmountain.dingclock.accessibility.screens.DingLoginFlow
import com.buzzingmountain.dingclock.accessibility.screens.DingScreen
import com.buzzingmountain.dingclock.core.StepResult
import com.buzzingmountain.dingclock.data.AppConfig
import com.buzzingmountain.dingclock.data.ConfigRepository
import com.buzzingmountain.dingclock.data.HolidayMode
import com.buzzingmountain.dingclock.data.PunchLogRepository
import com.buzzingmountain.dingclock.data.PunchType
import com.buzzingmountain.dingclock.databinding.ActivityMainBinding
import com.buzzingmountain.dingclock.dingtalk.DingTalkLauncher
import com.buzzingmountain.dingclock.net.NetworkProbe
import com.buzzingmountain.dingclock.scheduler.HolidayChecker
import com.buzzingmountain.dingclock.scheduler.PunchScheduler
import com.buzzingmountain.dingclock.ui.setup.SetupActivity
import com.buzzingmountain.dingclock.util.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: ConfigRepository

    /** Guard so programmatic setChecked in render() doesn't re-fire the listener. */
    private var suppressSwitchListener = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = ConfigRepository(this)

        binding.versionText.text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        binding.setupButton.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        binding.accessibilitySettingsButton.setOnClickListener { openAccessibilitySettings() }
        binding.decryptCheckButton.setOnClickListener { runDecryptCheck() }
        binding.launchAndCheckButton.setOnClickListener { runLaunchAndCheck() }
        binding.autoPunchSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSwitchListener) return@setOnCheckedChangeListener
            onAutoPunchToggled(isChecked)
        }
    }

    override fun onResume() {
        super.onResume()
        render(repo.load())
        renderAccessibilityStatus()
    }

    private fun renderAccessibilityStatus() {
        val on = AccessibilityHelper.isServiceEnabled(this, DingAccessibilityService::class.java)
        binding.accessibilityStatusText.text = getString(
            if (on) R.string.main_accessibility_on else R.string.main_accessibility_off,
        )
    }

    private fun render(cfg: AppConfig?) {
        if (cfg == null || !cfg.isComplete()) {
            renderUnconfigured()
            return
        }
        renderConfigured(cfg)
    }

    private fun renderUnconfigured() {
        binding.notConfiguredText.visibility = View.VISIBLE
        listOf(
            binding.colleagueText, binding.scheduleText, binding.holidayText,
            binding.passwordStateText, binding.webhookText, binding.decryptCheckButton,
        ).forEach { it.visibility = View.GONE }
        binding.setupButton.text = getString(R.string.main_open_setup)
        binding.decryptResultText.text = ""

        suppressSwitchListener = true
        binding.autoPunchSwitch.isChecked = false
        binding.autoPunchSwitch.isEnabled = false
        suppressSwitchListener = false

        binding.autoPunchStateText.text = getString(R.string.main_auto_punch_off)
        binding.nextRunText.visibility = View.GONE
        renderRecentLogs(emptyList())
    }

    private fun renderConfigured(cfg: AppConfig) {
        binding.notConfiguredText.visibility = View.GONE
        listOf(
            binding.colleagueText, binding.scheduleText, binding.holidayText,
            binding.passwordStateText, binding.webhookText, binding.decryptCheckButton,
        ).forEach { it.visibility = View.VISIBLE }
        binding.setupButton.text = getString(R.string.main_edit_setup)

        suppressSwitchListener = true
        binding.autoPunchSwitch.isChecked = cfg.enabled
        binding.autoPunchSwitch.isEnabled = true
        suppressSwitchListener = false

        binding.autoPunchStateText.text = getString(
            if (cfg.enabled) R.string.main_auto_punch_on else R.string.main_auto_punch_off,
        )

        binding.colleagueText.text = getString(
            R.string.label_colleague,
            cfg.colleagueName.ifBlank { getString(R.string.no_colleague_name) },
        )
        binding.scheduleText.text = getString(
            R.string.label_schedule,
            cfg.morningPunchAt,
            cfg.eveningPunchAt,
            cfg.randomJitterSeconds,
        )
        binding.holidayText.text = when (cfg.holidayMode) {
            HolidayMode.WEEKENDS_ONLY -> getString(R.string.label_holiday_weekends)
            HolidayMode.CUSTOM_LIST -> getString(R.string.label_holiday_custom, cfg.customHolidays.size)
        }
        binding.passwordStateText.text =
            if (cfg.passwordCipher.isNotEmpty()) getString(R.string.label_password_saved)
            else getString(R.string.label_password_missing)
        binding.webhookText.text =
            if (cfg.webhookUrl.isBlank()) getString(R.string.label_webhook_missing)
            else getString(R.string.label_webhook_set)

        // Next scheduled (only shown when auto-punch is on).
        binding.nextRunText.visibility = if (cfg.enabled) View.VISIBLE else View.GONE
        if (cfg.enabled) binding.nextRunText.text = nextScheduledLabel(cfg)

        // Recent 5 logs.
        lifecycleScope.launch {
            val recent = runCatching { PunchLogRepository(this@MainActivity).recent(limit = 5) }
                .getOrDefault(emptyList())
            renderRecentLogs(recent)
        }
    }

    private fun renderRecentLogs(entries: List<com.buzzingmountain.dingclock.db.PunchLogEntity>) {
        val container = binding.recentLogsContainer
        container.removeAllViews()
        if (entries.isEmpty()) {
            binding.recentLogsEmptyText.visibility = View.VISIBLE
            return
        }
        binding.recentLogsEmptyText.visibility = View.GONE
        val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        entries.forEach { e ->
            val tv = TextView(this).apply {
                val ts = fmt.format(
                    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(e.startedAt), ZoneId.systemDefault()),
                )
                val mark = if (e.success) "✓" else "✗"
                val typeZh = runCatching { PunchType.valueOf(e.type) }.getOrNull()?.zh ?: e.type
                text = getString(R.string.main_recent_log_line, ts, mark, typeZh, e.finalState)
                textSize = 13f
                setPadding(0, 4, 0, 4)
            }
            container.addView(tv)
        }
    }

    private fun onAutoPunchToggled(isChecked: Boolean) {
        val cfg = repo.load() ?: return
        if (cfg.enabled == isChecked) return
        val updated = cfg.copy(enabled = isChecked)
        repo.save(updated)
        Timber.i("auto-punch toggle → %s", isChecked)
        // Reprogram AlarmManager + WorkManager immediately. When disabled, this cancels
        // all pending punch alarms; when enabled, it rearms them.
        runCatching { PunchScheduler(this).rescheduleAll() }
            .onFailure { Timber.e(it, "rescheduleAll failed") }
        binding.autoPunchStateText.text = getString(
            if (isChecked) R.string.main_auto_punch_on else R.string.main_auto_punch_off,
        )
        binding.nextRunText.visibility = if (isChecked) View.VISIBLE else View.GONE
        if (isChecked) binding.nextRunText.text = nextScheduledLabel(updated)
    }

    private fun nextScheduledLabel(cfg: AppConfig): String {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val morning = TimeUtils.parseHHmm(cfg.morningPunchAt)
        val evening = TimeUtils.parseHHmm(cfg.eveningPunchAt)

        // Search up to 14 days ahead for the next workday slot.
        var date = now.toLocalDate()
        for (offset in 0..14) {
            val day = date.plusDays(offset.toLong())
            if (!HolidayChecker.isWorkday(day, cfg)) continue
            val candidates = listOf(morning to PunchType.MORNING, evening to PunchType.EVENING)
                .map { (t: LocalTime, type: PunchType) -> LocalDateTime.of(day, t) to type }
                .filter { (dt, _) -> dt.isAfter(now) }
                .sortedBy { it.first }
            val first = candidates.firstOrNull() ?: continue
            val (dt, type) = first
            val rel = when {
                day == now.toLocalDate() -> "今天"
                day == now.toLocalDate().plusDays(1) -> "明天"
                else -> DateTimeFormatter.ofPattern("MM-dd").format(day)
            }
            return getString(R.string.main_next_run, rel, DateTimeFormatter.ofPattern("HH:mm").format(dt), type.zh)
        }
        return getString(R.string.main_no_next_run)
    }

    private fun runLaunchAndCheck() {
        val btn = binding.launchAndCheckButton
        val out = binding.launchAndCheckResult
        val cfg = repo.load()
        btn.isEnabled = false
        out.text = getString(R.string.launch_check_running)
        lifecycleScope.launch {
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            val lines = mutableListOf<String>()
            fun append(line: String) {
                lines += "[${timeFmt.format(Date())}] $line"
                out.text = lines.joinToString("\n")
            }

            // 1. Accessibility readiness.
            if (!AccessibilityBridge.isReady()) {
                append(getString(R.string.launch_check_acc_missing))
                btn.isEnabled = true
                return@launch
            }

            // 2. Network probe.
            append(getString(R.string.launch_check_probing))
            when (val r = NetworkProbe.check()) {
                StepResult.Success -> append(getString(R.string.launch_check_probe_ok))
                is StepResult.Failure -> {
                    append(getString(R.string.launch_check_probe_fail, r.reason))
                    btn.isEnabled = true
                    return@launch
                }
            }

            // 3. Launch DingTalk and wait for foreground.
            append(getString(R.string.launch_check_launching))
            when (val r = DingTalkLauncher(applicationContext).launchAndAwaitForeground(timeoutMs = 15_000)) {
                StepResult.Success -> append(getString(R.string.launch_check_foreground_ok))
                is StepResult.Failure -> {
                    append(getString(R.string.launch_check_foreground_fail, r.reason))
                    btn.isEnabled = true
                    return@launch
                }
            }

            // 4. Classify current screen — login vs already logged in.
            delay(2_000)
            val screen = classifyScreenWithRetry()
            append(getString(R.string.launch_check_classified, screenLabel(screen)))

            if (screen == DingScreen.Login) {
                append(getString(R.string.launch_check_login_needed))
                when (val login = DingLoginFlow.completeSavedPasswordLogin { cfg?.let(repo::decryptPassword) }) {
                    StepResult.Success -> {
                        append(getString(R.string.launch_check_login_ok))
                        val afterLoginScreen = classifyScreenWithRetry()
                        append(getString(R.string.launch_check_classified, screenLabel(afterLoginScreen)))
                    }
                    is StepResult.Failure -> {
                        append(getString(R.string.launch_check_login_fail, login.reason))
                        btn.isEnabled = true
                        return@launch
                    }
                }
            }

            append(getString(R.string.launch_check_finished))

            btn.isEnabled = true
        }
    }

    private suspend fun classifyScreenWithRetry(): DingScreen {
        repeat(6) {
            when (val s = DingScreen.classify(AccessibilityBridge.currentRoot())) {
                DingScreen.Login,
                DingScreen.Home,
                DingScreen.Attendance,
                DingScreen.PunchSuccess,
                DingScreen.NotDingTalk -> return s
                DingScreen.Splash, DingScreen.Unknown -> delay(1_000)
            }
        }
        return DingScreen.Unknown
    }

    private fun screenLabel(screen: DingScreen): String =
        when (screen) {
            DingScreen.Login -> getString(R.string.launch_check_screen_login)
            DingScreen.Home -> getString(R.string.launch_check_screen_home)
            DingScreen.Attendance -> getString(R.string.launch_check_screen_attendance)
            DingScreen.PunchSuccess -> getString(R.string.launch_check_screen_punched)
            DingScreen.NotDingTalk -> getString(R.string.launch_check_screen_other)
            DingScreen.Splash, DingScreen.Unknown -> getString(R.string.launch_check_screen_unknown)
        }

    private fun runDecryptCheck() {
        val cfg = repo.load() ?: return
        val plain = repo.decryptPassword(cfg)
        if (plain == null) {
            binding.decryptResultText.text = getString(R.string.decrypt_failed)
        } else {
            val masked = "•".repeat(plain.length.coerceAtMost(12))
            binding.decryptResultText.text = getString(R.string.decrypt_ok, masked, plain.length)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, R.string.intent_unavailable, Toast.LENGTH_LONG).show()
            }
    }
}
