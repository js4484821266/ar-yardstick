package com.example.aryardstick

import kotlin.math.sqrt

enum class MeasurementMode(val label: String) {
    LINE("선 측정"),
    CIRCLE("원 측정"),
    REFERENCE("기준 물체")
}

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun length(): Float = sqrt(dot(this))

    fun normalized(): Vec3 {
        val len = length()
        return if (len > 0.000001f) this * (1f / len) else Zero
    }

    fun distanceTo(other: Vec3): Float = (this - other).length()

    companion object {
        val Zero = Vec3(0f, 0f, 0f)
    }
}

data class WorldPoint(
    val position: Vec3,
    val planeId: String? = null
)

data class CalibrationState(
    val correctionFactor: Float = 1f,
    val sourceLabel: String? = null
) {
    val isCalibrated: Boolean
        get() = sourceLabel != null
}

sealed class Measurement {
    abstract val points: List<WorldPoint>
    abstract val preferredPlaneId: String?

    data class Line(
        val start: WorldPoint,
        val end: WorldPoint
    ) : Measurement() {
        override val points: List<WorldPoint> = listOf(start, end)
        override val preferredPlaneId: String? = start.planeId ?: end.planeId
        val rawDistanceMeters: Float = start.position.distanceTo(end.position)
    }

    data class Circle(
        override val points: List<WorldPoint>,
        val center: Vec3,
        val radiusMeters: Float,
        val axisU: Vec3,
        val axisV: Vec3,
        val normal: Vec3
    ) : Measurement() {
        override val preferredPlaneId: String? = points.mapNotNull { it.planeId }.firstOrNull()
        val rawDiameterMeters: Float = radiusMeters * 2f
        val rawCircumferenceMeters: Float = (2.0 * Math.PI * radiusMeters).toFloat()
    }
}

data class CameraFrameSnapshot(
    val timestampNanos: Long,
    val viewMatrix: FloatArray,
    val projectionMatrix: FloatArray,
    val cameraPosition: Vec3,
    val cameraForward: Vec3,
    val horizontalFovDegrees: Float,
    val verticalFovDegrees: Float,
    val isTracking: Boolean,
    val trackedPlaneCount: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CameraFrameSnapshot) return false
        return timestampNanos == other.timestampNanos
    }

    override fun hashCode(): Int = timestampNanos.hashCode()
}
