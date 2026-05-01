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
            onComplete(Result.failure(IllegalStateException("캡처 실패: 화면이 아직 준비되지 않았습니다.")))
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
                        throw IOException("픽셀 복사 실패 코드: $copyResult")
                    }
                    val uri = saveBitmap(activity, bitmap)
                    Handler(Looper.getMainLooper()).post {
                        onComplete(Result.success(CaptureResult(uri, "캡처를 저장했습니다.")))
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
            ?: throw IOException("MediaStore가 Uri를 반환하지 않았습니다.")

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IOException("비트맵 압축에 실패했습니다.")
                }
            } ?: throw IOException("MediaStore 출력 스트림을 열 수 없습니다.")

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
