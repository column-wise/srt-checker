package io.github.columnwise.trainchecker.data.api.srt

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface SrtApiService {
    @FormUrlEncoded
    @POST("/apb/selectListApb01080_n.do")
    suspend fun login(
        @Field("auto") auto: String = "Y",
        @Field("check") check: String = "Y",
        @Field("page") page: String = "menu",
        @Field("deviceKey") deviceKey: String = "-",
        @Field("customerYn") customerYn: String = "",
        @Field("login_referer") loginReferer: String = "https://app.srail.or.kr/main/main.do",
        @Field("srchDvCd") loginType: String,
        @Field("srchDvNm") id: String,
        @Field("hmpgPwdCphd") pw: String,
    ): SrtLoginResponse

    @FormUrlEncoded
    @POST("/ara/selectListAra10007_n.do")
    suspend fun searchTrains(
        @Field("chtnDvCd") chtnDvCd: String = "1",
        @Field("dptDt") date: String,
        @Field("dptTm") time: String,
        @Field("dptDt1") date1: String,
        @Field("dptTm1") time1: String,
        @Field("dptRsStnCd") depCode: String,
        @Field("arvRsStnCd") arrCode: String,
        @Field("stlbTrnClsfCd") trainClass: String = "05",
        @Field("trnGpCd") trnGpCd: Int = 109,
        @Field("trnNo") trnNo: String = "",
        @Field("psgNum") psgNum: String = "1",
        @Field("seatAttCd") seatAttCd: String = "015",
        @Field("arriveTime") arriveTime: String = "N",
        @Field("dlayTnumAplFlg") dlayFlag: String = "Y",
        @Field("netfunnelKey") netfunnelKey: String,
    ): SrtSearchResponse

    @FormUrlEncoded
    @POST("/arc/selectListArc05013_n.do")
    suspend fun reserve(
        @Field("jobId") jobId: String,
        @Field("jrnyCnt") jrnyCnt: String = "1",
        @Field("jrnyTpCd") jrnyTpCd: String = "11",
        @Field("jrnySqno1") jrnySqno1: String = "001",
        @Field("stndFlg") stndFlg: String = "N",
        @Field("trnGpCd1") trnGpCd1: String = "300",
        @Field("trnGpCd") trnGpCd: String = "109",
        @Field("grpDv") grpDv: String = "0",
        @Field("rtnDv") rtnDv: String = "0",
        @Field("stlbTrnClsfCd1") trainCode: String,
        @Field("dptRsStnCd1") depCode: String,
        @Field("dptRsStnCdNm1") depName: String,
        @Field("arvRsStnCd1") arrCode: String,
        @Field("arvRsStnCdNm1") arrName: String,
        @Field("dptDt1") depDate: String,
        @Field("dptTm1") depTime: String,
        @Field("arvTm1") arrTime: String,
        @Field("trnNo1") trainNo: String,
        @Field("runDt1") runDate: String,
        @Field("dptStnConsOrdr1") depConsOrdr: String,
        @Field("arvStnConsOrdr1") arrConsOrdr: String,
        @Field("dptStnRunOrdr1") depRunOrdr: String,
        @Field("arvStnRunOrdr1") arrRunOrdr: String,
        @Field("totPrnb") totPrnb: String = "1",
        @Field("psgGridcnt") psgGridcnt: String = "1",
        @Field("psgTpCd1") psgTpCd: String = "1",
        @Field("psgInfoPerPrnb1") psgCount: String = "1",
        @Field("psrmClCd1") seatClass: String,
        @Field("locSeatAttCd1") locSeat: String = "000",
        @Field("rqSeatAttCd1") rqSeat: String = "015",
        @Field("dirSeatAttCd1") dirSeat: String = "009",
        @Field("smkSeatAttCd1") smkSeat: String = "000",
        @Field("etcSeatAttCd1") etcSeat: String = "000",
        @Field("reserveType") reserveType: String = "11",
        @Field("netfunnelKey") netfunnelKey: String,
    ): SrtReserveResponse
}
