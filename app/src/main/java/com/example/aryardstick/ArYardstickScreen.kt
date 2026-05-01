package com.example.aryardstick

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.opengl.GLSurfaceView
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class ArYardstickScreen(context: Context) : FrameLayout(context) {
    val surfaceView = GLSurfaceView(context)
    val overlay = MeasurementOverlay(context)

    var onClear: (() -> Unit)? = null
    var onLineMode: (() -> Unit)? = null
    var onCircleMode: (() -> Unit)? = null
    var onReferenceMode: (() -> Unit)? = null
    var onCapture: (() -> Unit)? = null
    var onArTap: ((Float, Float) -> Unit)? = null

    private val arAvailabilityText = statusText(16f, Color.rgb(180, 255, 220))
    private val modeText = statusText(15f, Color.WHITE)
    private val measurementText = statusText(15f, Color.WHITE)
    private val calibrationText = statusText(13f, Color.rgb(225, 245, 245))
    private val trackingText = statusText(12f, Color.rgb(210, 225, 225))
    private val messageText = statusText(14f, Color.WHITE)
    private val blockingPanel = LinearLayout(context)
    private val blockingTitle = statusText(20f, Color.WHITE)
    private val blockingMessage = statusText(15f, Color.WHITE)
    private lateinit var topStatusPanel: LinearLayout
    private lateinit var bottomControlsScrollView: HorizontalScrollView

    init {
        setBackgroundColor(Color.BLACK)

        addView(surfaceView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        overlay.isClickable = false
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(createTopStatusPanel(), topLayoutParams())
        addView(createBottomControls(), bottomLayoutParams())
        addView(createBlockingPanel(), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        installSystemInsetsHandler()

        surfaceView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                onArTap?.invoke(event.x, event.y)
                true
            } else {
                true
            }
        }
    }

    fun showArAvailability(message: String, isError: Boolean = false) {
        arAvailabilityText.setTextColor(if (isError) Color.rgb(255, 210, 140) else Color.rgb(180, 255, 220))
        arAvailabilityText.text = message
    }

    fun render(engine: MeasurementEngine) {
        modeText.text = "Mode: ${engine.mode.label}"
        measurementText.text = engine.measurementLabel()
        calibrationText.text = engine.calibrationLabel()
        overlay.measurement = engine.currentMeasurement
        overlay.measurementText = engine.overlayLabel()
    }

    fun updateFrame(snapshot: CameraFrameSnapshot) {
        overlay.frameSnapshot = snapshot
        val trackingLabel = if (snapshot.isTracking) "tracking" else "not tracking"
        trackingText.text = String.format(
            Locale.US,
            "AR: %s - planes %d - FOV %.0fdeg x %.0fdeg",
            trackingLabel,
            snapshot.trackedPlaneCount,
            snapshot.horizontalFovDegrees,
            snapshot.verticalFovDegrees
        )
    }

    fun showMessage(message: String, isError: Boolean = false) {
        messageText.setTextColor(if (isError) Color.rgb(255, 210, 140) else Color.WHITE)
        messageText.text = message
    }

    fun showBlockingMessage(title: String, message: String) {
        blockingTitle.text = title
        blockingMessage.text = message
        blockingPanel.visibility = View.VISIBLE
    }

    fun hideBlockingMessage() {
        blockingPanel.visibility = View.GONE
    }

    private fun createTopStatusPanel(): View {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(170, 0, 0, 0))
                cornerRadius = 0f
            }
        }
        topStatusPanel = panel
        panel.addView(arAvailabilityText)
        panel.addView(modeText)
        panel.addView(measurementText)
        panel.addView(calibrationText)
        panel.addView(trackingText)
        panel.addView(messageText)
        return panel
    }

    private fun createBottomControls(): View {
        val scrollView = HorizontalScrollView(context).apply {
            isFillViewport = false
            setBackgroundColor(Color.argb(185, 0, 0, 0))
            setPadding(0, dp(8), 0, dp(8))
        }
        bottomControlsScrollView = scrollView
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
        }

        controls.addView(controlButton("Clear") { onClear?.invoke() })
        controls.addView(controlButton("Measure Line") { onLineMode?.invoke() })
        controls.addView(controlButton("Measure Circle") { onCircleMode?.invoke() })
        controls.addView(controlButton("Reference Object") { onReferenceMode?.invoke() })
        controls.addView(controlButton("Capture") { onCapture?.invoke() })
        scrollView.addView(controls)
        return scrollView
    }

    private fun createBlockingPanel(): View {
        return blockingPanel.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(18, 18, 18))
            blockingTitle.gravity = Gravity.CENTER
            blockingMessage.gravity = Gravity.CENTER
            blockingMessage.setLineSpacing(dp(2).toFloat(), 1.0f)
            addView(blockingTitle)
            addView(blockingMessage, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            })
        }
    }

    private fun installSystemInsetsHandler() {
        setOnApplyWindowInsetsListener { _, insets ->
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            topStatusPanel.setPadding(
                dp(14) + systemBars.left,
                dp(12) + systemBars.top,
                dp(14) + systemBars.right,
                dp(10)
            )
            bottomControlsScrollView.setPadding(
                systemBars.left,
                dp(8),
                systemBars.right,
                dp(8) + systemBars.bottom
            )
            blockingPanel.setPadding(
                dp(24) + systemBars.left,
                dp(24) + systemBars.top,
                dp(24) + systemBars.right,
                dp(24) + systemBars.bottom
            )
            insets
        }
        post { requestApplyInsets() }
    }

    private fun controlButton(label: String, action: () -> Unit): Button {
        return Button(context).apply {
            text = label
            isAllCaps = false
            minWidth = dp(96)
            minHeight = dp(44)
            setTextColor(Color.WHITE)
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(Color.rgb(26, 40, 42))
                setStroke(dp(1), Color.rgb(0, 175, 160))
                cornerRadius = dp(6).toFloat()
            }
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8)
            }
        }
    }

    private fun statusText(sizeSp: Float, color: Int): TextView {
        return TextView(context).apply {
            textSize = sizeSp
            setTextColor(color)
            includeFontPadding = true
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }
    }

    private fun topLayoutParams(): LayoutParams = LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.TOP
    }

    private fun bottomLayoutParams(): LayoutParams = LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.BOTTOM
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
