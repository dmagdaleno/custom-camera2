package dmagdaleno.customcamera2.ui.fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Log
import android.util.Size
import android.view.*
import dmagdaleno.customcamera2.R
import dmagdaleno.customcamera2.constants.RequestCode
import dmagdaleno.customcamera2.model.AutoFitTextureView
import dmagdaleno.customcamera2.model.Camera
import dmagdaleno.customcamera2.ui.dialog.ConfirmationDialog
import dmagdaleno.customcamera2.ui.dialog.ErrorDialog

class CustomCameraFragment : Fragment(), View.OnClickListener,
        ActivityCompat.OnRequestPermissionsResultCallback {

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    }

    /**
     * An [AutoFitTextureView] for camera preview.
     */
    private lateinit var textureView: AutoFitTextureView

    /**
     * The [android.util.Size] of camera preview.
     */
    private lateinit var previewSize: Size

    /**
     * Camera module
     */
    private var camera: Camera? = null

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_custom_camera, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById(R.id.textureView)
    }

    override fun onResume() {
        super.onResume()

        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        camera?.close()
        super.onPause()
    }

    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            ConfirmationDialog().show(childFragmentManager, FRAGMENT_DIALOG)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), RequestCode.CAMERA)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == RequestCode.CAMERA) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(childFragmentManager, FRAGMENT_DIALOG)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int) {
        try {

            val displaySize = Point()
            activity.windowManager.defaultDisplay.getSize(displaySize)

            previewSize = Size(width, height)

            textureView.setAspectRatio(previewSize.width, previewSize.height)

        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(childFragmentManager, FRAGMENT_DIALOG)
        }

    }

    private fun openCamera(width: Int, height: Int) {
        val permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
            return
        }
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            camera = Camera.initInstance(manager).apply {
                setUpCameraOutputs(width, height)
                configureTransform(width, height)
                this.open()
                val texture = textureView.surfaceTexture
                texture.setDefaultBufferSize(previewSize.width, previewSize.height)
                this.start(Surface(texture))
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }

    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        activity ?: return
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = Math.max(
                    viewHeight.toFloat() / previewSize.height,
                    viewWidth.toFloat() / previewSize.width)
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    override fun onClick(view: View) {
        Log.d(TAG, "onClick")
    }

    companion object {

        private const val FRAGMENT_DIALOG = "dialog"
        /**
         * Tag for the [Log].
         */
        private const val TAG = "CustomCameraFragment"

        @JvmStatic fun newInstance(): CustomCameraFragment = CustomCameraFragment()
    }
}