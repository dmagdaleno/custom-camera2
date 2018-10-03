package dmagdaleno.customcamera2.ui.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import dmagdaleno.customcamera2.R
import dmagdaleno.customcamera2.constants.RequestCode
import kotlinx.android.synthetic.main.activity_custom_camera.*
import java.io.*

class CustomCameraActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CustomCameraActivity"
        private val ORIENTATIONS = SparseIntArray()

        init {
            ORIENTATIONS.append(Surface.ROTATION_0,    90)
            ORIENTATIONS.append(Surface.ROTATION_90,    0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

    private var cameraId: String? = null

    private var cameraDevice: CameraDevice? = null

    private val textureListener = object: TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureSizeChanged")
            //TODO not implemented
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
            Log.d(TAG, "onSurfaceTextureUpdated")
            //TODO not implemented
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            closeCamera()
            return false
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            openCamera()
        }

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_camera)

        texture.surfaceTextureListener = textureListener

        btn_capture.setOnClickListener { capture() }

    }

    private var backGroundHandler: Handler? = null
    private var backGroundThread: HandlerThread? = null

    private fun capture() {
        cameraDevice?.let { device ->
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cameraId ?: manager.cameraIdList.first()
            val characteristics = manager.getCameraCharacteristics(id)
            val sizes = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.JPEG)
            var width = 640
            var height = 480
            sizes?.firstOrNull()?.let { size ->
                width = size.width
                height = size.height
            }

            val reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
            val surfaces = listOf(
                    reader.surface,
                    Surface(texture.surfaceTexture)
            )

            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            builder.addTarget(reader.surface)
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

            val rotation = ORIENTATIONS.get(windowManager.defaultDisplay.rotation)
            builder.set(CaptureRequest.JPEG_ORIENTATION, rotation)

            val file = File("${Environment.getExternalStorageDirectory()}/pic.jpg")

            reader.setOnImageAvailableListener(object: ImageReader.OnImageAvailableListener{
                override fun onImageAvailable(reader: ImageReader?) {
                    var image: Image? = null
                    try {
                        image = reader?.acquireLatestImage()
                        image?.planes?.firstOrNull()?.buffer?.let { buffer ->
                            val bytes = ByteArray(buffer.capacity())
                            buffer.get(bytes)
                            save(bytes)
                        }
                    } catch (e: FileNotFoundException) {
                        Log.e(TAG, "File not found", e)
                    } catch (e: IOException) {
                        Log.e(TAG, "I/O Error", e)
                    } finally {
                        image?.close()
                    }

                }

                private fun save(bytes: ByteArray) {
                    var out: OutputStream? = null
                    try {
                        out = FileOutputStream(file)
                        out.write(bytes)
                    } finally {
                        out?.close()
                    }
                }
            }, backGroundHandler)

            val captureListener = object: CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    toast("Saved: $file")
                    createCameraPreview()
                }
            }

            device.createCaptureSession(surfaces,  object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession?) {
                    Log.e(TAG, "capture.onConfigureFailed")
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        session.capture(builder.build(), captureListener, backGroundHandler)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Failed on capture.createCaptureSession", e)
                    }
                }

            }, backGroundHandler)
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this@CustomCameraActivity, msg, Toast.LENGTH_SHORT).show()
    }

    private var imageDimension: Size? = null

    private var captureRequestBuilder: CaptureRequest.Builder? = null

    private var cameraCaptureSessions: CameraCaptureSession? = null

    private fun createCameraPreview() {
        try {
            val surfaceTexture = texture.surfaceTexture
            imageDimension?.let {
                image -> surfaceTexture.setDefaultBufferSize(image.width, image.height)
            }
            val surface = Surface(surfaceTexture)
            captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder?.addTarget(surface)

            cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession?) {
                    Log.e(TAG, "createCameraPreview.onConfigureFailed")
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    if(cameraDevice == null) return

                    cameraCaptureSessions = session
                    updatePreview()
                }

            }, backGroundHandler)

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed on createCameraPreview", e)
        }
    }

    private fun updatePreview() {
        if(cameraDevice == null) {
            Log.e(TAG, "Error on updatePreview")
            return
        }

        captureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        try {
            captureRequestBuilder?.let {
                cameraCaptureSessions?.setRepeatingRequest(it.build(), null, backGroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed on updatePreview", e)
        }
    }

    private var imageReader: ImageReader? = null

    private fun closeCamera() {
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode) {
            RequestCode.CAMERA -> {
                if(grantResults.first() == PackageManager.PERMISSION_DENIED) {
                    toast("This app needs camera permission!")
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if(texture.isAvailable)
            openCamera()
        else
            texture.surfaceTextureListener = textureListener
    }

    override fun onPause() {
        stopBackgroundThread()
        super.onPause()
    }

    private var stateCallback = object: CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice?) {
            cameraDevice?.close()
        }

        override fun onError(camera: CameraDevice?, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }

    }

    private fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cameraId ?: manager.cameraIdList.first()
        val characteristics = manager.getCameraCharacteristics(id)
        try {
            val sizes = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.JPEG)

            sizes?.firstOrNull()?.let { size ->
                imageDimension = size
            }

            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), RequestCode.CAMERA)
                return
            }

            manager.openCamera(id, stateCallback, null)

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed on openCamera", e)
        }
    }

    private fun startBackgroundThread() {
        backGroundThread = HandlerThread("Camera Background")
        backGroundThread?.let {
            it.start()
            backGroundHandler = Handler(it.looper)
        }
    }

    private fun stopBackgroundThread() {
        backGroundThread?.quitSafely()
        try {
            backGroundThread?.join()
            backGroundThread = null
            backGroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "stopBackgroundThread", e)
        }
    }
}
