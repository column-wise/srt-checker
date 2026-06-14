package io.github.columnwise.trainchecker.api.srt

import io.github.columnwise.trainchecker.data.api.srt.NetFunnelHelper
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NetFunnelHelperTest {
    private lateinit var server: MockWebServer
    private lateinit var helper: NetFunnelHelper

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        helper = NetFunnelHelper(OkHttpClient()).also { it.baseUrl = server.url("/").toString().trimEnd('/') }
    }

    @After fun teardown() = server.shutdown()

    @Test fun `getToken returns key on PASS status`() = runTest {
        server.enqueue(MockResponse().setBody(
            "NetFunnel.gControl.result='200:success:key=abc123&nwait=0&ip=${server.hostName}'"
        ))
        server.enqueue(MockResponse().setBody(
            "NetFunnel.gControl.result='200:success:key=abc123&nwait=0&ip=${server.hostName}'"
        ))
        val token = helper.getToken()
        assertEquals("abc123", token)
    }

    @Test fun `getToken uses cache within TTL`() = runTest {
        server.enqueue(MockResponse().setBody(
            "NetFunnel.gControl.result='200:success:key=cached&nwait=0&ip=${server.hostName}'"
        ))
        server.enqueue(MockResponse().setBody(
            "NetFunnel.gControl.result='200:success:key=cached&nwait=0&ip=${server.hostName}'"
        ))
        helper.getToken()
        val second = helper.getToken()
        assertEquals("cached", second)
    }
}
