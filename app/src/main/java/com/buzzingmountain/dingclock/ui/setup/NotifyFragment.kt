package com.buzzingmountain.dingclock.ui.setup

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.buzzingmountain.dingclock.R
import com.buzzingmountain.dingclock.databinding.FragmentSetupNotifyBinding
import com.buzzingmountain.dingclock.notify.DingRobotNotifier
import kotlinx.coroutines.launch

class NotifyFragment : Fragment() {

    private var _binding: FragmentSetupNotifyBinding? = null
    private val binding get() = _binding!!
    private val vm: SetupViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSetupNotifyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { cfg ->
                    if (binding.webhookUrlEdit.text?.toString().orEmpty() != cfg.webhookUrl) {
                        binding.webhookUrlEdit.setText(cfg.webhookUrl)
                    }
                    if (binding.webhookSecretEdit.text?.toString().orEmpty() != cfg.webhookSecret) {
                        binding.webhookSecretEdit.setText(cfg.webhookSecret)
                    }
                    binding.testWebhookBtn.isEnabled = cfg.webhookUrl.isNotBlank()
                }
            }
        }

        binding.webhookUrlEdit.addTextChangedListener(textWatcher { v ->
            vm.update { it.copy(webhookUrl = v.trim()) }
        })
        binding.webhookSecretEdit.addTextChangedListener(textWatcher { v ->
            vm.update { it.copy(webhookSecret = v.trim()) }
        })

        binding.testWebhookBtn.setOnClickListener { runWebhookTest() }
    }

    private fun runWebhookTest() {
        val cfg = vm.state.value
        binding.testWebhookBtn.isEnabled = false
        binding.testWebhookResult.text = getString(R.string.webhook_test_running)
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = DingRobotNotifier(cfg.webhookUrl, cfg.webhookSecret.takeIf { it.isNotBlank() })
                .send(
                    title = "DingClock 测试消息",
                    markdown = "### 测试消息\n来自 ${cfg.colleagueName.ifBlank { "钉钉打卡助手" }}\n\n如果你看到这条，说明 webhook 配置正确。",
                )
            binding.testWebhookResult.text = getString(
                if (ok) R.string.webhook_test_ok else R.string.webhook_test_fail,
            )
            binding.testWebhookBtn.isEnabled = cfg.webhookUrl.isNotBlank()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun textWatcher(onChange: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { onChange(s?.toString().orEmpty()) }
    }
}
