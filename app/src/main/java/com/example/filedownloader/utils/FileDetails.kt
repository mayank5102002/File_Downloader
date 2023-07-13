package com.example.filedownloader.utils

import android.net.Uri
import android.webkit.MimeTypeMap
import java.net.HttpURLConnection
import java.net.URL

// This class is used to extract file details from url
object FileDetails {

    // Get file details from url
    fun getFileDetailsFromUrl(url: String): Triple<String?, String?, Long?> {
        val uri = Uri.parse(url)
        val fileName = uri.lastPathSegment
        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(url)
        val fileSize = getFileSizeFromUrl(url)

        // Extract file name without extension
        val fileNameWithoutExtension = fileName?.substringBeforeLast(".")

        return Triple(fileNameWithoutExtension, fileExtension, fileSize)
    }

    // Get file size from url
    private fun getFileSizeFromUrl(url: String): Long? {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000 // Set a reasonable timeout value
            connection.readTimeout = 5000
            connection.setRequestProperty("Accept-Encoding", "identity") // Disable compression
            val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull()
            connection.disconnect()
            return contentLength
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

}