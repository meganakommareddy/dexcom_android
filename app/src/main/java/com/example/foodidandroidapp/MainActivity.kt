package com.example.foodidandroidapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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

@Composable
fun InferenceResultText(result: String) {
    Text(text = result)
}

class MainActivity : ComponentActivity() {
    private var interpreter: Interpreter? = null
    private var imageBitmap: Bitmap? = null
    private var resultText by mutableStateOf("")
    private lateinit var foodNames: List<String>

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to use this feature", Toast.LENGTH_LONG).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            runInferenceOnImage(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoodIDAndroidAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Greeting("Android")
                        CameraButton { checkCameraPermissionAndOpen() }
                        InferenceResultText(resultText)
                    }
                }
            }
        }
        FirebaseApp.initializeApp(this)
        downloadModel()
        readCsvFile()
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationaleDialog()
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("This app needs access to your camera to take pictures. Please grant the permission.")
            .setPositiveButton("Grant") { _, _ ->
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Camera permission is required to use this feature", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun openCamera() {
        takePictureLauncher.launch(null)
    }

    private fun downloadModel() {
        val conditions = CustomModelDownloadConditions.Builder()
            .requireWifi()
            .build()

        FirebaseModelDownloader.getInstance()
            .getModel("Dish-Identifier", DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND, conditions)
            .addOnSuccessListener { model: CustomModel? ->
                val modelFile: File? = model?.file
                if (modelFile != null) {
                    interpreter = Interpreter(modelFile)
                    Log.d("ModelDownload", "Model file path: ${modelFile.absolutePath}")
                }
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
            }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputSize = 192
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = resizedBitmap.getPixel(x, y)
                inputBuffer.put((pixel shr 16 and 0xFF).toByte())
                inputBuffer.put((pixel shr 8 and 0xFF).toByte())
                inputBuffer.put((pixel and 0xFF).toByte())
            }
        }

        return inputBuffer
    }

    private fun runInferenceOnImage(bitmap: Bitmap) {
        val inputData = preprocessImage(bitmap)
        val outputSize = 2024
        val outputData = ByteBuffer.allocateDirect(1 * outputSize)

        interpreter?.run(inputData, outputData)

        outputData.rewind()
        val result = ByteArray(outputSize)
        outputData.get(result)

        val maxIndex = result.indices.maxByOrNull { result[it].toInt() } ?: -1
        val maxValue = result[maxIndex].toInt()

        Log.d("InferenceResult", "Index of max value: $maxIndex, Max value: $maxValue")
        resultText = "Index of max value: $maxIndex, Max value: $maxValue"

        resultText = "Predicted food: ${foodNames[maxIndex]}"
    }

    private fun readCsvFile() {
        val csvReader = resources.openRawResource(R.raw.food_names).bufferedReader()
        val lines = csvReader.readLines()
        foodNames = lines.drop(1).map { line ->
            line.split(",")[1]
        }
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