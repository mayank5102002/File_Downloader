package com.example.filedownloader.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import okhttp3.*
import okio.buffer
import okio.sink
import java.io.*
import java.net.SocketException
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicBoolean


//Downloader class responsible for downloading the file
class Downloader {

    private var client = OkHttpClient()
    private val stateFileLock = Any()

    //Used to eheck if the download is paused
    var isPaused = AtomicBoolean(false)

    //Current call to be used to cancel the download
    private var currentCall: okhttp3.Call? = null

    //Function to check if the url supports range requests
    private fun checkRanges(url : String) : Boolean {
        val requestToCheckRanges = Request.Builder()
            .url(url)
            .build()

        val responseForRanges = client.newCall(requestToCheckRanges).execute()
        val acceptRanges = responseForRanges.header("Accept-Ranges") == "bytes"
        responseForRanges.close()
        return acceptRanges
    }

    // Function to format the file size
    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        val format = DecimalFormat("#,##0.#")
        return "${format.format(size / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }

    //Function to download the file
    fun download(url: String, fileUri: Uri, fileName: String, fileExtension: String, context: Context, listener: DownloadProgressListener) {
        val startTime = System.currentTimeMillis()
        val stateFile = File(context.filesDir, STATE_FILE_NAME + fileName)
        val outputFile = File(context.filesDir, "$fileName.$fileExtension")
        val startOffset = stateFile.readDownloadedBytes()

        //Creating requests based on whether the url supports range requests or not
        val acceptRanges = checkRanges(url)
        val request = if (acceptRanges) {
            Request.Builder()
                .url(url)
                .addHeader("Range", "bytes=$startOffset-")
                .build()
        } else {
            Request.Builder()
                .url(url)
                .build()
        }

        //Creating a call and enqueueing it
        currentCall = client.newCall(request)
        currentCall?.enqueue(object : Callback {

            // Variables to calculate speed and time remaining
            private var lastUpdateTime = System.currentTimeMillis()
            private var lastDownloadedBytes = 0L

            // Function to calculate the speed of the download
            private fun calculateSpeed(downloadedBytes: Long): String {
                val currentTime = System.currentTimeMillis()
                val timeElapsed = currentTime - lastUpdateTime
                val bytesDownloaded = downloadedBytes - lastDownloadedBytes
                val speed = if (timeElapsed > 0) bytesDownloaded.toDouble() / timeElapsed * 1000 else 0.0
                lastUpdateTime = currentTime
                lastDownloadedBytes = downloadedBytes
                return "${formatFileSize(speed.toLong())}/s"
            }

            // Function to calculate the estimated time remaining
            private fun calculateTimeRemaining(progress: Int, downloadedBytes: Long, totalBytes: Long): String {
                if (progress <= 0) return "--:--"
                val remainingBytes = totalBytes - downloadedBytes
                val timeRemaining = remainingBytes.toDouble() / downloadedBytes * (System.currentTimeMillis() - startTime)
                val secondsRemaining = (timeRemaining / 1000).toLong()
                val minutes = secondsRemaining / 60
                val seconds = secondsRemaining % 60
                return String.format("%02d:%02d", minutes, seconds)
            }

            //Function to stop the call
            override fun onFailure(call: Call, e: IOException) {
                if (isPaused.get()) {
                    stateFile.writeDownloadedBytes(outputFile.readDownloadedBytes())
                    return
                }
                listener.onDownloadError(e)
            }

            //Function to update the progress of the download
            override fun onResponse(call: Call, response: Response) {
                val responseBody: ResponseBody? = response.body
                try {
                    if (response.isSuccessful && responseBody != null) {
                        val totalBytes = responseBody.contentLength()
                        val outputStream = FileOutputStream(outputFile)
                        val bufferedSink = outputStream.sink().buffer()

                        //Writing the downloaded bytes to the file
                        var readBytes: Long
                        var downloadedBytes = 0L
                        if (acceptRanges) {
                            downloadedBytes = startOffset
                        }

                        //Writing the downloaded bytes to the file
                        val bufferedSource = responseBody.source()
                        while (bufferedSource.read(bufferedSink.buffer, SEGMENT_SIZE).also { readBytes = it } != -1L) {
                            synchronized(stateFileLock) {
                                if (isPaused.get()) {
                                    stateFile.writeDownloadedBytes(downloadedBytes)
                                    currentCall?.cancel()
                                    return
                                }
                            }

                            //Updating the progress of the download
                            downloadedBytes += readBytes
                            val progress = (downloadedBytes * 100 / totalBytes).toInt()
                            val speed = calculateSpeed(downloadedBytes)
                            val timeRemaining = calculateTimeRemaining(progress, downloadedBytes, totalBytes)
                            listener.onProgressUpdate(progress, downloadedBytes, totalBytes, speed, timeRemaining)
                            bufferedSink.emit()
                        }

                        bufferedSink.flush()
                        bufferedSink.close()
                        outputStream.close()

                        //Deleting the state file once the download is complete
                        stateFile.delete()
                        listener.onDownloadComplete(outputFile)
                    } else {
                        listener.onDownloadError(IOException("Download failed with response code: ${response.code}"))
                    }
                } catch (e: SocketException) {
                    if (!isPaused.get()) {
                        listener.onDownloadError(e)
                    }
                } catch (e: IOException) {
                    listener.onDownloadError(e)
                } finally {
                    responseBody?.close()
                    response.close()
                }
            }
        })
    }

