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
        screen.showArAvailability("ARCore: 지원 여부 확인 중...")
        screen.showBlockingMessage(
            "ARCore 확인 중",
            "이 기기에서 AR 측정을 실행할 수 있는지 확인하고 있습니다."
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
                "카메라 권한 없음",
                "AR Yardstick은 ARCore 히트 테스트와 월드 좌표 측정을 위해 카메라 권한이 필요합니다."
            )
            screen.showMessage("카메라 권한이 없습니다.", isError = true)
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
        if (message.startsWith("ARCore 실패 단계:") || message.startsWith("실패 단계:")) {
            screen.showArAvailability("ARCore: 지원됨, 카메라 세션 실패", isError = true)
            screen.showBlockingMessage("AR 카메라 실패", message, showRetry = true)
        }
        screen.showMessage(message, isError = true)
    }

    override fun onSessionMessage(message: String) {
        when (message) {
            "ARCore 설치 확인 중",
            "AR 세션 생성 중",
            "최소 AR 세션 설정 중",
            "AR 카메라 세션 시작 중",
            "AR 화면 재개 중" -> {
                screen.showArAvailability("ARCore: 지원됨, 카메라 세션 시작 전")
            }
            "ARCore가 평면을 찾을 때까지 휴대폰을 천천히 움직이세요." -> {
                screen.showArAvailability("ARCore: 카메라 세션 실행 중")
            }
        }
        screen.showMessage(message)
    }

    override fun onSessionUnsupported(message: String) {
        screen.showArAvailability("ARCore: 사용할 수 없음", isError = true)
        screen.showBlockingMessage("ARCore 사용 불가", message)
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
            screen.showArAvailability("ARCore: 카메라 세션 재시도 중...")
            screen.showMessage("AR 카메라 세션을 다시 시도합니다...")
            arSessionManager.retry()
        }
        screen.onArTap = { x, y ->
            if (!PermissionUtils.hasCameraPermission(this)) {
                screen.showMessage("카메라 권한이 없습니다.", isError = true)
                PermissionUtils.requestCameraPermission(this)
            } else {
                screen.showMessage("AR 히트 테스트 실행 중...")
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
                screen.showMessage("ARCore 지원 여부 확인 중...")
                mainHandler.postDelayed({ ensureCameraAndStartAr() }, 500L)
                return
            }
            availability == ArCoreApk.Availability.UNKNOWN_TIMED_OUT ||
                availability == ArCoreApk.Availability.UNKNOWN_ERROR -> {
                screen.showBlockingMessage(
                    "ARCore 확인 실패",
                    "ARCore 지원 여부를 확인하지 못했습니다. Google Play Services for AR을 확인한 뒤 다시 시도하세요."
                )
                screen.showMessage(arAvailabilityMessage(availability), isError = true)
                return
            }
            !availability.isSupported -> {
                screen.showBlockingMessage(
                    "ARCore 사용 불가",
                    "이 기기는 Google Play Services for AR을 지원하지 않습니다. 측정을 사용할 수 없습니다."
                )
                screen.showMessage("이 기기에서는 ARCore를 사용할 수 없습니다.", isError = true)
                return
            }
        }

        if (!PermissionUtils.hasCameraPermission(this)) {
            screen.showBlockingMessage(
                "ARCore 지원됨",
                "이 기기에서 AR 측정을 사용할 수 있습니다. 시작하려면 카메라 권한을 허용하세요."
            )
            screen.showMessage("ARCore가 지원됩니다. 카메라 권한을 기다리는 중입니다.")
            PermissionUtils.requestCameraPermission(this)
            return
        }

        screen.showArAvailability("ARCore: 지원됨, 카메라 세션 시작 전")
        screen.hideBlockingMessage()
        arSessionManager.onResume()
    }

    private fun arAvailabilityMessage(availability: ArCoreApk.Availability): String {
        return when (availability) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> "ARCore: 지원됨, 설치됨"
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> "ARCore: 지원됨, AR 서비스 업데이트 필요"
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> "ARCore: 지원됨, AR 서비스 설치 필요"
            ArCoreApk.Availability.UNKNOWN_CHECKING -> "ARCore: 지원 여부 확인 중..."
            ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> "ARCore: 지원 확인 시간 초과"
            ArCoreApk.Availability.UNKNOWN_ERROR -> "ARCore: 지원 확인 실패"
            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> "ARCore: 이 기기에서 지원되지 않음"
        }
    }

    private fun showReferenceObjectDialog() {
        val edges = KnownReferenceEdge.supportedEdges()
        val labels = edges.map { it.label }.toTypedArray()
        val automaticStatus = if (referenceDetector.isAutomaticDetectionAvailable) {
            "자동 감지를 사용할 수 있습니다."
        } else {
            "자동 감지는 아직 구현되지 않았습니다. 수동 변 보정은 사용할 수 있습니다."
        }

        AlertDialog.Builder(this)
            .setTitle("기준 물체")
            .setMessage(automaticStatus)
            .setItems(labels) { _, which ->
                val update = measurementEngine.startReferenceCalibration(edges[which])
                screen.render(measurementEngine)
                screen.showMessage(update.message)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun captureCurrentView() {
        screen.showMessage("캡처 저장 중...")
        ScreenshotSaver.captureAndSave(this, screen) { result ->
            result.fold(
                onSuccess = { capture ->
                    val message = capture.message
                    screen.showMessage(message)
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    val message = "캡처 실패: ${error.message ?: error.javaClass.simpleName}"
                    screen.showMessage(message, isError = true)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}
