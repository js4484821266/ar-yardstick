package com.example.aryardstick

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var arSceneView: ArSceneView
    private lateinit var modeText: TextView
    private lateinit var switchModeButton: Button
    private lateinit var clearButton: Button
    private lateinit var instructionText: TextView
    private lateinit var measurementText: TextView

    private var isArbitraryMode = true
    private val anchorNodes = mutableListOf<AnchorNode>()
    private var lineNode: Node? = null
    private var standardReferenceNode: Node? = null

    private val CAMERA_PERMISSION_CODE = 100
    private val STANDARD_REFERENCE_LENGTH = 0.3f // 30 cm reference line

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        modeText = findViewById(R.id.modeText)
        switchModeButton = findViewById(R.id.switchModeButton)
        clearButton = findViewById(R.id.clearButton)
        instructionText = findViewById(R.id.instructionText)
        measurementText = findViewById(R.id.measurementText)

        // Check camera permission
        if (!checkCameraPermission()) {
            requestCameraPermission()
        } else {
            initializeAR()
        }

        // Set up button listeners
        switchModeButton.setOnClickListener {
            toggleMode()
        }

        clearButton.setOnClickListener {
            clearMeasurements()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeAR()
            } else {
                Toast.makeText(
                    this,
                    R.string.camera_permission_required,
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun initializeAR() {
        arSceneView = ArSceneView(this)
        findViewById<View>(R.id.arFragment).let { container ->
            if (container is android.widget.FrameLayout) {
                container.addView(arSceneView)
            }
        }

        arSceneView.scene.addOnUpdateListener {
            val frame = arSceneView.arFrame ?: return@addOnUpdateListener
            if (frame.camera.trackingState == TrackingState.TRACKING) {
                // AR is ready
            }
        }

        // Set up touch listener
        arSceneView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                handleTap(event)
            }
            true
        }
    }

    private fun toggleMode() {
        isArbitraryMode = !isArbitraryMode
        
        if (isArbitraryMode) {
            modeText.text = getString(R.string.mode_arbitrary)
            instructionText.text = getString(R.string.arbitrary_mode_instructions)
            standardReferenceNode?.let { 
                arSceneView.scene.removeChild(it)
                standardReferenceNode = null
            }
        } else {
            modeText.text = getString(R.string.mode_standard)
            instructionText.text = getString(R.string.standard_mode_instructions)
            createStandardReference()
        }
        
        clearMeasurements()
    }

    private fun handleTap(event: MotionEvent) {
        val frame = arSceneView.arFrame ?: return
        
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            return
        }

        val hits = frame.hitTest(event.x, event.y)
        
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                if (isArbitraryMode) {
                    handleArbitraryModeTap(hit)
                } else {
                    handleStandardModeTap(hit)
                }
                break
            }
        }
    }

    private fun handleArbitraryModeTap(hit: HitResult) {
        val anchor = hit.createAnchor()
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arSceneView.scene)
        
        // Create a small sphere at the tap point
        MaterialFactory.makeOpaqueWithColor(this, com.google.ar.sceneform.rendering.Color(0f, 1f, 0f))
            .thenAccept { material ->
                val sphere = ShapeFactory.makeSphere(0.01f, Vector3.zero(), material)
                val node = Node()
                node.renderable = sphere
                node.setParent(anchorNode)
            }
        
        anchorNodes.add(anchorNode)
        
        // If we have 2 or more points, calculate distance
        if (anchorNodes.size >= 2) {
            val lastTwo = anchorNodes.takeLast(2)
            val distance = calculateDistance(
                lastTwo[0].worldPosition,
                lastTwo[1].worldPosition
            )
            showMeasurement(distance)
            drawLine(lastTwo[0].worldPosition, lastTwo[1].worldPosition)
        }
    }

    private fun handleStandardModeTap(hit: HitResult) {
        val anchor = hit.createAnchor()
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arSceneView.scene)
        
        // Create a measurement point
        MaterialFactory.makeOpaqueWithColor(this, com.google.ar.sceneform.rendering.Color(1f, 1f, 0f))
            .thenAccept { material ->
                val sphere = ShapeFactory.makeSphere(0.01f, Vector3.zero(), material)
                val node = Node()
                node.renderable = sphere
                node.setParent(anchorNode)
            }
        
        anchorNodes.add(anchorNode)
        
        // Calculate distance between consecutive points (using reference line for estimation)
        if (anchorNodes.size >= 2) {
            val lastTwo = anchorNodes.takeLast(2)
            val distance = calculateDistance(
                lastTwo[0].worldPosition,
                lastTwo[1].worldPosition
            )
            showMeasurement(distance)
            drawLine(lastTwo[0].worldPosition, lastTwo[1].worldPosition)
        }
    }

    private fun createStandardReference() {
        val frame = arSceneView.arFrame ?: return
        
        // Create a horizontal reference line in front of the camera
        val cameraPos = frame.camera.pose
        val forward = floatArrayOf(0f, 0f, -1.5f) // 1.5 meters in front
        cameraPos.transformPoint(forward)
        
        MaterialFactory.makeOpaqueWithColor(this, com.google.ar.sceneform.rendering.Color(1f, 0f, 0f))
            .thenAccept { material ->
                // Create a cylinder for the reference line
                val line = ShapeFactory.makeCylinder(
                    0.005f,
                    STANDARD_REFERENCE_LENGTH,
                    Vector3.zero(),
                    material
                )
                
                standardReferenceNode = Node().apply {
                    renderable = line
                    worldPosition = Vector3(forward[0], forward[1], forward[2])
                    // Rotate to be horizontal
                    worldRotation = com.google.ar.sceneform.math.Quaternion.axisAngle(
                        Vector3(0f, 0f, 1f),
                        90f
                    )
                    setParent(arSceneView.scene)
                }
            }
    }

    private fun drawLine(start: Vector3, end: Vector3) {
        val difference = Vector3.subtract(end, start)
        val directionNormalized = difference.normalized()
        val distance = difference.length()
        
        MaterialFactory.makeOpaqueWithColor(this, com.google.ar.sceneform.rendering.Color(0f, 1f, 0f))
            .thenAccept { material ->
                val line = ShapeFactory.makeCylinder(
                    0.003f,
                    distance,
                    Vector3.zero(),
                    material
                )
                
                lineNode?.let { arSceneView.scene.removeChild(it) }
                
                lineNode = Node().apply {
                    renderable = line
                    worldPosition = Vector3.add(start, difference.scaled(0.5f))
                    worldRotation = com.google.ar.sceneform.math.Quaternion.lookRotation(
                        directionNormalized,
                        Vector3.up()
                    )
                    setParent(arSceneView.scene)
                }
            }
    }

    private fun calculateDistance(start: Vector3, end: Vector3): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun showMeasurement(distanceMeters: Float) {
        val distanceCm = distanceMeters * 100
        measurementText.text = getString(R.string.distance_format, distanceCm)
        measurementText.visibility = View.VISIBLE
    }

    private fun clearMeasurements() {
        anchorNodes.forEach { it.anchor?.detach() }
        anchorNodes.clear()
        lineNode?.let { arSceneView.scene.removeChild(it) }
        lineNode = null
        measurementText.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (checkCameraPermission() && ::arSceneView.isInitialized) {
            try {
                arSceneView.resume()
            } catch (e: Exception) {
                Toast.makeText(this, R.string.arcore_not_supported, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::arSceneView.isInitialized) {
            arSceneView.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::arSceneView.isInitialized) {
            arSceneView.destroy()
        }
    }
}
