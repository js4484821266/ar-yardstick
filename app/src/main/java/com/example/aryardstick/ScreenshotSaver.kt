package com.example.aryardstick

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.View
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CaptureResult(
    val uri: Uri?,
    val message: String
)

object ScreenshotSaver {
    fun captureAndSave(
        activity: Activity,
        sourceView: View,
        onComplete: (Result<CaptureResult>) -> Unit
    ) {
        if (sourceView.width <= 0 || sourceView.height <= 0) {
            onComplete(Result.failure(IllegalStateException("Capture failed: view is not ready.")))
            return
        }

        val location = IntArray(2)
        sourceView.getLocationInWindow(location)
        val srcRect = Rect(
            location[0],
            location[1],
            location[0] + sourceView.width,
            location[1] + sourceView.height
        )
        val bitmap = Bitmap.createBitmap(sourceView.width, sourceView.height, Bitmap.Config.ARGB_8888)
        val worker = HandlerThread("AR Yardstick Capture")
        worker.start()

        try {
            PixelCopy.request(activity.window, srcRect, bitmap, { copyResult ->
                try {
                    if (copyResult != PixelCopy.SUCCESS) {
                        throw IOException("Pixel copy failed with code $copyResult.")
                    }
                    val uri = saveBitmap(activity, bitmap)
                    Handler(Looper.getMainLooper()).post {
                        onComplete(Result.success(CaptureResult(uri, "Capture saved.")))
                    }
                } catch (error: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        onComplete(Result.failure(error))
                    }
                } finally {
                    bitmap.recycle()
                    worker.quitSafely()
                }
            }, Handler(worker.looper))
        } catch (error: Exception) {
            bitmap.recycle()
            worker.quitSafely()
            onComplete(Result.failure(error))
        }
    }

    private fun saveBitmap(activity: Activity, bitmap: Bitmap): Uri {
        val resolver = activity.contentResolver
        val name = "AR_Yardstick_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AR Yardstick")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore insert returned no Uri.")

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IOException("Bitmap compression failed.")
                }
            } ?: throw IOException("Could not open MediaStore output stream.")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
