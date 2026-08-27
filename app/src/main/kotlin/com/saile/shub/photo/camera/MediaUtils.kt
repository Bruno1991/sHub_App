package com.saile.shub.photo.camera

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast

data class MediaItem(
    val uri: Uri,
    val isVideo: Boolean,
    val duration: Long,
    val dateAdded: Long,
    val size: Long,
    val name: String
)

object MediaUtils {
    fun getLastTakenMediaUri(context: Context): Uri? {
        val all = getAllMedia(context, maxItems = 1)
        return all.firstOrNull()?.uri
    }

    fun getAllMedia(context: Context, maxItems: Int = Int.MAX_VALUE): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Video.VideoColumns.DURATION
        ).apply {
            add(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.MediaColumns.RELATIVE_PATH
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.MediaColumns.DATA
                }
            )
        }.toTypedArray()
        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "(${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?)"
            selectionArgs = arrayOf("Pictures/sHub/%", "Movies/sHub/%")
        } else {
            @Suppress("DEPRECATION")
            val dataColumn = MediaStore.MediaColumns.DATA
            selection = "$dataColumn LIKE ?"
            selectionArgs = arrayOf("%/sHub/%")
        }
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val durCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val mediaType = cursor.getInt(typeCol)
                    val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                    val duration = if (durCol >= 0) cursor.getLong(durCol) else 0L

                    val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val isImage = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    
                    if (isVideo || isImage) {
                        val contentUri = if (isVideo) {
                            android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        } else {
                            android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        }
                        list.add(MediaItem(contentUri, isVideo, duration, dateAdded, size, name))
                        if (list.size >= maxItems) break
                    }
                }
            }
        }
        return list
    }
    
    fun deleteMedia(context: Context, uris: List<Uri>): Boolean {
        var success = true
        for (uri in uris) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                success = false
            }
        }
        return success
    }
    
    fun shareMedia(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uris.first())
                type = "*/*"
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                type = "*/*"
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, "Compartilhar")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun openGallery(context: Context, lastUri: Uri?, isVideoMode: Boolean) {
        try {
            val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Nenhum aplicativo de galeria encontrado.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao abrir a galeria: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
