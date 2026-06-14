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
        val tag = "${watchJob.trainType}#${watchJob.id}"
        val intervalMs = creds.pollIntervalSeconds * 1000L

        AppLogger.log(tag, "로그인 시도...")
        val loginOk = when (watchJob.trainType) {
            TrainType.SRT -> srtRepo.login(creds.srtId, creds.srtPw)
            TrainType.KTX -> ktxRepo.login(creds.ktxId, creds.ktxPw)
        }
        if (!loginOk) {
            AppLogger.log(tag, "로그인 실패")
            dao.updateStatus(watchJob.id, WatchStatus.FAILED, null, System.currentTimeMillis())
            notif.notifyError("로그인 실패 — 설정에서 로그인 정보를 확인하세요")
            jobs.remove(watchJob.id)
            if (jobs.isEmpty()) stopSelf()
            return
        }
        AppLogger.log(tag, "로그인 성공. 감시 시작 (${watchJob.depStation}→${watchJob.arrStation} ${watchJob.date})")

        while (true) {
            try {
                AppLogger.log(tag, "열차 조회 중...")
                val result = attemptReserve(watchJob, tag)
                if (result != null) {
                    AppLogger.log(tag, "예약 성공! 예약번호: $result")
                    dao.updateStatus(watchJob.id, WatchStatus.SUCCESS, result, System.currentTimeMillis())
                    notif.notifySuccess(
                        "예약 완료!",
                        "${watchJob.depStation}→${watchJob.arrStation} ${watchJob.date}"
                    )
                    jobs.remove(watchJob.id)
                    if (jobs.isEmpty()) stopSelf()
                    return
                } else {
                    AppLogger.log(tag, "취소표 없음. ${creds.pollIntervalSeconds}초 후 재시도")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.log(tag, "오류: ${e.message}")
            }
            delay(intervalMs)
        }
    }

    private suspend fun attemptReserve(watchJob: WatchJob, tag: String): String? {
        return when (watchJob.trainType) {
            TrainType.SRT -> {
                val trains = srtRepo.searchTrains(
                    watchJob.depStation, watchJob.arrStation,
                    watchJob.date, watchJob.timeFrom,
                )
                AppLogger.log(tag, "SRT 조회 결과: ${trains.size}개 열차")
                val available = trains.filter { it.seatAvailable(watchJob.seatType) &&
                    (watchJob.timeTo.isEmpty() || it.depTime <= "${watchJob.timeTo}00") }
                AppLogger.log(tag, "취소표 가능: ${available.size}개")
                val candidate = available.firstOrNull() ?: return null
                AppLogger.log(tag, "예약 시도: ${candidate.depTime} 열차")
                when (val r = srtRepo.reserve(candidate, watchJob.seatType)) {
                    is SrtResult.Success -> r.reservationNo
                    is SrtResult.Error -> { AppLogger.log(tag, "예약 실패: ${r.message}"); null }
                }
            }
            TrainType.KTX -> {
                val trains = ktxRepo.searchTrains(
                    watchJob.depStation, watchJob.arrStation,
                    watchJob.date, watchJob.timeFrom,
                )
                AppLogger.log(tag, "KTX 조회 결과: ${trains.size}개 열차")
                val available = trains.filter { it.seatAvailable(watchJob.seatType) &&
                    (watchJob.timeTo.isEmpty() || it.raw.h_dpt_tm <= "${watchJob.timeTo}00") }
                AppLogger.log(tag, "취소표 가능: ${available.size}개")
                val candidate = available.firstOrNull() ?: return null
                AppLogger.log(tag, "예약 시도: ${candidate.raw.h_dpt_tm} 열차")
                when (val r = ktxRepo.reserve(candidate, watchJob.seatType)) {
                    is KtxResult.Success -> r.reservationNo
                    is KtxResult.Error -> { AppLogger.log(tag, "예약 실패: ${r.message}"); null }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
