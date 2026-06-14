package io.github.columnwise.trainchecker.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.columnwise.trainchecker.data.db.AppDatabase
import io.github.columnwise.trainchecker.data.db.WatchJobDao
import io.github.columnwise.trainchecker.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WatchJobDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: WatchJobDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
        dao = db.watchJobDao()
    }

    @After fun teardown() = db.close()

    @Test fun insertAndGetAll() = runTest {
        val job = WatchJob(
            trainType = TrainType.SRT,
            depStation = "수서", arrStation = "부산",
            date = "20241225", timeFrom = "0800", timeTo = "",
            seatType = SeatType.GENERAL, status = WatchStatus.WATCHING
        )
        dao.insert(job)
        val all = dao.getAll().first()
        assertEquals(1, all.size)
        assertEquals("수서", all[0].depStation)
    }

    @Test fun updateStatus() = runTest {
        val id = dao.insert(WatchJob(
            trainType = TrainType.KTX,
            depStation = "서울", arrStation = "부산",
            date = "20241225", timeFrom = "0800", timeTo = "",
            seatType = SeatType.ANY, status = WatchStatus.WATCHING
        ))
        dao.updateStatus(id, WatchStatus.SUCCESS, "ABC123", System.currentTimeMillis())
        val active = dao.getActive()
        assertEquals(0, active.size)
    }
}
