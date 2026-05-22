package com.example.humandetected

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class PersonDetector(context: Context) {
    private var detector: ObjectDetector? = null

    init {
        try {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().useGpu().build())
                .setMaxResults(5)
                .setScoreThreshold(0.6f) // Ngưỡng chính xác cao (60%)
                .build()

            detector = ObjectDetector.createFromFileAndOptions(
                context,
                "efficientdet_lite0.tflite",
                options
            )
        } catch (e: Exception) {
            Log.e("PersonDetector", "TFLite init error: ${e.message}")
            try {
                val options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setNumThreads(4).build())
                    .setMaxResults(5)
                    .setScoreThreshold(0.6f)
                    .build()
                detector = ObjectDetector.createFromFileAndOptions(
                    context,
                    "efficientdet_lite0.tflite",
                    options
                )
            } catch (e2: Exception) {
                Log.e("PersonDetector", "TFLite total failure: ${e2.message}")
            }
        }
    }

    fun detectPerson(bitmap: Bitmap, rotationDegrees: Int, callback: (Boolean, List<String>) -> Unit) {
        if (detector == null) {
            callback(false, listOf("Model chưa sẵn sàng"))
            return
        }

        // Xử lý xoay ảnh để TFLite nhận diện chính xác hướng
        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(-rotationDegrees / 90))
            .build()
        
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))
        val results = detector?.detect(tensorImage)

        val detectedLabels = mutableListOf<String>()
        var hasPerson = false

        results?.forEach { obj ->
            obj.categories.forEach { category ->
                val label = "${category.label} (${(category.score * 100).toInt()}%)"
                detectedLabels.add(label)
                
                if (category.label.lowercase() == "person" && category.score > 0.6f) {
                    hasPerson = true
                }
            }
        }

        callback(hasPerson, detectedLabels)
    }
}