    //Function to pause the download
    fun pauseDownload(context: Context, fileUri : Uri, fileName : String, fileExtension: String, listener: DownloadProgressListener) {
        try {
            synchronized(stateFileLock) {
                isPaused.set(true)
                Log.i("Downloader", "Pausing download")
                //Cancel the ongoing call
                stopCall()
                val stateFile = File(context.filesDir, STATE_FILE_NAME + fileName)
                val outputFile = File(context.filesDir, "$fileName.$fileExtension")

                //Writing the downloaded bytes to the state file
                stateFile.writeDownloadedBytes(outputFile.readDownloadedBytes())
            }
        } catch (e: IOException) {
            Log.e("Downloader", "Failed to pause download", e)
            listener.onDownloadError(e)
        }
    }

    //Function to resume the download
    fun resumeDownload(url: String, fileUri: Uri, fileName: String,
                       fileExtension: String, context: Context, listener: DownloadProgressListener) {
        synchronized(stateFileLock) {
            isPaused.set(false)
            Log.i("Downloader", "Resuming download")
            client = OkHttpClient()

            //Check if the state file exists
            val stateFile = File(context.filesDir, STATE_FILE_NAME + fileName)

            //If the state file does not exist, start downloading from the beginning
            if (!stateFile.exists()) {
                // No saved download state found, start downloading from the beginning
                listener.onProgressUpdate(0, 0, 0, "0MB/S", "00:00")
                download(url, fileUri, fileName, fileExtension, context, listener)
                return
            } else {
                // Saved download state found, resume downloading from the saved state
                // Cancel the ongoing call before creating a new OkHttpClient instance
                currentCall?.cancel()

                // Resume the download from the saved state using the new OkHttpClient instance
                val outputFile = File(context.filesDir, "$fileName.$fileExtension")
                listener.onProgressUpdate(0, outputFile.readDownloadedBytes(), 0, "0MB/S", "00:00") // Notify the current progress to the listener
                download(url, fileUri, fileName, fileExtension, context, listener)
            }
        }
    }

    //Function to cancel the download
    fun cancelDownload(context: Context, fileUri : Uri, fileName : String, fileExtension: String) {
        synchronized(stateFileLock) {
            Log.i("Downloader", "Download cancelled")
            isPaused.set(false)
            //Cancel the ongoing call
            stopCall()

            //Delete the state file and the output file
            val stateFile = File(context.filesDir, STATE_FILE_NAME+fileName)
            stateFile.delete()
            val outputFile = File(context.filesDir, "$fileName.$fileExtension")
            outputFile.delete()
        }
    }

    // Stop the download
    private fun stopCall() {
        synchronized(stateFileLock) {
            currentCall?.cancel()  // Cancel the ongoing call
            currentCall = null
            client.dispatcher.executorService.shutdownNow()
            client.dispatcher.cancelAll()
        }
    }

    //Function to read the downloaded bytes from the file
    private fun File.readDownloadedBytes(): Long {
        if (!exists()) {
            return 0L
        }
        return try {
            BufferedInputStream(FileInputStream(this)).use { inputStream ->
                DataInputStream(inputStream).use { dataInputStream ->
                    dataInputStream.readLong()
                }
            }
        } catch (e: IOException) {
            0L
        }
    }

    //Function to write the downloaded bytes to the file
    private fun File.writeDownloadedBytes(downloadedBytes: Long) {
        BufferedOutputStream(FileOutputStream(this)).use { outputStream ->
            DataOutputStream(outputStream).use { dataOutputStream ->
                dataOutputStream.writeLong(downloadedBytes)
            }
        }
    }

    interface DownloadProgressListener {
        fun onProgressUpdate(progress: Int, downloadedBytes: Long, totalBytes: Long, speed : String, timeRemaining : String)
        fun onDownloadComplete(file: File)
        fun onDownloadError(exception: Exception)
    }

    companion object{
        private const val STATE_FILE_NAME = "download_state"
        private const val SEGMENT_SIZE = 8192L
    }
}