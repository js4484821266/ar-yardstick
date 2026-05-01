package com.example.aryardstick

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class MeasurementOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var measurement: Measurement? = null
        set(value) {
            field = value
            invalidate()
        }

    var measurementText: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var frameSnapshot: CameraFrameSnapshot? = null
        set(value) {
            field = value
            invalidate()
        }

    private var animatedStrokeAlpha = 0.4f
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 230, 210)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        textAlign = Paint.Align.CENTER
    }
    private val textFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }
    private val circlePath = Path()
    private val animator = ValueAnimator.ofFloat(0.4f, 0.7f).apply {
        duration = 300L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animatedStrokeAlpha = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setWillNotDraw(false)
        val metrics = resources.displayMetrics
        strokePaint.strokeWidth = (0.5f / 25.4f) * 160f * metrics.density
        val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PT, 10f, metrics)
        textFillPaint.textSize = textSizePx
        textHaloPaint.textSize = textSizePx
        textHaloPaint.strokeWidth = textSizePx * 0.22f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val activeMeasurement = measurement ?: return
        val snapshot = frameSnapshot ?: return
        strokePaint.alpha = (animatedStrokeAlpha * 255f).toInt()

        when (activeMeasurement) {
            is Measurement.Line -> drawLineMeasurement(canvas, snapshot, activeMeasurement)
            is Measurement.Circle -> drawCircleMeasurement(canvas, snapshot, activeMeasurement)
        }
    }

    private fun drawLineMeasurement(
        canvas: Canvas,
        snapshot: CameraFrameSnapshot,
        measurement: Measurement.Line
    ) {
        val start = project(snapshot, measurement.start.position) ?: return
        val end = project(snapshot, measurement.end.position) ?: return
        canvas.drawLine(start.x, start.y, end.x, end.y, strokePaint)

        if (measurementText.isBlank()) return

        val midX = (start.x + end.x) * 0.5f
        val midY = (start.y + end.y) * 0.5f
        var angle = (atan2(end.y - start.y, end.x - start.x) * 180f / PI.toFloat())
        if (angle > 90f || angle < -90f) angle += 180f

        canvas.save()
        canvas.rotate(angle, midX, midY)
        drawTextWithContrast(canvas, measurementText, midX, midY - dp(6f))
        canvas.restore()
    }

    private fun drawCircleMeasurement(
        canvas: Canvas,
        snapshot: CameraFrameSnapshot,
        measurement: Measurement.Circle
    ) {
        circlePath.reset()
        var started = false
        var visibleSamples = 0
        var topPoint: PointF? = null
        val sampleCount = 120

        for (index in 0..sampleCount) {
            val angle = (index.toFloat() / sampleCount.toFloat()) * (2f * PI.toFloat())
            val world = measurement.center +
                measurement.axisU * (cos(angle) * measurement.radiusMeters) +
                measurement.axisV * (sin(angle) * measurement.radiusMeters)
            val screen = project(snapshot, world)
            if (screen == null) {
                started = false
                continue
            }

            if (!started) {
                circlePath.moveTo(screen.x, screen.y)
                started = true
            } else {
                circlePath.lineTo(screen.x, screen.y)
            }
            visibleSamples += 1
            if (topPoint == null || screen.y < topPoint.y) {
                topPoint = screen
            }
        }

        if (visibleSamples < 3) return
        canvas.drawPath(circlePath, strokePaint)

        val labelPoint = topPoint ?: return
        if (measurementText.isNotBlank()) {
            drawTextWithContrast(canvas, measurementText, labelPoint.x, labelPoint.y - dp(8f))
        }
    }

    private fun drawTextWithContrast(canvas: Canvas, text: String, x: Float, baselineY: Float) {
        canvas.drawText(text, x, baselineY, textHaloPaint)
        canvas.drawText(text, x, baselineY, textFillPaint)
    }

    private fun project(snapshot: CameraFrameSnapshot, point: Vec3): PointF? {
        val input = floatArrayOf(point.x, point.y, point.z, 1f)
        val viewSpace = FloatArray(4)
        val clip = FloatArray(4)
        android.opengl.Matrix.multiplyMV(viewSpace, 0, snapshot.viewMatrix, 0, input, 0)
        android.opengl.Matrix.multiplyMV(clip, 0, snapshot.projectionMatrix, 0, viewSpace, 0)
        val w = clip[3]
        if (w <= 0.0001f) return null

        val ndcX = clip[0] / w
        val ndcY = clip[1] / w
        if (ndcX < -1.5f || ndcX > 1.5f || ndcY < -1.5f || ndcY > 1.5f) return null

        return PointF(
            (ndcX * 0.5f + 0.5f) * width,
            (1f - (ndcY * 0.5f + 0.5f)) * height
        )
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
