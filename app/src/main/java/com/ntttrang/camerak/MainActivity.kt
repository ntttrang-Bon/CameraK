package com.ntttrang.camerak

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ntttrang.camerak.ui.screen.CameraScreen
import com.ntttrang.camerak.ui.theme.CameraKTheme
import com.ntttrang.camerak.viewmodel.CameraViewModel
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {

    private val viewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CameraKTheme {
                val context = LocalContext.current
                val permissionsToRequest = arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { permissionsMap ->
                        // Check if all required permissions are granted
                        hasCameraPermission = permissionsMap[Manifest.permission.CAMERA] == true &&
                                permissionsMap[Manifest.permission.RECORD_AUDIO] == true
                    }
                )

                if (hasCameraPermission) {
                    val lastPhotoUri by viewModel.lastPhotoUri.collectAsState()
                    CameraScreen(viewModel, lastPhotoUri = lastPhotoUri)
                } else {
                    PermissionRationaleScreen(
                        onConfirm = { launcher.launch(permissionsToRequest) }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForegrounded()
    }
}

@Composable
fun PermissionRationaleScreen(onConfirm: () -> Unit) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            PermissionRationaleDialog(
                onConfirm = onConfirm,
                onDismiss = { /* Dismissing the dialog does not change the permission state */ }
            )
        }
    }
}

@Composable
fun PermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions Required") },
        text = { Text("To take pictures and record videos, this app needs access to your camera and microphone. To also see the latest photos and videos from your gallery as thumbnails, please grant gallery access.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Continue")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}