package com.instagrampostscraper.main

import java.io.IOException

import com.instagrampostscraper.scrapers.InstagramPostScraper
import com.instagrampostscraper.scrapers.InstagramStoryScraper


fun runInstagramPostScraper(igUrl: String) {

    val instagramPost = InstagramPostScraper()

    try {
        val postId = instagramPost.getPostIdByUrl(igUrl)

        val csrfToken = instagramPost.getCsrfToken(postId) 
            ?: throw RuntimeException("No se pudo obtener el token CSRF")
        
        val (igPostUrls, igPostThumb) = instagramPost.getIgPostUrls(csrfToken, postId)
        
        val itemsFilesize = instagramPost.getVideoFileSize(igPostUrls)

        val downloadedItems = instagramPost.download(igPostUrls, postId)

        println(downloadedItems)
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }

}

fun runInstagramStoryScraper(igUrl: String) {
    // set ur credencials
    val username = ""
    val password = ""
  
    val instagramStory = InstagramStoryScraper()

    try {
        val (username, storyId) = instagramStory.getUsernameStoryId(igUrl)

        val userId = instagramStory.getUserIdByUsername(username, storyId)

        instagramStory.igLogin(username, password)

        val (storiesUrls, thumbnailUrls) = instagramStory.getIgStoriesUrls(userId)

        val pathFilenames = instagramStory.download(storiesUrls)
        println(pathFilenames)
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }

}

fun main() {
    // set ur ig url
    val igUrl = ""

    if ("/s/" in igUrl || "/stories/" in igUrl){
        runInstagramStoryScraper(igUrl)
    }else{
        runInstagramPostScraper(igUrl)
    }
    
}
