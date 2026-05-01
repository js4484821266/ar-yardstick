package com.example.aryardstick

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import com.google.ar.core.ArCoreApk

class MainActivity : Activity(), ArSessionManager.Listener {
    private lateinit var screen: ArYardstickScreen
    private lateinit var arSessionManager: ArSessionManager
    private val measurementEngine = MeasurementEngine()
    private val referenceDetector: ReferenceObjectDetector = NoOpReferenceObjectDetector()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isActivityResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        screen = ArYardstickScreen(this)
        setContentView(screen)
        arSessionManager = ArSessionManager(this, screen.surfaceView, this)
        bindControls()
        screen.render(measurementEngine)
        screen.showArAvailability("ARCore: checking support...")
        screen.showBlockingMessage(
            "Checking ARCore",
            "Checking whether this device can run AR measurement."
        )
        screen.showMessage(measurementEngine.promptForCurrentMode())
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        ensureCameraAndStartAr()
    }

    override fun onPause() {
        isActivityResumed = false
        arSessionManager.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        arSessionManager.onDestroy()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionUtils.CameraRequestCode &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            screen.hideBlockingMessage()
            ensureCameraAndStartAr()
        } else if (requestCode == PermissionUtils.CameraRequestCode) {
            screen.showBlockingMessage(
                "Camera Permission Missing",
                "AR Yardstick needs camera permission to run ARCore hit tests and measure world-space points."
            )
            screen.showMessage("Camera permission missing.", isError = true)
        }
    }

    override fun onFrame(snapshot: CameraFrameSnapshot) {
        screen.updateFrame(snapshot)
    }

    override fun onTapPoint(point: WorldPoint) {
        val update = measurementEngine.addPoint(point)
        screen.render(measurementEngine)
        screen.showMessage(update.message, update.isError)
    }

    override fun onTapFailure(message: String) {
        if (message.startsWith("ARCore failed during:") || message.startsWith("Failed during:")) {
            screen.showArAvailability("ARCore: supported, camera session failed", isError = true)
            screen.showBlockingMessage("AR Camera Failed", message, showRetry = true)
        }
        screen.showMessage(message, isError = true)
    }

    override fun onSessionMessage(message: String) {
        when (message) {
            "Checking ARCore install",
            "Creating AR session",
            "Configuring minimal AR session",
            "Starting AR camera session",
            "Resuming AR view" -> {
                screen.showArAvailability("ARCore: supported, camera session not started")
            }
            "Move the phone slowly until ARCore detects a plane." -> {
                screen.showArAvailability("ARCore: camera session running")
            }
        }
        screen.showMessage(message)
    }

    override fun onSessionUnsupported(message: String) {
        screen.showArAvailability("ARCore: unavailable", isError = true)
        screen.showBlockingMessage("ARCore Unavailable", message)
        screen.showMessage(message, isError = true)
    }

    private fun bindControls() {
        screen.onClear = {
            val update = measurementEngine.clear()
            screen.render(measurementEngine)
            screen.showMessage(update.message)
        }
        screen.onLineMode = {
            val update = measurementEngine.setMode(MeasurementMode.LINE)
            screen.render(measurementEngine)
            screen.showMessage(update.message)
        }
        screen.onCircleMode = {
            val update = measurementEngine.setMode(MeasurementMode.CIRCLE)
            screen.render(measurementEngine)
            screen.showMessage(update.message)
        }
        screen.onReferenceMode = {
            showReferenceObjectDialog()
        }
        screen.onCapture = {
            captureCurrentView()
        }
        screen.onRetryAr = {
            screen.hideBlockingMessage()
            screen.showArAvailability("ARCore: retrying camera session...")
            screen.showMessage("Retrying AR camera session...")
            arSessionManager.retry()
        }
        screen.onArTap = { x, y ->
            if (!PermissionUtils.hasCameraPermission(this)) {
                screen.showMessage("Camera permission missing.", isError = true)
                PermissionUtils.requestCameraPermission(this)
            } else {
                screen.showMessage("Running AR hit test...")
                arSessionManager.queueTap(x, y, measurementEngine.preferredPlaneId)
            }
        }
    }

    private fun ensureCameraAndStartAr() {
        if (!isActivityResumed) return

        val availability = arSessionManager.checkAvailability()
        screen.showArAvailability(arAvailabilityMessage(availability), isError = !availability.isSupported && !availability.isTransient)
        when {
            availability.isTransient -> {
                screen.showMessage("Checking ARCore availability...")
                mainHandler.postDelayed({ ensureCameraAndStartAr() }, 500L)
                return
            }
            availability == ArCoreApk.Availability.UNKNOWN_TIMED_OUT ||
                availability == ArCoreApk.Availability.UNKNOWN_ERROR -> {
                screen.showBlockingMessage(
                    "ARCore Check Failed",
                    "Could not determine ARCore support. Check Google Play Services for AR and try again."
                )
                screen.showMessage(arAvailabilityMessage(availability), isError = true)
                return
            }
            !availability.isSupported -> {
                screen.showBlockingMessage(
                    "ARCore Unavailable",
                    "This device does not support Google Play Services for AR. Measurement is not available."
                )
                screen.showMessage("ARCore unavailable on this device.", isError = true)
                return
            }
        }

        if (!PermissionUtils.hasCameraPermission(this)) {
            screen.showBlockingMessage(
                "ARCore Supported",
                "AR measurement is available on this device. Grant camera permission to start."
            )
            screen.showMessage("ARCore supported. Waiting for camera permission.")
            PermissionUtils.requestCameraPermission(this)
            return
        }

        screen.showArAvailability("ARCore: supported, camera session not started")
        screen.hideBlockingMessage()
        arSessionManager.onResume()
    }

    private fun arAvailabilityMessage(availability: ArCoreApk.Availability): String {
        return when (availability) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> "ARCore: supported and installed"
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> "ARCore: supported, AR service update needed"
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> "ARCore: supported, AR service install needed"
            ArCoreApk.Availability.UNKNOWN_CHECKING -> "ARCore: checking support..."
            ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> "ARCore: support check timed out"
            ArCoreApk.Availability.UNKNOWN_ERROR -> "ARCore: support check failed"
            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> "ARCore: not supported on this device"
        }
    }

    private fun showReferenceObjectDialog() {
        val edges = KnownReferenceEdge.supportedEdges()
        val labels = edges.map { it.label }.toTypedArray()
        val automaticStatus = if (referenceDetector.isAutomaticDetectionAvailable) {
            "Automatic detection is available."
        } else {
            "Automatic detection is not implemented. Manual edge calibration is available."
        }

        AlertDialog.Builder(this)
            .setTitle("Reference Object")
            .setMessage(automaticStatus)
            .setItems(labels) { _, which ->
                val update = measurementEngine.startReferenceCalibration(edges[which])
                screen.render(measurementEngine)
                screen.showMessage(update.message)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun captureCurrentView() {
        screen.showMessage("Saving capture...")
        ScreenshotSaver.captureAndSave(this, screen) { result ->
            result.fold(
                onSuccess = { capture ->
                    val message = capture.message
                    screen.showMessage(message)
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    val message = "Capture failed: ${error.message ?: error.javaClass.simpleName}"
                    screen.showMessage(message, isError = true)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}
