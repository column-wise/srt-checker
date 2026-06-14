package io.github.columnwise.trainchecker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TrainType { SRT, KTX }
enum class SeatType { GENERAL, SPECIAL, ANY }
enum class WatchStatus { WATCHING, SUCCESS, FAILED, CANCELLED }

@Entity(tableName = "watch_jobs")
data class WatchJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainType: TrainType,
    val depStation: String,
    val arrStation: String,
    val date: String,         // YYYYMMDD
    val timeFrom: String,     // HHMM
    val timeTo: String,       // HHMM (empty = no limit)
    val seatType: SeatType,
    val status: WatchStatus,
    val reservationNumber: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
