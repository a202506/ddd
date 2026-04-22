package com.buzzingmountain.dingclock.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.buzzingmountain.dingclock.BuildConfig
import com.buzzingmountain.dingclock.R
import com.buzzingmountain.dingclock.accessibility.AccessibilityHelper
import com.buzzingmountain.dingclock.accessibility.DingAccessibilityService
import com.buzzingmountain.dingclock.data.AppConfig
import com.buzzingmountain.dingclock.data.ConfigRepository
import com.buzzingmountain.dingclock.data.HolidayMode
import com.buzzingmountain.dingclock.data.PunchLogRepository
import com.buzzingmountain.dingclock.data.PunchType
import com.buzzingmountain.dingclock.databinding.ActivityMainBinding
import com.buzzingmountain.dingclock.scheduler.HolidayChecker
import com.buzzingmountain.dingclock.ui.dryrun.DryRunActivity
import com.buzzingmountain.dingclock.ui.logs.LogsActivity
import com.buzzingmountain.dingclock.ui.setup.SetupActivity
import com.buzzingmountain.dingclock.util.TimeUtils
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: ConfigRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = ConfigRepository(this)

        binding.versionText.text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        binding.setupButton.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        binding.decryptCheckButton.setOnClickListener { runDecryptCheck() }
        binding.dryRunButton.setOnClickListener {
            startActivity(Intent(this, DryRunActivity::class.java))
        }
        binding.logsButton.setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
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
            binding.notConfiguredText.visibility = View.VISIBLE
            binding.configCard.visibility = View.GONE
            binding.decryptCheckButton.visibility = View.GONE
            binding.decryptResultText.text = ""
            binding.setupButton.text = getString(R.string.main_open_setup)
            return
        }
        binding.notConfiguredText.visibility = View.GONE
        binding.configCard.visibility = View.VISIBLE
        binding.decryptCheckButton.visibility = View.VISIBLE
        binding.setupButton.text = getString(R.string.main_edit_setup)

        binding.colleagueText.text = cfg.colleagueName.ifBlank { getString(R.string.no_colleague_name) }
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

        // Show next scheduled punch.
        binding.nextRunText.text = nextScheduledLabel(cfg)

        // Show most recent punch attempt from Room.
        lifecycleScope.launch {
            val recent = runCatching { PunchLogRepository(this@MainActivity).recent(limit = 1) }
                .getOrDefault(emptyList())
            val first = recent.firstOrNull()
            binding.recentLogText.text = if (first == null) {
                getString(R.string.main_no_punch_yet)
            } else {
                val ts = DateTimeFormatter.ofPattern("MM-dd HH:mm")
                    .format(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(first.startedAt), ZoneId.systemDefault()))
                val mark = if (first.success) "✓" else "✗"
                val typeZh = runCatching { PunchType.valueOf(first.type) }.getOrNull()?.zh ?: first.type
                getString(R.string.main_recent_log, ts, mark, typeZh, first.finalState)
            }
        }
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
}
