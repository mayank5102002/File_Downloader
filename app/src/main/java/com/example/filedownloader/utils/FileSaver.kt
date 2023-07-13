package com.example.filedownloader.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.IOException

// This class is used to save a file to a directory
class FileSaver {

    interface SaveFileCallback {
        fun onProgress(progress: Int)
        fun onSaveComplete(savedFileUri: Uri?)
        fun onError(e: Exception?)
    }

    // Save a file to a directory
    fun saveFileToDirectory(
        context: Context,
        folderUri: Uri,
        fileName: String,
        fileExtension : String,
        inputFile: File,
        saveFileCallback: SaveFileCallback
    ) {
        try {
            val documentFile = DocumentFile.fromTreeUri(context, folderUri)

            // Check if the selected directory exists
            documentFile?.let { directory ->
                // Check if a file with the same name already exists in the destination folder
                val newFileName = "$fileName.$fileExtension"
                // If a file with the same name already exists, append a counter to the file name
                fileExistsInDirectory(directory, newFileName)

                // Create a new file inside the selected directory
                val newFile = documentFile.createFile("", newFileName)
                    ?: throw IOException("Failed to create file")

                newFile.let { file ->
                    try {
                        // Open an OutputStream for the new file
                        val outputStream = context.contentResolver.openOutputStream(file.uri)

                        outputStream?.use { stream ->
                            // Calculate the total file size
                            val totalSize = inputFile.length()
                            var bytesWritten: Long = 0

                            // Write the file content to the OutputStream in chunks
                            val bufferSize = 1024 * 8 // 8 KB buffer size
                            val buffer = ByteArray(bufferSize)
                            var bytesRead: Int

                            val progressUpdateInterval = 2 // Update progress every 10% of progress
                            var lastProgress = 0

                            val inputStream = FileInputStream(inputFile)
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                stream.write(buffer, 0, bytesRead)
                                bytesWritten += bytesRead

                                // Update progress if necessary
                                val progress = ((bytesWritten * 100) / totalSize).toInt()
                                if (progress - lastProgress >= progressUpdateInterval) {
                                    lastProgress = progress
                                    saveFileCallback.onProgress(progress)
                                }
                            }

                            stream.flush()
                        }
                    } catch (e: IOException) {
                        // Handle any exceptions that occur during file saving
                        saveFileCallback.onError(e)
                    }
                }
                inputFile.delete()
                saveFileCallback.onSaveComplete(newFile.uri)
            }
        } catch (e: Exception) {
            // Handle any exceptions that may occur during the process
            e.printStackTrace()
            saveFileCallback.onError(e)
        }
    }

    // Check if a file with the same name already exists in the destination folder
    private fun fileExistsInDirectory(directory: DocumentFile, fileName: String){
        val files = directory.listFiles()
        for (file in files) {
            if (file.name == fileName) {
                file.delete()
            }
        }
    }

}