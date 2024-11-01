package com.instagrampostscraper.scrapers

import okhttp3.*
import okhttp3.JavaNetCookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.instagrampostscraper.jsonutils.parseJson
import com.instagrampostscraper.jsonutils.JsonWrapper
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.regex.Pattern
import kotlin.random.Random

class InstagramStoryScraper {
    private var client: OkHttpClient
    private val headers: Headers
    private val igStoryRegex = """https?://(?:www\.)?instagram\.com/stories/([^/]+)(?:/(\d+))?/?""".toRegex()
    private val igHighlightsRegex = """(?:https?://)?(?:www\.)?instagram\.com/s/(\w+)\?story_media_id=(\d+)_(\d+)""".toRegex()

    init {
        val cookieManager = CookieManager().apply {
            setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        }
        val cookieJar = JavaNetCookieJar(cookieManager)

        client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()

        headers = Headers.Builder()
            .add("x-ig-app-id", "936619743392459")
            .add("x-asbd-id", "198387")
            .add("x-ig-www-claim", "0")
            .add("origin", "https://www.instagram.com")
            .add("accept", "*/*")
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36")
            .build()
    }

    fun setProxy(protocol: String, ip: String, port: Int){
        val proxyType = when (protocol.lowercase()){
            "http", "https" -> Proxy.Type.HTTP
            "socks4", "socks5" -> Proxy.Type.SOCKS
            else -> throw IllegalArgumentException("Unsupported proxy protocol: $protocol")
        }

        val proxyAddress = InetSocketAddress.createUnresolved(ip, port)
        val proxy = Proxy(proxyType, proxyAddress)

        client = client.newBuilder()
            .proxy(proxy)
            .build()

        println("Proxy set to $protocol://$ip:$port")
    }

}
