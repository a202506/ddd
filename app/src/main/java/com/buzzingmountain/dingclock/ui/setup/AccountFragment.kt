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
import com.buzzingmountain.dingclock.data.ConfigRepository
import com.buzzingmountain.dingclock.databinding.FragmentSetupAccountBinding
import kotlinx.coroutines.launch

class AccountFragment : Fragment() {

    private var _binding: FragmentSetupAccountBinding? = null
    private val binding get() = _binding!!
    private val vm: SetupViewModel by activityViewModels()
    private val repo by lazy { ConfigRepository(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSetupAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { cfg ->
                    binding.passwordStatus.text = when {
                        vm.plaintextPassword.isNotEmpty() -> getString(R.string.password_pending_save)
                        cfg.passwordCipher.isNotEmpty() -> getString(R.string.password_already_saved)
                        else -> getString(R.string.password_not_set)
                    }
                }
            }
        }

        binding.passwordEdit.addTextChangedListener(textWatcher { v ->
            vm.setPassword(v)
            binding.passwordStatus.text =
                if (v.isEmpty()) {
                    if (vm.state.value.passwordCipher.isNotEmpty()) getString(R.string.password_already_saved)
                    else getString(R.string.password_not_set)
                } else {
                    getString(R.string.password_pending_save)
                }
        })

        binding.decryptCheckButton.setOnClickListener { runDecryptCheck() }
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

    private fun runDecryptCheck() {
        val cfg = vm.state.value
        val plain = repo.decryptPassword(cfg)
        binding.decryptResultText.text = if (plain == null) {
            getString(R.string.decrypt_failed)
        } else {
            getString(R.string.decrypt_ok, "•".repeat(plain.length.coerceAtMost(12)), plain.length)
        }
    }
}
