package io.github.columnwise.trainchecker.data.db

import androidx.room.*
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
}
