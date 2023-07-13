package com.example.filedownloader.ui.main

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filedownloader.models.DownloadModel
import com.example.filedownloader.models.DownloadStatus
import com.example.filedownloader.models.DownloadTask
import com.example.filedownloader.models.DownloadTaskPartial
import com.example.filedownloader.services.DownloadService
import com.example.filedownloader.utils.DownloadItemDatabaseHelper
import com.example.filedownloader.utils.FileDetails
import kotlinx.coroutines.*
import java.util.*
import kotlin.collections.HashMap

class MainViewModel : ViewModel() {

    //Database helper
    private var dbmsHelper : DownloadItemDatabaseHelper? = null

    //List of download task partials
    val downloadTaskPartials = MutableLiveData<List<DownloadTaskPartial>>(ArrayList())

    //Flag to check if download task partials are ready
    val downloadTaskPartialsReady = MutableLiveData(false)

    //List of showable downloads
    val showableDownloads = MutableLiveData<HashMap<String, DownloadModel>>(HashMap())

    //Flag to check if showable downloads are ready
    val downloadListReady = MutableLiveData(false)

    //Map of all downloads
    val downloads = HashMap<String, DownloadTask>()

    //Flag to check if downloads are ready
    val downloadsReady = MutableLiveData(false)

    //Queue of all ongoing downloads
    val downloadQueue: MutableLiveData<Queue<DownloadTask>> = MutableLiveData(LinkedList())

    //Queue of all paused downloads
    val pausedQueue: LiveData<Queue<DownloadTask>> = MutableLiveData(LinkedList())

    //Queue of all waiting downloads
    val waitingQueue: LiveData<Queue<DownloadTask>> = MutableLiveData(LinkedList())

    //List of all completed downloads
    val completedList: LiveData<ArrayList<DownloadTask>> = MutableLiveData(ArrayList())

    //File details
    val fileDetails = MutableLiveData<Triple<String?, String?, Long?>?>(null)
    var fileUrl = ""

    //Destination folder
    private val _destinationFolder = MutableLiveData<Uri>()
    val destinationFolder : LiveData<Uri>
        get() = _destinationFolder

    val destinationFolderSelected = MutableLiveData(false)

    fun setDestinationFolder(path : Uri){
        _destinationFolder.value = path
        destinationFolderSelected.value = true
    }

    //Getting data from database
    fun getDataService(context : Context) {
        if(dbmsHelper == null) {
            dbmsHelper = DownloadItemDatabaseHelper(context)
        }
        CoroutineScope(Dispatchers.IO).launch {
            val downloadsFromDb = dbmsHelper!!.getDownloadItems()
            downloadTaskPartials.postValue(downloadsFromDb)
        }
    }

    //Filtering data from database based on download status
    fun filterDownloads() {
        CoroutineScope(Dispatchers.IO).launch {
            val filteredDownloads = HashMap<String, DownloadModel>()
            completedList.value!!.clear()
            pausedQueue.value!!.clear()
            waitingQueue.value!!.clear()
            downloadQueue.value!!.clear()
            Log.i("MainViewModel", "downloads size = ${downloads.values.size}")
            for (download in downloads.values) {
                if(download.getDownloadStatus() == DownloadStatus.COMPLETED){
                    completedList.value!!.add(download)
                } else if(download.getDownloadStatus() == DownloadStatus.PAUSED){
                    pausedQueue.value!!.add(download)
                } else if(download.getDownloadStatus() == DownloadStatus.PENDING){
                    waitingQueue.value!!.add(download)
                } else if(download.getDownloadStatus() == DownloadStatus.DOWNLOADING){
                    downloadQueue.value!!.add(download)
                }
                filteredDownloads[download.id] = getDownloadModel(download)
            }
            showableDownloads.value!!.clear()
            showableDownloads.postValue(filteredDownloads)
            downloadsReady.postValue(true)
            downloadListReady.postValue(true)
        }
    }

    //Checking if any downloads are ongoing
    fun areDownloadsAvailable() : Boolean {
        synchronized(this){
            return downloadQueue.value!!.size > 0 || waitingQueue.value!!.size > 0
        }
    }

    //Getting downloadmodel from downloadtask
    private fun getDownloadModel(task : DownloadTask) : DownloadModel {
        return DownloadModel(task.id,
            task.fileName,
            task.url,
            task.startTime,
            task.fileUri,
            task.fileExtension,
            task.networkPreference,
            task.getDownloadStatus(),
            task.downloadProgressPercentage.value!!,
            task.downloadProgressBytes.value!!,
            task.downloadTotalBytes.value!!,
            task.failedMessage.value!!,
            task.outputFile.value
        )
    }

    //Getting file details from url
    fun getFileDetails(url : String) {
        viewModelScope.launch {
            fileUrl = url
            fileDetails.postValue(FileDetails.getFileDetailsFromUrl(url))
        }
    }

    fun afterFileDetailsUpdated(){
        fileDetails.postValue(null)
    }

    companion object {
        @Volatile
        private var instance: MainViewModel? = null

        fun getInstance(): MainViewModel {
            return instance ?: synchronized(this) {
                instance ?: MainViewModel().also { instance = it }
            }
        }
    }
}