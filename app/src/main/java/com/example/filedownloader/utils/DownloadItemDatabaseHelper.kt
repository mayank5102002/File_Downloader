package com.example.filedownloader.utils

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import com.example.filedownloader.models.DownloadStatus
import com.example.filedownloader.models.DownloadTaskPartial
import com.example.filedownloader.models.NetworkPreference
import com.example.filedownloader.services.DownloadService

// Database helper class for storing download items
class DownloadItemDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // Companion object to implement singleton pattern and provide static methods
    companion object {
        private const val DATABASE_NAME                 = "download_items.db"
        private const val DATABASE_VERSION              = 1
        private const val TABLE_NAME                    = "download_items"
        private const val COLUMN_FILE_NAME              = "file_name"
        private const val COLUMN_URL                    = "url"
        private const val COLUMN_FILE_URI               = "file_uri"
        private const val COLUMN_FILE_EXTENSION         = "file_extension"
        private const val COLUMN_NETWORK_PREFERENCE     = "network_preference"
        private const val COLUMN_STATUS                 = "status"
        private const val COLUMN_ID                     = "id"

        private val lock = Any()

        private var instance: DownloadItemDatabaseHelper? = null

        fun getInstance(context: Context): DownloadItemDatabaseHelper {
            synchronized(lock) {
                if (instance == null) {
                    instance = DownloadItemDatabaseHelper(context)
                }
                return instance!!
            }
        }
    }

    // Create table
    override fun onCreate(db: SQLiteDatabase) {
        try {
            val createTableQuery = "CREATE TABLE $TABLE_NAME (" +
                    "$COLUMN_FILE_NAME TEXT," +
                    "$COLUMN_URL TEXT," +
                    "$COLUMN_FILE_URI TEXT," +
                    "$COLUMN_FILE_EXTENSION TEXT," +
                    "$COLUMN_NETWORK_PREFERENCE TEXT," +
                    "$COLUMN_STATUS TEXT," +
                    "$COLUMN_ID TEXT PRIMARY KEY)"

            db.execSQL(createTableQuery)
        } catch (e: Exception) {
            throw DatabaseException("Error creating table", e)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle database schema upgrades if needed
    }

    // Insert item into database
    fun insertDownloadItem(item: DownloadTaskPartial, listener : DownloadService.DBListener) {
        synchronized(lock) {
            try {
                val db = writableDatabase
                val values = ContentValues().apply {
                    put(COLUMN_FILE_NAME, item.fileName)
                    put(COLUMN_URL, item.url)
                    put(COLUMN_FILE_URI, item.fileUri.toString())
                    put(COLUMN_FILE_EXTENSION, item.fileExtension)
                    put(COLUMN_NETWORK_PREFERENCE, item.networkPreference.toString())
                    put(COLUMN_STATUS, item.status.toString())
                    put(COLUMN_ID, item.id)
                }

                db.insert(TABLE_NAME, null, values)
                db.close()
                listener.changeSuccessful(item)
            } catch (e: Exception) {
                listener.onError(e.message.toString())
                throw DatabaseException("Error inserting item", e)
            }
        }
    }

    // Get all items from database
    fun getDownloadItems(): List<DownloadTaskPartial> {
        synchronized(lock) {
            try {
                val items = mutableListOf<DownloadTaskPartial>()
                val db = readableDatabase
                val selectAllQuery = "SELECT * FROM $TABLE_NAME"
                val cursor: Cursor = db.rawQuery(selectAllQuery, null)

                if (cursor.moveToFirst()) {
                    val fileNameIndex = cursor.getColumnIndex(COLUMN_FILE_NAME)
                    val urlIndex = cursor.getColumnIndex(COLUMN_URL)
                    val fileUriIndex = cursor.getColumnIndex(COLUMN_FILE_URI)
                    val fileExtensionIndex = cursor.getColumnIndex(COLUMN_FILE_EXTENSION)
                    val networkPreferenceIndex = cursor.getColumnIndex(COLUMN_NETWORK_PREFERENCE)
                    val statusIndex = cursor.getColumnIndex(COLUMN_STATUS)
                    val idIndex = cursor.getColumnIndex(COLUMN_ID)

                    do {
                        val fileName = if (fileNameIndex >= 0) cursor.getString(fileNameIndex) else ""
                        val url = if (urlIndex >= 0) cursor.getString(urlIndex) else ""
                        val fileUriString = if (fileUriIndex >= 0) cursor.getString(fileUriIndex) else ""
                        val fileUri = Uri.parse(fileUriString)
                        val fileExtension = if (fileExtensionIndex >= 0) cursor.getString(fileExtensionIndex) else ""
                        val networkPreferenceString =
                            if (networkPreferenceIndex >= 0) cursor.getString(networkPreferenceIndex) else ""
                        val networkPreference = NetworkPreference.valueOf(networkPreferenceString)
                        val statusString = if (statusIndex >= 0) cursor.getString(statusIndex) else ""
                        val status = DownloadStatus.valueOf(statusString)
                        val id = if (idIndex >= 0) cursor.getString(idIndex) else ""

                        val item = DownloadTaskPartial(
                            fileName,
                            url,
                            fileUri,
                            fileExtension,
                            networkPreference,
                            status,
                            id
                        )
                        items.add(item)
                    } while (cursor.moveToNext())
                }

                cursor.close()
                db.close()

                return items
            } catch (e: Exception) {
                throw DatabaseException("Error retrieving items", e)
            }
        }
    }

    // Delete all items from database
    fun deleteAllDownloadItems() {
        synchronized(lock) {
            try {
                val db = writableDatabase
                db.delete(TABLE_NAME, null, null)
                db.close()
            } catch (e: Exception) {
                throw DatabaseException("Error deleting all items", e)
            }
        }
    }

    // Update item in database
    fun updateDownloadItem(item: DownloadTaskPartial) {
        synchronized(lock) {
            try {
                val db = writableDatabase
                val values = ContentValues().apply {
                    put(COLUMN_FILE_NAME, item.fileName)
                    put(COLUMN_URL, item.url)
                    put(COLUMN_FILE_URI, item.fileUri.toString())
                    put(COLUMN_FILE_EXTENSION, item.fileExtension)
                    put(COLUMN_NETWORK_PREFERENCE, item.networkPreference.toString())
                    put(COLUMN_STATUS, item.status.toString())
                    put(COLUMN_ID, item.id)
                }

                db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(item.id))
                db.close()
            } catch (e: Exception) {
                throw DatabaseException("Error updating item", e)
            }
        }
    }

    // Delete item from database
    fun deleteDownloadItem(item: DownloadTaskPartial) {
        synchronized(lock) {
            try {
                val db = writableDatabase
                db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(item.id))
                db.close()
            } catch (e: Exception) {
                throw DatabaseException("Error deleting item", e)
            }
        }
    }

    class DatabaseException(message: String, cause: Throwable?) : Exception(message, cause){
        constructor(message: String) : this(message, null)}
}