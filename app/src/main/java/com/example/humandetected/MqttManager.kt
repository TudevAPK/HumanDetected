package com.example.humandetected

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttManager(context: Context, private val onStatusChange: (Boolean) -> Unit) {
    private var mqttClient: MqttClient? = null
    
    // Cấu hình từ thông tin bạn cung cấp
    private val brokerUrl = "tcp://192.168.2.28:1883" 
    private val clientId = "AndroidCameraAI_${System.currentTimeMillis()}"
    private val topic = "home/camera/person_detected"
    private val username = "esp32"
    private val password = "Tutran123"

    init {
        try {
            mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())
            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d("MQTT", "Connected to $serverURI")
                    onStatusChange(true)
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.e("MQTT", "Connection lost: ${cause?.message}")
                    onStatusChange(false)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {}
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
            connect()
        } catch (e: Exception) {
            Log.e("MQTT", "Error init: ${e.message}")
            onStatusChange(false)
        }
    }

    private fun connect() {
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            userName = username
            password = this@MqttManager.password.toCharArray()
            connectionTimeout = 10
            keepAliveInterval = 60
            isAutomaticReconnect = true
        }

        try {
            mqttClient?.connect(options)
        } catch (e: Exception) {
            Log.e("MQTT", "Connect fail: ${e.message}")
            onStatusChange(false)
        }
    }

    fun publishStatus(hasPerson: Boolean) {
        if (mqttClient?.isConnected == true) {
            val payload = if (hasPerson) "ON" else "OFF"
            val message = MqttMessage(payload.toByteArray()).apply {
                qos = 1
                isRetained = false
            }
            try {
                mqttClient?.publish(topic, message)
                Log.d("MQTT", "Published: $payload to $topic")
            } catch (e: Exception) {
                Log.e("MQTT", "Publish error: ${e.message}")
            }
        } else {
            // Thử kết nối lại nếu bị mất
            if (mqttClient != null && !mqttClient!!.isConnected) {
                try { connect() } catch (e: Exception) {}
            }
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            Log.e("MQTT", "Disconnect error: ${e.message}")
        }
    }
}
