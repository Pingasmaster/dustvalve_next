package com.dustvalve.next.android.data.remote.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class NewPipeDownloader : Downloader() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .method(
                httpMethod,
                dataToSend?.toRequestBody()
            )

        // Copy headers
        for ((headerName, headerValueList) in headers) {
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                for (headerValue in headerValueList) {
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        val responseBodyString = response.body.string()
        val latestUrl = response.request.url.toString()

        val responseHeaders: MutableMap<String, List<String>> = mutableMapOf()
        for (headerName in response.headers.names()) {
            responseHeaders[headerName] = response.headers.values(headerName)
        }

        return Response(
            response.code,
            response.message,
            responseHeaders,
            responseBodyString,
            latestUrl,
        )
    }
}
