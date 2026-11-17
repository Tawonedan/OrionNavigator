package com.orion.helios.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.orion.helios.model.BuildingMap
import com.orion.helios.model.MapEdge
import com.orion.helios.model.MapNode
import com.orion.helios.ui.theme.*
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualizerScreen(
    currentMap: BuildingMap,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val gson = remember { Gson() }

    var selectedMap by remember { mutableStateOf(currentMap) }
    var mapSourceLabel by remember { mutableStateOf("Current Workspace Map") }
    var stepLengthInput by remember { mutableStateOf("75") }
    var stepLengthCm by remember { mutableFloatStateOf(75f) }
    var isConfigured by remember { mutableStateOf(false) }
    var useGyroHeading by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var showStepDialog by remember { mutableStateOf(false) }
    var textSizeSp by remember { mutableFloatStateOf(8.5f) }
    var useArrivalHeading by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                val sb = StringBuilder()
                context.contentResolver.openInputStream(it)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            sb.append(line).append('\n')
                            line = reader.readLine()
                        }
                    }
                }
                val parsed = gson.fromJson(sb.toString(), BuildingMap::class.java)
                if (parsed != null && parsed.nodes.isNotEmpty()) {
                    selectedMap = parsed
                    mapSourceLabel = "Imported File (${parsed.nodes.size} nodes, ${parsed.edges.size} edges)"
                    Toast.makeText(context, "Loaded ${parsed.buildingName}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid JSON map format", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (!isConfigured) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Map Visualizer Setup", color = LightText) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LightText)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepCharcoal)
                )
            },
            containerColor = DeepCharcoal
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("1. Select Map JSON", style = MaterialTheme.typography.titleMedium, color = SolarGold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Current: ${selectedMap.buildingName}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = LightText,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = mapSourceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = DimText
                        )
                        Text(
                            text = "${selectedMap.nodes.size} nodes, ${selectedMap.edges.size} edges",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanAccent
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { filePickerLauncher.launch(arrayOf("application/json", "*/*")) },
                                colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceVariant),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = SolarGold, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose File", color = LightText)
                            }

                            if (selectedMap != currentMap) {
                                OutlinedButton(
                                    onClick = {
                                        selectedMap = currentMap
                                        mapSourceLabel = "Current Workspace Map"
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Reset to Draft")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("2. Step Length", style = MaterialTheme.typography.titleMedium, color = SolarGold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Used to calculate real-world distance between nodes", style = MaterialTheme.typography.bodySmall, color = DimText)

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = stepLengthInput,
                            onValueChange = {
                                stepLengthInput = it
                                val parsed = it.toFloatOrNull()
                                if (parsed != null && parsed > 0f) {
                                    stepLengthCm = parsed
                                }
                            },
                            label = { Text("Step Length (cm)") },
                            suffix = { Text("cm") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(65, 70, 75, 80, 85).forEach { cm ->
                                FilterChip(
                                    selected = stepLengthCm == cm.toFloat(),
                                    onClick = {
                                        stepLengthCm = cm.toFloat()
                                        stepLengthInput = "$cm"
                                    },
                                    label = { Text("${cm}cm") }
                                )
                            }
                        }
                    }
                }

                val hasGyroData = remember(selectedMap) { selectedMap.edges.any { it.gyro != null } }
                if (hasGyroData) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("3. Orientation Source", style = MaterialTheme.typography.titleMedium, color = SolarGold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !useGyroHeading,
                                    onClick = { useGyroHeading = false },
                                    label = { Text("Compass (degree)") }
                                )
                                FilterChip(
                                    selected = useGyroHeading,
                                    onClick = { useGyroHeading = true },
                                    label = { Text("Gyroscope (gyro)") }
                                )
                            }
                        }
                    }
                }

                val hasArrivalData = remember(selectedMap) { selectedMap.edges.any { it.arrivalDegree != null } }
                if (hasArrivalData) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("4. Direction Angle Mode", style = MaterialTheme.typography.titleMedium, color = SolarGold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !useArrivalHeading,
                                    onClick = { useArrivalHeading = false },
                                    label = { Text("Departure (Recommended)") }
                                )
                                FilterChip(
                                    selected = useArrivalHeading,
                                    onClick = { useArrivalHeading = true },
                                    label = { Text("Arrival") }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val parsed = stepLengthInput.toFloatOrNull() ?: 75f
                        stepLengthCm = parsed
                        isConfigured = true
                    },
                    enabled = selectedMap.nodes.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = SolarGold),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DeepCharcoal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Continue to Visualizer", color = DeepCharcoal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        val nodePositions = remember(selectedMap, stepLengthCm, useGyroHeading, useArrivalHeading) {
            calculateGraphLayout(selectedMap, stepLengthCm, useGyroHeading, useArrivalHeading)
        }

        val textMeasurer = rememberTextMeasurer()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(selectedMap.buildingName, color = LightText, style = MaterialTheme.typography.titleMedium)
                            val headingType = if (useArrivalHeading) "Arrival" else "Departure"
                            val sensorType = if (useGyroHeading) "Gyro" else "Compass"
                            Text(
                                "Step: ${stepLengthCm.roundToInt()}cm | $sensorType ($headingType)",
                                color = DimText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { isConfigured = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LightText)
                        }
                    },
                    actions = {
                        val hasArrival = selectedMap.edges.any { it.arrivalDegree != null }
                        if (hasArrival) {
                            IconButton(onClick = { useArrivalHeading = !useArrivalHeading }) {
                                Icon(
                                    Icons.Default.CompareArrows,
                                    contentDescription = "Toggle Departure/Arrival Angle",
                                    tint = if (useArrivalHeading) CyanAccent else SolarGold
                                )
                            }
                        }
                        val hasGyro = selectedMap.edges.any { it.gyro != null }
                        if (hasGyro) {
                            IconButton(onClick = { useGyroHeading = !useGyroHeading }) {
                                Icon(
                                    Icons.Default.SyncAlt,
                                    contentDescription = "Toggle Compass/Gyro",
                                    tint = if (useGyroHeading) CyanAccent else SolarGold
                                )
                            }
                        }
                        IconButton(onClick = { showStepDialog = true }) {
                            Icon(Icons.Default.Straighten, contentDescription = "Adjust Step Length", tint = LightText)
                        }
                        IconButton(onClick = {
                            scale = 1.0f
                            panOffset = Offset.Zero
                        }) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset Zoom", tint = SolarGold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepCharcoal)
                )
            },
            containerColor = DeepCharcoal
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(0.1f, 15f)
                                panOffset = (panOffset - centroid) * (newScale / scale) + centroid + pan
                                scale = newScale
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val centerCanvas = Offset(canvasWidth / 2f, canvasHeight / 2f)

                    withTransform({
                        translate(centerCanvas.x + panOffset.x, centerCanvas.y + panOffset.y)
                        scale(scale, scale, pivot = Offset.Zero)
                    }) {
                        val gridSize = 100f
                        val gridRange = 25
                        for (i in -gridRange..gridRange) {
                            val coord = i * gridSize
                            drawLine(
                                color = Color(0xFF22262E),
                                start = Offset(coord, -gridRange * gridSize),
                                end = Offset(coord, gridRange * gridSize),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = Color(0xFF22262E),
                                start = Offset(-gridRange * gridSize, coord),
                                end = Offset(gridRange * gridSize, coord),
                                strokeWidth = 1f
                            )
                        }

                        val drawnLineSegments = mutableSetOf<String>()
                        selectedMap.edges.forEach { edge ->
                            val p1 = nodePositions[edge.from]
                            val p2 = nodePositions[edge.to]
                            if (p1 != null && p2 != null) {
                                val segmentKey = if (edge.from < edge.to) "${edge.from}-${edge.to}" else "${edge.to}-${edge.from}"
                                if (drawnLineSegments.add(segmentKey)) {
                                    drawLine(
                                        color = Color(0xFF3F4654),
                                        start = p1,
                                        end = p2,
                                        strokeWidth = 3f / scale.coerceAtLeast(0.5f)
                                    )
                                }
                            }
                        }

                        selectedMap.edges.forEach { edge ->
                            val p1 = nodePositions[edge.from]
                            val p2 = nodePositions[edge.to]
                            if (p1 != null && p2 != null) {
                                val minId = minOf(edge.from, edge.to)
                                val maxId = maxOf(edge.from, edge.to)
                                val pA = nodePositions[minId] ?: p1
                                val pB = nodePositions[maxId] ?: p2

                                val dxAB = pB.x - pA.x
                                val dyAB = pB.y - pA.y
                                val lengthAB = hypot(dxAB, dyAB)

                                if (lengthAB > 1f) {
                                    val ux = dxAB / lengthAB
                                    val uy = dyAB / lengthAB
                                    val nx = -uy
                                    val ny = ux

                                    var baseDeg = Math.toDegrees(atan2(dyAB.toDouble(), dxAB.toDouble())).toFloat()
                                    if (baseDeg > 90f) {
                                        baseDeg -= 180f
                                    } else if (baseDeg < -90f) {
                                        baseDeg += 180f
                                    }

                                    val isForward = edge.from < edge.to
                                    val edgeColor = if (isForward) SolarGold else CyanAccent
                                    val sideSign = if (isForward) 1f else -1f

                                    val arrowX = if (isForward) pA.x + dxAB * 0.68f else pA.x + dxAB * 0.32f
                                    val arrowY = if (isForward) pA.y + dyAB * 0.68f else pA.y + dyAB * 0.32f
                                    val travelAngleRad = atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
                                    val arrowDist = 14f

                                    val arrowPath = Path().apply {
                                        moveTo(arrowX, arrowY)
                                        lineTo(
                                            (arrowX - arrowDist * cos(travelAngleRad - Math.PI / 6)).toFloat(),
                                            (arrowY - arrowDist * sin(travelAngleRad - Math.PI / 6)).toFloat()
                                        )
                                        lineTo(
                                            (arrowX - arrowDist * cos(travelAngleRad + Math.PI / 6)).toFloat(),
                                            (arrowY - arrowDist * sin(travelAngleRad + Math.PI / 6)).toFloat()
                                        )
                                        close()
                                    }
                                    drawPath(arrowPath, color = edgeColor)

                                    val headingVal = if (useGyroHeading) {
                                        if (useArrivalHeading) (edge.arrivalGyro ?: edge.departureGyro ?: edge.gyro ?: 0.0)
                                        else (edge.departureGyro ?: edge.gyro ?: edge.arrivalGyro ?: 0.0)
                                    } else {
                                        if (useArrivalHeading) (edge.arrivalDegree ?: edge.departureDegree ?: edge.degree)
                                        else (edge.departureDegree ?: edge.degree)
                                    }
                                    val distMeters = edge.steps * (stepLengthCm / 100f)
                                    val labelText = "${edge.steps}s (${"%.1f".format(distMeters)}m) ${headingVal.roundToInt()}°"

                                    val textResult = textMeasurer.measure(
                                        text = labelText,
                                        style = TextStyle(
                                            fontSize = textSizeSp.sp,
                                            color = edgeColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )

                                    val textMidDist = if (isForward) 0.52f else 0.48f
                                    val midX = pA.x + dxAB * textMidDist
                                    val midY = pA.y + dyAB * textMidDist
                                    val textOffsetDist = textSizeSp * 1.5f + 4f
                                    val labelCenterX = midX + nx * sideSign * textOffsetDist
                                    val labelCenterY = midY + ny * sideSign * textOffsetDist

                                    withTransform({
                                        translate(labelCenterX, labelCenterY)
                                        rotate(baseDeg, pivot = Offset.Zero)
                                    }) {
                                        drawRoundRect(
                                            color = DeepCharcoal.copy(alpha = 0.85f),
                                            topLeft = Offset(-textResult.size.width / 2f - 4f, -textResult.size.height / 2f - 2f),
                                            size = Size(
                                                textResult.size.width + 8f,
                                                textResult.size.height + 4f
                                            ),
                                            cornerRadius = CornerRadius(4f, 4f)
                                        )
                                        drawText(
                                            textLayoutResult = textResult,
                                            topLeft = Offset(-textResult.size.width / 2f, -textResult.size.height / 2f)
                                        )
                                    }
                                }
                            }
                        }

                        selectedMap.nodes.forEach { node ->
                            val pos = nodePositions[node.id] ?: Offset.Zero
                            val nodeRadius = 22f

                            val nodeBg = if (node.isDestination) SolarGold else CyanAccent
                            drawCircle(
                                color = DeepCharcoal,
                                radius = nodeRadius + 3f,
                                center = pos
                            )
                            drawCircle(
                                color = nodeBg,
                                radius = nodeRadius,
                                center = pos
                            )
                            drawCircle(
                                color = DeepCharcoal,
                                radius = nodeRadius - 5f,
                                center = pos
                            )

                            val idResult = textMeasurer.measure(
                                text = "${node.id}",
                                style = TextStyle(fontSize = 12.sp, color = nodeBg, fontWeight = FontWeight.Bold)
                            )
                            drawText(
                                textLayoutResult = idResult,
                                topLeft = Offset(pos.x - idResult.size.width / 2f, pos.y - idResult.size.height / 2f)
                            )

                            val nameResult = textMeasurer.measure(
                                text = node.name,
                                style = TextStyle(fontSize = (textSizeSp + 1.5f).sp, color = LightText, fontWeight = FontWeight.Medium)
                            )
                            drawRoundRect(
                                color = DeepCharcoal.copy(alpha = 0.85f),
                                topLeft = Offset(pos.x - nameResult.size.width / 2f - 4f, pos.y + nodeRadius + 3f),
                                size = Size(nameResult.size.width + 8f, nameResult.size.height + 2f),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                            drawText(
                                textLayoutResult = nameResult,
                                topLeft = Offset(pos.x - nameResult.size.width / 2f, pos.y + nodeRadius + 4f)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.92f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null, tint = SolarGold, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Text: ${"%.1f".format(textSizeSp)}sp",
                                style = MaterialTheme.typography.bodySmall,
                                color = LightText,
                                fontWeight = FontWeight.SemiBold
                            )
                            FilledTonalIconButton(
                                onClick = { if (textSizeSp > 5f) textSizeSp = (textSizeSp - 1f).coerceAtLeast(5f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease Text Size", modifier = Modifier.size(14.dp))
                            }
                            FilledTonalIconButton(
                                onClick = { if (textSizeSp < 18f) textSizeSp = (textSizeSp + 1f).coerceAtMost(18f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase Text Size", modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.92f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "${selectedMap.nodes.size} Nodes",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedMap.edges.size} Edges",
                                style = MaterialTheme.typography.bodySmall,
                                color = SolarGold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Zoom: ${(scale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = DimText
                            )
                            FilledTonalIconButton(
                                onClick = { scale = (scale * 1.25f).coerceAtMost(15f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
                            }
                            FilledTonalIconButton(
                                onClick = { scale = (scale / 1.25f).coerceAtLeast(0.1f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStepDialog) {
        var tempStep by remember { mutableStateOf("$stepLengthCm") }
        AlertDialog(
            onDismissRequest = { showStepDialog = false },
            title = { Text("Adjust Step Length") },
            text = {
                OutlinedTextField(
                    value = tempStep,
                    onValueChange = { tempStep = it },
                    label = { Text("Step length (cm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val p = tempStep.toFloatOrNull()
                    if (p != null && p > 0f) {
                        stepLengthCm = p
                        stepLengthInput = "$p"
                    }
                    showStepDialog = false
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStepDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun calculateGraphLayout(
    map: BuildingMap,
    stepLengthCm: Float,
    useGyro: Boolean,
    useArrivalHeading: Boolean = false
): Map<Int, Offset> {
    val positions = mutableMapOf<Int, Offset>()
    if (map.nodes.isEmpty()) return positions

    val nodeIds = map.nodes.map { it.id }.toSet()
    val pixelsPerMeter = 36f
    val stepLengthMeters = stepLengthCm / 100f

    val unvisited = nodeIds.toMutableSet()
    var componentOffsetX = 0f

    while (unvisited.isNotEmpty()) {
        val rootId = unvisited.first()
        unvisited.remove(rootId)
        positions[rootId] = Offset(componentOffsetX, 0f)

        val queue = ArrayDeque<Int>()
        queue.add(rootId)

        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            val posU = positions[u] ?: continue

            for (edge in map.edges) {
                val edgeHeading = if (useGyro) {
                    if (useArrivalHeading) (edge.arrivalGyro ?: edge.departureGyro ?: edge.gyro ?: 0.0)
                    else (edge.departureGyro ?: edge.gyro ?: edge.arrivalGyro ?: 0.0)
                } else {
                    if (useArrivalHeading) (edge.arrivalDegree ?: edge.departureDegree ?: edge.degree)
                    else (edge.departureDegree ?: edge.degree)
                }

                if (edge.from == u && edge.to in unvisited) {
                    val v = edge.to
                    val distPixels = edge.steps * stepLengthMeters * pixelsPerMeter
                    val rad = Math.toRadians(edgeHeading)
                    val dx = (distPixels * sin(rad)).toFloat()
                    val dy = (-distPixels * cos(rad)).toFloat()

                    positions[v] = Offset(posU.x + dx, posU.y + dy)
                    unvisited.remove(v)
                    queue.add(v)
                } else if (edge.to == u && edge.from in unvisited) {
                    val v = edge.from
                    val revDeg = (edgeHeading + 180.0) % 360.0
                    val distPixels = edge.steps * stepLengthMeters * pixelsPerMeter
                    val rad = Math.toRadians(revDeg)
                    val dx = (distPixels * sin(rad)).toFloat()
                    val dy = (-distPixels * cos(rad)).toFloat()

                    positions[v] = Offset(posU.x + dx, posU.y + dy)
                    unvisited.remove(v)
                    queue.add(v)
                }
            }
        }

        componentOffsetX += 300f
    }

    if (positions.isNotEmpty()) {
        val minX = positions.values.minOf { it.x }
        val maxX = positions.values.maxOf { it.x }
        val minY = positions.values.minOf { it.y }
        val maxY = positions.values.maxOf { it.y }
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        for ((id, pos) in positions) {
            positions[id] = Offset(pos.x - centerX, pos.y - centerY)
        }
    }

    return positions
}

