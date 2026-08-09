package com.dustvalve.next.android.data.remote

import com.google.common.truth.Truth.assertThat
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress

/**
 * SSRF redirect + DNS fail-closed coverage for [PublicHostGuard].
 */
class PublicHostGuardTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun guardedClient(extraDns: Dns? = null): OkHttpClient {
        val dns = if (extraDns == null) {
            PublicHostGuard.dns()
        } else {
            PublicHostGuard.dns(extraDns)
        }
        return OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .dns(dns)
            .addInterceptor(PublicHostGuard.redirectInterceptor)
            .build()
    }

    @Test fun `redirect interceptor refuses literal private hosts before connect`() {
        val client = guardedClient()
        val ex = runCatching {
            client.newCall(Request.Builder().url("http://127.0.0.1/").build()).execute()
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IOException::class.java)
        assertThat(ex!!.message).contains("private")
    }

    @Test fun `redirect interceptor refuses redirect hop to private host`() {
        // First hop: MockWebServer on loopback, but addressed via a public-looking
        // hostname so the initial requirePublicHost (system DNS NXDOMAIN -> not
        // private) and Dns rewrite can connect. Second hop: link-local.
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "http://169.254.169.254/latest/meta-data/"),
        )
        val publicHost = "public.test"
        val port = server.port
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            // Bypass PublicHostGuard.dns for the first hop only so the mock
            // server on 127.0.0.1 is reachable; redirect hop still hits
            // requirePublicHost on the Location host.
            .dns(
                Dns { hostname ->
                    if (hostname == publicHost) {
                        listOf(InetAddress.getByName("127.0.0.1"))
                    } else {
                        Dns.SYSTEM.lookup(hostname)
                    }
                },
            )
            .addInterceptor(PublicHostGuard.redirectInterceptor)
            .addInterceptor { chain ->
                val req = chain.request()
                val rewritten = if (req.url.host == publicHost && req.url.port != port) {
                    req.newBuilder().url(req.url.newBuilder().port(port).build()).build()
                } else {
                    req
                }
                chain.proceed(rewritten)
            }
            .build()
        val ex = runCatching {
            client.newCall(Request.Builder().url("http://$publicHost/start").build()).execute()
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IOException::class.java)
        assertThat(ex!!.message).contains("private")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test fun `dns refuses private resolved addresses`() {
        val loopbackDns = Dns { listOf(InetAddress.getByName("127.0.0.1")) }
        val guarded = PublicHostGuard.dns(loopbackDns)
        val ex = runCatching { guarded.lookup("evil.example") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(java.net.UnknownHostException::class.java)
        assertThat(ex!!.message).contains("private")
    }

    @Test fun `dns keeps public addresses`() {
        val public = InetAddress.getByName("8.8.8.8")
        assertThat(PublicHostGuard.isDisallowedResolved(public)).isFalse()
        val dns = PublicHostGuard.dns { listOf(public) }
        assertThat(dns.lookup("dns.google")).containsExactly(public)
    }
}
