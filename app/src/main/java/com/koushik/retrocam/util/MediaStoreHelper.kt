package com.koushik.retrocam.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.koushik.retrocam.data.model.MediaItem
import com.koushik.retrocam.data.model.SaveFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

object MediaStoreHelper {
    private const val TAG = "MediaStoreHelper"

    suspend fun saveImage(
        context: Context,
        bitmap: Bitmap,
        format: SaveFormat = SaveFormat.JPEG_HIGH,
        presetName: String = "photo"
    ): Uri? = withContext(Dispatchers.IO) {
        val cleanPreset = presetName.replace("[^a-zA-Z0-9]".toRegex(), "")
        val filename = "RetroCam_${cleanPreset}_${System.currentTimeMillis()}.${format.extension}"
        val mimeType = if (format == SaveFormat.PNG) "image/png" else "image/jpeg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RetroCam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null

        try {
            resolver.openOutputStream(uri)?.use { stream: OutputStream ->
                when (format) {
                    SaveFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    SaveFormat.JPEG_MAX -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    SaveFormat.JPEG_HIGH -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                stream.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, updateValues, null, null)
            }

            // Request media scan
            MediaScannerConnection.scanFile(
                context,
                arrayOf(uri.toString()),
                arrayOf(mimeType),
                null
            )

            Log.i(TAG, "Saved image to MediaStore: $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Error saving photo: ${e.message}", e)
            resolver.delete(uri, null, null)
            null
        }
    }

    suspend fun createVideoContentValues(presetName: String = "video"): Pair<ContentValues, String> {
        val cleanPreset = presetName.replace("[^a-zA-Z0-9]".toRegex(), "")
        val filename = "RetroCam_${cleanPreset}_${System.currentTimeMillis()}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RetroCam")
            }
        }
        return Pair(values, filename)
    }

    suspend fun queryGalleryMedia(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )

        // Query images
        try {
            val imageCursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            imageCursor?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                var count = 0
                while (cursor.moveToNext() && count < 200) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val dateAdded = cursor.getLong(dateCol)
                    val size = cursor.getLong(sizeCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    // Include only RetroCam photos
                    if (name.startsWith("RetroCam")) {
                        mediaList.add(
                            MediaItem(
                                id = id,
                                uri = uri,
                                displayName = name,
                                dateAdded = dateAdded,
                                isVideo = false,
                                sizeBytes = size,
                                width = width,
                                height = height
                            )
                        )
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying images: ${e.message}")
        }

        // Query videos
        try {
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
            )

            val videoCursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )

            videoCursor?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                var count = 0
                while (cursor.moveToNext() && count < 50) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val dateAdded = cursor.getLong(dateCol)
                    val duration = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    if (name.startsWith("RetroCam")) {
                        mediaList.add(
                            MediaItem(
                                id = id,
                                uri = uri,
                                displayName = name,
                                dateAdded = dateAdded,
                                isVideo = true,
                                durationMs = duration,
                                sizeBytes = size,
                                width = width,
                                height = height
                            )
                        )
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying videos: ${e.message}")
        }

        Log.i(TAG, "queryGalleryMedia returning ${mediaList.size} items")
        mediaList.sortedByDescending { it.dateAdded }
    }

    suspend fun deleteMedia(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting media: ${e.message}")
            false
        }
    }

    fun shareMedia(context: Context, uri: Uri, isVideo: Boolean) {
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share RetroCam")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

