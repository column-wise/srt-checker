package io.github.columnwise.trainchecker.data.api.ktx

import io.github.columnwise.trainchecker.data.model.SeatType
import javax.inject.Inject
import javax.inject.Singleton

data class KtxTrain(val raw: KtxTrainData) {
    fun generalAvailable() = raw.h_gen_rsv_cd == "11"
    fun specialAvailable() = raw.h_spe_rsv_cd == "11"
    fun seatAvailable(seatType: SeatType) = when (seatType) {
        SeatType.GENERAL -> generalAvailable()
        SeatType.SPECIAL -> specialAvailable()
        SeatType.ANY -> generalAvailable() || specialAvailable()
    }
    fun waitAvailable() = raw.h_wait_rsv_flg == "9"
}

sealed class KtxResult {
    data class Success(val reservationNo: String) : KtxResult()
    data class Error(val message: String) : KtxResult()
}

@Singleton
class KtxRepository @Inject constructor(
    private val api: KtxApiService,
    private val aes: KtxAesHelper,
) {
    suspend fun login(id: String, pw: String): Boolean {
        val codeResp = api.getCode()
        val cipherInfo = codeResp.cipherInfo ?: return false
        val encPw = aes.encrypt(pw, cipherInfo.key)
        val inputFlg = when {
            Regex("[^@]+@[^@]+\\.[^@]+").matches(id) -> "5"
            Regex("\\d{3}-\\d{3,4}-\\d{4}").matches(id) -> "4"
            else -> "2"
        }
        val resp = api.login(id = id, encPw = encPw, inputFlg = inputFlg, idx = cipherInfo.idx)
        return resp.strResult == "SUCC" && resp.strMbCrdNo != null
    }

    suspend fun searchTrains(
        dep: String, arr: String, date: String, timeFrom: String,
    ): List<KtxTrain> {
        val resp = api.searchTrains(dep = dep, arr = arr, date = date, time = "${timeFrom}00")
        if (resp.strResult != "SUCC") return emptyList()
        return resp.trn_infos?.trn_info
            ?.filter { it.h_trn_clsf_cd in listOf("00", "07", "10") }
            ?.map { KtxTrain(it) } ?: emptyList()
    }

    suspend fun reserve(train: KtxTrain, seatType: SeatType): KtxResult {
        val isSpecial = when (seatType) {
            SeatType.SPECIAL -> true
            SeatType.GENERAL -> false
            SeatType.ANY -> !train.generalAvailable()
        }
        val jobId = if (train.seatAvailable(seatType)) "1101" else "1102"
        val resp = api.reserve(
            jobId = jobId,
            depDate = train.raw.h_dpt_dt,
            depCode = train.raw.h_dpt_rs_stn_cd,
            depTime = train.raw.h_dpt_tm,
            arrCode = train.raw.h_arv_rs_stn_cd,
            trnNo = train.raw.h_trn_no,
            runDate = train.raw.h_run_dt,
            trnClsfCd = train.raw.h_trn_clsf_cd,
            trnGpCd = train.raw.h_trn_gp_cd,
            psrmClCd = if (isSpecial) "2" else "1",
        )
        return if (resp.strResult == "SUCC" && resp.h_pnr_no != null) {
            KtxResult.Success(resp.h_pnr_no)
        } else {
            KtxResult.Error(resp.h_msg_txt ?: "예약 실패")
        }
    }
}
