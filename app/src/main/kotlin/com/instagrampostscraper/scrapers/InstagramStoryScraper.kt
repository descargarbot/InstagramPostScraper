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
import java.util.Base64

class InstagramStoryScraper {
    private var client: OkHttpClient
    private val headers: Headers
    private val igStoryRegex = """https?://(?:www\.)?instagram\.com/stories/([^/]+)(?:/(\d+))?/?""".toRegex()
    private val igHighlightsRegex = """(?:https?://)?(?:www\.)?instagram\.com/s/(\w+)(?:\?story_media_id=(\d+)_(\d+))?""".toRegex()

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

    fun getUsernameStoryId(igStoryUrl: String): Pair<String, String> {
        if ("/s/" in igStoryUrl) {
            val code = igHighlightsRegex.find(igStoryUrl)?.groupValues?.get(1)
                ?: throw IllegalStateException("Error getting short code from highlights")
                
            val decoded = String(Base64.getDecoder().decode(code))
            val storyId = decoded.split(":")[1].replace("'", "")
            return Pair("highlights", storyId)
        }

        val match = igStoryRegex.find(igStoryUrl)
            ?: throw IllegalStateException("Error getting username")
            
        val username = match.groupValues[1]
        var storyId = match.groupValues[2]
        
        if (storyId.isEmpty()) {
            storyId = "3446487468465775665"
        }

        return Pair(username, storyId)
    }

    fun getUserIdByUsername(username: String, storyId: String): String {
        // If it's a highlight, return the highlight ID format
        if (username == "highlights") {
            return "highlight:$storyId"  // w/highlights user id is not necessary
        }

        val request = Request.Builder()
            .url("https://www.instagram.com/api/v1/users/web_profile_info/?username=$username")
            .headers(headers)
            .build()

        var userId = ""
        try{
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw Exception("Failed to get user info: ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")

            val jsonRespond = parseJson(responseBody)

            userId = jsonRespond["data"]["user"]["id"].asString()

            return userId

        }catch (e: Exception) {
            println("Error: ${e.message}")
            throw RuntimeException("Error getting user id")
        }
    }

    fun igLogin(username: String, password: String) {

        // TODO handle cookies
        
        // Get login page first to extract tokens
        val loginPageRequest = Request.Builder()
            .url("https://www.instagram.com/accounts/login")
            .headers(headers)
            .build()

        try {
            client.newCall(loginPageRequest).execute().use { response ->
                val pageContent = response.body?.string() ?: throw Exception("Empty response body")
                
                // Extract csrf token
                val csrfTokenRegex = """\"csrf_token\":\"(\w+)\"""".toRegex()
                val csrfToken = csrfTokenRegex.find(pageContent)?.groupValues?.get(1)
                    ?: throw Exception("Could not extract CSRF token")

                // Extract rollout hash
                val rolloutHashRegex = """\"rollout_hash\":\"(\w+)\"""".toRegex()
                val rolloutHash = rolloutHashRegex.find(pageContent)?.groupValues?.get(1)
                    ?: throw Exception("Could not extract rollout hash")

                // Prepare headers for login request
                val loginHeaders = Headers.Builder()
                    .addAll(headers)
                    .add("x-requested-with", "XMLHttpRequest")
                    .add("x-csrftoken", csrfToken)
                    .add("x-instagram-ajax", rolloutHash)
                    .add("referer", "https://www.instagram.com/")
                    .build()

                // Prepare login payload
                val loginPayload = FormBody.Builder()
                    .add("enc_password", "#PWD_INSTAGRAM_BROWSER:0:${(System.currentTimeMillis() / 1000).toInt()}:$password")
                    .add("username", username)
                    .add("queryParams", "{}")
                    .add("optIntoOneTap", "false")
                    .add("stopDeletionNonce", "")
                    .add("trustedDeviceRecords", "{}")
                    .build()

                // Make login request
                val loginRequest = Request.Builder()
                    .url("https://www.instagram.com/accounts/login/ajax/")
                    .headers(loginHeaders)
                    .post(loginPayload)
                    .build()

                client.newCall(loginRequest).execute().use { loginResponse ->
                    if (!loginResponse.isSuccessful) {
                        throw Exception("Login failed with code: ${loginResponse.code}")
                    }
                    
                    // TODO handle cookies
                }
            }
        } catch (e: Exception) {
            println("Error during login: ${e.message}")
            throw RuntimeException("Login failed", e)
        }
    }
    
    fun getIgStoriesUrls(userId: String): Pair<List<String>, List<String>> {
        val igStoriesEndpoint = "https://i.instagram.com/api/v1/feed/reels_media/?reel_ids=$userId"
        
        val igUrlJson = try {
            val request = Request.Builder()
                .url(igStoriesEndpoint)
                .headers(headers)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                parseJson(response.body?.string() ?: throw IOException("Response body is null"))
            }
        } catch (e: Exception) {
            throw RuntimeException("Error getting JSON stories", e)
        }

        val storiesUrls = mutableListOf<String>()
        val thumbnailUrls = mutableListOf<String>()

        try {
            for (item in igUrlJson["reels"][userId]["items"]) {
                if (item.containsKey("video_versions")) {
                    storiesUrls.add(item["video_versions"][0]["url"].asString())
                    thumbnailUrls.add(item["image_versions2"]["candidates"][0]["url"].asString())
                } else {
                    storiesUrls.add(item["image_versions2"]["candidates"][0]["url"].asString())
                    thumbnailUrls.add(item["image_versions2"]["candidates"][0]["url"].asString())
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Error getting URLs stories", e)
        }

        return Pair(storiesUrls, thumbnailUrls)
    }

    fun download(igVideoUrls: List<String>): List<String> {
        val pathFilenames = mutableListOf<String>()

        for ( itemUrl in igVideoUrls ){
            val request = Request.Builder()
                .url(itemUrl)
                .headers(headers)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val filename = itemUrl.split("?")[0].split("/").last()

                val file = File(filename)
                file.outputStream().use { fileOutputStream ->
                    response.body?.byteStream()?.copyTo(fileOutputStream)
                }
                
                pathFilenames.add(filename)
            }
        }
        return pathFilenames
    }
    
}
