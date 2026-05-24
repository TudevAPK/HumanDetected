package com.example.humandetected

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttManager(context: Context, private val onStatusChange: (Boolean) -> Unit, private val onDataReceived: (String, String) -> Unit) {
    private var mqttClient: MqttClient? = null
    
    // Cấu hình từ thông tin bạn cung cấp
    private val brokerUrl = "tcp://192.168.2.28:1883" 
    private val clientId = "AndroidCameraAI_${System.currentTimeMillis()}"
    private val tempTopic = "esp32/dht11/temperature" // Topic nhiệt độ phòng của bạn
    private val humidityTopic = "esp32/dht11/humidity" // Topic độ ẩm
    private val relay3StateTopic = "esp32c3/relay3/state"
    private val relay4StateTopic = "esp32c3/relay4/state"
    private val relay3CommandTopic = "esp32c3/relay3"
    private val relay4CommandTopic = "esp32c3/relay4"
    private val detectionTopic = "home/camera/person_detected"
    private val username = "esp32"
    private val password = "Tutran123"

    init {
        try {
            mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())
            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d("MQTT", "Connected to $serverURI")
                    onStatusChange(true)
                    // Đăng ký nhận dữ liệu
                    mqttClient?.subscribe(tempTopic)
                    mqttClient?.subscribe(humidityTopic)
                    mqttClient?.subscribe(relay3StateTopic)
                    mqttClient?.subscribe(relay4StateTopic)
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.e("MQTT", "Connection lost: ${cause?.message}")
                    onStatusChange(false)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    topic?.let { t ->
                        message?.let { msg ->
                            onDataReceived(t, String(msg.payload))
                        }
                    }
                }
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
                mqttClient?.publish(detectionTopic, message)
            } catch (e: Exception) {
                Log.e("MQTT", "Publish error: ${e.message}")
            }
        }
    }

    fun toggleRelay(relayNumber: Int, turnOn: Boolean) {
        if (mqttClient?.isConnected == true) {
            val topic = if (relayNumber == 3) relay3CommandTopic else relay4CommandTopic
            val payload = if (turnOn) "ON" else "OFF"
            val message = MqttMessage(payload.toByteArray()).apply {
                qos = 1
                isRetained = true
            }
            try {
                mqttClient?.publish(topic, message)
            } catch (e: Exception) {
                Log.e("MQTT", "Relay publish error: ${e.message}")
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
