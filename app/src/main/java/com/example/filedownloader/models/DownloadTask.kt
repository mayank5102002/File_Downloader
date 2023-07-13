package com.example.filedownloader.models

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.MutableLiveData
import com.example.filedownloader.MainActivity
import com.example.filedownloader.R
import com.example.filedownloader.utils.DownloadListenerHolder
import com.example.filedownloader.utils.Downloader
import com.example.filedownloader.utils.FileSaver
import com.example.filedownloader.utils.NotificationBroadcastReceiver
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

//enum class for DownloadStatus
enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

//enum class for NetworkPreference
enum class NetworkPreference {
    WIFI_ONLY,
    WIFI_AND_CELLULAR
}


//Class for DownloadTask
class DownloadTask(
    val fileName: String,
    val url: String,
    private val context : Context,
    val fileUri : Uri,
    val fileExtension : String,
    val networkPreference: NetworkPreference,
    val listener : DownloadListener,
    var status: DownloadStatus,
    val id : String
) {
    var startTime: Long = System.currentTimeMillis()

    //Initialising the downloader for downloading the file
    val downloadManager = Downloader()
    var isPaused = true

    //Notification manager
    private val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(context)

    //Notification details
    private val notificationId: Int = startTime.toInt()
    private val channelId: String = "download_channel"
    private val channelName: String = "Download Channel"

    //Progress details
    val downloadProgressPercentage = MutableLiveData(0)
    val downloadProgressBytes = MutableLiveData("0MB")
    val downloadTotalBytes = MutableLiveData("0MB")

    var failedMessage = MutableLiveData("")

    //Output file
    val outputFile = MutableLiveData<Uri?>()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var downloadJob: Job? = null


    //Function to start the download
    fun startDownload() {
        downloadJob = coroutineScope.launch {
            Log.i("Download", "Starting download for $fileName")
            isPaused = false
            status = DownloadStatus.DOWNLOADING
            listener.onDownloadStatusChange(id)
            createNotificationChannel()

            try {
                downloadManager.download(url, fileUri, fileName, fileExtension, context, createDownloadProgressListener(listener, id))
            } catch (e: Exception) {
                status = DownloadStatus.FAILED
            }
        }
    }


    //Changing the status of the download to pending
    fun putToPending(){
        status = DownloadStatus.PENDING
        listener.onDownloadStatusChange(id)
    }

    //Pausing the download
    fun pauseDownload() {
        try {
            if(isPaused) return
            isPaused = true
            Log.i("Download", "Pausing download for $fileName")
            coroutineScope.launch {
                status = DownloadStatus.PAUSED
                listener.onDownloadStatusChange(id)
                updateNotificationPaused()
                downloadJob?.cancel()
                downloadManager.pauseDownload(context, fileUri, fileName, fileExtension, createDownloadProgressListener(listener, id))
            }
        } catch (e : Exception){
            e.printStackTrace()
        }
    }

    //Resuming the download
    fun resumeDownload() {
        if(!isPaused) return
        isPaused = false
        Log.i("Download", "Resuming download for $fileName")
        downloadJob?.cancel()
        downloadJob = coroutineScope.launch {
            status = DownloadStatus.DOWNLOADING
            listener.onDownloadStatusChange(id)
            downloadManager.resumeDownload(url, fileUri, fileName, fileExtension, context, createDownloadProgressListener(listener, id))
        }
    }

    //Function when the download is completed
    fun completeDownload(savedFileUri : Uri?) {
        status = DownloadStatus.COMPLETED
        outputFile.postValue(savedFileUri)
        listener.onDownloadComplete(id)
        updateNotificationDownloaded()
    }

    //Function when the download fails
    fun failDownload() {
        status = DownloadStatus.FAILED
        Log.i("Download", "Download failed!")
        failedMessage.postValue("Please check your internet connection")
        listener.onDownloadFailed(id)
        updateNotificationFailed()
    }

    //Function to cancel the download
    fun cancelDownload() {
        try {
            coroutineScope.launch {
                status = DownloadStatus.FAILED
                downloadJob?.cancelAndJoin()
                downloadManager.cancelDownload(context, fileUri, fileName, fileExtension)
                removeNotification()
            }
        } catch (e : Exception){
            e.printStackTrace()
        }
    }

    //Function to get the download status
    fun getDownloadStatus(): DownloadStatus {
        return status
    }


    //Function to create the download progress listener
    private fun createDownloadProgressListener(listener: DownloadListener, id : String): Downloader.DownloadProgressListener {
        return object : Downloader.DownloadProgressListener {

            //Function to update the progress of the download
            override fun onProgressUpdate(progress: Int, downloadedBytes: Long, totalBytes: Long, speed : String, timeRemaining : String) {
                val percentage = "$progress%"
                val downloaded = formatBytes(downloadedBytes)
                val total = formatBytes(totalBytes)
//                Log.i("Download", "Progress: $percentage Downloaded: $downloaded/$total")
                if(abs(progress-downloadProgressPercentage.value!!) > 0.9) {
                    downloadProgressPercentage.postValue(progress)
                    downloadProgressBytes.postValue(downloaded)
                    downloadTotalBytes.postValue(total)

                    updateNotification(listener, progress, downloaded, total, speed, timeRemaining)
                    listener.onDownloadProgress(id)
                }
            }

            //Function to update the download when the download is completed
            override fun onDownloadComplete(file: File) {
                Log.i("Download", "Download complete: ${file.absolutePath}")

                //Function to save the file
                saveFile(file)
            }

            //Function to update the download when the download fails
            override fun onDownloadError(exception: Exception) {
                exception.printStackTrace()
                failDownload()
                listener.onDownloadFailed(id)
            }
        }
    }

    //Function to save file to the directory desired
    private fun saveFile(file: File) {
        try{
            coroutineScope.launch {
                val filerSaver = FileSaver()
                filerSaver.saveFileToDirectory(context, fileUri, fileName, fileExtension, file, createFileSaverCallback())
            }
        } catch (e : Exception){
            e.printStackTrace()
        }
    }

    //Function to create the file saver callback
    private fun createFileSaverCallback() : FileSaver.SaveFileCallback {
        return object : FileSaver.SaveFileCallback {
            override fun onProgress(progress: Int) {
                //Do nothing
            }

            //Function to update the download when the file is saved
            override fun onSaveComplete(savedFileUri: Uri?) {
                Log.i("Download", "File saved to: $savedFileUri")

                //Function to complete the download
                completeDownload(savedFileUri)
            }

            //Function to update the download when the file fails to save
            override fun onError(e: Exception?) {
                e!!.printStackTrace()
                failDownload()
                listener.onDownloadFailed(id)
            }
        }
    }

    //Function to inform about the download progress through notification
    private fun updateNotification(
        downloadListener: DownloadListener,
        progress: Int,
        downloaded: String,
        total: String,
        speed: String,
        timeRemaining: String
    ) {
        // Create an instance of YourBroadcastReceiver
        val broadcastReceiver = NotificationBroadcastReceiver()
        DownloadListenerHolder.setDownloadListener(id, downloadListener)

        // Intent for the notification click
        val notificationIntent = Intent(context, MainActivity::class.java)
        notificationIntent.flags =
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val notificationPendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for the pause action
        val pauseIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        pauseIntent.action = "com.example.filedownloader.PAUSE_ACTION"
        pauseIntent.putExtra("TASK_ID", id) // Pass the task ID as an extra
        val pausePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for the cancel action
        val cancelIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        cancelIntent.action = "com.example.filedownloader.CANCEL_ACTION"
        cancelIntent.putExtra("TASK_ID", id) // Pass the task ID as an extra
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Downloading $fileName.$fileExtension")
            .setContentText("$downloaded of $total | $speed")
            .setSubText("$timeRemaining left")
            .setSmallIcon(R.drawable.main_icon)
            .setProgress(100, progress, false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(R.drawable.ic_pause, "Pause", pausePendingIntent)
            .addAction(R.drawable.cancel_icon, "Cancel", cancelPendingIntent)
            .setContentIntent(notificationPendingIntent)

        val notification = builder.build()
        notification.flags =
            notification.flags or NotificationCompat.FLAG_ONGOING_EVENT
        notification.flags =
            notification.flags or NotificationCompat.FLAG_NO_CLEAR

        // Register the broadcast receiver dynamically
        val filter = IntentFilter()
        filter.addAction("com.example.filedownloader.PAUSE_ACTION")
        filter.addAction("com.example.filedownloader.CANCEL_ACTION")
        context.registerReceiver(broadcastReceiver, filter)

        notificationManager.notify(notificationId, notification)
    }


    //Function to update the progress when the download is paused through notification
    private fun updateNotificationPaused() {
        val notificationIntent = Intent(context, MainActivity::class.java)
        notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val notificationPendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        notificationManager.cancel(notificationId)
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Download Paused")
            .setContentText("$fileName.$fileExtension")
            .setSmallIcon(R.drawable.main_icon)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setContentIntent(notificationPendingIntent) // Set the pending intent for notification click
        notificationManager.notify(notificationId, builder.build())
    }


    //Function to update the progress when the download is completed through notification
    private fun updateNotificationDownloaded() {
        val openIntent = Intent(Intent.ACTION_VIEW)
        openIntent.setDataAndType(outputFile.value, getMimeType(outputFile.value!!))
        openIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val openPendingIntent = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        notificationManager.cancel(notificationId)
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Downloaded")
            .setContentText("$fileName.$fileExtension")
            .setSmallIcon(R.drawable.main_icon)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setContentIntent(openPendingIntent) // Set the pending intent for notification click
        notificationManager.notify(notificationId, builder.build())
    }

    private fun getMimeType(uri: Uri): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }


    //Function to update the progress when the download is failed through notification
    private fun updateNotificationFailed() {
        val notificationIntent = Intent(context, MainActivity::class.java)
        notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val notificationPendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        notificationManager.cancel(notificationId)
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Download Failed")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.main_icon)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setContentIntent(notificationPendingIntent) // Set the pending intent for notification click
        notificationManager.notify(notificationId, builder.build())
    }

    //Function to create the notification channel
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    //Function to format the bytes to KB, MB, GB, etc.
    fun formatBytes(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1] + "B"
        return String.format("%.1f %s", bytes / unit.toDouble().pow(exp.toDouble()), pre)
    }

    //Function to remove the notification
    private fun removeNotification() {
        notificationManager.cancel(notificationId)
    }

    interface DownloadListener {
        fun onDownloadProgress(taskId : String)
        fun onDownloadComplete(taskId : String)
        fun onDownloadFailed(taskId : String)
        fun onDownloadStatusChange(taskId : String)
        fun pauseThisDownload(taskId : String)
        fun cancelThisDownload(taskId : String)
    }
}