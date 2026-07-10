package com.josemendez.guardiansync

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.*
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import com.google.android.gms.wearable.MessageClient
import android.content.Intent
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.os.Build

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener, TextToSpeech.OnInitListener {
    private val dataClient by lazy { Wearable.getDataClient(this) }
    private var currentSteps by mutableStateOf(0)
    private var stepGoal by mutableStateOf(10000)
    private var pressure by mutableStateOf<Float?>(null)
    private var temperature by mutableStateOf<Float?>(null)
    private var heartRate by mutableStateOf<Float?>(null)
    private var heartBeat by mutableStateOf<Float?>(null)
    private var battery by mutableStateOf<Int?>(null)
    private var humidity by mutableStateOf<Float?>(null)
    private var proximity by mutableStateOf<Float?>(null)
    private var light by mutableStateOf<Float?>(null)
    private var magneticField by mutableStateOf<Float?>(null)
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var photoFile: File? = null
    private var showCamera by mutableStateOf(false)
    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    internal var previewViewRef: PreviewView? = null
    private val CAMERA_PERMISSION_CODE = 1001
    private val SOS_CHANNEL_ID = "guardian_sync_sos"
    private lateinit var tts: TextToSpeech
    private var accelX by mutableStateOf<Float?>(null)
    private var accelY by mutableStateOf<Float?>(null)
    private var accelZ by mutableStateOf<Float?>(null)
    private var gyroX by mutableStateOf<Float?>(null)
    private var gyroY by mutableStateOf<Float?>(null)
    private var gyroZ by mutableStateOf<Float?>(null)
    private var lastSosTime by mutableStateOf("Sin alertas")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("WearAppMobile", "onCreate called")
        cameraExecutor = Executors.newSingleThreadExecutor()
        tts = TextToSpeech(this, this)
        createSosNotificationChannel()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.i("WearAppMobile", "Solicitando permiso de cámara")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        } else {
            Log.i("WearAppMobile", "Permiso de cámara ya concedido")
        }
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "main") {
                composable("main") {
                    MainScreen(
                        currentSteps = currentSteps,
                        stepGoal = stepGoal,
                        onGoalChange = { newGoal -> stepGoal = newGoal },
                        pressure = pressure,
                        temperature = temperature,
                        heartRate = heartRate,
                        heartBeat = heartBeat,
                        battery = battery,
                        humidity = humidity,
                        proximity = proximity,
                        light = light,
                        magneticField = magneticField,
                        accelX = accelX,
                        accelY = accelY,
                        accelZ = accelZ,
                        gyroX = gyroX,
                        gyroY = gyroY,
                        gyroZ = gyroZ,
                        lastSosTime = lastSosTime,
                        onSpeakStatus = { speakCurrentStatus() },
                        showCamera = showCamera,
                        activity = this@MainActivity,
                        onOpenGallery = { navController.navigate("gallery") }
                    )
                }
                composable("gallery") {
                    PhotoGalleryScreen(
                        activity = this@MainActivity,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
        dataClient.addListener(this)
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onResume() {
        super.onResume()
        Log.i("WearAppMobile", "onResume called")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso de cámara concedido", Toast.LENGTH_SHORT).show()
                Log.i("WearAppMobile", "Permiso de cámara concedido por el usuario")
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
                Log.e("WearAppMobile", "Permiso de cámara denegado por el usuario")
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path?.compareTo("/steps") == 0) {
                    DataMapItem.fromDataItem(dataItem).dataMap.apply {
                        val steps = getInt("steps")
                        val pressureVal = if (containsKey("pressure")) getFloat("pressure") else null
                        val temperatureVal = if (containsKey("temperature")) getFloat("temperature") else null
                        val heartRateVal = if (containsKey("heartRate")) getFloat("heartRate") else null
                        val heartBeatVal = if (containsKey("heartBeat")) getFloat("heartBeat") else null
                        val batteryVal = if (containsKey("battery")) getInt("battery") else null
                        val humidityVal = if (containsKey("humidity")) getFloat("humidity") else null
                        val proximityVal = if (containsKey("proximity")) getFloat("proximity") else null
                        val lightVal = if (containsKey("light")) getFloat("light") else null
                        val magneticFieldVal = if (containsKey("magneticField")) getFloat("magneticField") else null
                        val accelXVal = if (containsKey("accelX")) getFloat("accelX") else null
                        val accelYVal = if (containsKey("accelY")) getFloat("accelY") else null
                        val accelZVal = if (containsKey("accelZ")) getFloat("accelZ") else null
                        val gyroXVal = if (containsKey("gyroX")) getFloat("gyroX") else null
                        val gyroYVal = if (containsKey("gyroY")) getFloat("gyroY") else null
                        val gyroZVal = if (containsKey("gyroZ")) getFloat("gyroZ") else null
                        runOnUiThread {
                            currentSteps = steps
                            pressure = pressureVal
                            temperature = temperatureVal
                            heartRate = heartRateVal
                            heartBeat = heartBeatVal
                            battery = batteryVal
                            humidity = humidityVal
                            proximity = proximityVal
                            light = lightVal
                            magneticField = magneticFieldVal
                            accelX = accelXVal
                            accelY = accelYVal
                            accelZ = accelZVal
                            gyroX = gyroXVal
                            gyroY = gyroYVal
                            gyroZ = gyroZVal
                            lastSosTime = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date())
                            showSosNotification()
                            speakCurrentStatus()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dataClient.removeListener(this)
        Wearable.getMessageClient(this).removeListener(this)
        if (::tts.isInitialized) tts.shutdown()
    }


    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("es", "MX")
        }
    }

    private fun createSosNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SOS_CHANNEL_ID,
                "Alertas Guardian Sync",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de alerta recibidas desde el smartwatch"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showSosNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = Notification.Builder(this, SOS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🆘 Alerta SOS recibida")
            .setContentText("Guardian Sync recibió datos del smartwatch.")
            .setPriority(Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(2026, notification)
    }

    private fun speakCurrentStatus() {
        if (!::tts.isInitialized) return

        val heart = heartRate?.toInt()?.toString() ?: "sin lectura"
        val stepsText = currentSteps.toString()
        val batteryText = battery?.toString() ?: "sin lectura"
        val movement = if ((accelX ?: 0f) != 0f || (accelY ?: 0f) != 0f || (accelZ ?: 0f) != 0f) {
            "movimiento detectado"
        } else {
            "sin movimiento detectado"
        }

        val message = "Alerta recibida desde el reloj. Frecuencia cardíaca $heart latidos por minuto. Pasos $stepsText. Batería del reloj $batteryText por ciento. Estado de movimiento: $movement."
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "guardian_sync_status")
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            "/take_photo" -> {
                runOnUiThread {
                    showCamera = true
                    // startCamera se llamará desde el Composable cuando el PreviewView esté listo
                    sendPhotoTakenConfirmation()
                    Toast.makeText(this, "Cámara abierta, listo para tomar foto", Toast.LENGTH_SHORT).show()
                    println("DEBUG: Cámara activada, showCamera = $showCamera")
                }
            }
            "/capture_photo" -> {
                runOnUiThread {
                    takePhoto()
                    showCamera = false
                    println("DEBUG: Foto tomada, showCamera = $showCamera")
                }
            }
        }
    }

    fun startCamera(previewView: PreviewView) {
        println("DEBUG: Iniciando startCamera(previewView)")
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    imageCapture = ImageCapture.Builder().build()
                    cameraProvider.unbindAll()
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.bindToLifecycle(
                        this as LifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    println("DEBUG: SurfaceProvider configurado correctamente")
                } catch (e: Exception) {
                    println("DEBUG: Error al configurar cámara: "+Log.getStackTraceString(e))
                    Toast.makeText(this, "Error al abrir cámara: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            println("DEBUG: Error general en startCamera: "+Log.getStackTraceString(e))
            Toast.makeText(this, "Error general al abrir cámara: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        photoFile = File(
            getExternalFilesDir(null),
            "photo_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile!!).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    sendPhotoToWatch()
                    sendPhotoCapturedConfirmation()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Error al tomar foto: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun sendPhotoToWatch() {
        photoFile?.let { file ->
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val bytes = stream.toByteArray()

            val asset = Asset.createFromBytes(bytes)
            val dataMap = PutDataMapRequest.create("/photo_image").apply {
                dataMap.putAsset("photo", asset)
            }.asPutDataRequest()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dataClient.putDataItem(dataMap).await()
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error al enviar foto: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun sendPhotoTakenConfirmation() {
        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, "/photo_taken", null)
                }
            } catch (e: Exception) {
                Log.e("WearAppMobile", "Error enviando confirmación de foto: ", e)
            }
        }
    }

    private fun sendPhotoCapturedConfirmation() {
        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, "/photo_captured", null)
                }
            } catch (e: Exception) {
                Log.e("WearAppMobile", "Error enviando confirmación de captura: ", e)
            }
        }
    }
}

