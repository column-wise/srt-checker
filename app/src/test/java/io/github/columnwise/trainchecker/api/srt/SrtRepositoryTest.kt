package io.github.columnwise.trainchecker.api.srt

import io.github.columnwise.trainchecker.data.api.srt.*
import io.github.columnwise.trainchecker.data.model.SeatType
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SrtRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: SrtRepository
    private lateinit var netFunnel: NetFunnelHelper

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SrtApiService::class.java)
        netFunnel = NetFunnelHelper(client).also { it.baseUrl = server.url("/").toString().trimEnd('/') }
        repo = SrtRepository(api, netFunnel)
    }

    @After fun teardown() = server.shutdown()

    private fun enqueueNetFunnel() {
        server.enqueue(MockResponse().setBody(
            "NetFunnel.gControl.result='200:success:key=tok123&nwait=0&ip=${server.hostName}'"
        ))
        server.enqueue(MockResponse().setBody(
            "NetFunnel.gControl.result='200:success:key=tok123&nwait=0&ip=${server.hostName}'"
        ))
    }

    @Test fun `login returns true on success`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"userMap":{"MB_CRD_NO":"12345","CUST_NM":"홍길동","MBL_PHONE":"01012345678"}}"""
        ))
        assertTrue(repo.login("test@email.com", "pw"))
    }

    @Test fun `searchTrains returns list on SUCC`() = runTest {
        enqueueNetFunnel()
        server.enqueue(MockResponse().setBody("""
            {
              "resultMap":[{"strResult":"SUCC","msgTxt":""}],
              "outDataSets":{"dsOutput1":[{
                "stlbTrnClsfCd":"17","trnNo":"101",
                "dptDt":"20241225","dptTm":"080000",
                "dptRsStnCd":"0551","arvDt":"20241225","arvTm":"110000",
                "arvRsStnCd":"0020",
                "gnrmRsvPsbStr":"예약가능","sprmRsvPsbStr":"매진",
                "rsvWaitPsbCd":"-1","rsvWaitPsbCdNm":"없음",
                "dptStnRunOrdr":"001","dptStnConsOrdr":"001",
                "arvStnRunOrdr":"010","arvStnConsOrdr":"010"
              }]}
            }
        """.trimIndent()))
        val trains = repo.searchTrains("수서", "부산", "20241225", "0800")
        assertEquals(1, trains.size)
        assertTrue(trains[0].generalAvailable())
    }

    @Test fun `searchTrains returns empty on FAIL`() = runTest {
        enqueueNetFunnel()
        server.enqueue(MockResponse().setBody(
            """{"resultMap":[{"strResult":"FAIL","msgTxt":"조회 오류"}]}"""
        ))
        val trains = repo.searchTrains("수서", "부산", "20241225", "0800")
        assertEquals(0, trains.size)
    }
}
