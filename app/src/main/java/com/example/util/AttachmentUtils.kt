package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class AttachedData(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val base64Data: String?,
    val textContent: String?,
    val isImage: Boolean
)

object AttachmentUtils {

    fun getFileName(context: Context, uri: Uri): String {
        var name = "attachment"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        return name
    }

    fun processUri(context: Context, uri: Uri): AttachedData? {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val fileName = getFileName(context, uri)
            val isImage = mimeType.startsWith("image/")

            if (isImage) {
                val input: InputStream? = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(input)
                input?.close()

                if (bitmap != null) {
                    // Downscale if very large for network transfer
                    val scaled = if (bitmap.width > 1024 || bitmap.height > 1024) {
                        val ratio = Math.min(1024f / bitmap.width, 1024f / bitmap.height)
                        Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
                    } else bitmap

                    val outputStream = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                    val bytes = outputStream.toByteArray()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                    AttachedData(
                        uri = uri,
                        fileName = fileName,
                        mimeType = "image/jpeg",
                        base64Data = base64,
                        textContent = null,
                        isImage = true
                    )
                } else null
            } else {
                // Read text / code content
                val input = contentResolver.openInputStream(uri)
                val bytes = input?.readBytes() ?: ByteArray(0)
                input?.close()

                val text = try {
                    String(bytes, Charsets.UTF_8)
                } catch (_: Exception) {
                    null
                }
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                AttachedData(
                    uri = uri,
                    fileName = fileName,
                    mimeType = mimeType,
                    base64Data = base64,
                    textContent = text,
                    isImage = false
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
