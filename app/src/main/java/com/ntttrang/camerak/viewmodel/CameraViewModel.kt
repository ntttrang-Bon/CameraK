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
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.exifinterface.media.ExifInterface
import java.io.File
import android.content.Intent

enum class AspectRatio(val displayName: String) {
    FULL("Full"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    RATIO_1_1("1:1")
}

enum class CameraMode(val displayName: String, val icon: String) {
    PHOTO("Photo", "📷"),
    VIDEO("Video", "🎥")
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _previewSurfaceTexture = MutableStateFlow<SurfaceTexture?>(null)
    val previewSurfaceTexture = _previewSurfaceTexture.asStateFlow()

    private val _lastPhotoUri = MutableStateFlow<Uri?>(null)
    val lastPhotoUri = _lastPhotoUri.asStateFlow()

    private val _lastVideoUri = MutableStateFlow<Uri?>(null)
    val lastVideoUri = _lastVideoUri.asStateFlow()

    // Combined latest media from gallery (regardless of type)
    private val _latestGalleryMedia = MutableStateFlow<Uri?>(null)
    val latestGalleryMedia = _latestGalleryMedia.asStateFlow()

    private val _aspectRatio = MutableStateFlow(AspectRatio.FULL)
    val aspectRatio = _aspectRatio.asStateFlow()

    private val _isRatioSelectorExpanded = MutableStateFlow(false)
    val isRatioSelectorExpanded = _isRatioSelectorExpanded.asStateFlow()

    private val _cameraMode = MutableStateFlow(CameraMode.PHOTO)
    val cameraMode = _cameraMode.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _recordingTime = MutableStateFlow(0L)
    val recordingTime = _recordingTime.asStateFlow()

    private val _previewSize = MutableStateFlow(Size(1920, 1080)) // Default
    val previewSize = _previewSize.asStateFlow()

    private val _flashEnabled = MutableStateFlow(false)
    val flashEnabled = _flashEnabled.asStateFlow()

    private val _flashSupported = MutableStateFlow(false)
    val flashSupported = _flashSupported.asStateFlow()

    private val _cameraReady = MutableStateFlow(false)
    val cameraReady = _cameraReady.asStateFlow()

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingSurface: Surface? = null

    private val cameraManager by lazy {
        getApplication<Application>().getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private var frontCameraId: String? = null
    private var backCameraId: String? = null
    private var currentCameraId: String? = null

    private var textureViewWidth: Int = 0
    private var textureViewHeight: Int = 0

    private var lastCaptureDisplayRotation: Int = 0
    private var recordingStartTime: Long = 0
    private var lastRecordingDisplayRotation: Int = 0

    init {
        findCameraIds()
        // Try to find a camera with flash first, otherwise use back camera
        val cameraWithFlash = findCameraWithFlash()
        currentCameraId = cameraWithFlash ?: backCameraId
        Log.d("CameraViewModel", "Initial camera ID: $currentCameraId")
        loadLatestPhotoFromGallery()
        loadLatestVideoFromGallery()
        loadLatestGalleryMedia()
    }

    fun setCameraMode(mode: CameraMode) {
        if (_cameraMode.value != mode) {
            _cameraMode.value = mode
            // Stop recording if switching from video mode
            if (_isRecording.value) {
                stopRecording()
            }
            // Reconfigure camera for new mode
            if (cameraDevice != null) {
                createCameraPreviewSession()
            }
        }
    }

    fun startRecording(displayRotation: Int) {
        if (_cameraMode.value != CameraMode.VIDEO || _isRecording.value) {
            return
        }

        try {
            lastRecordingDisplayRotation = displayRotation
            setupMediaRecorder(displayRotation)
            createRecordingSession()
            _isRecording.value = true
            recordingStartTime = System.currentTimeMillis()
            startRecordingTimer()
            Log.d("CameraViewModel", "Recording started")
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Error starting recording", e)
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) {
            return
        }

        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            recordingSurface?.release()
            recordingSurface = null
            
            _isRecording.value = false
            _recordingTime.value = 0L
            
            // Refresh gallery thumbnails after recording is complete
            refreshGalleryThumbnails()
            // Also update the latest gallery media
            loadLatestGalleryMedia()
            
            // Restore preview session
            createCameraPreviewSession()
            Log.d("CameraViewModel", "Recording stopped")
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Error stopping recording", e)
        }
    }

