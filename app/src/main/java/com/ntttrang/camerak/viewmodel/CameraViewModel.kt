package com.ntttrang.camerak.viewmodel

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.ContentUris
import androidx.core.content.ContextCompat
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.util.Size
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File

enum class AspectRatio(val displayName: String) {
    FULL("Full"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    RATIO_1_1("1:1")
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _previewSurfaceTexture = MutableStateFlow<SurfaceTexture?>(null)
    val previewSurfaceTexture = _previewSurfaceTexture.asStateFlow()

    private val _lastPhotoUri = MutableStateFlow<Uri?>(null)
    val lastPhotoUri = _lastPhotoUri.asStateFlow()

    private val _aspectRatio = MutableStateFlow(AspectRatio.FULL)
    val aspectRatio = _aspectRatio.asStateFlow()

    private val _previewSize = MutableStateFlow(Size(1920, 1080)) // Default
    val previewSize = _previewSize.asStateFlow()

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private val cameraManager by lazy {
        getApplication<Application>().getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private var frontCameraId: String? = null
    private var backCameraId: String? = null
    private var currentCameraId: String? = null

    private var textureViewWidth: Int = 0
    private var textureViewHeight: Int = 0

    init {
        findCameraIds()
        currentCameraId = backCameraId // Default to back camera
        loadLatestPhotoFromGallery()
    }

    private fun loadLatestPhotoFromGallery() {
        if (ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return // Don't load if permission is not granted
        }

        val resolver = getApplication<Application>().contentResolver
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { it ->
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                _lastPhotoUri.value = uri
            }
        }
    }

    private fun findCameraIds() {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> frontCameraId = cameraId
                CameraCharacteristics.LENS_FACING_BACK -> backCameraId = cameraId
            }
        }
    }

    fun getPreviewTransform(viewWidth: Int, viewHeight: Int, displayRotation: Int): Matrix {
        val matrix = Matrix()
        if (currentCameraId == null) return matrix

        val characteristics = cameraManager.getCameraCharacteristics(currentCameraId!!)
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val previewSize = _previewSize.value

        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())

        // The camera preview buffer has its own coordinate system.
        // For a sensor with 90 or 270 degrees orientation, its width and height are swapped relative to the device.
        val bufferRect = if (sensorOrientation == 90 || sensorOrientation == 270) {
            RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        } else {
            RectF(0f, 0f, previewSize.width.toFloat(), previewSize.height.toFloat())
        }

        val bufferCenterX = bufferRect.centerX()
        val bufferCenterY = bufferRect.centerY()

        val displayRotationDegrees = when (displayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        // Configure the matrix to scale and rotate.
        matrix.setRectToRect(bufferRect, viewRect, Matrix.ScaleToFit.CENTER)
        matrix.postRotate(
            ((sensorOrientation - displayRotationDegrees + 360) % 360).toFloat(),
            viewRect.centerX(),
            viewRect.centerY()
        )

        return matrix
    }

    fun cycleAspectRatio() {
        val nextIndex = (_aspectRatio.value.ordinal + 1) % AspectRatio.values().size
        _aspectRatio.value = AspectRatio.values()[nextIndex]
        // Reconfigure camera with new ratio
        if (cameraDevice != null) {
            createCameraPreviewSession()
        }
    }

    fun switchCamera() {
        val newCameraId = if (currentCameraId == backCameraId) {
            frontCameraId
        } else {
            backCameraId
        }

        if (newCameraId != null && newCameraId != currentCameraId) {
            closeCamera()
            currentCameraId = newCameraId
            openCamera()
        }
    }

    fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        _previewSurfaceTexture.value = surfaceTexture
        textureViewWidth = width
        textureViewHeight = height
        Log.d("CameraViewModel", "SurfaceTexture available: ${width}x${height}")
        openCamera()
    }

    private fun openCamera() {
        val cameraId = currentCameraId ?: return
        if (ActivityCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createCameraPreviewSession()
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                cameraDevice = null
            }
        }, null)
    }

    private fun createCameraPreviewSession() {
        val texture = previewSurfaceTexture.value ?: return
        val cameraId = currentCameraId ?: return
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val outputSizes = streamConfigurationMap?.getOutputSizes(SurfaceTexture::class.java)
        if (!outputSizes.isNullOrEmpty()) {
            val bestSize = getBestSizeForAspectRatio(outputSizes, _aspectRatio.value)
            _previewSize.value = bestSize
        }
        // Use the actual TextureView size for the buffer
        texture.setDefaultBufferSize(textureViewWidth, textureViewHeight)
        val surface = Surface(texture)

        if (ActivityCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        imageReader = ImageReader.newInstance(_previewSize.value.width, _previewSize.value.height, ImageFormat.JPEG, 1)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                // Crop the image to the selected aspect ratio
                val croppedBytes = cropImageToAspectRatio(bytes, _aspectRatio.value)

                // Write croppedBytes to a temp file, set EXIF orientation, then copy to gallery
                val tempFile = File.createTempFile("cropped_img", ".jpg", getApplication<Application>().cacheDir)
                tempFile.writeBytes(croppedBytes)
                val exif = ExifInterface(tempFile)
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                exif.saveAttributes()
                val correctedBytes = tempFile.readBytes()

                val resolver = getApplication<Application>().contentResolver
                val contentValues = ContentValues().apply {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_$timeStamp.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    }
                }

                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                imageUri?.let { uri ->
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(correctedBytes)
                    }
                    _lastPhotoUri.value = uri
                }
            } finally {
                image?.close()
            }
        }, null)

        val previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewRequestBuilder.addTarget(surface)

        cameraDevice?.createCaptureSession(
            listOf(surface, imageReader?.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    previewRequestBuilder.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    session.setRepeatingRequest(previewRequestBuilder.build(), null, null)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    // Handle failure
                }
            },
            null
        )
    }

    fun takePicture() {
        val captureBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureBuilder?.addTarget(imageReader!!.surface)
        captureBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        captureSession?.capture(captureBuilder!!.build(), null, null)
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    override fun onCleared() {
        super.onCleared()
        closeCamera()
        imageReader?.close()
    }

    private fun getBestSizeForAspectRatio(sizes: Array<Size>, aspectRatio: AspectRatio): Size {
        val targetRatio = when (aspectRatio) {
            AspectRatio.RATIO_16_9 -> 9.0 / 16.0
            AspectRatio.RATIO_4_3 -> 3.0 / 4.0
            AspectRatio.RATIO_1_1 -> 1.0
            else -> null
        }
        if (targetRatio == null) return sizes[0]
        return sizes.minByOrNull { size ->
            val ratio = size.width.toDouble() / size.height.toDouble()
            Math.abs(ratio - targetRatio)
        } ?: sizes[0]
    }

    private fun cropImageToAspectRatio(jpegBytes: ByteArray, aspectRatio: AspectRatio): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        val width = bitmap.width
        val height = bitmap.height
        val targetRatio = when (aspectRatio) {
            AspectRatio.RATIO_16_9 -> 9.0 / 16.0
            AspectRatio.RATIO_4_3 -> 3.0 / 4.0
            AspectRatio.RATIO_1_1 -> 1.0
            else -> width.toDouble() / height.toDouble()
        }
        val currentRatio = width.toDouble() / height.toDouble()
        var cropWidth = width
        var cropHeight = height
        if (currentRatio > targetRatio) {
            // Image is too wide
            cropWidth = (height * targetRatio).toInt()
        } else if (currentRatio < targetRatio) {
            // Image is too tall
            cropHeight = (width / targetRatio).toInt()
        }
        val x = (width - cropWidth) / 2
        val y = (height - cropHeight) / 2
        val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
        val outputStream = java.io.ByteArrayOutputStream()
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        return outputStream.toByteArray()
    }
} 