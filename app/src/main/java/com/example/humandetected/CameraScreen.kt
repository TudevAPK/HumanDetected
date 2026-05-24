package com.example.humandetected

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.WindPower
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// Khai báo font DS-Digital mà bạn vừa thêm
val dsDigitalFont = FontFamily(
    Font(R.font.ds_digital, FontWeight.Normal)
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: DetectionViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    val isPersonDetected by viewModel.isPersonDetected.collectAsStateWithLifecycle()
    val isDetecting by viewModel.isDetecting.collectAsStateWithLifecycle()
    val isMqttConnected by viewModel.isMqttConnected.collectAsStateWithLifecycle()
    val showCameraView by viewModel.showCameraView.collectAsStateWithLifecycle()
    val roomTemp by viewModel.roomTemp.collectAsStateWithLifecycle()
    val roomHumidity by viewModel.roomHumidity.collectAsStateWithLifecycle()
    val fan1Status by viewModel.fan1Status.collectAsStateWithLifecycle()
    val fan2Status by viewModel.fan2Status.collectAsStateWithLifecycle()

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var currentDay by remember { mutableStateOf("") }
    var isDayTime by remember { mutableStateOf(true) }
    
    // State cho chống lưu ảnh (Burn-in protection)
    var burnInX by remember { mutableStateOf(0.dp) }
    var burnInY by remember { mutableStateOf(0.dp) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            currentDate = SimpleDateFormat("dd MMMM, yyyy", Locale.getDefault()).format(now)
            currentDay = SimpleDateFormat("EEEE", Locale.getDefault()).format(now)
            isDayTime = hour in 6..18
            
            // Cứ mỗi 1 phút dịch chuyển nhẹ -5 đến 5 pixel
            burnInX = ((-5)..5).random().dp
            burnInY = ((-5)..5).random().dp
            
            delay(60000)
        }
    }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                val bitmap = imageProxy.toBitmap()
                val rotation = imageProxy.imageInfo.rotationDegrees
                viewModel.processImage(bitmap, rotation)
                imageProxy.close()
            }
        }
    }

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            controller.bindToLifecycle(lifecycleOwner)
        } else {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { viewModel.toggleCameraView() }
    ) {
        // Container chính được dịch chuyển nhẹ để chống burn-in
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .offset(x = burnInX, y = burnInY)
        ) {
            // --- CỘT TRÁI (1/3 MÀN HÌNH): THỜI TIẾT & CAMERA ---
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .align(Alignment.CenterStart),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                // Góc trên bên trái: Thời tiết
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isDayTime) Icons.Rounded.WbSunny else Icons.Rounded.NightlightRound,
                            contentDescription = null,
                            tint = if (isDayTime) Color(0xFFFFD700) else Color(0xFFADD8E6),
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "$roomTemp°C",
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "Độ ẩm: $roomHumidity%",
                        color = Color.Cyan.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = currentDay.uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Cổ Lễ, Nam Định",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Nút điều khiển quạt
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            onClick = { viewModel.toggleFan1() },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WindPower,
                                contentDescription = null,
                                tint = if (fan1Status) Color.Green else Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        TextButton(
                            onClick = { viewModel.toggleFan2() },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WindPower,
                                contentDescription = null,
                                tint = if (fan2Status) Color.Green else Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }

                // Góc dưới bên trái: Camera & Status
                Column {
                    if (cameraPermissionState.status.isGranted && showCameraView) {
                        Box(
                            modifier = Modifier
                                .size(width = 160.dp, height = 120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A1A1A))
                        ) {
                            AndroidView(
                                factory = {
                                    PreviewView(it).apply {
                                        this.controller = controller
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Status Minimalist
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = if (isMqttConnected) Color.Green else Color.Red
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MQTT",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "MOTION",
                            color = if (isPersonDetected) Color.Yellow else Color.DarkGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // --- CỘT PHẢI (2/3 MÀN HÌNH): ĐỒNG HỒ VỚI FONT MỚI ---
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.65f)
                    .align(Alignment.CenterEnd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 200.sp,
                    fontFamily = dsDigitalFont,
                    lineHeight = 200.sp,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentDate.uppercase(),
                    color = Color.Gray,
                    fontSize = 16.sp,
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Light
                )
            }
            
            // Nút ẩn nhỏ ở góc
            IconButton(
                onClick = { viewModel.toggleDetection() },
                modifier = Modifier.align(Alignment.BottomEnd).size(32.dp)
            ) {
                Icon(
                    imageVector = if (isDetecting) Icons.Default.Circle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isDetecting) Color.Red.copy(alpha = 0.5f) else Color.DarkGray,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
