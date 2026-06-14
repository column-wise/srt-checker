package io.github.columnwise.trainchecker.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.columnwise.trainchecker.data.api.ktx.KtxRepository
import io.github.columnwise.trainchecker.data.api.ktx.KtxResult
import io.github.columnwise.trainchecker.data.api.srt.SrtRepository
import io.github.columnwise.trainchecker.data.api.srt.SrtResult
import io.github.columnwise.trainchecker.data.db.WatchJobDao
import io.github.columnwise.trainchecker.data.model.TrainType
import io.github.columnwise.trainchecker.data.model.WatchJob
import io.github.columnwise.trainchecker.data.model.WatchStatus
import io.github.columnwise.trainchecker.data.prefs.CredentialStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class TicketWatcherService : Service() {
    @Inject lateinit var srtRepo: SrtRepository
    @Inject lateinit var ktxRepo: KtxRepository
    @Inject lateinit var dao: WatchJobDao
    @Inject lateinit var creds: CredentialStore
    @Inject lateinit var notif: NotificationHelper

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobs = mutableMapOf<Long, Job>()

    companion object {
        const val ACTION_START = "io.github.columnwise.trainchecker.START_WATCH"
        const val ACTION_STOP = "io.github.columnwise.trainchecker.STOP_WATCH"
        const val EXTRA_JOB_ID = "job_id"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NotificationHelper.NOTIF_ID_WATCHING,
            notif.buildWatchingNotification("시작 중...")
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val jobId = intent.getLongExtra(EXTRA_JOB_ID, -1L)
                if (jobId >= 0) startWatching(jobId)
            }
            ACTION_STOP -> {
                val jobId = intent.getLongExtra(EXTRA_JOB_ID, -1L)
                if (jobId >= 0) stopWatching(jobId)
            }
        }
        return START_STICKY
    }

    private fun startWatching(jobId: Long) {
        if (jobs.containsKey(jobId)) return
        jobs[jobId] = scope.launch {
            val watchJob = dao.getActive().find { it.id == jobId } ?: return@launch
            watchLoop(watchJob)
        }
    }

    private fun stopWatching(jobId: Long) {
        jobs.remove(jobId)?.cancel()
        scope.launch {
            dao.updateStatus(jobId, WatchStatus.CANCELLED, null, System.currentTimeMillis())
        }
        if (jobs.isEmpty()) stopSelf()
    }

    private suspend fun watchLoop(watchJob: WatchJob) {
        val intervalMs = creds.pollIntervalSeconds * 1000L

        val loginOk = when (watchJob.trainType) {
            TrainType.SRT -> srtRepo.login(creds.srtId, creds.srtPw)
            TrainType.KTX -> ktxRepo.login(creds.ktxId, creds.ktxPw)
        }
        if (!loginOk) {
            dao.updateStatus(watchJob.id, WatchStatus.FAILED, null, System.currentTimeMillis())
            notif.notifyError("로그인 실패 — 설정에서 로그인 정보를 확인하세요")
            return
        }

        while (true) {
            try {
                val result = attemptReserve(watchJob)
                if (result != null) {
                    dao.updateStatus(watchJob.id, WatchStatus.SUCCESS, result, System.currentTimeMillis())
                    notif.notifySuccess(
                        "예약 완료!",
                        "${watchJob.depStation}→${watchJob.arrStation} ${watchJob.date}"
                    )
                    jobs.remove(watchJob.id)
                    if (jobs.isEmpty()) stopSelf()
                    return
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // network error — retry
            }
            delay(intervalMs)
        }
    }

    private suspend fun attemptReserve(watchJob: WatchJob): String? {
        return when (watchJob.trainType) {
            TrainType.SRT -> {
                val trains = srtRepo.searchTrains(
                    watchJob.depStation, watchJob.arrStation,
                    watchJob.date, watchJob.timeFrom,
                )
                val candidate = trains.firstOrNull { t ->
                    t.seatAvailable(watchJob.seatType) &&
                    (watchJob.timeTo.isEmpty() || t.depTime <= "${watchJob.timeTo}00")
                } ?: return null
                when (val r = srtRepo.reserve(candidate, watchJob.seatType)) {
                    is SrtResult.Success -> r.reservationNo
                    is SrtResult.Error -> null
                }
            }
            TrainType.KTX -> {
                val trains = ktxRepo.searchTrains(
                    watchJob.depStation, watchJob.arrStation,
                    watchJob.date, watchJob.timeFrom,
                )
                val candidate = trains.firstOrNull { t ->
                    t.seatAvailable(watchJob.seatType) &&
                    (watchJob.timeTo.isEmpty() || t.raw.h_dpt_tm <= "${watchJob.timeTo}00")
                } ?: return null
                when (val r = ktxRepo.reserve(candidate, watchJob.seatType)) {
                    is KtxResult.Success -> r.reservationNo
                    is KtxResult.Error -> null
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
