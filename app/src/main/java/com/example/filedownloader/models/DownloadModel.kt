package com.example.filedownloader.models

import android.net.Uri
import java.io.File

//Data class for DownloadModel to show the progress and current status of the download
data class DownloadModel(
    val id : String,
    val fileName: String,
    val url: String,
    val time : Long,
    val folderUri : Uri,
    val fileExtension : String,
    val networkPreference: NetworkPreference,
    var status: DownloadStatus,
    var downloadProgressPercentage: Int,
    var downloadProgressBytes: String,
    var downloadTotalBytes: String,
    var failedMessage: String,
    var outputFile: Uri?
)