package io.github.columnwise.trainchecker.data.api.srt

import com.google.gson.annotations.SerializedName

data class SrtLoginResponse(
    @SerializedName("MSG") val msg: String? = null,
    val userMap: SrtUserMap? = null,
)
data class SrtUserMap(
    @SerializedName("MB_CRD_NO") val membershipNo: String,
    @SerializedName("CUST_NM") val name: String,
    @SerializedName("MBL_PHONE") val phone: String,
)

data class SrtSearchResponse(
    val resultMap: List<SrtResultMap>? = null,
    val outDataSets: SrtOutDataSets? = null,
    @SerializedName("ErrorCode") val errorCode: String? = null,
    @SerializedName("ErrorMsg") val errorMsg: String? = null,
)
data class SrtResultMap(val strResult: String, val msgTxt: String = "")
data class SrtOutDataSets(val dsOutput1: List<SrtTrainData>? = null)
data class SrtTrainData(
    val stlbTrnClsfCd: String,
    val trnNo: String,
    val dptDt: String,
    val dptTm: String,
    val dptRsStnCd: String,
    val arvDt: String,
    val arvTm: String,
    val arvRsStnCd: String,
    val gnrmRsvPsbStr: String,
    val sprmRsvPsbStr: String,
    val rsvWaitPsbCd: String,
    val rsvWaitPsbCdNm: String,
    val dptStnRunOrdr: String,
    val dptStnConsOrdr: String,
    val arvStnRunOrdr: String,
    val arvStnConsOrdr: String,
)

data class SrtReserveResponse(
    val resultMap: List<SrtResultMap>? = null,
    val reservListMap: List<Map<String, String>>? = null,
)

val SRT_STATION_CODE = mapOf(
    "수서" to "0551", "동탄" to "0552", "평택지제" to "0553",
    "경주" to "0508", "공주" to "0514", "광주송정" to "0036",
    "대전" to "0010", "동대구" to "0015", "부산" to "0020",
    "서대구" to "0506", "오송" to "0297", "울산(통도사)" to "0509",
    "익산" to "0030", "전주" to "0045", "천안아산" to "0502",
    "포항" to "0515", "목포" to "0041", "순천" to "0051",
    "여수EXPO" to "0053", "진주" to "0063", "창원" to "0057",
)
val SRT_STATION_NAME = SRT_STATION_CODE.entries.associate { (k, v) -> v to k }
