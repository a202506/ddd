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
import com.buzzingmountain.dingclock.data.HolidayMode
import com.buzzingmountain.dingclock.databinding.FragmentSetupScheduleBinding
import com.buzzingmountain.dingclock.util.TimeUtils
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class ScheduleFragment : Fragment() {

    private var _binding: FragmentSetupScheduleBinding? = null
    private val binding get() = _binding!!
    private val vm: SetupViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSetupScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { cfg ->
                    setIfDifferent(binding.morningEdit, cfg.morningPunchAt)
                    setIfDifferent(binding.eveningEdit, cfg.eveningPunchAt)
                    val jitterStr = cfg.randomJitterSeconds.toString()
                    setIfDifferent(binding.jitterEdit, jitterStr)
                    val isCustom = cfg.holidayMode == HolidayMode.CUSTOM_LIST
                    if (binding.customHolidaySwitch.isChecked != isCustom) {
                        binding.customHolidaySwitch.isChecked = isCustom
                    }
                    binding.customHolidaysLayout.visibility = if (isCustom) View.VISIBLE else View.GONE
                    val holidaysText = cfg.customHolidays.sorted().joinToString("\n")
                    setIfDifferent(binding.customHolidaysEdit, holidaysText)
                }
            }
        }

        binding.morningEdit.setOnClickListener { showTimePicker(true) }
        binding.eveningEdit.setOnClickListener { showTimePicker(false) }

        binding.jitterEdit.addTextChangedListener(textWatcher { v ->
            val n = v.toIntOrNull()?.coerceIn(0, 600) ?: 0
            vm.update { it.copy(randomJitterSeconds = n) }
        })

        binding.customHolidaySwitch.setOnCheckedChangeListener { _, checked ->
            vm.update { it.copy(holidayMode = if (checked) HolidayMode.CUSTOM_LIST else HolidayMode.WEEKENDS_ONLY) }
        }
        binding.customHolidaysEdit.addTextChangedListener(textWatcher { v ->
            val parsed = parseHolidays(v)
            vm.update { it.copy(customHolidays = parsed) }
        })
    }

    private fun setIfDifferent(edit: com.google.android.material.textfield.TextInputEditText, value: String) {
        if (edit.text?.toString().orEmpty() != value) {
            edit.setText(value)
        }
    }

    private fun showTimePicker(isMorning: Boolean) {
        val cur = TimeUtils.parseHHmm(
            if (isMorning) vm.state.value.morningPunchAt else vm.state.value.eveningPunchAt,
        )
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(cur.hour)
            .setMinute(cur.minute)
            .setTitleText(if (isMorning) R.string.field_morning_time else R.string.field_evening_time)
            .build()
        picker.addOnPositiveButtonClickListener {
            val time = String.format("%02d:%02d", picker.hour, picker.minute)
            if (isMorning) vm.update { it.copy(morningPunchAt = time) }
            else vm.update { it.copy(eveningPunchAt = time) }
        }
        picker.show(parentFragmentManager, "time_picker_${if (isMorning) "m" else "e"}")
    }

    private fun parseHolidays(text: String): Set<String> {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return text.split(Regex("\\s+|,|;"))
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .mapNotNull {
                try {
                    LocalDate.parse(it, fmt).toString()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
            .toSortedSet()
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
