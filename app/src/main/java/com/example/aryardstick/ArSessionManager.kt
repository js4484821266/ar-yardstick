package com.example.aryardstick

import android.app.Activity
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.Coordinates2d
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.Trackable
import com.google.ar.core.TrackingState
import com.google.ar.core.Config as ArConfig
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.SessionPausedException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.max

class ArSessionManager(
    private val activity: Activity,
    private val surfaceView: GLSurfaceView,
    private val listener: Listener
) {
    interface Listener {
        fun onFrame(snapshot: CameraFrameSnapshot)
        fun onTapPoint(point: WorldPoint)
        fun onTapFailure(message: String)
        fun onSessionMessage(message: String)
        fun onSessionUnsupported(message: String)
    }

    private val renderer = ArRenderer(activity, listener)
    private var session: Session? = null
    private var installRequested = false

    init {
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setRenderer(renderer)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    fun checkAvailability(): ArCoreApk.Availability {
        return ArCoreApk.getInstance().checkAvailability(activity)
    }

    fun onResume() {
        if (!PermissionUtils.hasCameraPermission(activity)) {
            listener.onTapFailure("Camera permission missing.")
            return
        }

        try {
            if (session == null) {
                when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        listener.onSessionMessage("Google Play Services for AR installation requested.")
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> Unit
                }

                session = Session(activity).also { newSession ->
                    val config = ArConfig(newSession).apply {
                        planeFindingMode = ArConfig.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        updateMode = ArConfig.UpdateMode.LATEST_CAMERA_IMAGE
                        focusMode = ArConfig.FocusMode.AUTO
                        if (newSession.isDepthModeSupported(ArConfig.DepthMode.AUTOMATIC)) {
                            depthMode = ArConfig.DepthMode.AUTOMATIC
                        }
                    }
                    newSession.configure(config)
                    renderer.setSession(newSession)
                }
            }

            session?.resume()
            surfaceView.onResume()
            listener.onSessionMessage("Move the phone slowly until ARCore detects a plane.")
        } catch (error: UnavailableArcoreNotInstalledException) {
            listener.onSessionUnsupported("ARCore is not installed on this device.")
        } catch (error: UnavailableUserDeclinedInstallationException) {
            listener.onSessionUnsupported("ARCore installation was declined.")
        } catch (error: UnavailableApkTooOldException) {
            listener.onSessionUnsupported("Google Play Services for AR is too old.")
        } catch (error: UnavailableSdkTooOldException) {
            listener.onSessionUnsupported("This app's ARCore SDK is too old for the installed service.")
        } catch (error: UnavailableDeviceNotCompatibleException) {
            listener.onSessionUnsupported("ARCore is unavailable on this device.")
        } catch (error: CameraNotAvailableException) {
            listener.onTapFailure("Camera is not available. Close other camera apps and try again.")
        } catch (error: Throwable) {
            listener.onTapFailure("Failed to start AR session: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun onPause() {
        surfaceView.onPause()
        session?.pause()
    }

    fun onDestroy() {
        renderer.setSession(null)
        session?.close()
        session = null
    }

    fun queueTap(x: Float, y: Float, preferredPlaneId: String?) {
        renderer.queueTap(TapRequest(x, y, preferredPlaneId))
    }

    private data class TapRequest(
        val x: Float,
        val y: Float,
        val preferredPlaneId: String?
    )

    private data class HitCandidate(
        val point: WorldPoint,
        val trackable: Trackable
    )

    private class ArRenderer(
        private val activity: Activity,
        private val listener: Listener
    ) : GLSurfaceView.Renderer {
        private val mainHandler = Handler(Looper.getMainLooper())
        private val pendingTaps = ConcurrentLinkedQueue<TapRequest>()
        private val backgroundRenderer = BackgroundRenderer()
        private var session: Session? = null
        private var viewportWidth = 1
        private var viewportHeight = 1
        private var cameraTextureSet = false

        fun setSession(newSession: Session?) {
            session = newSession
            cameraTextureSet = false
        }

        fun queueTap(tapRequest: TapRequest) {
            pendingTaps += tapRequest
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            backgroundRenderer.createOnGlThread()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewportWidth = max(1, width)
            viewportHeight = max(1, height)
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
            session?.setDisplayGeometry(displayRotation(), viewportWidth, viewportHeight)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            val activeSession = session ?: return

            try {
                if (!cameraTextureSet) {
                    activeSession.setCameraTextureNames(intArrayOf(backgroundRenderer.textureId))
                    cameraTextureSet = true
                }

                activeSession.setDisplayGeometry(displayRotation(), viewportWidth, viewportHeight)
                val frame = activeSession.update()
                backgroundRenderer.updateDisplayGeometry(frame)
                backgroundRenderer.draw(frame)

                val camera = frame.camera
                val snapshot = createSnapshot(activeSession, frame, camera)
                post { listener.onFrame(snapshot) }
                processTaps(frame, camera, snapshot)
            } catch (error: SessionPausedException) {
                // Session not yet resumed, skip this frame
            } catch (error: CameraNotAvailableException) {
                post { listener.onTapFailure("Camera is not available. Close other camera apps and resume AR Yardstick.") }
            } catch (error: Throwable) {
                post { listener.onTapFailure("AR frame failed: ${error.message ?: error.javaClass.simpleName}") }
            }
        }

        private fun processTaps(frame: Frame, camera: Camera, snapshot: CameraFrameSnapshot) {
            while (true) {
                val tap = pendingTaps.poll() ?: break
                if (camera.trackingState != TrackingState.TRACKING) {
                    post { listener.onTapFailure("Camera is not tracking yet. Move slowly and aim at a textured surface.") }
                    continue
                }

                val candidates = frame.hitTest(tap.x, tap.y).mapNotNull { hit -> hit.toCandidate() }
                val chosen = candidates.firstOrNull {
                    tap.preferredPlaneId != null && it.point.planeId == tap.preferredPlaneId
                } ?: candidates.firstOrNull()

                if (chosen == null) {
                    val message = if (snapshot.trackedPlaneCount == 0) {
                        "Plane not detected yet. Move slowly until a surface is found."
                    } else {
                        "Hit test failed. Tap a detected surface."
                    }
                    post { listener.onTapFailure(message) }
                    continue
                }

                if (!isInsideCameraView(snapshot, chosen.point.position)) {
                    post { listener.onTapFailure("Hit test failed outside the camera view. Try again.") }
                    continue
                }

                post { listener.onTapPoint(chosen.point) }
            }
        }

        private fun HitResult.toCandidate(): HitCandidate? {
            val hitTrackable = trackable
            val accepted = when (hitTrackable) {
                is Plane -> hitTrackable.trackingState == TrackingState.TRACKING && hitTrackable.isPoseInPolygon(hitPose)
                is Point -> hitTrackable.trackingState == TrackingState.TRACKING &&
                    hitTrackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
                is DepthPoint -> hitTrackable.trackingState == TrackingState.TRACKING
                else -> false
            }
            if (!accepted) return null

            val planeId = if (hitTrackable is Plane) "plane-${System.identityHashCode(hitTrackable)}" else null
            val pose = hitPose
            return HitCandidate(WorldPoint(Vec3(pose.tx(), pose.ty(), pose.tz()), planeId), hitTrackable)
        }

        private fun createSnapshot(session: Session, frame: Frame, camera: Camera): CameraFrameSnapshot {
            val view = FloatArray(16)
            val projection = FloatArray(16)
            camera.getViewMatrix(view, 0)
            camera.getProjectionMatrix(projection, 0, 0.01f, 100f)

            val pose = camera.pose
            val forward = FloatArray(3)
            pose.getTransformedAxis(2, -1f, forward, 0)
            val horizontalFov = (2.0 * atan(1.0 / projection[0].toDouble()) * 180.0 / PI).toFloat()
            val verticalFov = (2.0 * atan(1.0 / projection[5].toDouble()) * 180.0 / PI).toFloat()
            val trackedPlaneCount = session.getAllTrackables(Plane::class.java)
                .count { it.trackingState == TrackingState.TRACKING }

            return CameraFrameSnapshot(
                timestampNanos = frame.timestamp,
                viewMatrix = view,
                projectionMatrix = projection,
                cameraPosition = Vec3(pose.tx(), pose.ty(), pose.tz()),
                cameraForward = Vec3(forward[0], forward[1], forward[2]).normalized(),
                horizontalFovDegrees = horizontalFov,
                verticalFovDegrees = verticalFov,
                isTracking = camera.trackingState == TrackingState.TRACKING,
                trackedPlaneCount = trackedPlaneCount
            )
        }

        private fun isInsideCameraView(snapshot: CameraFrameSnapshot, point: Vec3): Boolean {
            val toPoint = (point - snapshot.cameraPosition).normalized()
            val dot = snapshot.cameraForward.dot(toPoint).coerceIn(-1f, 1f)
            val angleDegrees = (acos(dot.toDouble()) * 180.0 / PI).toFloat()
            val allowed = max(snapshot.horizontalFovDegrees, snapshot.verticalFovDegrees) * 0.5f + 8f
            return angleDegrees <= allowed
        }

        private fun displayRotation(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.rotation
            }
        }

        private fun post(action: () -> Unit) {
            mainHandler.post(action)
        }
    }

    private class BackgroundRenderer {
        var textureId: Int = 0
            private set

        private var program = 0
        private var positionAttribute = 0
        private var textureCoordinateAttribute = 0
        private var textureUniform = 0
        private val quadCoords: FloatBuffer = floatBufferOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
        )
        private val textureCoords: FloatBuffer = ByteBuffer.allocateDirect(8 * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        private var hasTransformedTextureCoordinates = false

        fun createOnGlThread() {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            positionAttribute = GLES20.glGetAttribLocation(program, "a_Position")
            textureCoordinateAttribute = GLES20.glGetAttribLocation(program, "a_TexCoord")
            textureUniform = GLES20.glGetUniformLocation(program, "u_Texture")
        }

        fun updateDisplayGeometry(frame: Frame) {
            if (!hasTransformedTextureCoordinates || frame.hasDisplayGeometryChanged()) {
                quadCoords.position(0)
                textureCoords.position(0)
                frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    quadCoords,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    textureCoords
                )
                hasTransformedTextureCoordinates = true
            }
        }

        fun draw(frame: Frame) {
            if (frame.timestamp == 0L || textureId == 0 || program == 0) return

            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthMask(false)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(textureUniform, 0)

            quadCoords.position(0)
            GLES20.glVertexAttribPointer(positionAttribute, 2, GLES20.GL_FLOAT, false, 0, quadCoords)
            GLES20.glEnableVertexAttribArray(positionAttribute)

            textureCoords.position(0)
            GLES20.glVertexAttribPointer(textureCoordinateAttribute, 2, GLES20.GL_FLOAT, false, 0, textureCoords)
            GLES20.glEnableVertexAttribArray(textureCoordinateAttribute)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionAttribute)
            GLES20.glDisableVertexAttribArray(textureCoordinateAttribute)
            GLES20.glDepthMask(true)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val linkedProgram = GLES20.glCreateProgram()
            GLES20.glAttachShader(linkedProgram, vertexShader)
            GLES20.glAttachShader(linkedProgram, fragmentShader)
            GLES20.glLinkProgram(linkedProgram)

            val status = IntArray(1)
            GLES20.glGetProgramiv(linkedProgram, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val message = GLES20.glGetProgramInfoLog(linkedProgram)
                GLES20.glDeleteProgram(linkedProgram)
                throw IllegalStateException("Could not link camera shader: $message")
            }
            return linkedProgram
        }

        private fun loadShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val message = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw IllegalStateException("Could not compile camera shader: $message")
            }
            return shader
        }

        companion object {
            private const val FLOAT_SIZE_BYTES = 4

            private fun floatBufferOf(vararg values: Float): FloatBuffer {
                val buffer = ByteBuffer.allocateDirect(values.size * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                buffer.put(values)
                buffer.position(0)
                return buffer
            }

            private const val VERTEX_SHADER = """
                attribute vec4 a_Position;
                attribute vec2 a_TexCoord;
                varying vec2 v_TexCoord;

                void main() {
                    gl_Position = a_Position;
                    v_TexCoord = a_TexCoord;
                }
            """

            private const val FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                uniform samplerExternalOES u_Texture;
                varying vec2 v_TexCoord;

                void main() {
                    gl_FragColor = texture2D(u_Texture, v_TexCoord);
                }
            """
        }
    }
}
