package com.example.filedownloader.models

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.filedownloader.ui.main.MainViewModel
import com.example.filedownloader.utils.DownloadItemDatabaseHelper
import com.example.filedownloader.utils.NetworkUtil
import com.example.filedownloader.utils.SharedPreferencesUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

//DownloadRepository is responsible for creating and managing downloads.
class DownloadRepository(private val context: Context) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private lateinit var viewModel : MainViewModel
    private lateinit var dbHelper : DownloadItemDatabaseHelper

    //Initializes the repository and sets up the view model and database helper.
    fun init(){
        this.viewModel = MainViewModel.getInstance()
        this.dbHelper = DownloadItemDatabaseHelper.getInstance(context)
    }

    //Creates a new download and adds it to the download queue.
    fun createNewDownload(fileName : String, url : String, fileUri : Uri,
                          fileExtension : String,
                          networkPreference: NetworkPreference, id : String){
        val downloadTask = DownloadTask(fileName, url, context, fileUri, fileExtension,
            networkPreference, createDownloadListener(), DownloadStatus.PENDING, id)
        Log.i("DownloadRepository", "New Download Created for $fileName")
        addDownload(downloadTask)
    }

    //Processes the downloads in the download queue and waiting queue.
    fun processDownloads(){
        coroutineScope.launch {
            val maxParallelDownloads = SharedPreferencesUtil.getMaxParallelDownloads(context)
            val networkState = NetworkUtil.getNetworkState(context)

            //Process downloads in the download queue.
            viewModel.downloadQueue.value!!.forEach { downloadTask ->
                if(downloadTask.isPaused){

                    if (networkState == NetworkUtil.NetworkState.DISCONNECTED
                        || (networkState == NetworkUtil.NetworkState.CELLULAR
                                && downloadTask?.networkPreference == NetworkPreference.WIFI_ONLY)
                    ) {
                        viewModel.waitingQueue.value!!.add(downloadTask)
                        viewModel.downloadQueue.value!!.remove(downloadTask)
                        downloadTask.putToPending()
                    } else {
                        Log.i(
                            "DownloadRepository",
                            "Processing download for ${downloadTask.fileName}"
                        )
                        downloadTask.startDownload()
                    }
                }
            }

            //Process downloads in the waiting queue.
            while(viewModel.downloadQueue.value!!.size < maxParallelDownloads && viewModel.waitingQueue.value!!.size > 0){
                val downloadTask = viewModel.waitingQueue.value!!.poll()
                Log.i("DownloadRepository", "Processing download for ${downloadTask?.fileName}")
                if(networkState == NetworkUtil.NetworkState.DISCONNECTED || (networkState == NetworkUtil.NetworkState.CELLULAR && downloadTask?.networkPreference == NetworkPreference.WIFI_ONLY)){
                    continue
                }
                downloadTask?.let {
                    downloadTask.startDownload()
                    viewModel.downloadQueue.value!!.add(downloadTask)
                    postToDownloadQueue()
                }
            }
        }
    }

    //Puts all download in PENDING stage
    fun putAllToWaitingQueue(){
        viewModel.downloads.values.forEach {
            it.putToPending()
            viewModel.waitingQueue.value!!.add(it)
        }
    }

    //Posts the downloadqueue to the view model.
    private fun postToDownloadQueue(){
        viewModel.downloadQueue.postValue(viewModel.downloadQueue.value)
    }

    //Restarts a download.
    @RequiresApi(Build.VERSION_CODES.O)
    fun restartDownload(id: String) {
        coroutineScope.launch {
            val downloadTask = viewModel.downloads[id]
            downloadTask?.let {
                try {
                    Files.delete(Paths.get(downloadTask.outputFile.value!!.toString()))
                    println("File deleted successfully.")
                } catch (e: Exception) {
                    println("Failed to delete the file: ${e.message}")
                }
                addDownload(downloadTask)
            }
        }
    }

    //Add a new download
    private fun addDownload(downloadTask: DownloadTask){
        coroutineScope.launch {
            Log.i("DownloadRepository", "Adding download for ${downloadTask.fileName}")
            val maxParallelDownloads = SharedPreferencesUtil.getMaxParallelDownloads(context)
            val networkState = NetworkUtil.getNetworkState(context)

            //Check if download queue is full or network is not available.
            if(viewModel.downloadQueue.value!!.size >= maxParallelDownloads){
                Log.i("DownloadRepository", "Download queue full for ${downloadTask.fileName}")
                downloadTask.failedMessage.value = "Waiting for download"
                viewModel.downloads[downloadTask.id] = downloadTask
                viewModel.waitingQueue.value!!.add(downloadTask)
                addToShowableDownloads(downloadTask)
                return@launch
            }
            if(networkState == NetworkUtil.NetworkState.DISCONNECTED
                || (networkState == NetworkUtil.NetworkState.CELLULAR
                        && downloadTask.networkPreference == NetworkPreference.WIFI_ONLY)){
                Log.i("DownloadRepository", "No network for ${downloadTask.fileName}")
                downloadTask.failedMessage.value = "Waiting for download"
                viewModel.downloads[downloadTask.id] = downloadTask
                viewModel.waitingQueue.value!!.add(downloadTask)
                addToShowableDownloads(downloadTask)
                return@launch
            }

            //Start the download and put it in the download queue.
            Log.i("DownloadRepository", "Starting download for ${downloadTask.fileName}")
            viewModel.downloads[downloadTask.id] = downloadTask
            viewModel.downloadQueue.value!!.add(downloadTask)
            downloadTask.startDownload()
            addToShowableDownloads(downloadTask)
            postToDownloadQueue()
        }
    }

    //Puts a download in the showable downloads.
    private fun addToShowableDownloads(downloadTask: DownloadTask){
        postToShowable(downloadTask.id, DownloadModel(
            downloadTask.id,
            downloadTask.fileName,
            downloadTask.url,
            downloadTask.startTime,
            downloadTask.fileUri,
            downloadTask.fileExtension,
            downloadTask.networkPreference,
            downloadTask.status,
            downloadTask.downloadProgressPercentage.value!!,
            downloadTask.downloadProgressBytes.value!!,
            downloadTask.downloadTotalBytes.value!!,
            downloadTask.failedMessage.value!!,
            downloadTask.outputFile.value
        ))
    }

    //Process downloads in waiting queue when a download is complete.
    fun downloadComplete(){
        coroutineScope.launch {
            Log.i("DownloadRepository", "Download Complete")
            val maxParallelDownloads = SharedPreferencesUtil.getMaxParallelDownloads(context)
            val networkState = NetworkUtil.getNetworkState(context)

            while(viewModel.downloadQueue.value!!.size < maxParallelDownloads && viewModel.waitingQueue.value!!.size > 0){
                val downloadTask = viewModel.waitingQueue.value!!.poll()
                if(!(networkState == NetworkUtil.NetworkState.DISCONNECTED || (networkState == NetworkUtil.NetworkState.CELLULAR && downloadTask?.networkPreference == NetworkPreference.WIFI_ONLY))) {
                    downloadTask?.let {
                        downloadTask.resumeDownload()
                        viewModel.downloadQueue.value!!.add(downloadTask)
                        postToDownloadQueue()
                    }
                }
            }
        }
    }

    //Adds a download to downloads
    fun preExistingDownload(task : DownloadTaskPartial){
        Log.i("DownloadRepository", "Pre-existing download for ${task.fileName}")
        val downloadTask = DownloadTask(task.fileName, task.url, context, task.fileUri, task.fileExtension,
            task.networkPreference, createDownloadListener(), task.status, task.id)
        downloadTask.failedMessage.value = "Waiting for download"
        viewModel.downloads[task.id] = downloadTask
    }

    //Returns a download
    fun getDownload(id: String): DownloadTask?{
        return viewModel.downloads[id]
    }

    //Pause a download
    fun pauseDownload(id: String) {
        coroutineScope.launch {
            val downloadTask = viewModel.downloads[id]
            Log.i("DownloadRepository", "Pausing download for ${downloadTask?.fileName}")
            downloadTask?.let {
                downloadTask.pauseDownload()

                // Remove from download queue and add to paused queue
                if (downloadTask.getDownloadStatus() == DownloadStatus.DOWNLOADING) {
                    viewModel.downloadQueue.value!!.remove(downloadTask)
                } else if (downloadTask.getDownloadStatus() == DownloadStatus.PENDING) {
                    viewModel.waitingQueue.value!!.remove(downloadTask)
                }
                viewModel.pausedQueue.value!!.add(downloadTask)

                // Create a copy of the showableDownloads map
                val showableDownloadsCopy = viewModel.showableDownloads.value!!.toMutableMap()
                showableDownloadsCopy[downloadTask.id]?.let {
                    it.status = downloadTask.getDownloadStatus()
                    postToShowable(downloadTask.id, it)
                }

                postToDownloadQueue()
            }
        }
    }

    //Resume a download
    fun resumeDownload(id: String){
        coroutineScope.launch {
            val maxParallelDownloads = SharedPreferencesUtil.getMaxParallelDownloads(context)
            val networkState = NetworkUtil.getNetworkState(context)

            //Check if download queue is full or network is not available.
            val downloadTask = viewModel.downloads[id]
            if(viewModel.downloadQueue.value!!.size >= maxParallelDownloads ||
                networkState == NetworkUtil.NetworkState.DISCONNECTED ||
                (networkState == NetworkUtil.NetworkState.CELLULAR && downloadTask?.networkPreference == NetworkPreference.WIFI_ONLY)){
                viewModel.waitingQueue.value!!.add(downloadTask!!)
                downloadTask.putToPending()
            } else {
                downloadTask?.let {
                    downloadTask.resumeDownload()
                    viewModel.downloadQueue.value!!.add(downloadTask)
                    postToDownloadQueue()
                }
            }

            viewModel.pausedQueue.value!!.remove(downloadTask)
        }
    }

    //Cancel a download
    fun cancelDownload(id: String){
        coroutineScope.launch {
            val downloadTask = viewModel.downloads[id]
            Log.i("DownloadRepository", "Cancelling download for ${downloadTask?.fileName}")
            downloadTask?.let {

                //Cancel the download and remove it from the appropriate queue.
                downloadTask.cancelDownload()
                if(downloadTask.getDownloadStatus() == DownloadStatus.DOWNLOADING){
                    viewModel.downloadQueue.value!!.remove(downloadTask)
                } else if(downloadTask.getDownloadStatus() == DownloadStatus.PENDING){
                    viewModel.waitingQueue.value!!.remove(downloadTask)
                } else if(downloadTask.getDownloadStatus() == DownloadStatus.PAUSED){
                    viewModel.pausedQueue.value!!.remove(downloadTask)
                }
                viewModel.downloads.remove(downloadTask.id)
                viewModel.showableDownloads.value!!.remove(downloadTask.id)
                viewModel.showableDownloads.postValue(viewModel.showableDownloads.value)
                dbHelper.deleteDownloadItem(toDownloadTaskPartial(downloadTask))

                postToDownloadQueue()
            }
        }
    }

    //Cancel all downloads
    fun cancelAllDownloads(){
        coroutineScope.launch {
            Log.i("DownloadRepository", "Cancelling all downloads")
            viewModel.downloadQueue.value!!.forEach {
                it.cancelDownload()
            }
            viewModel.downloadQueue.value!!.clear()
            dbHelper.deleteAllDownloadItems()
            viewModel.showableDownloads.value!!.clear()
            viewModel.showableDownloads.postValue(viewModel.showableDownloads.value)

            postToDownloadQueue()
        }
    }

    //Pause all downloads
    fun pauseAllDownloads() {
        coroutineScope.launch {
            Log.i("DownloadRepository", "Pausing all downloads")
            val downloadQueueCopy = viewModel.downloadQueue.value!!.toMutableList() // Create a copy of the downloadQueue

            downloadQueueCopy.forEach { downloadTask ->
                downloadTask.pauseDownload()
                viewModel.pausedQueue.value!!.add(downloadTask)
                viewModel.downloadQueue.value!!.remove(downloadTask)
            }

            val waitingQueueCopy = viewModel.waitingQueue.value!!.toMutableList() // Create a copy of the waitingQueue

            waitingQueueCopy.forEach { downloadTask ->
                downloadTask.pauseDownload()
                viewModel.pausedQueue.value!!.add(downloadTask)
                viewModel.waitingQueue.value!!.remove(downloadTask)
            }

            postToDownloadQueue()
        }
    }

    //Resume all downloads
    fun resumeAllDownloads() {
        coroutineScope.launch {
            Log.i("DownloadRepository", "Resuming all downloads")
            val maxParallelDownloads = SharedPreferencesUtil.getMaxParallelDownloads(context)
            val networkState = NetworkUtil.getNetworkState(context)

            val pausedQueueCopy = viewModel.pausedQueue.value!!.toMutableList() // Create a copy of the pausedQueue

            pausedQueueCopy.forEach { downloadTask ->

                if(viewModel.downloadQueue.value!!.size >= maxParallelDownloads ||
                    networkState == NetworkUtil.NetworkState.DISCONNECTED ||
                    (networkState == NetworkUtil.NetworkState.CELLULAR && downloadTask?.networkPreference == NetworkPreference.WIFI_ONLY)){
                    viewModel.waitingQueue.value!!.add(downloadTask!!)
                    downloadTask.putToPending()
                } else {
                    downloadTask?.let {
                        downloadTask.resumeDownload()
                        viewModel.downloadQueue.value!!.add(downloadTask)
                        postToDownloadQueue()
                    }
                }

                viewModel.pausedQueue.value!!.remove(downloadTask)
            }
        }
    }

    //Post the showable downloads to the view model
    private fun postToShowable(taskId : String, it : DownloadModel){
        val temp = viewModel.showableDownloads.value!!
        temp[taskId] = it
        viewModel.showableDownloads.postValue(temp)
    }

    //Download listener for download tasks
    private fun createDownloadListener() : DownloadTask.DownloadListener{
        return object : DownloadTask.DownloadListener{

            //Update the download progress
            override fun onDownloadProgress(taskId : String) {

                //Update the progress of the download to the showable downloads map
                viewModel.showableDownloads.value!![taskId]?.let {
                    it.downloadProgressBytes = viewModel.downloads[taskId]?.downloadProgressBytes!!.value!!
                    it.downloadProgressPercentage = viewModel.downloads[taskId]?.downloadProgressPercentage!!.value!!
                    it.downloadTotalBytes = viewModel.downloads[taskId]?.downloadTotalBytes!!.value!!

                    postToShowable(taskId, it)
                }
//                Log.i("DownloadRepository", "Download progress for ${viewModel.downloads[taskId]?.fileName} is ${viewModel.downloads[taskId]?.downloadProgressPercentage!!.value!!}")
            }

            //Update the download status
            override fun onDownloadStatusChange(taskId : String) {
                val task = viewModel.downloads[taskId]
                if(task?.status == DownloadStatus.PENDING){
                    task.failedMessage.value = "Waiting for download"
                } else if(task?.status == DownloadStatus.PAUSED){
                    task.failedMessage.value = "Download Paused"
                }

                //Update the status of the download to the showable downloads map
                viewModel.showableDownloads.value!![taskId]?.let {
                    it.status = viewModel.downloads[taskId]?.getDownloadStatus()!!
                    postToShowable(taskId, it)
                }

                //Update the status of the download to the database
                dbHelper.updateDownloadItem(toDownloadTaskPartial(viewModel.downloads[taskId]!!))
                Log.i("DownloadRepository", "Download status for ${viewModel.downloads[taskId]?.fileName} is ${viewModel.downloads[taskId]?.getDownloadStatus()}")
            }

            //Pause the download
            override fun pauseThisDownload(taskId: String) {
                pauseDownload(taskId)
            }

            //Cancel the download
            override fun cancelThisDownload(taskId: String) {
                cancelDownload(taskId)
            }

            //On completion of the download
            override fun onDownloadComplete(taskId : String) {

                //Update the status of the download to the showable downloads map
                viewModel.showableDownloads.value!![taskId]?.let {
                    it.status = viewModel.downloads[taskId]?.getDownloadStatus()!!
                    it.outputFile = viewModel.downloads[taskId]?.outputFile!!.value
                    postToShowable(taskId, it)
                }

                //Remove the download from the download queue
                viewModel.downloadQueue.value!!.remove(viewModel.downloads[taskId])
                postToDownloadQueue()

                //Update the status of the download to the database
                dbHelper.updateDownloadItem(toDownloadTaskPartial(viewModel.downloads[taskId]!!))

                //Process next download in the queue
                downloadComplete()
                Log.i("DownloadRepository", "Download complete for ${viewModel.downloads[taskId]?.fileName}")
            }

            //On failure of the download
            override fun onDownloadFailed(taskId : String) {
                viewModel.downloads[taskId]?.failedMessage?.value = "Download Failed"

                //Update the status of the download to the showable downloads map
                viewModel.showableDownloads.value!![taskId]?.let {
                    it.status = viewModel.downloads[taskId]?.getDownloadStatus()!!
                    it.failedMessage = viewModel.downloads[taskId]?.failedMessage!!.value!!
                    postToShowable(taskId, it)
                }

                //Remove the download from the download queue
                viewModel.downloadQueue.value!!.remove(viewModel.downloads[taskId])
                postToDownloadQueue()

                //Remove the download from the database
                if(viewModel.downloads.containsKey(taskId)){
                    dbHelper.updateDownloadItem(toDownloadTaskPartial(viewModel.downloads[taskId]!!))
                }
                Log.i("DownloadRepository", "Download failed for ${viewModel.downloads[taskId]?.fileName}")
            }


        }
    }

    //Convert DownloadTask to DownloadTaskPartial
    private fun toDownloadTaskPartial(task : DownloadTask) : DownloadTaskPartial {
        return DownloadTaskPartial(
            task.fileName,
            task.url,
            task.fileUri,
            task.fileExtension,
            task.networkPreference,
            task.status,
            task.id
        )
    }

    //Companion object
    companion object {
        private var instance: DownloadRepository? = null

        fun getInstance(context: Context): DownloadRepository {
            synchronized(this){
                if (instance == null) {
                    instance = DownloadRepository(context)
                    instance!!.init()
                }
            }
            return instance!!
        }
    }

}