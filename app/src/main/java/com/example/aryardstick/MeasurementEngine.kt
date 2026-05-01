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
        return EngineUpdate("Measurement cleared.")
    }

    fun startReferenceCalibration(edge: KnownReferenceEdge): EngineUpdate {
        mode = MeasurementMode.REFERENCE
        selectedReferenceEdge = edge
        pendingPoints.clear()
        currentMeasurement = null
        return EngineUpdate("Reference mode: tap the two endpoints of ${edge.label}.")
    }

    fun addPoint(point: WorldPoint): EngineUpdate {
        return when (mode) {
            MeasurementMode.LINE -> addLinePoint(point)
            MeasurementMode.CIRCLE -> addCirclePoint(point)
            MeasurementMode.REFERENCE -> addReferencePoint(point)
        }
    }

    fun measurementLabel(): String = when (val measurement = currentMeasurement) {
        is Measurement.Line -> "Line: ${formatDistance(measurement.rawDistanceMeters)}"
        is Measurement.Circle -> {
            "Circle: D ${formatDistance(measurement.rawDiameterMeters)}, C ${formatDistance(measurement.rawCircumferenceMeters)}"
        }
        null -> "No measurement"
    }

    fun overlayLabel(): String = when (val measurement = currentMeasurement) {
        is Measurement.Line -> formatDistance(measurement.rawDistanceMeters)
        is Measurement.Circle -> "D ${formatDistance(measurement.rawDiameterMeters)}"
        null -> ""
    }

    fun calibrationLabel(): String {
        val source = calibrationState.sourceLabel ?: "manual calibration not set"
        return "Calibration: $source (${String.format(Locale.US, "%.4fx", calibrationState.correctionFactor)})"
    }

    fun promptForCurrentMode(): String = when (mode) {
        MeasurementMode.LINE -> when (pendingPoints.size) {
            0 -> "Line mode: tap the first point."
            else -> "Line mode: tap the second point."
        }
        MeasurementMode.CIRCLE -> "Circle mode: tap ${pendingPoints.size + 1} of 3 points."
        MeasurementMode.REFERENCE -> {
            val edge = selectedReferenceEdge
            if (edge == null) {
                "Reference mode: choose a reference object edge."
            } else {
                "Reference mode: tap endpoint ${pendingPoints.size + 1} of 2 for ${edge.label}."
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
            return EngineUpdate("Line mode: tap the second point.")
        }

        val measurement = Measurement.Line(pendingPoints[0], pendingPoints[1])
        currentMeasurement = measurement
        pendingPoints.clear()
        return EngineUpdate("Line measured: ${formatDistance(measurement.rawDistanceMeters)}.")
    }

    private fun addCirclePoint(point: WorldPoint): EngineUpdate {
        if (pendingPoints.size == 3) pendingPoints.clear()
        pendingPoints += point
        if (pendingPoints.size < 3) {
            return EngineUpdate("Circle mode: tap ${pendingPoints.size + 1} of 3 points.")
        }

        val measurement = calculateCircle(pendingPoints.toList())
        pendingPoints.clear()
        return if (measurement == null) {
            EngineUpdate("Circle points are nearly collinear. Choose three wider-spaced points.", isError = true)
        } else {
            currentMeasurement = measurement
            EngineUpdate("Circle estimated: diameter ${formatDistance(measurement.rawDiameterMeters)}.")
        }
    }

    private fun addReferencePoint(point: WorldPoint): EngineUpdate {
        val edge = selectedReferenceEdge
            ?: return EngineUpdate("Choose Credit Card or A4 before marking a reference edge.", isError = true)

        pendingPoints += point
        if (pendingPoints.size < 2) {
            return EngineUpdate("Reference mode: tap the second endpoint of ${edge.label}.")
        }

        val measuredLength = pendingPoints[0].position.distanceTo(pendingPoints[1].position)
        pendingPoints.clear()

        return try {
            val factor = manualCalibrator.calculateCorrectionFactor(edge.lengthMeters, measuredLength)
            calibrationState = CalibrationState(factor, edge.label)
            EngineUpdate("Calibration saved: ${edge.label}, correction ${String.format(Locale.US, "%.4fx", factor)}.")
        } catch (error: IllegalArgumentException) {
            EngineUpdate("Calibration failed: ${error.message}", isError = true)
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
