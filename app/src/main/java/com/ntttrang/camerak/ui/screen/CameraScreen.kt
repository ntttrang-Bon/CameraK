package com.ntttrang.camerak.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.ntttrang.camerak.ui.theme.CameraKTheme
import com.ntttrang.camerak.viewmodel.CameraViewModel
import com.ntttrang.camerak.viewmodel.AspectRatio
import com.ntttrang.camerak.viewmodel.CameraMode
import androidx.compose.foundation.layout.aspectRatio
import android.util.Size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri

private fun formatRecordingTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

@Composable
fun CameraScreen(viewModel: CameraViewModel, lastPhotoUri: Uri?) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { 
        // Refresh thumbnails when returning from gallery
        viewModel.refreshGalleryThumbnails()
    }
    
    // Refresh thumbnails when the screen is first displayed
    LaunchedEffect(Unit) {
        // Small delay to ensure app is fully initialized
        delay(500)
        // Check permissions first, then refresh thumbnails
        if (viewModel.checkMediaPermissions()) {
            viewModel.refreshGalleryThumbnails()
        }
    }
    
    Scaffold { innerPadding ->
        CameraPreview(
            viewModel,
            paddingValues = innerPadding,
            onThumbnailClick = {
                viewModel.openLatestMedia()
            }
        )
    }
}