@Composable
fun MainScreen(
    currentSteps: Int,
    stepGoal: Int,
    onGoalChange: (Int) -> Unit,
    pressure: Float? = null,
    temperature: Float? = null,
    heartRate: Float? = null,
    heartBeat: Float? = null,
    battery: Int? = null,
    humidity: Float? = null,
    proximity: Float? = null,
    light: Float? = null,
    magneticField: Float? = null,
    accelX: Float? = null,
    accelY: Float? = null,
    accelZ: Float? = null,
    gyroX: Float? = null,
    gyroY: Float? = null,
    gyroZ: Float? = null,
    lastSosTime: String = "Sin alertas",
    onSpeakStatus: (() -> Unit)? = null,
    showCamera: Boolean = false,
    activity: MainActivity? = null,
    onOpenGallery: (() -> Unit)? = null
) {
    var goalInput by remember { mutableStateOf(TextFieldValue(stepGoal.toString())) }
    val context = LocalContext.current
    val percentage = if (stepGoal > 0) (currentSteps.toFloat() / stepGoal).coerceAtMost(1f) else 0f
    val statusText = when {
        heartRate != null && heartRate >= 120f -> "ALERTA"
        heartRate != null && heartRate >= 100f -> "PRECAUCIÓN"
        else -> "NORMAL"
    }
    val statusColor = when (statusText) {
        "ALERTA" -> Color(0xFFE53935)
        "PRECAUCIÓN" -> Color(0xFFFFA000)
        else -> Color(0xFF00C853)
    }

    if (showCamera) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        (context as? MainActivity)?.let { activity ->
                            activity.previewViewRef = this
                            Log.i("WearAppMobile", "Antes de llamar a startCamera")
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                activity.startCamera(this)
                            } else {
                                Toast.makeText(context, "Permiso de cámara no concedido", Toast.LENGTH_SHORT).show()
                                Log.e("WearAppMobile", "Permiso de cámara no concedido en Composable")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF07111F),
                            Color(0xFF102A43),
                            Color(0xFF0B1320)
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                elevation = CardDefaults.cardElevation(10.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "🛡️ Wear Safe Alert",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = "Monitoreo inteligente desde Wear OS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0BEC5)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                                .border(1.dp, statusColor, RoundedCornerShape(18.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Estado: $statusText",
                                color = statusColor,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = "SOS activo",
                            color = Color(0xFFFFCDD2),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "${heartRate?.toInt()?.toString() ?: "--"} bpm",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Frecuencia cardíaca recibida del reloj",
                        color = Color(0xFFB0BEC5),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF172033)),
                elevation = CardDefaults.cardElevation(8.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "📡 Datos recibidos del smartwatch",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        SensorCard(
                            title = "Pasos",
                            value = currentSteps.toString(),
                            unit = "hoy",
                            icon = "🏃‍♂️",
                            color = Color(0xFF00B8D4),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        SensorCard(
                            title = "Batería",
                            value = battery?.toString() ?: "--",
                            unit = "%",
                            icon = "🔋",
                            color = Color(0xFF00C853),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        SensorCard(
                            title = "Temperatura",
                            value = temperature?.toInt()?.toString() ?: "--",
                            unit = "°C",
                            icon = "🌡️",
                            color = Color(0xFFFF6D00),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        SensorCard(
                            title = "Presión",
                            value = pressure?.toInt()?.toString() ?: "--",
                            unit = "hPa",
                            icon = "🌬️",
                            color = Color(0xFF448AFF),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        SensorCard(
                            title = "Luz",
                            value = light?.toInt()?.toString() ?: "--",
                            unit = "lx",
                            icon = "🌞",
                            color = Color(0xFFFFD600),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        SensorCard(
                            title = "Humedad",
                            value = humidity?.toInt()?.toString() ?: "--",
                            unit = "%",
                            icon = "💧",
                            color = Color(0xFF40C4FF),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SensorWideCard(
                        icon = "🧲",
                        title = "Campo magnético",
                        value = "${magneticField?.toInt()?.toString() ?: "--"} μT",
                        color = Color(0xFF64FFDA)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SensorWideCard(
                        icon = "📍",
                        title = "Proximidad",
                        value = "${proximity?.toString() ?: "--"} cm",
                        color = Color(0xFFCE93D8)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SensorWideCard(
                        icon = "📳",
                        title = "Acelerómetro",
                        value = "X:${accelX?.toInt() ?: "--"}  Y:${accelY?.toInt() ?: "--"}  Z:${accelZ?.toInt() ?: "--"}",
                        color = Color(0xFFB388FF)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SensorWideCard(
                        icon = "🌀",
                        title = "Giroscopio",
                        value = "X:${gyroX?.toInt() ?: "--"}  Y:${gyroY?.toInt() ?: "--"}  Z:${gyroZ?.toInt() ?: "--"}",
                        color = Color(0xFF18FFFF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2438)),
                elevation = CardDefaults.cardElevation(8.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "🎯 Meta diaria de pasos",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$currentSteps de $stepGoal pasos",
                        color = Color(0xFFCFD8DC),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = percentage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Progreso: ${(percentage * 100).toInt()}%",
                        color = Color(0xFF80CBC4),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it },
                        label = { Text("Nueva meta de pasos") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val newGoal = goalInput.text.toIntOrNull()
                            if (newGoal != null && newGoal > 0) {
                                onGoalChange(newGoal)
                                Toast.makeText(context, "Meta actualizada", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Ingrese un número válido", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Actualizar meta")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1115)),
                elevation = CardDefaults.cardElevation(8.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "🆘 Flujo de emergencia",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cuando el usuario presiona SOS en el reloj, el celular recibe los sensores, genera notificación y lee el estado mediante TTS.",
                        color = Color(0xFFFFCDD2),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Última alerta: $lastSosTime",
                        color = Color(0xFFFFAB91),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onSpeakStatus?.invoke() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("🔊 Leer estado con voz")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onOpenGallery?.invoke() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = "Galería")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ver galería de evidencias")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SensorCard(
    title: String,
    value: String,
    unit: String,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(126.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = icon, style = MaterialTheme.typography.headlineSmall)
            Column {
                Text(text = title, color = Color(0xFFB0BEC5), style = MaterialTheme.typography.bodySmall)
                Text(text = "$value $unit", color = color, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
fun SensorWideCard(
    icon: String,
    title: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(18.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, color = Color(0xFFB0BEC5), style = MaterialTheme.typography.bodyMedium)
            Text(text = value, color = color, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun PhotoGalleryScreen(activity: MainActivity, onBack: () -> Unit) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf(listPhotoFiles(context)) }
    var selectedPhoto by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) {
                Text("Volver")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Galería de Fotos", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (photos.isEmpty()) {
            Text("No hay fotos disponibles.", modifier = Modifier.align(CenterHorizontally))
        } else {
            LazyColumn {
                items(photos) { file ->
                    val bitmap = remember(file) { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clickable { selectedPhoto = file },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Foto",
                                modifier = Modifier.size(64.dp),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(64.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(file.name)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_delete),
                            contentDescription = "Borrar",
                            modifier = Modifier
                                .size(32.dp)
                                .clickable {
                                    selectedPhoto = file
                                    showDeleteDialog = true
                                }
                        )
                    }
                }
            }
        }
    }
    // Vista grande de la foto
    if (selectedPhoto != null && !showDeleteDialog) {
        Dialog(onDismissRequest = { selectedPhoto = null }) {
            val bitmap = BitmapFactory.decodeFile(selectedPhoto!!.absolutePath)?.asImageBitmap()
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Foto grande",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("No se pudo cargar la imagen")
            }
        }
    }
    // Diálogo de confirmación de borrado
    if (showDeleteDialog && selectedPhoto != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Borrar foto?") },
            text = { Text("¿Seguro que deseas borrar esta foto?") },
            confirmButton = {
                TextButton(onClick = {
                    selectedPhoto?.delete()
                    photos = listPhotoFiles(context)
                    showDeleteDialog = false
                    selectedPhoto = null
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

fun listPhotoFiles(context: android.content.Context): List<File> {
    val dir = context.getExternalFilesDir(null)
    return dir?.listFiles { file -> file.name.startsWith("photo_") && file.name.endsWith(".jpg") }?.sortedByDescending { it.lastModified() } ?: emptyList()
}