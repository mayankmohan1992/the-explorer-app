package com.explorer.app.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.explorer.app.ui.components.GlassmorphicCard
import com.explorer.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.atan2
import kotlin.math.roundToInt

sealed class ActiveTool {
    object PDF : ActiveTool()
    object Torch : ActiveTool()
    object Compass : ActiveTool()
    object Calculator : ActiveTool()
    object Stopwatch : ActiveTool()
    object BubbleLevel : ActiveTool()
    object VoiceRecorder : ActiveTool()
    object UnitConverter : ActiveTool()
}

data class ToolItem(val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tool: ActiveTool)

@Composable
fun ToolsTab() {
    val context = LocalContext.current
    var activeTool by remember { mutableStateOf<ActiveTool?>(null) }

    val toolsList = remember {
        listOf(
            ToolItem("Doc/PDF Reader", Icons.Default.PictureAsPdf, ActiveTool.PDF),
            ToolItem("Quick Torch", Icons.Default.FlashlightOn, ActiveTool.Torch),
            ToolItem("Compass", Icons.Default.Explore, ActiveTool.Compass),
            ToolItem("Calculator", Icons.Default.Calculate, ActiveTool.Calculator),
            ToolItem("Stopwatch", Icons.Default.Timer, ActiveTool.Stopwatch),
            ToolItem("Bubble Level", Icons.Default.AlignHorizontalCenter, ActiveTool.BubbleLevel),
            ToolItem("Voice Recorder", Icons.Default.Mic, ActiveTool.VoiceRecorder),
            ToolItem("Unit Converter", Icons.Default.Scale, ActiveTool.UnitConverter)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        Text(
            text = "SYSTEM UTILITIES",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = NeonCyan,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(toolsList) { item ->
                ToolCard(item = item, onClick = { activeTool = item.tool })
            }
        }
    }

    // Launch active tool dialogs
    activeTool?.let { tool ->
        Dialog(
            onDismissRequest = { activeTool = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepSpaceBackground.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { activeTool = null },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Close Tool", tint = Color.White)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 72.dp)
                ) {
                    when (tool) {
                        is ActiveTool.PDF -> PdfOrganizerTool(context)
                        is ActiveTool.Torch -> TorchTool(context)
                        is ActiveTool.Compass -> CompassTool(context)
                        is ActiveTool.Calculator -> CalculatorTool()
                        is ActiveTool.Stopwatch -> StopwatchTool()
                        is ActiveTool.BubbleLevel -> BubbleLevelTool(context)
                        is ActiveTool.VoiceRecorder -> VoiceRecorderTool(context)
                        is ActiveTool.UnitConverter -> UnitConverterTool()
                    }
                }
            }
        }
    }
}

@Composable
fun ToolCard(item: ToolItem, onClick: () -> Unit) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.name,
                tint = NeonCyan,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.name,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// 1. PDF Organizer & Reader Tool
