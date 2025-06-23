package com.ntttrang.camerak.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ntttrang.camerak.ui.theme.CameraKTheme
import com.ntttrang.camerak.viewmodel.CameraViewModel
import com.ntttrang.camerak.viewmodel.AspectRatio
import androidx.compose.foundation.layout.aspectRatio
import android.util.Size
import androidx.compose.foundation.layout.fillMaxHeight

@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    Scaffold { innerPadding ->
        CameraPreview(viewModel, paddingValues = innerPadding)
    }
}

@Composable
fun CameraPreview(viewModel: CameraViewModel, paddingValues: PaddingValues) {
    val aspectRatio by viewModel.aspectRatio.collectAsState()

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

        Box(modifier = boxModifier) {
            val view = LocalView.current
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
        TextButton(
            onClick = { viewModel.cycleAspectRatio() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            Text(aspectRatio.displayName, color = Color.White)
        }
        // This box will hold the UI controls and respect the insets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(paddingValues)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumbnail(viewModel)
            IconButton(
                onClick = { viewModel.takePicture() },
                modifier = Modifier.size(72.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White, CircleShape)
                )
            }
            IconButton(
                onClick = { viewModel.switchCamera() },
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

@Composable
fun Thumbnail(viewModel: CameraViewModel) {
    val lastPhotoUri by viewModel.lastPhotoUri.collectAsState()
    val thumbnailSize = 64.dp

    if (lastPhotoUri != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(lastPhotoUri)
                .crossfade(true)
                .build(),
            contentDescription = "Last captured photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(thumbnailSize)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape)
        )
    } else {
        Spacer(modifier = Modifier.size(thumbnailSize))
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