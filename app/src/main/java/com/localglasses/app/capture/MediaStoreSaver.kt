package com.localglasses.app.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Writes captures into the shared gallery so they show up in Photos / Files. */
object MediaStoreSaver {

    private const val PHOTO_ALBUM = "Pictures/LocalGlasses"
    private const val VIDEO_ALBUM = "Movies/LocalGlasses"

    fun savePhoto(context: Context, bitmap: Bitmap, name: String): Result<Uri> = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, PHOTO_ALBUM)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore rejected the photo insert")

        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        } ?: error("Could not open output stream for $uri")

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    }

    /** Moves a finished recording out of the cache and into the gallery. */
    fun saveVideo(context: Context, file: File, name: String): Result<Uri> = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, VIDEO_ALBUM)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore rejected the video insert")

        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        } ?: error("Could not open output stream for $uri")

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        file.delete()
        uri
    }

    fun scratchFile(context: Context, name: String): File =
        File(context.cacheDir, "$name.mp4").also { it.delete() }

    @Suppress("unused")
    fun galleryRoot(): String = Environment.DIRECTORY_PICTURES
}