    private fun startRecordingTimer() {
        CoroutineScope(Dispatchers.Main).launch {
            while (_isRecording.value) {
                delay(1000) // Update every second
                _recordingTime.value = (System.currentTimeMillis() - recordingStartTime) / 1000
            }
        }
    }

    private fun setupMediaRecorder(displayRotation: Int) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val videoFileName = "VID_$timeStamp.mp4"
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, videoFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
            }
        }

        val resolver = getApplication<Application>().contentResolver
        val videoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(getApplication())
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder?.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(10000000) // 10 Mbps
            setVideoFrameRate(30)
            setVideoSize(1920, 1080)
            // Compute and set orientation hint so the recorded video matches device orientation
            val cameraId = currentCameraId
            if (cameraId != null) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                val displayRotationDegrees = when (displayRotation) {
                    Surface.ROTATION_0 -> 0
                    Surface.ROTATION_90 -> 90
                    Surface.ROTATION_180 -> 180
                    Surface.ROTATION_270 -> 270
                    else -> 0
                }
                val orientationHint = (sensorOrientation - displayRotationDegrees + 360) % 360
                try {
                    setOrientationHint(orientationHint)
                    Log.d("CameraViewModel", "Video orientationHint set to $orientationHint (sensor=$sensorOrientation, display=$displayRotationDegrees)")
                } catch (e: Exception) {
                    Log.e("CameraViewModel", "Failed to set orientation hint", e)
                }
            }
            setOutputFile(resolver.openFileDescriptor(videoUri!!, "w")?.fileDescriptor)
            prepare()
        }

        recordingSurface = mediaRecorder?.surface
        _lastVideoUri.value = videoUri
        // Refresh gallery thumbnails after setting up video recording
        refreshGalleryThumbnails()
    }

    private fun createRecordingSession() {
        val texture = previewSurfaceTexture.value ?: return
        
        val surface = Surface(texture)
        val recordingSurface = recordingSurface ?: return

        val previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        previewRequestBuilder?.addTarget(surface)
        previewRequestBuilder?.addTarget(recordingSurface)

        cameraDevice?.createCaptureSession(
            listOf(surface, recordingSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    
                    session.setRepeatingRequest(previewRequestBuilder?.build()!!, null, null)
                    mediaRecorder?.start()
                    Log.d("CameraViewModel", "Recording session configured")
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("CameraViewModel", "Failed to configure recording session")
                }
            },
            null
        )
    }

    private fun loadLatestVideoFromGallery() {
        if (ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.READ_MEDIA_VIDEO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val resolver = getApplication<Application>().contentResolver
        val projection = arrayOf(MediaStore.Video.Media._ID)
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { it ->
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                _lastVideoUri.value = uri
            }
        }
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

    fun openLatestMedia() {
        // Ensure we have an up-to-date latest media
        if (!validateLatestMedia()) {
            Log.d("CameraViewModel", "Latest media validation failed, refreshing thumbnails")
            refreshGalleryThumbnails()
        }
        val latest = _latestGalleryMedia.value
        if (latest == null) {
            Log.d("CameraViewModel", "No latest media found, opening gallery only")
            openGallery()
            return
        }

        try {
            val app = getApplication<Application>()
            val packageManager = app.packageManager

            // Try to open the gallery's recents/grid view directly
            // This approach opens the gallery app showing the media grid, not just the main activity
            
            // Method 1: Try to open with Photos app intent that shows recents
            val photosIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_GALLERY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val resolveInfo = packageManager.resolveActivity(photosIntent, 0)
            if (resolveInfo != null) {
                val galleryPackage = resolveInfo.activityInfo.packageName
                Log.d("CameraViewModel", "Found gallery app: $galleryPackage")
                
                // Try to open the gallery's recents view with the latest media highlighted
                try {
                    // Create intent to view the latest media, which should open in gallery context
                    val mimeType = app.contentResolver.getType(latest) ?: "*/*"
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(latest, mimeType)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        // Set the gallery package so it opens within the same app
                        setPackage(galleryPackage)
                    }
                    
                    app.startActivity(viewIntent)
                    Log.d("CameraViewModel", "Opened latest media in gallery recents view: $latest")
                    return
                } catch (e: Exception) {
                    Log.d("CameraViewModel", "Failed to open media in gallery, falling back to gallery only", e)
                }
                
                // Fallback: just open the gallery app
                app.startActivity(photosIntent)
                Log.d("CameraViewModel", "Opened gallery app directly: $galleryPackage")
                return
            }
            
            // Method 2: Try to open with a generic media intent that should show gallery
            val mediaIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val mediaResolveInfo = packageManager.resolveActivity(mediaIntent, 0)
            if (mediaResolveInfo != null) {
                app.startActivity(mediaIntent)
                Log.d("CameraViewModel", "Opened gallery using media intent")
                return
            }
            
            // Method 3: Fallback to GET_CONTENT intent
            val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(getContentIntent)
            Log.d("CameraViewModel", "Opened gallery using GET_CONTENT intent")
            
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Error opening gallery recents view", e)
            openGallery()
        }
    }

    fun openGallery() {
        try {
            // Try multiple approaches to open the gallery app directly
            // We want to avoid any chooser dialogs and go straight to gallery
            
            val packageManager = getApplication<Application>().packageManager
            
            // Approach 1: Try to open with Photos app intent (most direct)
            val photosIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_GALLERY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val resolveInfo = packageManager.resolveActivity(photosIntent, 0)
            if (resolveInfo != null) {
                getApplication<Application>().startActivity(photosIntent)
                Log.d("CameraViewModel", "Opened gallery using CATEGORY_APP_GALLERY")
                return
            }
            
            // Approach 2: Try to open with a generic media intent that should show gallery
            // This approach opens the gallery app directly without showing specific media
            val mediaIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Check if there's an app that can handle this intent
            val mediaResolveInfo = packageManager.resolveActivity(mediaIntent, 0)
            if (mediaResolveInfo != null) {
                getApplication<Application>().startActivity(mediaIntent)
                Log.d("CameraViewModel", "Opened gallery using media intent")
                return
            }
            
            // Approach 3: Try to open with GET_CONTENT intent (fallback)
            val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(getContentIntent)
            Log.d("CameraViewModel", "Opened gallery using GET_CONTENT intent")
            
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Error opening gallery", e)
        }
    }

    fun refreshGalleryThumbnails() {
        loadLatestPhotoFromGallery()
        loadLatestVideoFromGallery()
        loadLatestGalleryMedia()
    }
    
    fun forceRefreshThumbnails() {
        // Force refresh all gallery thumbnails
        refreshGalleryThumbnails()
    }
    
    fun checkMediaPermissions(): Boolean {
        val hasImagePermission = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasVideoPermission = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED
        
        Log.d("CameraViewModel", "Media permissions - Images: $hasImagePermission, Videos: $hasVideoPermission")
        return hasImagePermission || hasVideoPermission
    }
    
    fun validateLatestMedia(): Boolean {
        val latestMedia = _latestGalleryMedia.value
        if (latestMedia == null) return false
        
        return try {
            // Try to access the media to see if it's still valid
            val resolver = getApplication<Application>().contentResolver
            resolver.openInputStream(latestMedia)?.use { it.read() }
            true
        } catch (e: Exception) {
            Log.d("CameraViewModel", "Latest media is no longer accessible, refreshing...")
            false
        }
    }

    private fun loadLatestGalleryMedia() {
        Log.d("CameraViewModel", "Loading latest gallery media...")
        
        if (ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.READ_MEDIA_VIDEO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("CameraViewModel", "No media permissions granted")
            return
        }

        val resolver = getApplication<Application>().contentResolver
        
        // Query both photos and videos to find the most recent one
        val photoProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        val videoProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED)
        
        var latestPhotoTime = 0L
        var latestPhotoId: Long? = null
        var latestVideoTime = 0L
        var latestVideoId: Long? = null
        
        // Get latest photo
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            photoProjection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { it ->
            if (it.moveToFirst()) {
                latestPhotoTime = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                latestPhotoId = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            }
        }
        
        // Get latest video
        resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { it ->
            if (it.moveToFirst()) {
                latestVideoTime = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED))
                latestVideoId = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
            }
        }
        
        // Determine which is more recent and set the latest gallery media
        val resultUri = when {
            latestPhotoId != null && latestVideoId != null -> {
                // Both photo and video exist, compare times
                if (latestPhotoTime >= latestVideoTime) {
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        latestPhotoId!!
                    )
                } else {
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        latestVideoId!!
                    )
                }
            }
            latestPhotoId != null -> {
                // Only photo exists
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    latestPhotoId!!
                )
            }
            latestVideoId != null -> {
                // Only video exists
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    latestVideoId!!
                )
            }
            else -> {
                // No media exists
                null
            }
        }
        
        _latestGalleryMedia.value = resultUri
        Log.d("CameraViewModel", "Latest gallery media set to: $resultUri")
    }

    private fun findCameraIds() {
        Log.d("CameraViewModel", "Available camera IDs: ${cameraManager.cameraIdList.toList()}")
        
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            
            Log.d("CameraViewModel", "Camera $cameraId: lensFacing=$lensFacing, flashAvailable=$flashAvailable")
            
            when (lensFacing) {
                CameraCharacteristics.LENS_FACING_FRONT -> {
                    frontCameraId = cameraId
                    Log.d("CameraViewModel", "Front camera ID: $cameraId")
                }
                CameraCharacteristics.LENS_FACING_BACK -> {
                    backCameraId = cameraId
                    Log.d("CameraViewModel", "Back camera ID: $cameraId")
                }
            }
        }
        
        Log.d("CameraViewModel", "Selected back camera ID: $backCameraId")
        Log.d("CameraViewModel", "Selected front camera ID: $frontCameraId")
    }

    private fun checkFlashSupport() {
        val cameraId = currentCameraId ?: return
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        
        // Check if flash is available
        val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
        
        // Check if torch mode is supported
        val flashModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
        val torchSupported = flashModes?.contains(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH) ?: false
        
        // For debugging, let's also check other flash-related capabilities
        val flashModesAvailable = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
        val flashModesList = flashModesAvailable?.toList() ?: emptyList()
        
        // Check available flash modes
        val availableFlashModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
        Log.d("CameraViewModel", "Available flash modes: $availableFlashModes")
        
        // Check what flash modes are supported
        val flashModesSupported = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
        Log.d("CameraViewModel", "Supported flash modes: $flashModesSupported")
        
        Log.d("CameraViewModel", "Camera ID: $cameraId")
        Log.d("CameraViewModel", "Flash available: $flashAvailable")
        Log.d("CameraViewModel", "Torch supported: $torchSupported")
        Log.d("CameraViewModel", "Available AE modes: $flashModesList")
        
        // For now, let's force flash support to true for testing
        _flashSupported.value = true
        Log.d("CameraViewModel", "Forcing flash support to true for testing")
        
        // Reset flash state if flash is not supported
        if (!_flashSupported.value) {
            _flashEnabled.value = false
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

    fun setAspectRatio(ratio: AspectRatio) {
        if (_aspectRatio.value != ratio) {
            _aspectRatio.value = ratio
            // Reconfigure camera with new ratio
            if (cameraDevice != null) {
                createCameraPreviewSession()
            }
        }
    }

    fun toggleRatioSelector() {
        _isRatioSelectorExpanded.value = !_isRatioSelectorExpanded.value
    }

    fun collapseRatioSelector() {
        _isRatioSelectorExpanded.value = false
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
            // Reset flash state when switching cameras
            _flashEnabled.value = false
            openCamera()
        }
    }

    fun findCameraWithFlash(): String? {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            if (flashAvailable) {
                Log.d("CameraViewModel", "Found camera with flash: $cameraId")
                return cameraId
            }
        }
        Log.d("CameraViewModel", "No camera with flash found")
        return null
    }

    fun switchToCameraWithFlash() {
        val cameraWithFlash = findCameraWithFlash()
        if (cameraWithFlash != null && cameraWithFlash != currentCameraId) {
            Log.d("CameraViewModel", "Switching to camera with flash: $cameraWithFlash")
            closeCamera()
            currentCameraId = cameraWithFlash
            openCamera()
        } else {
            Log.d("CameraViewModel", "No camera with flash available or already using it")
        }
    }

    fun toggleFlash() {
        // Temporarily remove support check for testing
        // if (!_flashSupported.value) {
        //     Log.d("CameraViewModel", "Cannot toggle flash - not supported")
        //     return
        // }
        
        _flashEnabled.value = !_flashEnabled.value
        Log.d("CameraViewModel", "Flash toggled to: ${_flashEnabled.value}")
        // Update the preview session with new flash setting
        updateFlashMode()
    }





    private fun updateFlashMode() {
        captureSession?.let { session ->
            try {
                // Stop current repeating request
                session.stopRepeating()
                
                val previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                previewRequestBuilder?.let { builder ->
                    // Add the preview surface
                    _previewSurfaceTexture.value?.let { surfaceTexture ->
                        val surface = Surface(surfaceTexture)
                        builder.addTarget(surface)
                    }

                    builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                    // Set flash mode
                    if (_flashEnabled.value) {
                        Log.d("CameraViewModel", "Enabling flash/torch")
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                    } else {
                        Log.d("CameraViewModel", "Disabling flash/torch")
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    }

                    val previewRequest = builder.build()
                    session.setRepeatingRequest(previewRequest, null, null)
                    Log.d("CameraViewModel", "Flash mode updated: ${_flashEnabled.value}")
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error updating flash mode", e)
            }
        }
    }

    fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        _previewSurfaceTexture.value = surfaceTexture
        textureViewWidth = width
        textureViewHeight = height
        Log.d("CameraViewModel", "SurfaceTexture available: ${width}x${height}")
        
        // Only open camera if we have permission and the app is in foreground
        if (ActivityCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        }
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
        
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    checkFlashSupport()
                    createCameraPreviewSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e("CameraViewModel", "Camera error: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, null)
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Error opening camera", e)
            // Don't crash the app, just log the error
        }
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

        val surfaces = mutableListOf<Surface>(surface)

        // Add ImageReader for photo mode
        if (_cameraMode.value == CameraMode.PHOTO) {
            val jpegSizes = streamConfigurationMap?.getOutputSizes(ImageFormat.JPEG)
            val captureSize = jpegSizes?.maxByOrNull { it.width * it.height } ?: _previewSize.value
            imageReader = ImageReader.newInstance(captureSize.width, captureSize.height, ImageFormat.JPEG, 1)
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
                        // Refresh gallery thumbnails after capturing a new photo
                        refreshGalleryThumbnails()
                        // Also update the latest gallery media
                        loadLatestGalleryMedia()
                    }
                } finally {
                    image?.close()
                }
            }, null)
            imageReader?.surface?.let { surfaces.add(it) }
        }

        val template = if (_cameraMode.value == CameraMode.VIDEO) {
            CameraDevice.TEMPLATE_RECORD
        } else {
            CameraDevice.TEMPLATE_PREVIEW
        }

        val previewRequestBuilder = cameraDevice!!.createCaptureRequest(template)
        previewRequestBuilder.addTarget(surface)

        cameraDevice?.createCaptureSession(
            surfaces,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    
                    val afMode = if (_cameraMode.value == CameraMode.VIDEO) {
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    } else {
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    }
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, afMode)
                    
                    // Set initial flash mode (only if flash is supported and in photo mode)
                    if (_flashSupported.value && _cameraMode.value == CameraMode.PHOTO) {
                        if (_flashEnabled.value) {
                            Log.d("CameraViewModel", "Setting flash ON in preview session")
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                            previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                        } else {
                            Log.d("CameraViewModel", "Setting flash OFF in preview session")
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                        }
                    }
                    
                    session.setRepeatingRequest(previewRequestBuilder.build(), null, null)
                    _cameraReady.value = true
                    Log.d("CameraViewModel", "Camera session configured successfully for ${_cameraMode.value}")
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    _cameraReady.value = false
                    Log.e("CameraViewModel", "Failed to configure camera session")
                }
            },
            null
        )
    }

    fun captureAction(displayRotation: Int = 0) {
        when (_cameraMode.value) {
            CameraMode.PHOTO -> takePicture(displayRotation)
            CameraMode.VIDEO -> {
                if (_isRecording.value) {
                    stopRecording()
                } else {
                    startRecording(displayRotation)
                }
            }
        }
    }

    fun takePicture(displayRotation: Int = 0) {
        if (!_cameraReady.value) {
            Log.e("CameraViewModel", "Cannot take picture: camera is not ready")
            return
        }
        
        if (imageReader == null) {
            Log.e("CameraViewModel", "Cannot take picture: imageReader is null")
            return
        }
        
        if (cameraDevice == null) {
            Log.e("CameraViewModel", "Cannot take picture: cameraDevice is null")
            return
        }
        
        if (captureSession == null) {
            Log.e("CameraViewModel", "Cannot take picture: captureSession is null")
            return
        }
        
        lastCaptureDisplayRotation = displayRotation
        
        // Stop the repeating preview first
        captureSession?.stopRepeating()
        
        try {
            val captureBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder?.addTarget(imageReader!!.surface)
            captureBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            captureBuilder?.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE)

            // Set flash mode for photo capture
            if (_flashSupported.value && _flashEnabled.value) {
                Log.d("CameraViewModel", "Setting flash ON for photo capture")
                captureBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                captureBuilder?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
            } else {
                Log.d("CameraViewModel", "Setting flash OFF for photo capture")
                captureBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                captureBuilder?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }

            captureBuilder?.let { builder ->
                captureSession?.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        super.onCaptureCompleted(session, request, result)
                        Log.d("CameraViewModel", "Photo capture completed")
                        
                        // Check if flash actually fired
                        val flashState = result.get(CaptureResult.FLASH_STATE)
                        Log.d("CameraViewModel", "Flash state during capture: $flashState")
                        val aeMode = result.get(CaptureResult.CONTROL_AE_MODE)
                        Log.d("CameraViewModel", "AE mode during capture: $aeMode")

                        // Restore preview with proper flash state
                        restorePreviewAfterCapture(session)
                    }
                    
                    override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                        super.onCaptureFailed(session, request, failure)
                        Log.e("CameraViewModel", "Photo capture failed: ${failure.reason}")
                        // Restore preview even on failure
                        restorePreviewAfterCapture(session)
                    }
                }, null)
                Log.d("CameraViewModel", "Picture capture request sent with flash: ${_flashEnabled.value}")
            }
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Error taking picture", e)
            // Restore preview on exception
            captureSession?.let { restorePreviewAfterCapture(it) }
        }
    }

    private fun restorePreviewAfterCapture(session: CameraCaptureSession) {
        try {
            // Create a new preview request with proper flash state
            val previewBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewBuilder?.let { builder ->
                _previewSurfaceTexture.value?.let { surfaceTexture ->
                    val surface = Surface(surfaceTexture)
                    builder.addTarget(surface)
                }
                
                builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                
                // Restore flash state based on current setting
                if (_flashSupported.value && _flashEnabled.value) {
                    Log.d("CameraViewModel", "Restoring flash ON in preview")
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                } else {
                    Log.d("CameraViewModel", "Restoring flash OFF in preview")
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                
                // Restart repeating preview
                session.setRepeatingRequest(builder.build(), null, null)
                Log.d("CameraViewModel", "Preview restored after capture")
            }
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Error restoring preview after capture", e)
        }
    }

    private fun closeCamera() {
        _cameraReady.value = false
        
        // Stop recording if active
        if (_isRecording.value) {
            stopRecording()
        }
        
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        mediaRecorder?.release()
        mediaRecorder = null
        recordingSurface?.release()
        recordingSurface = null
    }

    override fun onCleared() {
        super.onCleared()
        closeCamera()
        imageReader?.close()
    }

    fun onAppBackgrounded() {
        Log.d("CameraViewModel", "App backgrounded, closing camera")
        closeCamera()
    }

    fun onAppForegrounded() {
        Log.d("CameraViewModel", "App foregrounded, reopening camera")
        if (_previewSurfaceTexture.value != null) {
            openCamera()
        }
        // Refresh gallery thumbnails when app comes to foreground
        refreshGalleryThumbnails()
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
        // Read original EXIF orientation
        val tempInputFile = File.createTempFile("input_img", ".jpg", getApplication<Application>().cacheDir)
        tempInputFile.writeBytes(jpegBytes)
        val originalExif = ExifInterface(tempInputFile)
        val sensorOrientation = cameraDevice?.let {
            val characteristics = cameraManager.getCameraCharacteristics(currentCameraId!!)
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        } ?: 0
        val orientation = originalExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        // Decode bitmap
        var bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

        // Calculate the total rotation
        val totalRotation = (sensorOrientation - lastCaptureDisplayRotation + 360) % 360
        val matrix = Matrix()
        matrix.postRotate(totalRotation.toFloat())
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

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
        // Use 100 for best quality
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        val croppedBytes = outputStream.toByteArray()

        // Write to temp file and set EXIF orientation to normal
        val tempFile = File.createTempFile("cropped_img", ".jpg", getApplication<Application>().cacheDir)
        tempFile.writeBytes(croppedBytes)
        val exif = ExifInterface(tempFile)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.saveAttributes()
        return tempFile.readBytes()
    }
} 