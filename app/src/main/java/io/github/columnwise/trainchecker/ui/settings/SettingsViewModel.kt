package io.github.columnwise.trainchecker.ui.settings

import androidx.lifecycle.ViewModel
import io.github.columnwise.trainchecker.data.prefs.CredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: CredentialStore,
) : ViewModel() {
    val srtId = MutableStateFlow(store.srtId)
    val srtPw = MutableStateFlow(store.srtPw)
    val pollInterval: StateFlow<Int> = MutableStateFlow(store.pollIntervalSeconds)

    fun save(srtId: String, srtPw: String, interval: Int) {
        store.srtId = srtId; store.srtPw = srtPw
        store.pollIntervalSeconds = interval.coerceIn(15, 60)
    }
}
