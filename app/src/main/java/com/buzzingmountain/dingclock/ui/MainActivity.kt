package com.buzzingmountain.dingclock.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import com.buzzingmountain.dingclock.databinding.ActivityMainBinding
import com.buzzingmountain.dingclock.dingtalk.DingTalkLauncher
import com.buzzingmountain.dingclock.net.NetworkProbe
import com.buzzingmountain.dingclock.ui.setup.SetupActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: ConfigRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = ConfigRepository(this)

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                startActivity(Intent(this, SetupActivity::class.java))
                true
            } else {
                false
            }
        }
        binding.versionText.text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        binding.accessibilitySettingsButton.setOnClickListener { openAccessibilitySettings() }
        binding.launchAndCheckButton.setOnClickListener { runLaunchAndCheck() }
    }

    override fun onResume() {
        super.onResume()
        val cfg = repo.load()
        renderAccessibilityStatus()
        renderConfigStatus(cfg)
    }

    private fun renderAccessibilityStatus() {
        val on = AccessibilityHelper.isServiceEnabled(this, DingAccessibilityService::class.java)
        binding.accessibilityStatusText.text = getString(
            if (on) R.string.main_accessibility_on else R.string.main_accessibility_off,
        )
        binding.accessibilitySettingsButton.isEnabled = !on
        binding.accessibilitySettingsButton.text = getString(
            if (on) R.string.main_accessibility_ready else R.string.main_restore_accessibility,
        )
    }

    private fun renderConfigStatus(cfg: AppConfig?) {
        val configured = cfg?.isComplete() == true
        binding.notConfiguredText.text = getString(
            if (configured) R.string.main_configured_help else R.string.main_not_configured,
        )
        binding.passwordStateText.text = getString(
            if (configured) R.string.label_password_saved else R.string.label_password_missing,
        )
    }

    private fun runLaunchAndCheck() {
        val btn = binding.launchAndCheckButton
        val out = binding.launchAndCheckResult
        val cfg = repo.load()
        btn.isEnabled = false
        out.text = getString(R.string.launch_check_running)
        lifecycleScope.launch {
            if (!AccessibilityBridge.isReady()) {
                out.text = getString(R.string.launch_check_acc_missing)
                btn.isEnabled = true
                return@launch
            }

            out.text = getString(R.string.launch_check_probing)
            when (val r = NetworkProbe.check()) {
                StepResult.Success -> out.text = getString(R.string.launch_check_probe_ok)
                is StepResult.Failure -> {
                    out.text = getString(R.string.launch_check_probe_fail, r.reason)
                    btn.isEnabled = true
                    return@launch
                }
            }

            when (val r = DingTalkLauncher(applicationContext).launchAndAwaitForeground(timeoutMs = 15_000)) {
                StepResult.Success -> out.text = getString(R.string.launch_check_foreground_ok)
                is StepResult.Failure -> {
                    out.text = getString(R.string.launch_check_foreground_fail, r.reason)
                    btn.isEnabled = true
                    return@launch
                }
            }

            delay(2_000)
            val screen = classifyScreenWithRetry()
            if (screen == DingScreen.Login) {
                out.text = getString(R.string.launch_check_login_needed)
                when (val login = DingLoginFlow.completeSavedPasswordLogin { cfg?.let(repo::decryptPassword) }) {
                    StepResult.Success -> {
                        out.text = getString(R.string.launch_check_login_ok)
                    }
                    is StepResult.Failure -> {
                        out.text = getString(R.string.launch_check_login_fail, login.reason)
                        btn.isEnabled = true
                        return@launch
                    }
                }
            } else {
                out.text = getString(R.string.launch_check_finished, screenLabel(screen))
            }

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

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, R.string.intent_unavailable, Toast.LENGTH_LONG).show()
            }
    }
}
