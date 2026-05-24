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

    private val _roomTemp = MutableStateFlow("--")
    val roomTemp: StateFlow<String> = _roomTemp

    private val _roomHumidity = MutableStateFlow("--")
    val roomHumidity: StateFlow<String> = _roomHumidity

    private val _fan1Status = MutableStateFlow(false)
    val fan1Status: StateFlow<Boolean> = _fan1Status

    private val _fan2Status = MutableStateFlow(false)
    val fan2Status: StateFlow<Boolean> = _fan2Status

    private val mqttManager = MqttManager(application, { isConnected ->
        _isMqttConnected.value = isConnected
    }, { topic, message ->
        // Định dạng lấy 1 chữ số sau dấu phẩy
        val formattedValue = message.toFloatOrNull()?.let {
            String.format(Locale.US, "%.1f", it)
        } ?: message

        when (topic) {
            "esp32/dht11/temperature" -> _roomTemp.value = formattedValue
            "esp32/dht11/humidity" -> _roomHumidity.value = formattedValue
            "esp32c3/relay3/state" -> _fan1Status.value = message == "ON"
            "esp32c3/relay4/state" -> _fan2Status.value = message == "ON"
        }
    })

    private val _isPersonDetected = MutableStateFlow(false)
    val isPersonDetected: StateFlow<Boolean> = _isPersonDetected

    private val _isDetecting = MutableStateFlow(true) // Start detecting by default
    val isDetecting: StateFlow<Boolean> = _isDetecting

    private val _targetBrightness = MutableStateFlow(0.6f) // Mặc định 60%
    val targetBrightness: StateFlow<Float> = _targetBrightness

    private var lastPersonDetectedTime = System.currentTimeMillis()

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
                
                if (hasPerson) {
                    lastPersonDetectedTime = currentTime
                    _targetBrightness.value = 0.6f
                } else {
                    if (currentTime - lastPersonDetectedTime >= 60000) { // 1 phút
                        _targetBrightness.value = 0.05f
                    }
                }

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

    fun toggleFan1() {
        val nextState = !_fan1Status.value
        mqttManager.toggleRelay(3, nextState)
    }

    fun toggleFan2() {
        val nextState = !_fan2Status.value
        mqttManager.toggleRelay(4, nextState)
    }
}
