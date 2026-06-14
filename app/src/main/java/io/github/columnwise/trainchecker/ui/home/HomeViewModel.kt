package io.github.columnwise.trainchecker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.columnwise.trainchecker.data.db.WatchJobDao
import io.github.columnwise.trainchecker.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val dao: WatchJobDao) : ViewModel() {

    fun startWatch(
        trainType: TrainType,
        dep: String, arr: String,
        date: String, timeFrom: String, timeTo: String,
        seatType: SeatType,
        onJobId: (Long) -> Unit,
    ) {
        viewModelScope.launch {
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
