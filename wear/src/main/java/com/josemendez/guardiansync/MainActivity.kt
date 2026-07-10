package com.josemendez.guardiansync

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Surface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.josemendez.guardiansync.ui.theme.WearAppTheme
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.items
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.SwipeToDismissBoxState
import androidx.wear.compose.material.rememberSwipeToDismissBoxState
import com.google.android.gms.wearable.MessageClient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Asset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.offset
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity(), SensorEventListener, MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var steps by mutableStateOf(0)
    private val scope = CoroutineScope(Dispatchers.Main)
    private val dataClient by lazy { Wearable.getDataClient(this) }
    private var pressure by mutableStateOf<Float?>(null)
    private var temperature by mutableStateOf<Float?>(null)
    private var heartRate by mutableStateOf<Float?>(null)
    private var heartBeat by mutableStateOf<Float?>(null)
    private var batteryLevel by mutableStateOf<Int?>(null)
    private var pressureSensor: Sensor? = null
    private var temperatureSensor: Sensor? = null
    private var heartRateSensor: Sensor? = null
    private var heartBeatSensor: Sensor? = null
    private var hasBodySensorsPermission = false
    private var humidity by mutableStateOf<Float?>(null)
    private var proximity by mutableStateOf<Float?>(null)
    private var light by mutableStateOf<Float?>(null)
    private var magneticField by mutableStateOf<Float?>(null)
    private var humiditySensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var magneticFieldSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null
    private var accelX by mutableStateOf<Float?>(null)
    private var accelY by mutableStateOf<Float?>(null)
    private var accelZ by mutableStateOf<Float?>(null)
    private var gyroX by mutableStateOf<Float?>(null)
    private var gyroY by mutableStateOf<Float?>(null)
    private var gyroZ by mutableStateOf<Float?>(null)
    private var cameraReady by mutableStateOf(false)
    private var isProcessing by mutableStateOf(false)
    private var photoUri by mutableStateOf<String?>(null)
    private var notificationMessage by mutableStateOf<String?>(null)
    private var notificationType by mutableStateOf(NotificationType.INFO)
    private var showNotification by mutableStateOf(false)

    enum class NotificationType {
        SUCCESS, ERROR, INFO
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startStepCounting()
        } else {
            showNotification("Se necesitan permisos para contar pasos", NotificationType.ERROR)
        }
    }

    private val requestBodySensorsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasBodySensorsPermission = isGranted
        if (isGranted) {
            heartRateSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            heartBeatSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        heartBeatSensor = sensorManager.getDefaultSensor(65572) // TYPE_HEART_BEAT, valor constante no pública
        humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            WearAppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    val swipeState = rememberSwipeToDismissBoxState()
                    SwipeToDismissBox(state = swipeState) { isBackground ->
                        if (!isBackground) {
                            StepCounterScreen(
                                steps = steps,
                                onIncrement = { incrementSteps() },
                                onDecrement = { decrementSteps() },
                                onSyncClick = { syncSteps() },
                                pressure = pressure,
                                temperature = temperature,
                                heartRate = if (heartRateSensor == null || !hasBodySensorsPermission) null else heartRate,
                                heartBeat = if (heartBeatSensor == null || !hasBodySensorsPermission) null else heartBeat,
                                battery = batteryLevel,
                                heartRateAvailable = heartRateSensor != null,
                                heartBeatAvailable = heartBeatSensor != null,
                                humidity = humidity,
                                proximity = proximity,
                                light = light,
                                magneticField = magneticField,
                                accelX = accelX,
                                accelY = accelY,
                                accelZ = accelZ,
                                gyroX = gyroX,
                                gyroY = gyroY,
                                gyroZ = gyroZ
                            )
                        } else {
                            TakePhotoScreen(
                                onTakePhoto = { sendTakePhotoMessage() },
                                isProcessing = isProcessing,
                                cameraReady = cameraReady,
                                photoUri = photoUri
                            )
                        }
                    }

                    // Componente de notificación moderno
                    if (showNotification) {
                        ModernNotification(
                            message = notificationMessage ?: "",
                            type = notificationType,
                            onDismiss = { showNotification = false }
                        )
                    }
                }
            }
        }

        checkAndRequestPermissions()
        getBatteryLevel()
        Wearable.getMessageClient(this).addListener(this)
        Wearable.getDataClient(this).addListener(this)
    }

    private fun incrementSteps() {
        steps += 10
    }

    private fun decrementSteps() {
        if (steps >= 10) {
            steps -= 10
        }
    }

    private fun checkAndRequestPermissions() {
        val activityRecognitionGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
        val bodySensorsGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
        hasBodySensorsPermission = bodySensorsGranted
        if (activityRecognitionGranted) {
            startStepCounting()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (!bodySensorsGranted) {
            requestBodySensorsPermissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        } else {
            heartRateSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            heartBeatSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
        humiditySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        proximitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        magneticFieldSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometerSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscopeSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    private fun startStepCounting() {
        stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        pressureSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        temperatureSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        // heartRate y heartBeat solo si hay permiso, ya se manejan en checkAndRequestPermissions
        humiditySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        proximitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        magneticFieldSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometerSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscopeSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> steps = event.values[0].toInt()
            Sensor.TYPE_PRESSURE -> pressure = event.values[0]
            Sensor.TYPE_AMBIENT_TEMPERATURE -> temperature = event.values[0]
            Sensor.TYPE_HEART_RATE -> heartRate = event.values[0]
            65572 -> heartBeat = event.values[0] // TYPE_HEART_BEAT
            Sensor.TYPE_RELATIVE_HUMIDITY -> humidity = event.values[0]
            Sensor.TYPE_PROXIMITY -> proximity = event.values[0]
            Sensor.TYPE_LIGHT -> light = event.values[0]
            Sensor.TYPE_MAGNETIC_FIELD -> magneticField = event.values[0]
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroX = event.values[0]
                gyroY = event.values[1]
                gyroZ = event.values[2]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun getBatteryLevel() {
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryLevel = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    }

    private fun syncSteps() {
        scope.launch {
            try {
                val dataMap = com.google.android.gms.wearable.PutDataMapRequest.create("/steps").apply {
                    dataMap.putInt("steps", steps)
                    pressure?.let { dataMap.putFloat("pressure", it) }
                    temperature?.let { dataMap.putFloat("temperature", it) }
                    heartRate?.let { dataMap.putFloat("heartRate", it) }
                    heartBeat?.let { dataMap.putFloat("heartBeat", it) }
                    batteryLevel?.let { dataMap.putInt("battery", it) }
                    humidity?.let { dataMap.putFloat("humidity", it) }
                    proximity?.let { dataMap.putFloat("proximity", it) }
                    light?.let { dataMap.putFloat("light", it) }
                    magneticField?.let { dataMap.putFloat("magneticField", it) }
                    accelX?.let { dataMap.putFloat("accelX", it) }
                    accelY?.let { dataMap.putFloat("accelY", it) }
                    accelZ?.let { dataMap.putFloat("accelZ", it) }
                    gyroX?.let { dataMap.putFloat("gyroX", it) }
                    gyroY?.let { dataMap.putFloat("gyroY", it) }
                    gyroZ?.let { dataMap.putFloat("gyroZ", it) }
                }.asPutDataRequest()
                dataClient.putDataItem(dataMap).await()
                showNotification("Alerta SOS enviada", NotificationType.SUCCESS)
            } catch (e: Exception) {
                showNotification("Error al sincronizar", NotificationType.ERROR)
            }
        }
    }

    fun sendTakePhotoMessage() {
        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isNotEmpty()) {
                    if (!cameraReady) {
                        // Primer clic: abrir cámara
                        messageClient.sendMessage(nodes[0].id, "/take_photo", null).await()
                    } else {
                        // Segundo clic: tomar foto
                        isProcessing = true
                        messageClient.sendMessage(nodes[0].id, "/capture_photo", null).await()
                    }
                }else{
                    showNotification("Error: Empareje su celular con su SmartWatch", NotificationType.ERROR)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showNotification("Error: ${e.message}", NotificationType.ERROR)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onMessageReceived(event: com.google.android.gms.wearable.MessageEvent) {
        when (event.path) {
            "/photo_taken" -> {
                runOnUiThread {
                    cameraReady = true
                    showNotification("Cámara lista")
                }
            }
            "/photo_captured" -> {
                runOnUiThread {
                    isProcessing = false
                    cameraReady = false
                    // La imagen se recibirá como Asset
                    showNotification("Foto tomada")
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == com.google.android.gms.wearable.DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path?.compareTo("/photo_image") == 0) {
                    // Recibir imagen como Asset
                    val asset = dataItem.assets["photo"]
                    asset?.let {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val inputStream = Wearable.getDataClient(this@MainActivity).getFdForAsset(it).await().inputStream
                                // Guardar temporalmente y mostrar
                                val tempFile = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                                tempFile.outputStream().use { output ->
                                    inputStream.copyTo(output)
                                }
                                runOnUiThread {
                                    photoUri = tempFile.absolutePath
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    showNotification("Error al recibir imagen: ${e.message}", NotificationType.ERROR)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showNotification(message: String, type: NotificationType = NotificationType.INFO) {
        notificationMessage = message
        notificationType = type
        showNotification = true
    }
}

@Composable
fun StepCounterScreen(
    steps: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSyncClick: () -> Unit,
    pressure: Float?,
    temperature: Float?,
    heartRate: Float?,
    heartBeat: Float?,
    battery: Int?,
    heartRateAvailable: Boolean,
    heartBeatAvailable: Boolean,
    humidity: Float?,
    proximity: Float?,
    light: Float?,
    magneticField: Float?,
    accelX: Float?,
    accelY: Float?,
    accelZ: Float?,
    gyroX: Float?,
    gyroY: Float?,
    gyroZ: Float?
) {
    val statusText = when {
        heartRate != null && heartRate >= 120f -> "ALERTA"
        heartRate != null && heartRate >= 100f -> "CUIDADO"
        else -> "NORMAL"
    }
    val statusColor = when (statusText) {
        "ALERTA" -> Color(0xFFFF5252)
        "CUIDADO" -> Color(0xFFFFB300)
        else -> Color(0xFF00E676)
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF050B14),
                        Color(0xFF0A1B2E),
                        Color(0xFF050B14)
                    )
                )
            )
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🛡️",
                    style = MaterialTheme.typography.title1,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Wear Safe",
                    style = MaterialTheme.typography.title2,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
                Text(
                    text = "José Juan",
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF90A4AE)
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(statusColor.copy(alpha = 0.20f), RoundedCornerShape(50))
                    .border(1.dp, statusColor, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Estado $statusText",
                    color = statusColor,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827), RoundedCornerShape(20.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${heartRate?.toInt()?.toString() ?: "--"} bpm",
                        color = Color.White,
                        style = MaterialTheme.typography.title1,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Frecuencia cardíaca",
                        color = Color(0xFFB0BEC5),
                        style = MaterialTheme.typography.caption1,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            Button(
                onClick = onSyncClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFE53935))
            ) {
                Text("🆘 Enviar SOS", color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onDecrement,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263238))
                ) { Text("-10") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onIncrement,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263238))
                ) { Text("+10") }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            WearMetricChip("👣", "Pasos", steps.toString(), Color(0xFF40C4FF))
        }

        item {
            WearMetricChip("🌡️", "Temp.", "${temperature?.toInt() ?: "--"} °C", Color(0xFFFFAB40))
        }

        item {
            WearMetricChip("🌬️", "Presión", "${pressure?.toInt() ?: "--"} hPa", Color(0xFF448AFF))
        }

        item {
            WearMetricChip(
                "💓",
                "Heart Beat",
                if (!heartBeatAvailable) "NA" else heartBeat?.toInt()?.toString() ?: "--",
                Color(0xFFFF4081)
            )
        }

        item {
            WearMetricChip("🔋", "Batería", "${battery ?: "--"}%", Color(0xFF69F0AE))
        }

        item {
            WearMetricChip("💧", "Humedad", "${humidity?.toInt() ?: "--"}%", Color(0xFF4FC3F7))
        }

        item {
            WearMetricChip("☀️", "Luz", "${light?.toInt() ?: "--"} lx", Color(0xFFFFD740))
        }

        item {
            WearMetricChip("📍", "Proximidad", "${proximity?.toString() ?: "--"} cm", Color(0xFFCE93D8))
        }

        item {
            WearMetricChip("🧲", "Magnético", "${magneticField?.toInt() ?: "--"} μT", Color(0xFF64FFDA))
        }

        item {
            WearMetricChip("📳", "Movimiento", "X:${accelX?.toInt() ?: "--"} Y:${accelY?.toInt() ?: "--"}", Color(0xFFB388FF))
        }

        item {
            WearMetricChip("🌀", "Giro", "X:${gyroX?.toInt() ?: "--"} Y:${gyroY?.toInt() ?: "--"}", Color(0xFF18FFFF))
        }
    }
}

@Composable
fun WearMetricChip(
    icon: String,
    title: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Color(0xFF111827), RoundedCornerShape(18.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFFB0BEC5),
                style = MaterialTheme.typography.caption1
            )
            Text(
                text = value,
                color = color,
                style = MaterialTheme.typography.body1
            )
        }
    }
}

