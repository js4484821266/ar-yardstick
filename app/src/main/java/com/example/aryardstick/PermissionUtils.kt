package com.example.aryardstick

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager

object PermissionUtils {
    const val CameraRequestCode = 40_031

    fun hasCameraPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    fun requestCameraPermission(activity: Activity) {
        activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), CameraRequestCode)
    }
}
