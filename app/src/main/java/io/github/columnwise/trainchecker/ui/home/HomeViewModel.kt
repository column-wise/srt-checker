package io.github.columnwise.trainchecker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.columnwise.trainchecker.data.db.WatchJobDao
import io.github.columnwise.trainchecker.data.model.SeatType
import io.github.columnwise.trainchecker.data.model.TrainType
import io.github.columnwise.trainchecker.data.model.WatchJob
import io.github.columnwise.trainchecker.data.model.WatchStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val dao: WatchJobDao) : ViewModel() {

    fun topRoutes(): Flow<List<Pair<String, String>>> =
        dao.getAll().map { jobs ->
            jobs.groupBy { it.depStation to it.arrStation }
                .entries
                .sortedByDescending { it.value.size }
                .take(2)
                .map { it.key }
        }

    fun startWatch(
        trainType: TrainType,
        dep: String, arr: String,
        date: String, timeFrom: String, timeTo: String,
        seatType: SeatType,
        onAlreadyWatching: () -> Unit,
        onDuplicate: () -> Unit,
        onJobId: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            if (dao.getActive().isNotEmpty()) {
                onAlreadyWatching()
                return@launch
            }
            if (dao.findDuplicate(trainType, dep, arr, date, timeFrom, seatType) != null) {
                onDuplicate()
                return@launch
            }
            val id = dao.insert(WatchJob(
                trainType = trainType,
                depStation = dep, arrStation = arr,
                date = date, timeFrom = timeFrom, timeTo = timeTo,
                seatType = seatType,
                status = WatchStatus.WATCHING,
            ))
            onJobId(id)
        }
    }
}
