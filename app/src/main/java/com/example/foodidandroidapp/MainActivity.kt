package com.example.foodidandroidapp

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.foodidandroidapp.ui.theme.FoodIDAndroidAppTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.ml.modeldownloader.CustomModel
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions
import com.google.firebase.ml.modeldownloader.DownloadType
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment


class MainActivity : ComponentActivity() {
    private var interpreter: Interpreter? = null
    private val CAMERA_PERMISSION_CODE = 100
    private val CAMERA_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContent {
            FoodIDAndroidAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
        }
        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Download and initialize the model
        downloadModel()
    }


    private fun downloadModel() {
        val conditions = CustomModelDownloadConditions.Builder()
            .requireWifi()
            .build()

        FirebaseModelDownloader.getInstance()
            .getModel("Dish-Identifier", DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND, conditions)
            .addOnSuccessListener { model: CustomModel? ->
                // Download complete. Enable the ML feature or switch from the local model to the remote model.
                val modelFile: File? = model?.file
                if (modelFile != null) {
                    interpreter = Interpreter(modelFile)
                    Log.d("ModelDownload", "Model file path: ${modelFile.absolutePath}")
                    runInferenceOnImage()
                    //onComplete(true, "Model downloaded successfully!")
                } else {
                    //onComplete(false, "Model file is null")
                }
            }
            .addOnFailureListener { exception ->
                // Handle the failure
                exception.printStackTrace()
                //onComplete(false, "Failed to download model: ${exception.message}")
            }
    }

    private fun loadImageFromResources(): Bitmap {
        // Load the image from drawable resources
        return BitmapFactory.decodeResource(resources, R.drawable.surd_and_turf) // Replace with your image name
    }


    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        // Define the input size expected by the model
        val inputSize = 192 // Replace with your model's input size

        // Resize the image to the input size expected by your model
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        // Create a ByteBuffer to hold the input data
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3) // 1 image, width * height * channels
        inputBuffer.order(ByteOrder.nativeOrder()) // Set the byte order

        // Populate the ByteBuffer with pixel values
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = resizedBitmap.getPixel(x, y)
                // Store the pixel values as UINT8 (0-255)
                inputBuffer.put((pixel shr 16 and 0xFF).toByte()) // Red channel
                inputBuffer.put((pixel shr 8 and 0xFF).toByte())  // Green channel
                inputBuffer.put((pixel and 0xFF).toByte())         // Blue channel
            }
        }

        return inputBuffer
    }

    private fun runInferenceOnImage() {
        // Load and preprocess the image
        val bitmap = loadImageFromResources()
        val inputData = preprocessImage(bitmap) // This returns a ByteBuffer

        // Prepare an output array for the model's predictions
        val outputSize = 2024 // Adjust OUTPUT_SIZE based on your model (2024 for background + 2023 classes)
        //val outputData = Array(1) { FloatArray(outputSize) } // Prepare output as FloatArray
        val outputData = ByteBuffer.allocateDirect(1 * outputSize)

        // Run inference
        interpreter?.run(inputData, outputData) // Pass the ByteBuffer and output array

        outputData.rewind() // Reset the buffer position to read the data
        val result = ByteArray(outputSize)
        outputData.get(result) // Read the output data into a byte array

        // Find the index of the maximum value
        val maxIndex = result.indices.maxByOrNull { result[it].toInt() } ?: -1 // Convert to Int for comparison
        val maxValue = result[maxIndex].toInt() // Get the maximum value as Int

        Log.d("InferenceResult", "Index of max value: $maxIndex, Max value: $maxValue")

    }

}


@Composable
fun CameraButton(onButtonClick: () -> Unit) {
    Button(onClick = onButtonClick) {
        Text("Take Picture")
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FoodIDAndroidAppTheme {
        Greeting("Android")
    }
}