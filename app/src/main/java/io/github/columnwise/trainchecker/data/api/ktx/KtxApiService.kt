package io.github.columnwise.trainchecker.data.api.ktx

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface KtxApiService {
    @FormUrlEncoded
    @POST("/classes/com.korail.mobile.common.code.do")
    suspend fun getCode(@Field("code") code: String = "app.login.cphd"): KtxCodeResponse

    @FormUrlEncoded
    @POST("/classes/com.korail.mobile.login.Login")
    suspend fun login(
        @Field("Device") device: String = "AD",
        @Field("Version") version: String = "240531001",
        @Field("Key") key: String = "korail1234567890",
        @Field("txtMemberNo") id: String,
        @Field("txtPwd") encPw: String,
        @Field("txtInputFlg") inputFlg: String,
        @Field("idx") idx: String,
    ): KtxLoginResponse

    @FormUrlEncoded
    @POST("/classes/com.korail.mobile.seatMovie.ScheduleView")
    suspend fun searchTrains(
        @Field("Device") device: String = "AD",
        @Field("Version") version: String = "240531001",
        @Field("Sid") sid: String = "",
        @Field("txtMenuId") menuId: String = "11",
        @Field("radJobId") jobId: String = "1",
        @Field("selGoTrain") goTrain: String = "05",
        @Field("txtTrnGpCd") trnGpCd: String = "05",
        @Field("txtGoStart") dep: String,
        @Field("txtGoEnd") arr: String,
        @Field("txtGoAbrdDt") date: String,
        @Field("txtGoHour") time: String,
        @Field("txtPsgFlg_1") adult: Int = 1,
        @Field("txtPsgFlg_2") child: Int = 0,
        @Field("txtPsgFlg_3") senior: Int = 0,
        @Field("txtPsgFlg_4") dis13: Int = 0,
        @Field("txtPsgFlg_5") dis46: Int = 0,
        @Field("txtSeatAttCd1") seatAtt: String = "000",
        @Field("txtSeatAttCd2") seatAtt2: String = "000",
        @Field("txtSeatAttCd3") seatAtt3: String = "000",
        @Field("txtSeatAttCd4") seatAtt4: String = "015",
        @Field("txtSeatAttCd5") seatAtt5: String = "000",
    ): KtxSearchResponse

    @GET("/classes/com.korail.mobile.certification.TicketReservation")
    suspend fun reserve(
        @Query("Device") device: String = "AD",
        @Query("Version") version: String = "240531001",
        @Query("Key") key: String = "korail1234567890",
        @Query("txtMenuId") menuId: String = "11",
        @Query("txtJobId") jobId: String,
        @Query("txtGdNo") gdNo: String = "",
        @Query("hidFreeFlg") freeFlg: String = "N",
        @Query("txtTotPsgCnt") psgCnt: Int = 1,
        @Query("txtSeatAttCd1") sa1: String = "000",
        @Query("txtSeatAttCd2") sa2: String = "000",
        @Query("txtSeatAttCd3") sa3: String = "000",
        @Query("txtSeatAttCd4") sa4: String = "015",
        @Query("txtSeatAttCd5") sa5: String = "000",
        @Query("txtStndFlg") stndFlg: String = "N",
        @Query("txtSrcarCnt") srcarCnt: String = "0",
        @Query("txtJrnyCnt") jrnyCnt: String = "1",
        @Query("txtJrnySqno1") jrnySqno: String = "001",
        @Query("txtJrnyTpCd1") jrnyTpCd: String = "11",
        @Query("txtDptDt1") depDate: String,
        @Query("txtDptRsStnCd1") depCode: String,
        @Query("txtDptTm1") depTime: String,
        @Query("txtArvRsStnCd1") arrCode: String,
        @Query("txtTrnNo1") trnNo: String,
        @Query("txtRunDt1") runDate: String,
        @Query("txtTrnClsfCd1") trnClsfCd: String,
        @Query("txtTrnGpCd1") trnGpCd: String,
        @Query("txtPsrmClCd1") psrmClCd: String,
        @Query("txtChgFlg1") chgFlg: String = "",
        @Query("txtPsgTpCd1") psgTpCd: String = "1",
        @Query("txtDiscKndCd1") discKnd: String = "000",
        @Query("txtPsgInfoPerPrnb1") psgInfo: String = "1",
        @Query("txtCompaCd1") compaCd: String = "5",
    ): KtxReserveResponse
}
