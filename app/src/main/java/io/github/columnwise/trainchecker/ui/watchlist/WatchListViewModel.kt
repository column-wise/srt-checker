package io.github.columnwise.trainchecker.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.columnwise.trainchecker.data.db.WatchJobDao
import io.github.columnwise.trainchecker.data.model.WatchJob
import io.github.columnwise.trainchecker.data.model.WatchStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchListViewModel @Inject constructor(private val dao: WatchJobDao) : ViewModel() {
    val jobs = dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cancel(job: WatchJob, stopServiceIntent: (Long) -> Unit) {
        viewModelScope.launch {
            dao.updateStatus(job.id, WatchStatus.CANCELLED, null, System.currentTimeMillis())
            if (job.status == WatchStatus.WATCHING) stopServiceIntent(job.id)
        }
    }

    fun delete(job: WatchJob) {
        viewModelScope.launch { dao.delete(job.id) }
    }
}
