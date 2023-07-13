package com.example.filedownloader.utils

import com.example.filedownloader.models.DownloadTask
import java.util.concurrent.ConcurrentHashMap

object DownloadListenerHolder {
    private val downloadListeners: ConcurrentHashMap<String, DownloadTask.DownloadListener> = ConcurrentHashMap()

    fun setDownloadListener(taskId: String, downloadListener: DownloadTask.DownloadListener) {
        downloadListeners[taskId] = downloadListener
    }

    fun getDownloadListener(taskId: String): DownloadTask.DownloadListener? {
        return downloadListeners[taskId]
    }

    fun removeDownloadListener(taskId: String) {
        downloadListeners.remove(taskId)
    }
}