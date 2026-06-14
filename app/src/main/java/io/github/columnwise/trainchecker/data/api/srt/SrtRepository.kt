package io.github.columnwise.trainchecker.data.api.srt

import io.github.columnwise.trainchecker.data.model.SeatType
import javax.inject.Inject
import javax.inject.Singleton

data class SrtTrain(val raw: SrtTrainData) {
    val depStation get() = SRT_STATION_NAME[raw.dptRsStnCd] ?: raw.dptRsStnCd
    val arrStation get() = SRT_STATION_NAME[raw.arvRsStnCd] ?: raw.arvRsStnCd
    val depDate get() = raw.dptDt
    val depTime get() = raw.dptTm
    val trainNo get() = raw.trnNo.padStart(5, '0')
    fun generalAvailable() = "예약가능" in raw.gnrmRsvPsbStr
    fun specialAvailable() = "예약가능" in raw.sprmRsvPsbStr
    fun seatAvailable(seatType: SeatType) = when (seatType) {
        SeatType.GENERAL -> generalAvailable()
        SeatType.SPECIAL -> specialAvailable()
        SeatType.ANY -> generalAvailable() || specialAvailable()
    }
    fun waitAvailable() = raw.rsvWaitPsbCd == "9"
}

class SessionExpiredException(message: String) : Exception(message)

sealed class SrtResult {
    data class Success(val reservationNo: String) : SrtResult()
    data class Error(val message: String) : SrtResult()
}

@Singleton
class SrtRepository @Inject constructor(
    private val api: SrtApiService,
    private val netFunnel: NetFunnelHelper,
) {
    suspend fun login(id: String, pw: String): Boolean {
        val loginType = when {
            Regex("[^@]+@[^@]+\\.[^@]+").matches(id) -> "2"
            Regex("\\d{3}-\\d{3,4}-\\d{4}").matches(id) -> "3"
            else -> "1"
        }
        val cleanId = if (loginType == "3") id.replace("-", "") else id
        val resp = api.login(loginType = loginType, id = cleanId, pw = pw)
        return resp.userMap != null
    }

    suspend fun searchTrains(
        dep: String, arr: String, date: String, timeFrom: String,
    ): List<SrtTrain> {
        val depCode = SRT_STATION_CODE[dep] ?: error("Unknown station: $dep")
        val arrCode = SRT_STATION_CODE[arr] ?: error("Unknown station: $arr")
        val time = "${timeFrom}00"
        val key = netFunnel.getToken()
        val resp = api.searchTrains(
            date = date, time = time, date1 = date, time1 = "${timeFrom.take(2)}0000",
            depCode = depCode, arrCode = arrCode, netfunnelKey = key,
        )
        val result = resp.resultMap?.firstOrNull() ?: return emptyList()
        if (result.strResult != "SUCC") {
            if ("로그인" in result.msgTxt) throw SessionExpiredException(result.msgTxt)
            return emptyList()
        }
        return resp.outDataSets?.dsOutput1
            ?.filter { it.stlbTrnClsfCd == "17" }
            ?.map { SrtTrain(it) } ?: emptyList()
    }

    suspend fun reserve(train: SrtTrain, seatType: SeatType): SrtResult {
        val isSpecial = when (seatType) {
            SeatType.SPECIAL -> true
            SeatType.GENERAL -> false
            SeatType.ANY -> !train.generalAvailable()
        }
        val key = netFunnel.getToken()
        val resp = api.reserve(
            jobId = "1101",
            trainCode = train.raw.stlbTrnClsfCd,
            depCode = train.raw.dptRsStnCd,
            depName = train.depStation,
            arrCode = train.raw.arvRsStnCd,
            arrName = train.arrStation,
            depDate = train.raw.dptDt,
            depTime = train.raw.dptTm,
            arrTime = train.raw.arvTm,
            trainNo = train.trainNo,
            runDate = train.raw.dptDt,
            depConsOrdr = train.raw.dptStnConsOrdr,
            arrConsOrdr = train.raw.arvStnConsOrdr,
            depRunOrdr = train.raw.dptStnRunOrdr,
            arrRunOrdr = train.raw.arvStnRunOrdr,
            seatClass = if (isSpecial) "2" else "1",
            netfunnelKey = key,
        )
        val result = resp.resultMap?.firstOrNull()
        return if (result?.strResult == "SUCC") {
            val resNo = resp.reservListMap?.firstOrNull()?.get("pnrNo") ?: ""
            SrtResult.Success(resNo)
        } else {
            SrtResult.Error(result?.msgTxt ?: "예약 실패")
        }
    }
}