@Composable
fun CameraPreview(viewModel: CameraViewModel, paddingValues: PaddingValues, onThumbnailClick: () -> Unit = {}) {
    val aspectRatio by viewModel.aspectRatio.collectAsState()
    val cameraMode by viewModel.cameraMode.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isRatioSelectorExpanded by viewModel.isRatioSelectorExpanded.collectAsState()
    val view = LocalView.current

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val ratio = when (aspectRatio) {
            AspectRatio.RATIO_16_9 -> 9f / 16f
            AspectRatio.RATIO_4_3 -> 3f / 4f
            AspectRatio.RATIO_1_1 -> 1f / 1f
            else -> 0f // Full will fill the screen
        }

        val boxModifier = if (ratio > 0) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(ratio)
        } else {
            Modifier.fillMaxSize()
        }

        Box(
            modifier = boxModifier
                .clickable(
                    enabled = isRatioSelectorExpanded,
                    onClick = { viewModel.collapseRatioSelector() }
                )
        ) {
            AndroidView(
                factory = { context ->
                    android.view.TextureView(context).apply {
                        surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                viewModel.onSurfaceTextureAvailable(surface, width, height)
                            }

                            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}

                            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                        }
                    }
                },
                update = { textureView ->
                    // val displayRotation = view.display.rotation
                    // textureView.setTransform(viewModel.getPreviewTransform(textureView.width, textureView.height, displayRotation))
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Aspect ratio selector at the top
        AspectRatioSelector(
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .zIndex(1f)
        )

        // Recording indicator and timer
        if (isRecording) {
            val recordingTime by viewModel.recordingTime.collectAsState()
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clickable(
                        enabled = isRatioSelectorExpanded,
                        onClick = { viewModel.collapseRatioSelector() }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color.Red, CircleShape)
                )
                Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    text = formatRecordingTime(recordingTime),
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        
        // Flash button in top-right corner (only show in photo mode)
        if (cameraMode == CameraMode.PHOTO) {
            val flashEnabled by viewModel.flashEnabled.collectAsState()
            val flashSupported by viewModel.flashSupported.collectAsState()
            
            IconButton(
                onClick = { viewModel.toggleFlash() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clickable(
                        enabled = false,
                        onClick = { /* Prevent click outside from collapsing */ }
                    )
            ) {
                Icon(
                    imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = if (flashEnabled) "Flash On" else "Flash Off",
                    tint = if (flashEnabled) Color.Yellow else Color.White
                )
            }
        }

        // Bottom controls area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(paddingValues)
                .padding(16.dp)
                .clickable(
                    enabled = isRatioSelectorExpanded,
                    onClick = { viewModel.collapseRatioSelector() }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode selector above the capture button
            ModeSelector(
                currentMode = cameraMode,
                onModeSelected = { viewModel.setCameraMode(it) },
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .clickable(
                        enabled = false,
                        onClick = { /* Prevent click outside from collapsing */ }
                    )
            )
            
            // Bottom row with thumbnail, capture button, and camera switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = isRatioSelectorExpanded,
                        onClick = { viewModel.collapseRatioSelector() }
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Thumbnail(
                    viewModel = viewModel, 
                    onClick = onThumbnailClick, 
                    cameraMode = cameraMode,
                    onRatioSelectorCollapse = { viewModel.collapseRatioSelector() },
                    isRatioSelectorExpanded = isRatioSelectorExpanded
                )
                val cameraReady by viewModel.cameraReady.collectAsState()
                
                // Capture/Record button
                IconButton(
                    onClick = {
                        val displayRotation = view.display.rotation
                        viewModel.captureAction(displayRotation)
                    },
                    enabled = cameraReady,
                    modifier = Modifier
                        .size(72.dp)
                        .clickable(
                            enabled = false,
                            onClick = { /* Prevent click outside from collapsing */ }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isRecording) Color.Red else if (cameraReady) Color.White else Color.Gray,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRecording) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Recording",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        } else if (cameraMode == CameraMode.VIDEO) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = "Start Recording",
                                tint = Color.Red,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                
                IconButton(
                    onClick = { viewModel.switchCamera() },
                    modifier = Modifier.clickable(
                        enabled = false,
                        onClick = { /* Prevent click outside from collapsing */ }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ModeSelector(
    currentMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CameraMode.values().forEach { mode ->
            val isSelected = mode == currentMode
            Box(
                modifier = Modifier
                    .clickable { onModeSelected(mode) }
                    .background(
                        if (isSelected) Color.White else Color.Transparent,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable(
                        enabled = false,
                        onClick = { /* Prevent click outside from collapsing */ }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${mode.icon} ${mode.displayName}",
                    color = if (isSelected) Color.Black else Color.White
                )
            }
        }
    }
}

@Composable
fun Thumbnail(
    viewModel: CameraViewModel, 
    onClick: () -> Unit = {}, 
    cameraMode: CameraMode,
    onRatioSelectorCollapse: () -> Unit = {},
    isRatioSelectorExpanded: Boolean = false
) {
    val latestGalleryMedia by viewModel.latestGalleryMedia.collectAsState()
    val thumbnailSize = 64.dp

    if (latestGalleryMedia != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(latestGalleryMedia)
                // If the URI is a video, this tells Coil to extract a frame for the thumbnail
                .videoFrameMillis(0)
                .crossfade(true)
                .build(),
            contentDescription = "Latest media from gallery",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(thumbnailSize)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape)
                .clickable { 
                    if (isRatioSelectorExpanded) {
                        onRatioSelectorCollapse()
                    } else {
                        onClick()
                    }
                }
        )
    } else {
        // Show gallery icon when no content is available
        Box(
            modifier = Modifier
                .size(thumbnailSize)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable { 
                    if (isRatioSelectorExpanded) {
                        onRatioSelectorCollapse()
                    } else {
                        // Try to refresh thumbnails first, then call onClick
                        viewModel.forceRefreshThumbnails()
                        onClick()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "Open Gallery",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun AspectRatioSelector(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val aspectRatio by viewModel.aspectRatio.collectAsState()
    val isExpanded by viewModel.isRatioSelectorExpanded.collectAsState()
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main button that shows current ratio and expand/collapse icon
        TextButton(
            onClick = { viewModel.toggleRatioSelector() },
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable(
                    enabled = false,
                    onClick = { /* Prevent click outside from collapsing */ }
                )
        ) {
            Text(
                text = aspectRatio.displayName,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        
        // Expanded ratio options
        if (isExpanded) {
            Spacer(modifier = Modifier.padding(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
                    .clickable(
                        enabled = false,
                        onClick = { /* Prevent click outside from collapsing */ }
                    )
            ) {
                items(AspectRatio.values()) { ratio ->
                    val isSelected = ratio == aspectRatio
                    TextButton(
                        onClick = { 
                            viewModel.setAspectRatio(ratio)
                            viewModel.collapseRatioSelector()
                        },
                        modifier = Modifier
                            .background(
                                if (isSelected) Color.White else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable(
                                enabled = false,
                                onClick = { /* Prevent click outside from collapsing */ }
                            )
                    ) {
                        Text(
                            text = ratio.displayName,
                            color = if (isSelected) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Camera UI")
@Composable
fun CameraUiPreview() {
    CameraKTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            // Simulate a 4:3 aspect ratio preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(Color.DarkGray)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    // Simulate system navigation bar padding
                    .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Placeholder for Thumbnail
                Spacer(modifier = Modifier.size(64.dp).border(1.dp, Color.White, CircleShape))

                // Placeholder for capture button
                Box(modifier = Modifier.size(72.dp).background(Color.White, CircleShape))

                IconButton(onClick = { /* No-op */ }) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }
            }
        }
    }
} 