package com.example.humandetected

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

class DetectionViewModel(application: Application) : AndroidViewModel(application) {
    private val personDetector = PersonDetector(application)
    
    private val _isMqttConnected = MutableStateFlow(false)
    val isMqttConnected: StateFlow<Boolean> = _isMqttConnected

    private val mqttManager = MqttManager(application) { isConnected ->
        _isMqttConnected.value = isConnected
    }

    private val _isPersonDetected = MutableStateFlow(false)
    val isPersonDetected: StateFlow<Boolean> = _isPersonDetected

    private val _isDetecting = MutableStateFlow(true) // Start detecting by default
    val isDetecting: StateFlow<Boolean> = _isDetecting

    private val _showCameraView = MutableStateFlow(true)
    val showCameraView: StateFlow<Boolean> = _showCameraView

    private val _lastDetectionTime = MutableStateFlow("Chưa bắt đầu")
    val lastDetectionTime: StateFlow<String> = _lastDetectionTime

    private val _detectedLabels = MutableStateFlow<List<String>>(emptyList())
    val detectedLabels: StateFlow<List<String>> = _detectedLabels

    private var lastProcessTime = 0L

    fun processImage(bitmap: Bitmap, rotationDegrees: Int) {
        if (!_isDetecting.value) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessTime >= 500) { // Giảm xuống 0.5 giây để cực kỳ nhạy
            lastProcessTime = currentTime
            personDetector.detectPerson(bitmap, rotationDegrees) { hasPerson, labels ->
                if (_isPersonDetected.value != hasPerson) {
                    mqttManager.publishStatus(hasPerson)
                }
                _isPersonDetected.value = hasPerson
                _detectedLabels.value = labels
                _lastDetectionTime.value = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mqttManager.disconnect()
    }

    fun toggleDetection() {
        _isDetecting.value = !_isDetecting.value
    }

    fun toggleCameraView() {
        _showCameraView.value = !_showCameraView.value
    }
}
