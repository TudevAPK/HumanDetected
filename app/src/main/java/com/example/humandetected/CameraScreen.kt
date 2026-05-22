package com.example.humandetected

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: DetectionViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context)
            ) { imageProxy ->
                val bitmap = imageProxy.toBitmap()
                val rotation = imageProxy.imageInfo.rotationDegrees
                viewModel.processImage(bitmap, rotation)
                imageProxy.close()
            }
        }
    }

    val isPersonDetected by viewModel.isPersonDetected.collectAsStateWithLifecycle()
    val isDetecting by viewModel.isDetecting.collectAsStateWithLifecycle()
    val lastDetectionTime by viewModel.lastDetectionTime.collectAsStateWithLifecycle()
    val detectedLabels by viewModel.detectedLabels.collectAsStateWithLifecycle()
    val isMqttConnected by viewModel.isMqttConnected.collectAsStateWithLifecycle()
    val showCameraView by viewModel.showCameraView.collectAsStateWithLifecycle()

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            controller.bindToLifecycle(lifecycleOwner)
        } else {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraPermissionState.status.isGranted) {
            if (showCameraView) {
                AndroidView(
                    factory = {
                        PreviewView(it).apply {
                            this.controller = controller
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Tối giản thông tin ở góc trên bên trái
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.4f)) // Mờ nhẹ để đọc chữ trên nền cam
                .padding(8.dp)
        ) {
            Text(
                text = "MQTT: ${if (isMqttConnected) "Connected" else "Disconnected"}",
                color = if (isMqttConnected) Color.Green else Color.Red,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "AI: ${if (isDetecting) "Running" else "Stopped"}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "Status: ${if (isPersonDetected) "PERSON DETECTED" else "Idle"}",
                color = if (isPersonDetected) Color.Yellow else Color.White,
                style = MaterialTheme.typography.labelSmall
            )
            if (detectedLabels.isNotEmpty() && isDetecting) {
                Text(
                    text = "See: ${detectedLabels.take(1).joinToString()}",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // Nút điều khiển tối giản ở góc dưới
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { viewModel.toggleDetection() }) {
                Text(
                    text = if (isDetecting) "[ STOP AI ]" else "[ START AI ]",
                    color = if (isDetecting) Color.Red else Color.Green,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            
            TextButton(onClick = { viewModel.toggleCameraView() }) {
                Text(
                    text = if (showCameraView) "[ HIDE CAM ]" else "[ SHOW CAM ]",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
