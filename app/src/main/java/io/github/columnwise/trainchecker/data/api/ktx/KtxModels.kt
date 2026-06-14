package io.github.columnwise.trainchecker.data.api.ktx

import com.google.gson.annotations.SerializedName

data class KtxCodeResponse(
    val strResult: String = "",
    @SerializedName("app.login.cphd") val cipherInfo: KtxCipherInfo? = null,
)
data class KtxCipherInfo(val key: String, val idx: String)

data class KtxLoginResponse(
    val strResult: String = "",
    val strMbCrdNo: String? = null,
    val strCustNm: String? = null,
    val strCpNo: String? = null,
)

data class KtxSearchResponse(
    val strResult: String? = null,
    val h_msg_txt: String? = null,
    val trn_infos: KtxTrnInfos? = null,
)
data class KtxTrnInfos(val trn_info: List<KtxTrainData>? = null)
data class KtxTrainData(
    val h_trn_clsf_cd: String,
    val h_trn_gp_cd: String,
    val h_trn_no: String,
    val h_dpt_rs_stn_nm: String,
    val h_dpt_rs_stn_cd: String,
    val h_dpt_dt: String,
    val h_dpt_tm: String,
    val h_arv_rs_stn_nm: String,
    val h_arv_rs_stn_cd: String,
    val h_arv_dt: String,
    val h_arv_tm: String,
    val h_run_dt: String,
    val h_rsv_psb_flg: String,
    val h_spe_rsv_cd: String,
    val h_gen_rsv_cd: String,
    val h_wait_rsv_flg: String,
)

data class KtxReserveResponse(
    val strResult: String = "",
    val h_msg_txt: String? = null,
    val h_pnr_no: String? = null,
)