@Composable
fun TakePhotoScreen(
    onTakePhoto: () -> Unit,
    isProcessing: Boolean,
    cameraReady: Boolean,
    photoUri: String?
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Button(
                onClick = onTakePhoto,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = when {
                        isProcessing -> Color.Gray
                        cameraReady -> Color.Green
                        else -> MaterialTheme.colors.primary
                    }
                ),
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 220.dp)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(50))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        when {
                            isProcessing -> "Procesando..."
                            cameraReady -> "Tomar foto"
                            else -> "Preparar cámara"
                        },
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
            }
        }
        photoUri?.let { uri ->
            item {
                Spacer(modifier = Modifier.height(16.dp))
                ZoomableImage(imageUri = uri)
            }
        }
    }
}

@Composable
fun ZoomableImage(imageUri: String) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale *= zoom
                    offset += pan
                }
            }
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = "Foto tomada",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun ModernNotification(
    message: String,
    type: MainActivity.NotificationType,
    onDismiss: () -> Unit
) {
    val backgroundColor = when (type) {
        MainActivity.NotificationType.SUCCESS -> Color(0xFF4CAF50)
        MainActivity.NotificationType.ERROR -> Color(0xFFF44336)
        MainActivity.NotificationType.INFO -> Color(0xFF2196F3)
    }

    val icon = when (type) {
        MainActivity.NotificationType.SUCCESS -> Icons.Filled.CheckCircle
        MainActivity.NotificationType.ERROR -> Icons.Filled.Error
        MainActivity.NotificationType.INFO -> Icons.Filled.Info
    }

    val offsetY by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(durationMillis = 300),
        label = "notification_animation"
    )

    LaunchedEffect(Unit) {
        delay(3000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .zIndex(1000f)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = with(LocalDensity.current) { offsetY.toDp() })
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}