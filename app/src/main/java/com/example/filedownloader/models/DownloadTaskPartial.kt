package com.example.filedownloader.models

import android.net.Uri

// This class is used to store the partial data of the download task
data class DownloadTaskPartial(
    val fileName : String,
    val url : String,
    val fileUri : Uri,
    val fileExtension : String,
    val networkPreference : NetworkPreference,
    val status : DownloadStatus,
    val id : String
)