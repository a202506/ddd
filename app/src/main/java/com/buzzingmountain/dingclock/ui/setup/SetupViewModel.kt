package com.buzzingmountain.dingclock.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.buzzingmountain.dingclock.data.AppConfig
import com.buzzingmountain.dingclock.data.ConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Activity-scoped state for the 4-step setup wizard. Each fragment mutates fields via
 * [update]; the final page calls [persist] to commit (encrypting the plaintext password).
 */
class SetupViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ConfigRepository(app)

    private val _state = MutableStateFlow(repo.load() ?: AppConfig())
    val state: StateFlow<AppConfig> = _state.asStateFlow()

    var plaintextPassword: String = ""
        private set

    fun setPassword(plain: String) {
        plaintextPassword = plain
    }

    fun update(mutator: (AppConfig) -> AppConfig) {
        _state.value = mutator(_state.value)
    }

    fun persist(): AppConfig {
        var cfg = _state.value
        if (plaintextPassword.isNotEmpty()) {
            cfg = cfg.copy(passwordCipher = repo.encryptPassword(plaintextPassword))
        }
        repo.save(cfg)
        _state.value = cfg
        plaintextPassword = ""
        return cfg
    }

    override fun onCleared() {
        plaintextPassword = ""
        super.onCleared()
    }
}
