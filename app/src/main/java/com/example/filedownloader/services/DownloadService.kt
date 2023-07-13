package com.example.filedownloader.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.filedownloader.MainActivity
import com.example.filedownloader.R
import com.example.filedownloader.models.DownloadRepository
import com.example.filedownloader.models.DownloadStatus
import com.example.filedownloader.models.DownloadTaskPartial
import com.example.filedownloader.models.NetworkPreference
import com.example.filedownloader.ui.main.MainFragment
import com.example.filedownloader.ui.main.MainViewModel
import com.example.filedownloader.utils.AppVisibilityTracker
import com.example.filedownloader.utils.DownloadItemDatabaseHelper

// Service to handle downloading files in the background
class DownloadService : Service() {

    private lateinit var downloadRepository: DownloadRepository
    private lateinit var viewModel: MainViewModel
    private lateinit var appVisibilityTracker: AppVisibilityTracker
    private lateinit var dbHelper: DownloadItemDatabaseHelper
    private lateinit var wakeLock: PowerManager.WakeLock
    private var activeDownloads = 0

    private val binder = MyBinder()

    inner class MyBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    override fun onCreate() {
        super.onCreate()
        // Create an instance of the DownloadRepository
        downloadRepository = DownloadRepository.getInstance(applicationContext)
        viewModel = MainViewModel.getInstance()
        dbHelper = DownloadItemDatabaseHelper.getInstance(applicationContext)
        appVisibilityTracker = AppVisibilityTracker(application)

        // Start the service in the foreground
        startForeground(NOTIFICATION_ID, createNotification())

        // Acquire a partial wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DownloadService:WakeLock"
        )
        wakeLock.acquire(10*60*1000L /*10 minutes*/)

        init()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle any incoming commands or actions
        // ...

        return START_STICKY
    }

    private fun init(){
        initObservers()
    }

    // Initialize observers
    private fun initObservers(){
        if(viewModel.downloadsReady.value == true){
            downloadsComplete()
        } else {
            Log.i("DownloadService", "init: ")
            viewModel.getDataService(applicationContext)
            viewModel.downloadsReady.observeForever {
                if(it){
                    downloadsComplete()
                }
            }
        }

        //Observe the download task partials as they are being loaded from the database
        viewModel.downloadTaskPartials.observeForever { taskPartials ->
            if(taskPartials.isNotEmpty()){
                Log.i("DownloadService", "dDownloadTaskPartials: ${taskPartials.size}")
                taskPartials.forEach{
                    downloadRepository.preExistingDownload(it)
                }
            }
            viewModel.downloadTaskPartialsReady.postValue(true)
        }

        //Observe the download queue
        viewModel.downloadQueue.observeForever { queue ->
            Log.i("DownloadService", "ACTIVE DOWNLOADS: ${queue.size}")
            synchronized(this) {
                activeDownloads = queue.size
                closeService()
            }
        }
    }

    //Process the downloads
    private fun downloadsComplete() {
        if (viewModel.areDownloadsAvailable()) {
            Log.i("DownloadService", "Processing downloads")
            activeDownloads = viewModel.downloadQueue.value?.size ?: 0
            downloadRepository.processDownloads()
        }

        closeService()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {

        Log.i("DownloadService", "onDestroy called")
        super.onDestroy()
        // Clean up any resources or tasks
        // ...

        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        // Check if the app is visible and there are no active downloads
        val isAppVisible = appVisibilityTracker.isAppVisible()
        val isDownloadsEmpty = synchronized(this){activeDownloads == 0}
        if (!isAppVisible && isDownloadsEmpty) {
            stopSelf()
            Log.i("DownloadService", "onDestroy: ${activeDownloads} ${isAppVisible} : ${viewModel.downloadQueue.value?.size}")
        }

        appVisibilityTracker.unregister()
    }

    //Task removed
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Restart the service when removed from recent tasks
        val restartIntent = Intent(applicationContext, DownloadService::class.java)
        restartIntent.setPackage(packageName)
        startService(restartIntent)
    }

    // Create a notification for the foreground service
    private fun createNotification(): Notification {
        // Create a notification channel (required for Android 8.0 and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "download_channel"
            val channelName = "Download Channel"
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Create the notification builder
        val builder = NotificationCompat.Builder(this, "download_channel")
            .setContentTitle("File Downloader")
            .setContentText("Running...")
            .setSmallIcon(R.drawable.main_icon)

        // Create the pending intent for the notification (optional)
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        builder.setContentIntent(pendingIntent)

        // Build the notification
        val notification = builder.build()

        return notification
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }

    // Function to pause a specific download
    fun pauseDownload(downloadId: String) {
        Log.i("DownloadService", "pauseDownload: $downloadId")
        downloadRepository.pauseDownload(downloadId)
    }

    // Function to resume a specific download
    fun resumeDownload(downloadId: String) {
        Log.i("DownloadService", "resumeDownload: $downloadId")
        downloadRepository.resumeDownload(downloadId)
    }

    // Function to pause all downloads
    fun pauseAllDownloads() {
        Log.i("DownloadService", "pauseAllDownloads")
        downloadRepository.pauseAllDownloads()
    }

    // Function to resume all downloads
    fun resumeAllDownloads() {
        Log.i("DownloadService", "resumeAllDownloads")
        downloadRepository.resumeAllDownloads()
    }

    // Function to cancel a specific download
    fun cancelDownload(downloadId: String) {
        Log.i("DownloadService", "cancelDownload: $downloadId")
        downloadRepository.cancelDownload(downloadId)
    }

    // Function to cancel all downloads
    fun cancelAllDownloads() {
        Log.i("DownloadService", "cancelAllDownloads")
        downloadRepository.cancelAllDownloads()
    }

    // Function to restart a specific download
    @RequiresApi(Build.VERSION_CODES.O)
    fun restartDownload(downloadId: String){
        Log.i("DownloadService", "restartDownload: $downloadId")
        downloadRepository.restartDownload(downloadId)
    }

    private fun closeService() {
        Log.i("DownloadService", "closeService: ${activeDownloads} ${appVisibilityTracker.isAppVisible()}")
        if(!appVisibilityTracker.isAppVisible() && activeDownloads == 0){
            stopSelf()
        }
    }

    // Function to add a new download
    fun addDownload(url: String, fileName: String, fileExtension: String, folderUri : Uri,
                    networkPrefence : NetworkPreference, listener : MainFragment.AddDownloadListener) {
        Log.i("DownloadService", "addDownload: $url\n$fileName\n$fileExtension\n$folderUri\n$networkPrefence")
        dbHelper.insertDownloadItem(
            DownloadTaskPartial(
                fileName,
                url,
                folderUri,
                fileExtension,
                networkPrefence,
                DownloadStatus.PENDING,
                System.currentTimeMillis().toString()
            ), createDBListenerForAdding(listener)
        )
    }

    interface DBListener{
        fun changeSuccessful(item : DownloadTaskPartial)
        fun onError(message: String)
    }

    //Create a listener for adding a download
    private fun createDBListenerForAdding(listener: MainFragment.AddDownloadListener) : DBListener{
    return object : DBListener{
            override fun changeSuccessful(item : DownloadTaskPartial) {
                listener.successfullyAdded()
                downloadRepository.createNewDownload(item.fileName, item.url,
                    item.fileUri, item.fileExtension, item.networkPreference, item.id)
            }

            override fun onError(message: String) {
                listener.failedToAdd(message)
            }
        }
    }
}