@Composable
fun PdfOrganizerTool(context: Context) {
    var pdfFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedPdfFile by remember { mutableStateOf<File?>(null) }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Copy file descriptor to local app storage cache
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val destFile = File(context.cacheDir, "explorer_${System.currentTimeMillis()}.pdf")
                inputStream?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                pdfFiles = pdfFiles + destFile
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (selectedPdfFile != null) {
        PdfViewerScreen(selectedPdfFile!!) { selectedPdfFile = null }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PDF ORGANIZER", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, tint = DeepSpaceBackground)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Import PDF File", color = DeepSpaceBackground)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (pdfFiles.isEmpty()) {
                Text("No imported PDFs. Click import to load a document.", color = TextSecondary, fontSize = 12.sp)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(pdfFiles) { file ->
                        GlassmorphicCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPdfFile = file }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Book, contentDescription = null, tint = NeonCyan)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(file.name.take(30), color = TextPrimary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfViewerScreen(file: File, onClose: () -> Unit) {
    var pageCount by remember { mutableStateOf(0) }
    var currentPageIndex by remember { mutableStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val fileDescriptor = remember {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    val pdfRenderer = remember { PdfRenderer(fileDescriptor) }

    LaunchedEffect(file) {
        pageCount = pdfRenderer.pageCount
        currentPageIndex = 0
    }

    LaunchedEffect(currentPageIndex) {
        if (pageCount > 0) {
            val page = pdfRenderer.openPage(currentPageIndex)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pageBitmap = bitmap
            page.close()
        }
    }

    DisposableEffect(file) {
        onDispose {
            pdfRenderer.close()
            fileDescriptor.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "Page ${currentPageIndex + 1} of $pageCount",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Box(modifier = Modifier.width(48.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            pageBitmap?.let {
                androidx.compose.foundation.Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "PDF Page",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                enabled = currentPageIndex > 0
            ) {
                Text("Previous")
            }
            Button(
                onClick = { if (currentPageIndex < pageCount - 1) currentPageIndex++ },
                enabled = currentPageIndex < pageCount - 1
            ) {
                Text("Next")
            }
        }
    }
}

// 2. Torch Utility
@Composable
fun TorchTool(context: Context) {
    var isTorchOn by remember { mutableStateOf(false) }
    val cameraManager = remember {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    val cameraId = remember {
        try { cameraManager.cameraIdList[0] } catch (e: Exception) { null }
    }

    LaunchedEffect(isTorchOn) {
        cameraId?.let { id ->
            try {
                cameraManager.setTorchMode(id, isTorchOn)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraId?.let { id ->
                try { cameraManager.setTorchMode(id, false) } catch (e: Exception) {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("QUICK TORCH", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(40.dp))
        
        IconButton(
            onClick = { isTorchOn = !isTorchOn },
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(if (isTorchOn) NeonCyan else Color(0x11FFFFFF))
                .border(2.dp, NeonCyan, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.FlashlightOn,
                contentDescription = "Flashlight",
                tint = if (isTorchOn) DeepSpaceBackground else NeonCyan,
                modifier = Modifier.size(54.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (isTorchOn) "FLASHLIGHT ENABLED" else "FLASHLIGHT DISABLED",
            color = if (isTorchOn) NeonCyan else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// 3. Digital Compass Tool
@Composable
fun CompassTool(context: Context) {
    var headingDegrees by remember { mutableStateOf(0f) }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            var gravity: FloatArray? = null
            var geomagnetic: FloatArray? = null

            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    gravity = event.values
                }
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    geomagnetic = event.values
                }
                if (gravity != null && geomagnetic != null) {
                    val r = FloatArray(9)
                    val i = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(r, orientation)
                        val azimuthRad = orientation[0]
                        val azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                        headingDegrees = (azimuthDeg + 360) % 360
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("DIGITAL COMPASS", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(30.dp))
        
        Box(
            modifier = Modifier
                .size(240.dp)
                .border(2.dp, NeonCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = center
                val radius = (size.minDimension / 2f) - 16.dp.toPx()
                
                // Compass markings
                drawCircle(color = Color.White.copy(alpha = 0.1f), radius = radius)
                
                // Draw Dial rotated by heading
                rotate(-headingDegrees, centerOffset) {
                    // North indicator
                    drawLine(
                        color = NeonPink,
                        start = centerOffset,
                        end = Offset(centerOffset.x, centerOffset.y - radius + 10.dp.toPx()),
                        strokeWidth = 4.dp.toPx()
                    )
                    // South indicator
                    drawLine(
                        color = NeonCyan,
                        start = centerOffset,
                        end = Offset(centerOffset.x, centerOffset.y + radius - 10.dp.toPx()),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }
            Text("N", modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp), fontWeight = FontWeight.Bold, color = NeonPink)
            Text("S", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), fontWeight = FontWeight.Bold, color = NeonCyan)
            Text("W", modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp), fontWeight = FontWeight.Bold, color = Color.White)
            Text("E", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), fontWeight = FontWeight.Bold, color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "${headingDegrees.roundToInt()}°",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = NeonCyan
        )
    }
}

// 4. Custom Calculator Tool
@Composable
fun CalculatorTool() {
    var display by remember { mutableStateOf("0") }
    var expression by remember { mutableStateOf("") }

    val buttons = listOf(
        listOf("C", "(", ")", "/"),
        listOf("7", "8", "9", "*"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "⌫", "=")
    )

    fun onButtonClick(btn: String) {
        when (btn) {
            "C" -> {
                display = "0"
                expression = ""
            }
            "⌫" -> {
                if (expression.isNotEmpty()) {
                    expression = expression.dropLast(1)
                    display = expression.ifEmpty { "0" }
                }
            }
            "=" -> {
                try {
                    val result = evaluateMathExpression(expression)
                    display = result.toString()
                    expression = result.toString()
                } catch (e: Exception) {
                    display = "Error"
                }
            }
            else -> {
                if (display == "0" || display == "Error") {
                    expression = btn
                } else {
                    expression += btn
                }
                display = expression
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = display,
            fontSize = 48.sp,
            color = Color.White,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        )
        
        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { char ->
                    val isAction = char == "=" || char == "/" || char == "*" || char == "-" || char == "+"
                    Button(
                        onClick = { onButtonClick(char) },
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAction) NeonCyan else Color(0x11FFFFFF),
                            contentColor = if (isAction) DeepSpaceBackground else Color.White
                        )
                    ) {
                        Text(char, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun evaluateMathExpression(expr: String): Double {
    // Basic calculator evaluation logic
    val cleanExpr = expr.replace(" ", "")
    var currentVal = 0.0
    // Try simple operations first
    when {
        cleanExpr.contains("+") -> {
            val parts = cleanExpr.split("+")
            currentVal = parts[0].toDouble() + parts[1].toDouble()
        }
        cleanExpr.contains("-") -> {
            val parts = cleanExpr.split("-")
            currentVal = parts[0].toDouble() - parts[1].toDouble()
        }
        cleanExpr.contains("*") -> {
            val parts = cleanExpr.split("*")
            currentVal = parts[0].toDouble() * parts[1].toDouble()
        }
        cleanExpr.contains("/") -> {
            val parts = cleanExpr.split("/")
            currentVal = parts[0].toDouble() / parts[1].toDouble()
        }
        else -> currentVal = cleanExpr.toDouble()
    }
    return currentVal
}

// 5. Stopwatch Tool
@Composable
fun StopwatchTool() {
    var timeElapsed by remember { mutableStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    var laps = remember { mutableStateListOf<String>() }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            val startTime = SystemClock.uptimeMillis() - timeElapsed
            while (isRunning) {
                timeElapsed = SystemClock.uptimeMillis() - startTime
                delay(10)
            }
        }
    }

    val minutes = (timeElapsed / 60000) % 60
    val seconds = (timeElapsed / 1000) % 60
    val millis = (timeElapsed / 10) % 100
    val timeString = String.format("%02d:%02d.%02d", minutes, seconds, millis)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("STOPWATCH", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(30.dp))
        
        Text(
            text = timeString,
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 2.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    if (isRunning) {
                        isRunning = false
                    } else {
                        isRunning = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text(if (isRunning) "Pause" else "Start", color = DeepSpaceBackground)
            }

            Button(
                onClick = {
                    if (isRunning) {
                        laps.add(timeString)
                    } else {
                        timeElapsed = 0L
                        laps.clear()
                    }
                }
            ) {
                Text(if (isRunning) "Lap" else "Reset")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(laps.toList().reversed()) { lap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Lap", color = TextSecondary)
                    Text(lap, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 6. Bubble Level Tool
@Composable
fun BubbleLevelTool(context: Context) {
    var pitch by remember { mutableStateOf(0f) }
    var roll by remember { mutableStateOf(0f) }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    // Simple angle approximations
                    roll = event.values[0] * 9f
                    pitch = event.values[1] * 9f
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("BUBBLE LEVEL", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(200.dp)
                .border(2.dp, NeonCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Circle center grid
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = Color.White.copy(alpha = 0.05f), radius = size.minDimension / 4)
            }
            // Dynamic level bubble
            Box(
                modifier = Modifier
                    .offset(x = roll.dp, y = pitch.dp)
                    .size(24.dp)
                    .background(NeonCyan, CircleShape)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Roll: ${roll.roundToInt()}°  |  Pitch: ${pitch.roundToInt()}°", color = TextSecondary)
    }
}

// 7. Voice Recorder Tool
@Composable
fun VoiceRecorderTool(context: Context) {
    var isRecording by remember { mutableStateOf(false) }
    var recordList by remember { mutableStateOf<List<File>>(emptyList()) }
    var currentRecorder: MediaRecorder? by remember { mutableStateOf(null) }
    var currentRecordFile: File? by remember { mutableStateOf(null) }

    fun refreshRecords() {
        val recordsDir = context.getExternalFilesDir(null) ?: context.filesDir
        val files = recordsDir.listFiles { _, name -> name.endsWith(".3gp") }
        recordList = files?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    LaunchedEffect(Unit) {
        refreshRecords()
    }

    DisposableEffect(Unit) {
        onDispose {
            currentRecorder?.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("VOICE RECORDER", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(24.dp))

        IconButton(
            onClick = {
                if (isRecording) {
                    try {
                        currentRecorder?.apply {
                            stop()
                            release()
                        }
                    } catch (e: Exception) {}
                    currentRecorder = null
                    isRecording = false
                    refreshRecords()
                } else {
                    val file = File(
                        context.getExternalFilesDir(null) ?: context.filesDir,
                        "recording_${System.currentTimeMillis()}.3gp"
                    )
                    currentRecordFile = file
                    
                    val recorder = MediaRecorder().apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                        setOutputFile(file.absolutePath)
                        prepare()
                        start()
                    }
                    currentRecorder = recorder
                    isRecording = true
                }
            },
            modifier = Modifier
                .size(90.dp)
                .background(if (isRecording) NeonPink else NeonCyan, CircleShape)
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(if (isRecording) "RECORDING AUDIO..." else "TAP TO RECORD", color = TextSecondary, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(recordList) { file ->
                GlassmorphicCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val player = MediaPlayer().apply {
                                setDataSource(file.absolutePath)
                                prepare()
                                start()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(file.name.take(30), color = TextPrimary)
                    }
                }
            }
        }
    }
}

// 8. Unit Converter Tool
@Composable
fun UnitConverterTool() {
    var inputValue by remember { mutableStateOf("1") }
    var outputValue by remember { mutableStateOf("1000") }
    var conversionType by remember { mutableStateOf(0) } // 0 = km to m, 1 = kg to g, 2 = C to F

    fun convert() {
        val input = inputValue.toDoubleOrNull() ?: 0.0
        outputValue = when (conversionType) {
            0 -> (input * 1000.0).toString()
            1 -> (input * 1000.0).toString()
            else -> ((input * 9.0 / 5.0) + 32.0).toString()
        }
    }

    LaunchedEffect(inputValue, conversionType) {
        convert()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("UNIT CONVERTER", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { conversionType = 0 }) { Text("Km to M") }
            Button(onClick = { conversionType = 1 }) { Text("Kg to G") }
            Button(onClick = { conversionType = 2 }) { Text("C to F") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = inputValue,
            onValueChange = { inputValue = it },
            label = { Text("Input Value") },
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Converted Value:",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Text(
            text = outputValue,
            color = NeonCyan,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
