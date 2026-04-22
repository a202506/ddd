package com.buzzingmountain.dingclock.ui.setup

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.buzzingmountain.dingclock.R
import com.buzzingmountain.dingclock.databinding.ActivitySetupBinding
import com.buzzingmountain.dingclock.scheduler.PunchScheduler
import com.google.android.material.tabs.TabLayoutMediator

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val vm: SetupViewModel by viewModels()

    private val pages: List<Pair<String, () -> Fragment>> = listOf(
        "账号" to ::AccountFragment,
        "时间" to ::ScheduleFragment,
        "权限" to ::PermissionsFragment,
        "通知" to ::NotifyFragment,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = pages.size
            override fun createFragment(position: Int): Fragment = pages[position].second.invoke()
        }
        TabLayoutMediator(binding.tabs, binding.pager) { tab, pos ->
            tab.text = pages[pos].first
        }.attach()

        binding.prevButton.setOnClickListener {
            val cur = binding.pager.currentItem
            if (cur > 0) binding.pager.currentItem = cur - 1
        }
        binding.nextButton.setOnClickListener {
            val cur = binding.pager.currentItem
            if (cur < pages.lastIndex) {
                binding.pager.currentItem = cur + 1
            } else {
                finishSetup()
            }
        }

        binding.pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.prevButton.isEnabled = position > 0
                binding.nextButton.text =
                    if (position == pages.lastIndex) getString(R.string.setup_finish)
                    else getString(R.string.setup_next)
            }
        })
    }

    private fun finishSetup() {
        val cfg = vm.state.value
        if (vm.plaintextPassword.isEmpty() && cfg.passwordCipher.isEmpty()) {
            Toast.makeText(this, R.string.setup_err_password_required, Toast.LENGTH_LONG).show()
            binding.pager.currentItem = 0
            return
        }
        vm.persist()
        // Re-arm AlarmManager + WorkManager based on the new schedule.
        runCatching { PunchScheduler(this).rescheduleAll() }
        Toast.makeText(this, R.string.setup_saved, Toast.LENGTH_SHORT).show()
        finish()
    }
}
