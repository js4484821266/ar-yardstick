package com.example.aryardstick

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class EngineUpdate(
    val message: String,
    val isError: Boolean = false
)

class MeasurementEngine(
    private val manualCalibrator: ManualReferenceObjectCalibrator = ManualReferenceObjectCalibrator()
) {
    var mode: MeasurementMode = MeasurementMode.LINE
        private set

    var currentMeasurement: Measurement? = null
        private set

    var calibrationState: CalibrationState = CalibrationState()
        private set

    private val pendingPoints = mutableListOf<WorldPoint>()
    private var selectedReferenceEdge: KnownReferenceEdge? = null

    val pendingPointCount: Int
        get() = pendingPoints.size

    val preferredPlaneId: String?
        get() = pendingPoints.firstOrNull()?.planeId ?: currentMeasurement?.preferredPlaneId

    fun setMode(newMode: MeasurementMode): EngineUpdate {
        mode = newMode
        pendingPoints.clear()
        if (newMode != MeasurementMode.REFERENCE) {
            selectedReferenceEdge = null
        }
        return EngineUpdate(promptForCurrentMode())
    }

    fun clear(): EngineUpdate {
        pendingPoints.clear()
        currentMeasurement = null
        selectedReferenceEdge = null
        return EngineUpdate("측정을 지웠습니다.")
    }

    fun startReferenceCalibration(edge: KnownReferenceEdge): EngineUpdate {
        mode = MeasurementMode.REFERENCE
        selectedReferenceEdge = edge
        pendingPoints.clear()
        currentMeasurement = null
        return EngineUpdate("기준 물체 모드: ${edge.label}의 양 끝점을 탭하세요.")
    }

    fun addPoint(point: WorldPoint): EngineUpdate {
        return when (mode) {
            MeasurementMode.LINE -> addLinePoint(point)
            MeasurementMode.CIRCLE -> addCirclePoint(point)
            MeasurementMode.REFERENCE -> addReferencePoint(point)
        }
    }

    fun measurementLabel(): String = when (val measurement = currentMeasurement) {
        is Measurement.Line -> "선: ${formatDistance(measurement.rawDistanceMeters)}"
        is Measurement.Circle -> {
            "원: 지름 ${formatDistance(measurement.rawDiameterMeters)}, 둘레 ${formatDistance(measurement.rawCircumferenceMeters)}"
        }
        null -> "측정 없음"
    }

    fun overlayLabel(): String = when (val measurement = currentMeasurement) {
        is Measurement.Line -> formatDistance(measurement.rawDistanceMeters)
        is Measurement.Circle -> "지름 ${formatDistance(measurement.rawDiameterMeters)}"
        null -> ""
    }

    fun calibrationLabel(): String {
        val source = calibrationState.sourceLabel ?: "수동 보정 안 됨"
        return "보정: $source (${String.format(Locale.US, "%.4fx", calibrationState.correctionFactor)})"
    }

    fun promptForCurrentMode(): String = when (mode) {
        MeasurementMode.LINE -> when (pendingPoints.size) {
            0 -> "선 측정 모드: 첫 번째 점을 탭하세요."
            else -> "선 측정 모드: 두 번째 점을 탭하세요."
        }
        MeasurementMode.CIRCLE -> "원 측정 모드: 3개 점 중 ${pendingPoints.size + 1}번째 점을 탭하세요."
        MeasurementMode.REFERENCE -> {
            val edge = selectedReferenceEdge
            if (edge == null) {
                "기준 물체 모드: 기준 물체의 변을 선택하세요."
            } else {
                "기준 물체 모드: ${edge.label}의 끝점 ${pendingPoints.size + 1}/2를 탭하세요."
            }
        }
    }

    fun formatDistance(rawMeters: Float): String {
        val meters = rawMeters * calibrationState.correctionFactor
        return when {
            meters < 0.01f -> String.format(Locale.US, "%.1f mm", meters * 1000f)
            meters < 1f -> String.format(Locale.US, "%.2f cm", meters * 100f)
            else -> String.format(Locale.US, "%.3f m", meters)
        }
    }

    private fun addLinePoint(point: WorldPoint): EngineUpdate {
        if (pendingPoints.size == 2) pendingPoints.clear()
        pendingPoints += point
        if (pendingPoints.size < 2) {
            return EngineUpdate("선 측정 모드: 두 번째 점을 탭하세요.")
        }

        val measurement = Measurement.Line(pendingPoints[0], pendingPoints[1])
        currentMeasurement = measurement
        pendingPoints.clear()
        return EngineUpdate("선을 측정했습니다: ${formatDistance(measurement.rawDistanceMeters)}.")
    }

    private fun addCirclePoint(point: WorldPoint): EngineUpdate {
        if (pendingPoints.size == 3) pendingPoints.clear()
        pendingPoints += point
        if (pendingPoints.size < 3) {
            return EngineUpdate("원 측정 모드: 3개 점 중 ${pendingPoints.size + 1}번째 점을 탭하세요.")
        }

        val measurement = calculateCircle(pendingPoints.toList())
        pendingPoints.clear()
        return if (measurement == null) {
            EngineUpdate("원 점들이 거의 한 줄에 있습니다. 더 넓게 떨어진 세 점을 다시 선택하세요.", isError = true)
        } else {
            currentMeasurement = measurement
            EngineUpdate("원을 추정했습니다: 지름 ${formatDistance(measurement.rawDiameterMeters)}.")
        }
    }

    private fun addReferencePoint(point: WorldPoint): EngineUpdate {
        val edge = selectedReferenceEdge
            ?: return EngineUpdate("기준 변을 표시하기 전에 신용카드 또는 A4를 선택하세요.", isError = true)

        pendingPoints += point
        if (pendingPoints.size < 2) {
            return EngineUpdate("기준 물체 모드: ${edge.label}의 두 번째 끝점을 탭하세요.")
        }

        val measuredLength = pendingPoints[0].position.distanceTo(pendingPoints[1].position)
        pendingPoints.clear()

        return try {
            val factor = manualCalibrator.calculateCorrectionFactor(edge.lengthMeters, measuredLength)
            calibrationState = CalibrationState(factor, edge.label)
            EngineUpdate("보정을 저장했습니다: ${edge.label}, 보정값 ${String.format(Locale.US, "%.4fx", factor)}.")
        } catch (error: IllegalArgumentException) {
            EngineUpdate("보정 실패: ${error.message}", isError = true)
        }
    }

    private fun calculateCircle(points: List<WorldPoint>): Measurement.Circle? {
        val p1 = points[0].position
        val p2 = points[1].position
        val p3 = points[2].position
        val a = p2 - p1
        val b = p3 - p1
        val c = p3 - p2
        val longestEdge = max(a.length(), max(b.length(), c.length()))
        if (longestEdge < 0.002f) return null

        val normal = a.cross(b)
        val areaScale = normal.length()
        if (areaScale < longestEdge * longestEdge * 0.01f) return null

        val axisU = a.normalized()
        val axisNormal = normal.normalized()
        val axisV = axisNormal.cross(axisU).normalized()
        val p2x = a.length().toDouble()
        val p3x = b.dot(axisU).toDouble()
        val p3y = b.dot(axisV).toDouble()
        if (abs(p3y) < 0.000001) return null

        val centerX = p2x / 2.0
        val centerY = ((p3x * p3x) + (p3y * p3y) - (p2x * p3x)) / (2.0 * p3y)
        val center = p1 + axisU * centerX.toFloat() + axisV * centerY.toFloat()
        val radius = center.distanceTo(p1)
        if (!radius.isFinite() || radius < 0.001f) return null

        return Measurement.Circle(
            points = points,
            center = center,
            radiusMeters = radius,
            axisU = axisU,
            axisV = axisV,
            normal = axisNormal
        )
    }
}
