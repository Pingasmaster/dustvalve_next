package com.dustvalve.next.android.data.remote

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.net.InetAddress

/**
 * Shared HTTPS MockWebServer helper. The scrapers require https URLs whose host contains a dot
 * (NetworkUtils.isValidHttpsUrl), so we give MockWebServer a self-signed cert for fake FQDNs
 * and rewrite DNS on the OkHttpClient so requests for those FQDNs resolve to MockWebServer.
 *
 * "album.test" is the generic test host; "bandcamp.com" is routed to the same mock server so
 * scrapers that hardcode the bandcamp.com URL (search / discover / collection / download) can
 * also be exercised.
 */
object TlsTestServer {

    const val HOST = "album.test"
    private val ALL_HOSTS = listOf(HOST, "bandcamp.com")

    data class Setup(val server: MockWebServer, val client: OkHttpClient) {
        fun url(path: String): String = "https://$HOST:${server.port}$path"
    }

    fun start(): Setup {
        val heldCert = HeldCertificate.Builder().apply {
            ALL_HOSTS.forEach { addSubjectAlternativeName(it) }
        }.build()
        val serverCerts = HandshakeCertificates.Builder()
            .heldCertificate(heldCert)
            .build()
        val clientCerts = HandshakeCertificates.Builder()
            .addTrustedCertificate(heldCert.certificate)
            .build()

        val server = MockWebServer()
        server.useHttps(serverCerts.sslSocketFactory(), false)
        server.start()

        val port = server.port
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    if (hostname in ALL_HOSTS) listOf(InetAddress.getByName("127.0.0.1"))
                    else Dns.SYSTEM.lookup(hostname)
            })
            // The scrapers call e.g. https://bandcamp.com/... with no explicit port, but our
            // MockWebServer listens on an ephemeral port. We rewrite the URL on the way out.
            .addInterceptor { chain ->
                val req = chain.request()
                val host = req.url.host
                val newReq = if (host in ALL_HOSTS && req.url.port != port) {
                    req.newBuilder().url(req.url.newBuilder().port(port).build()).build()
                } else req
                chain.proceed(newReq)
            }
            .build()
        return Setup(server, client)
    }
}
