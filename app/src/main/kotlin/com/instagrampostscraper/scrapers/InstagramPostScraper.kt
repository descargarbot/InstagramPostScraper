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

    fun getIgPostUrls(csrfToken: String, postId: String): Pair<MutableList<String>, MutableList<String>> {

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

        var responseBody: String? = null
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

            responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

        } catch (e: Exception) {
            println("Error: ${e.message}")
            throw RuntimeException("Error getting post details: ${e.message}")
        }

        val igPostData = parseJson(responseBody)

        var postUrls = mutableListOf<String>()
        var thumbnailUrls = mutableListOf<String>()

        try {
            // Single video
            if ( igPostData["data"]["xdt_shortcode_media"]["__typename"].asString() == "XDTGraphVideo") {
                postUrls.add(igPostData["data"]["xdt_shortcode_media"]["video_url"].asString())
                thumbnailUrls.add(igPostData["data"]["xdt_shortcode_media"]["thumbnail_src"].asString())
            }
            // Single image
            else if (igPostData["data"]["xdt_shortcode_media"]["__typename"].asString() == "XDTGraphImage" ) {
                var imgUrl: String? = null
                for ( image in igPostData["data"]["xdt_shortcode_media"]["display_resources"] ){
                    imgUrl = image["src"].asString()
                }
                postUrls.add(imgUrl!!)
                thumbnailUrls.add(igPostData["data"]["xdt_shortcode_media"]["display_resources"][0]["src"].asString()) // In this case, use the smallest display_resources
            }
            // Sidecar (multiple images/videos)
            else if ( igPostData["data"]["xdt_shortcode_media"]["__typename"].asString() == "XDTGraphSidecar" ) {
                for ( node in igPostData["data"]["xdt_shortcode_media"]["edge_sidecar_to_children"]["edges"] ){
                    if ( node["node"]["__typename"].asString() == "XDTGraphVideo" ) {
                        postUrls.add(node["node"]["video_url"].asString())
                        thumbnailUrls.add(node["node"]["display_resources"][0]["src"].asString())
                    }
                    else if ( node["node"]["__typename"].asString() == "XDTGraphImage" ) {
                        var imgUrl: String? = null
                        for( image in node["node"]["display_resources"] ){
                            imgUrl = image["src"].asString() // Last one is generally the best quality
                        }
                        postUrls.add(imgUrl!!)
                        thumbnailUrls.add(node["node"]["display_resources"][0]["src"].asString())
                    }
                }
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
            throw RuntimeException("Error getting json data: ${e.message}")
        }

        return Pair(postUrls, thumbnailUrls)
    }

    fun download(postUrls: List<String>, postId: String): List<String> {
        val headersBuilder = Headers.Builder()
        for (i in 0 until headers.size) {
            headersBuilder.add(headers.name(i), headers.value(i))
        }
        headersBuilder.add("referer", "https://www.instagram.com/p/$postId/")
        val downloadHeaders = headersBuilder.build()

        val downloadedItems = mutableListOf<String>()
        
        for (postUrl in postUrls) {
            try {
                val request = Request.Builder()
                    .url(postUrl)
                    .headers(downloadHeaders)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Failed to download file: ${response.code}")
                    }

                    val filename = postUrl.split("?")[0].split("/").last()

                    val file = File(filename)
                    file.outputStream().use { fileOutputStream ->
                        response.body?.byteStream()?.copyTo(fileOutputStream)
                            ?: throw IOException("Response body is null")
                    }

                    downloadedItems.add(filename)
                }

            } catch (e: Exception) {
                println("Error: ${e.message}")
                throw RuntimeException("Error downloading file: ${e.message}")
            }
        }

        return downloadedItems
    }

    fun getVideoFileSize(videoUrls: List<String>): List<String> {
        val itemsFileSize = mutableListOf<String>()
        
        val headersBuilder = Headers.Builder()
            .add("Content-Type", "text")
            .build()

        for (videoUrl in videoUrls) {
            try {
                val request = Request.Builder()
                    .url(videoUrl)
                    .headers(headersBuilder)
                    .head()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Failed to get file size: ${response.code}")
                    }

                    val contentLength = response.header("content-length")
                        ?: throw IOException("Content-Length header not found")
                    
                    itemsFileSize.add(contentLength)
                }
            } catch (e: Exception) {
                println("Error: ${e.message}")
                throw RuntimeException("Error getting file size: ${e.message}")
            }
        }

        return itemsFileSize
    }
}
