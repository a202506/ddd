package com.buzzingmountain.dingclock.ui.setup

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.buzzingmountain.dingclock.R
import com.buzzingmountain.dingclock.databinding.FragmentSetupPermissionsBinding
import com.buzzingmountain.dingclock.databinding.ItemPermissionStepBinding
import com.buzzingmountain.dingclock.util.VivoUtils
import timber.log.Timber

class PermissionsFragment : Fragment() {

    private var _binding: FragmentSetupPermissionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSetupPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences(PREFS_NAME, 0)

        binding.romText.text = getString(R.string.setup_permissions_rom, VivoUtils.romDescription())

        val items = PermissionItem.all()
        items.forEach { addItemRow(it) }
    }

    private fun addItemRow(item: PermissionItem) {
        val ctx = requireContext()
        val rowBinding = ItemPermissionStepBinding.inflate(layoutInflater, binding.itemsContainer, false)
        rowBinding.itemTitle.text = item.title
        rowBinding.itemDesc.text = item.description
        rowBinding.itemDoneCheck.isChecked = prefs.getBoolean(checkKey(item.key), false)
        rowBinding.itemDoneCheck.setOnCheckedChangeListener { _, checked ->
            prefs.edit { putBoolean(checkKey(item.key), checked) }
        }
        rowBinding.itemActionBtn.setOnClickListener {
            val intent = item.intentFor(ctx)
            if (intent == null) {
                Toast.makeText(ctx, R.string.intent_unavailable, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            runCatching { startActivity(intent) }
                .onFailure {
                    Timber.e(it, "permission intent failed: %s", item.key)
                    Toast.makeText(ctx, R.string.intent_unavailable, Toast.LENGTH_LONG).show()
                }
        }
        binding.itemsContainer.addView(rowBinding.root)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun checkKey(itemKey: String): String = "perm.$itemKey.done"

    companion object {
        private const val PREFS_NAME = "permission_progress"
    }
}
