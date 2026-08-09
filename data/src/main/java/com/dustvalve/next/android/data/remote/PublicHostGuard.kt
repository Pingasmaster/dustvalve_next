package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.util.NetworkUtils
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Shared SSRF defenses for user-influenced HTTP (sniffer, playlist art/audio
 * fetch, Coil image loads, and any other client that opts in).
 *
 * [redirectInterceptor] disables reliance on OkHttp's opaque redirect follower:
 * it validates the public-host policy on the initial URL and every Location
 * hop (fail closed), then proceeds. Pair with [dns] so a public-looking name
 * cannot resolve into RFC1918 / link-local / CGNAT / ULA either.
 *
 * Clients must use [followRedirects] = false when installing
 * [redirectInterceptor] (NetworkModule does this) so hops are not double-followed.
 */
object PublicHostGuard {

    private const val MAX_REDIRECTS = 20

    /**
     * Application interceptor: validate host, then manually follow redirects
     * while re-validating every hop. Install with OkHttp followRedirects=false.
     */
    val redirectInterceptor: Interceptor = Interceptor { chain ->
        var request = chain.request()
        requirePublicHost(request.url.host)
        var response = chain.proceed(request)
        var hops = 0
        while (isRedirect(response.code) && hops < MAX_REDIRECTS) {
            val location = response.header("Location")
                ?: throw IOException("redirect without Location")
            val nextUrl = request.url.resolve(location)
                ?: throw IOException("unresolvable redirect Location: $location")
            requirePublicHost(nextUrl.host)
            response.close()
            request = requestPriorResponse(request, nextUrl, response.code)
            response = chain.proceed(request)
            hops++
        }
        if (isRedirect(response.code) && hops >= MAX_REDIRECTS) {
            response.close()
            throw IOException("too many redirects")
        }
        response
    }

    /** Dns that drops disallowed answers and fails when none remain. */
    fun dns(delegate: Dns = Dns.SYSTEM): Dns = Dns { hostname ->
        if (NetworkUtils.isLiteralDisallowedHost(hostname)) {
            throw UnknownHostException("refusing private/link-local host: $hostname")
        }
        val answers = try {
            delegate.lookup(hostname)
        } catch (e: UnknownHostException) {
            throw e
        } catch (e: Exception) {
            throw UnknownHostException("DNS lookup failed for $hostname: ${e.message}")
        }
        val publicAnswers = answers.filterNot(NetworkUtils::isDisallowedAddress)
        if (publicAnswers.isEmpty()) {
            throw UnknownHostException("refusing private/link-local resolution for $hostname")
        }
        publicAnswers
    }

    fun requirePublicHost(host: String) {
        if (host.isBlank() || NetworkUtils.isDisallowedPrivateHost(host)) {
            throw IOException("refusing private/link-local host: $host")
        }
    }

    /** Exposed for tests that assert address classification without DNS. */
    fun isDisallowedResolved(address: InetAddress): Boolean = NetworkUtils.isDisallowedAddress(address)

    private fun isRedirect(code: Int): Boolean = code in REDIRECT_CODES

    /**
     * Mirror OkHttp's redirect method rules: 307/308 keep the verb; other
     * redirects turn non-GET/HEAD into GET (and drop the body).
     */
    private fun requestPriorResponse(prior: Request, nextUrl: HttpUrl, code: Int): Request {
        val builder = prior.newBuilder().url(nextUrl)
        if (code == 307 || code == 308) {
            return builder.build()
        }
        val method = prior.method
        if (method != "GET" && method != "HEAD") {
            builder.method("GET", null)
            builder.removeHeader("Transfer-Encoding")
            builder.removeHeader("Content-Length")
            builder.removeHeader("Content-Type")
        }
        return builder.build()
    }

    private val REDIRECT_CODES = setOf(300, 301, 302, 303, 307, 308)
}
