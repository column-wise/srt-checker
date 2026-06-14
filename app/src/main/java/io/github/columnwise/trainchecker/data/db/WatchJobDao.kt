package io.github.columnwise.trainchecker.data.db

import androidx.room.*
import io.github.columnwise.trainchecker.data.model.SeatType
import io.github.columnwise.trainchecker.data.model.TrainType
import io.github.columnwise.trainchecker.data.model.WatchJob
import io.github.columnwise.trainchecker.data.model.WatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchJobDao {
    @Query("SELECT * FROM watch_jobs ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WatchJob>>

    @Query("SELECT * FROM watch_jobs WHERE status = 'WATCHING'")
    suspend fun getActive(): List<WatchJob>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: WatchJob): Long

    @Update
    suspend fun update(job: WatchJob)

    @Query("UPDATE watch_jobs SET status = :status, reservationNumber = :resNo, updatedAt = :ts WHERE id = :id")
    suspend fun updateStatus(id: Long, status: WatchStatus, resNo: String?, ts: Long)

    @Query("DELETE FROM watch_jobs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM watch_jobs WHERE status = 'WATCHING' AND trainType = :trainType AND depStation = :dep AND arrStation = :arr AND date = :date AND timeFrom = :timeFrom AND seatType = :seatType LIMIT 1")
    suspend fun findDuplicate(trainType: TrainType, dep: String, arr: String, date: String, timeFrom: String, seatType: SeatType): WatchJob?
}
