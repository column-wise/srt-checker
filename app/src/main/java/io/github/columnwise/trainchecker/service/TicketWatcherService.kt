package io.github.columnwise.trainchecker.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.columnwise.trainchecker.data.api.srt.SessionExpiredException
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
    @Inject lateinit var dao: WatchJobDao
    @Inject lateinit var creds: CredentialStore
    @Inject lateinit var notif: NotificationHelper

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobs = mutableMapOf<Long, Job>()

    companion object {
        const val ACTION_START = "io.github.columnwise.trainchecker.START_WATCH"
        const val ACTION_STOP = "io.github.columnwise.trainchecker.STOP_WATCH"
        const val EXTRA_JOB_ID = "job_id"
        private const val SESSION_EXPIRED = "__SESSION_EXPIRED__"
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

    private fun updateWatchingNotification(watchJob: WatchJob) {
        val timeTo = if (watchJob.timeTo.isEmpty()) "제한 없음" else "${watchJob.timeTo.take(2)}:00"
        val summary = "${watchJob.depStation}→${watchJob.arrStation} ${watchJob.timeFrom.take(2)}:00~$timeTo"
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NotificationHelper.NOTIF_ID_WATCHING, notif.buildWatchingNotification(summary))
    }

    private fun stopWatching(jobId: Long) {
        jobs.remove(jobId)?.cancel()
        scope.launch {
            dao.updateStatus(jobId, WatchStatus.CANCELLED, null, System.currentTimeMillis())
        }
        if (jobs.isEmpty()) stopSelf()
    }

    private suspend fun watchLoop(watchJob: WatchJob) {
        val tag = "SRT#${watchJob.id}"
        val intervalMs = creds.pollIntervalSeconds * 1000L

        AppLogger.log(tag, "로그인 시도...")
        val loginOk = srtRepo.login(creds.srtId, creds.srtPw)
        if (!loginOk) {
            AppLogger.log(tag, "로그인 실패")
            dao.updateStatus(watchJob.id, WatchStatus.FAILED, null, System.currentTimeMillis())
            notif.notifyError("로그인 실패 — 설정에서 SRT 아이디/비밀번호를 확인하세요")
            jobs.remove(watchJob.id)
            if (jobs.isEmpty()) stopSelf()
            return
        }
        AppLogger.log(tag, "로그인 성공. 감시 시작 (${watchJob.depStation}→${watchJob.arrStation} ${watchJob.date})")
        updateWatchingNotification(watchJob)

        while (true) {
            try {
                AppLogger.log(tag, "열차 조회 중...")
                val result = attemptReserve(watchJob, tag)
                if (result == null) {
                    AppLogger.log(tag, "취소표 없음. ${creds.pollIntervalSeconds}초 후 재시도")
                } else if (result == SESSION_EXPIRED) {
                    AppLogger.log(tag, "세션 만료. 재로그인 시도...")
                    val reloginOk = srtRepo.login(creds.srtId, creds.srtPw)
                    if (!reloginOk) {
                        AppLogger.log(tag, "재로그인 실패. 감시 중단")
                        dao.updateStatus(watchJob.id, WatchStatus.FAILED, null, System.currentTimeMillis())
                        notif.notifyError("세션 만료 후 재로그인 실패 — 아이디/비밀번호를 확인하세요")
                        jobs.remove(watchJob.id)
                        if (jobs.isEmpty()) stopSelf()
                        return
                    }
                    AppLogger.log(tag, "재로그인 성공. 감시 재개")
                } else {
                    AppLogger.log(tag, "예약 성공! 예약번호: $result")
                    dao.updateStatus(watchJob.id, WatchStatus.SUCCESS, result, System.currentTimeMillis())
                    notif.notifySuccess(
                        "예약 완료! 결제를 완료해주세요.",
                        "${watchJob.depStation}→${watchJob.arrStation} ${watchJob.date}",
                        "https://etk.srail.kr"
                    )
                    jobs.remove(watchJob.id)
                    if (jobs.isEmpty()) stopSelf()
                    return
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                AppLogger.log(tag, "세션 만료(조회): 재로그인 시도...")
                val reloginOk = srtRepo.login(creds.srtId, creds.srtPw)
                if (!reloginOk) {
                    AppLogger.log(tag, "재로그인 실패. 감시 중단")
                    dao.updateStatus(watchJob.id, WatchStatus.FAILED, null, System.currentTimeMillis())
                    notif.notifyError("세션 만료 후 재로그인 실패 — 아이디/비밀번호를 확인하세요")
                    jobs.remove(watchJob.id)
                    if (jobs.isEmpty()) stopSelf()
                    return
                }
                AppLogger.log(tag, "재로그인 성공. 감시 재개")
            } catch (e: Exception) {
                AppLogger.log(tag, "오류: ${e.message}")
            }
            delay(intervalMs)
        }
    }

    private suspend fun attemptReserve(watchJob: WatchJob, tag: String): String? {
        val now = java.util.Date()
        val nowDate = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(now)
        val nowHHmm = java.text.SimpleDateFormat("HHmm", java.util.Locale.getDefault()).format(now)
        val effectiveTimeFrom = if (watchJob.date == nowDate && nowHHmm > watchJob.timeFrom)
            nowHHmm else watchJob.timeFrom
        val trains = srtRepo.searchTrains(
            watchJob.depStation, watchJob.arrStation,
            watchJob.date, effectiveTimeFrom,
        )
        val trainTimes = trains.joinToString(", ") { t -> "${t.depTime.take(2)}:${t.depTime.substring(2,4)}" }
        AppLogger.log(tag, "SRT 조회 결과: ${trains.size}개 열차 [$trainTimes]")
        val available = trains.filter {
            it.seatAvailable(watchJob.seatType) &&
            (watchJob.timeTo.isEmpty() || it.depTime <= "${watchJob.timeTo}00")
        }
        AppLogger.log(tag, "취소표 가능: ${available.size}개")
        val candidate = available.firstOrNull() ?: return null
        AppLogger.log(tag, "예약 시도: ${candidate.depTime} 열차")
        return when (val r = srtRepo.reserve(candidate, watchJob.seatType)) {
            is SrtResult.Success -> r.reservationNo
            is SrtResult.Error -> {
                if ("로그인" in r.message) SESSION_EXPIRED
                else { AppLogger.log(tag, "예약 실패: ${r.message}"); null }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
