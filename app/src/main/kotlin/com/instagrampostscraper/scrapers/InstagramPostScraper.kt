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

class InstagramPostScraper {
    private var client: OkHttpClient
    private val headers: Headers
    private val instagramRegex = """(https?://(?:www\.)?instagram\.com(?:/[^/]+)?/(?:p|tv|reel)/([^/?#&]+))""".toRegex()
    private val gson = Gson()

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

    fun getPostIdByUrl(instagramUrl: String): String {

        val matchResult = instagramRegex.find(instagramUrl)
            ?: throw IllegalArgumentException("Invalid Instagram URL")

        return matchResult.groupValues[2]
    }

    fun postIdToPk(postId: String): Long {
        return decodeBaseN(postId.substring(0, 11), table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_")
    }

    private fun baseNTable(n: Int? = null, table: String? = null): String {
        val defaultTable = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return if (table != null) {
            if (n != null) table.substring(0, n) else table
        } else {
            if (n != null) defaultTable.substring(0, n) else defaultTable
        }
    }

    private fun decodeBaseN(string: String, n: Int? = null, table: String? = null): Long {
        val finalTable = baseNTable(n, table)
        val charToIndex = finalTable.withIndex().associate { (index, char) -> char to index }
        var result = 0L
        val base = finalTable.length

        for (char in string) {
            result = result * base + (charToIndex[char] ?: throw IllegalArgumentException("Invalid character in input: $char"))
        }

        return result
    }

    fun getCsrfToken(postId: String): String? {
        val igCsrfEndpoint = "https://i.instagram.com/api/v1/web/get_ruling_for_content/?content_type=MEDIA&target_id=${postIdToPk(postId)}"
        
        try {
            val request = Request.Builder()
                .url(igCsrfEndpoint)
                .headers(headers)
                .build()

            val response = client.newCall(request).execute()
            
            val cookies = client.cookieJar.loadForRequest(request.url)

            return cookies.find { it.name == "csrftoken" }?.value
            
        } catch (e: Exception) {
            println("Error: ${e.message}")
            throw RuntimeException("error getting csrf token")
        }
    }

    fun getIgPostUrls(csrfToken: String, postId: String): JsonWrapper {

        val headersBuilder = Headers.Builder()

        for (i in 0 until headers.size) {
            headersBuilder.add(headers.name(i), headers.value(i))
        }

        headersBuilder.add("x-csrftoken", csrfToken)
        headersBuilder.add("x-requested-with", "XMLHttpRequest")
        headersBuilder.add("referer", "https://www.instagram.com/p/$postId/")

        val finalHeaders = headersBuilder.build()

        val variablesPostDetails = mapOf(
            "shortcode" to postId,
            "child_comment_count" to 3,
            "fetch_comment_count" to 40,
            "parent_comment_count" to 24,
            "has_threaded_comments" to true
        )

        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("www.instagram.com")
                .addPathSegments("graphql/query/")
                .addQueryParameter("doc_id", "8845758582119845")
                .addQueryParameter("variables", gson.toJson(variablesPostDetails))
                .build()

            val request = Request.Builder()
                .url(url)
                .headers(finalHeaders)
                .build()

            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("Request failed with code ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            val postData = parseJson(responseBody)
            println(postData["data"]["xdt_shortcode_media"]["video_url"].toString())
            throw IOException(".p")

            return parseJson(responseBody)

        } catch (e: Exception) {
            println("Error: ${e.message}")
            throw RuntimeException("Error getting post details: ${e.message}")
        }
    }
}
