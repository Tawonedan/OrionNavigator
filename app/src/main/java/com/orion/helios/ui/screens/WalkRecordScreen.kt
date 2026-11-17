package com.orion.helios.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orion.helios.model.BuildingMap
import com.orion.helios.model.MapNode
import com.orion.helios.sensor.OrientationManager
import com.orion.helios.ui.components.LiveMapVisualizer
import com.orion.helios.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkRecordScreen(
    buildingMap: BuildingMap,
    compassHeading: Float,
    gyroHeading: Float,
    rawGyroHeading: Float,
    stepCount: Int,
    onCalibrateGyroZero: (Float) -> Unit,
    onAlignGyroToCompass: (Float, Float) -> Unit,
    onStartWalking: (originNodeId: Int?, customName: String?, isDest: Boolean) -> Unit,
    onStepCountAdjusted: (delta: Int) -> Unit,
    onCancelWalking: () -> Unit,
    onSaveArrival: (
        nodeName: String,
        isDestination: Boolean,
        steps: Int,
        departureDegree: Double,
        departureGyro: Double,
        arrivalDegree: Double,
        arrivalGyro: Double,
        autoReverse: Boolean,
        continueWalking: Boolean
    ) -> Unit,
    isWalking: Boolean,
    currentOriginNodeId: Int?,
    onNavigateBack: () -> Unit
) {
    var snapToGrid by remember { mutableStateOf(false) }
    var selectedOriginId by remember { mutableStateOf(currentOriginNodeId ?: buildingMap.nodes.firstOrNull()?.id) }
    var isCreatingNewOrigin by remember { mutableStateOf(buildingMap.nodes.isEmpty()) }
    var newOriginName by remember { mutableStateOf("") }
    var newOriginIsDest by remember { mutableStateOf(false) }

    var departureCompass by remember { mutableDoubleStateOf(0.0) }
    var departureGyro by remember { mutableDoubleStateOf(0.0) }

    var showArrivalDialog by remember { mutableStateOf(false) }
    var arrivalNodeName by remember { mutableStateOf("") }
    var arrivalIsDestination by remember { mutableStateOf(false) }
    var autoCreateReverse by remember { mutableStateOf(true) }

    LaunchedEffect(currentOriginNodeId) {
        if (currentOriginNodeId != null) {
            selectedOriginId = currentOriginNodeId
            isCreatingNewOrigin = false
        }
    }

    val displayCompass = remember(compassHeading, snapToGrid) {
        if (snapToGrid) {
            OrientationManager.snapToGrid(compassHeading.toDouble())
        } else {
            compassHeading.toDouble()
        }
    }

    val displayGyro = remember(gyroHeading, snapToGrid) {
        if (snapToGrid) {
            OrientationManager.snapToGrid(gyroHeading.toDouble())
        } else {
            gyroHeading.toDouble()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Walk & Record", color = LightText) },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Dual Sensor Orientation", style = MaterialTheme.typography.labelSmall, color = DimText)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onCalibrateGyroZero(rawGyroHeading) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SolarGold)
                        ) {
                            Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Zero Gyro (0 deg)", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { onAlignGyroToCompass(rawGyroHeading, compassHeading) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Align to Compass", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Snap to 90 deg corridors", style = MaterialTheme.typography.bodyMedium, color = LightText)
                        Switch(
                            checked = snapToGrid,
                            onCheckedChange = { snapToGrid = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("COMPASS", style = MaterialTheme.typography.labelMedium, color = DimText)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${displayCompass.roundToInt()} deg",
                            style = MaterialTheme.typography.headlineMedium,
                            color = SolarGold
                        )
                        Text(
                            text = getCardinalDirection(displayCompass),
                            style = MaterialTheme.typography.bodySmall,
                            color = DimText
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("GYRO", style = MaterialTheme.typography.labelMedium, color = DimText)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${displayGyro.roundToInt()} deg",
                            style = MaterialTheme.typography.headlineMedium,
                            color = CyanAccent
                        )
                        Text(
                            text = getCardinalDirection(displayGyro),
                            style = MaterialTheme.typography.bodySmall,
                            color = DimText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!isWalking) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Select Origin Point", style = MaterialTheme.typography.titleMedium, color = LightText)
                        Spacer(modifier = Modifier.height(12.dp))

                        if (buildingMap.nodes.isNotEmpty() && !isCreatingNewOrigin) {
                            var expanded by remember { mutableStateOf(false) }
                            val originNode = buildingMap.nodes.find { it.id == selectedOriginId }

                            OutlinedCard(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = originNode?.name ?: "Select Starting Node",
                                        color = LightText
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = DimText)
                                }
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                buildingMap.nodes.forEach { node ->
                                    DropdownMenuItem(
                                        text = { Text("ID ${node.id}: ${node.name}") },
                                        onClick = {
                                            selectedOriginId = node.id
                                            expanded = false
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { isCreatingNewOrigin = true }) {
                                Text("+ Mark as completely new node instead", color = CyanAccent)
                            }
                        } else {
                            OutlinedTextField(
                                value = newOriginName,
                                onValueChange = { newOriginName = it },
                                label = { Text("Starting Node Name (e.g. Lobby)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = newOriginIsDest,
                                    onCheckedChange = { newOriginIsDest = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Is a destination room (not corridor)", color = LightText)
                            }
                            if (buildingMap.nodes.isNotEmpty()) {
                                TextButton(onClick = { isCreatingNewOrigin = false }) {
                                    Text("Pick from existing nodes", color = CyanAccent)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                departureCompass = displayCompass
                                departureGyro = displayGyro
                                if (isCreatingNewOrigin) {
                                    if (newOriginName.isNotBlank()) {
                                        onStartWalking(null, newOriginName.trim(), newOriginIsDest)
                                    }
                                } else {
                                    selectedOriginId?.let { onStartWalking(it, null, false) }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SolarGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = DeepCharcoal)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Walking to Next Node", color = DeepCharcoal, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val currentOrigin = buildingMap.nodes.find { it.id == currentOriginNodeId }
                        Text(
                            text = "Walking from: ${currentOrigin?.name ?: newOriginName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyanAccent
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("LOCKED DEPARTURE DIRECTION", style = MaterialTheme.typography.labelSmall, color = DimText)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Compass: ${departureCompass.roundToInt()} deg | Gyro: ${departureGyro.roundToInt()} deg",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SolarGold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "$stepCount",
                            fontSize = 72.sp,
                            color = SolarGold,
                            style = MaterialTheme.typography.displayLarge
                        )
                        Text(
                            text = "STEPS COUNTED",
                            style = MaterialTheme.typography.labelSmall,
                            color = DimText
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(onClick = { onStepCountAdjusted(-1) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Step -1")
                            }
                            Text("Manual Adjust", style = MaterialTheme.typography.bodySmall, color = DimText)
                            FilledTonalIconButton(onClick = { onStepCountAdjusted(1) }) {
                                Icon(Icons.Default.Add, contentDescription = "Step +1")
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Button(
                            onClick = {
                                arrivalNodeName = ""
                                arrivalIsDestination = false
                                autoCreateReverse = true
                                showArrivalDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = DeepCharcoal)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("I HAVE ARRIVED AT NEXT NODE", color = DeepCharcoal, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(onClick = onCancelWalking) {
                            Text("Cancel Segment", color = CrimsonRed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LiveMapVisualizer(
                buildingMap = buildingMap,
                highlightNodeId = if (isWalking) currentOriginNodeId else selectedOriginId,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showArrivalDialog) {
        AlertDialog(
            onDismissRequest = { showArrivalDialog = false },
            title = { Text("Arrival Details") },
            text = {
                Column {
                    Text(
                        text = "Segment: $stepCount steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = LightText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Departure: ${departureCompass.roundToInt()} deg (Gyro: ${departureGyro.roundToInt()} deg)",
                        style = MaterialTheme.typography.bodySmall,
                        color = SolarGold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Arrival: ${displayCompass.roundToInt()} deg (Gyro: ${displayGyro.roundToInt()} deg)",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanAccent
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = arrivalNodeName,
                        onValueChange = { arrivalNodeName = it },
                        label = { Text("Name of this arrival spot") },
                        placeholder = { Text("e.g. Pertigaan Koridor, Ruang Guru") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = arrivalIsDestination,
                            onCheckedChange = { arrivalIsDestination = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Destination room (not corridor junction)")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = autoCreateReverse,
                            onCheckedChange = { autoCreateReverse = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-create return edge (${((departureCompass + 180.0) % 360.0).roundToInt()} deg)")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (arrivalNodeName.isNotBlank()) {
                            showArrivalDialog = false
                            onSaveArrival(
                                arrivalNodeName.trim(),
                                arrivalIsDestination,
                                stepCount,
                                departureCompass,
                                departureGyro,
                                displayCompass,
                                displayGyro,
                                autoCreateReverse,
                                true
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SolarGold)
                ) {
                    Text("Save & Next Segment", color = DeepCharcoal)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (arrivalNodeName.isNotBlank()) {
                            showArrivalDialog = false
                            onSaveArrival(
                                arrivalNodeName.trim(),
                                arrivalIsDestination,
                                stepCount,
                                departureCompass,
                                departureGyro,
                                displayCompass,
                                displayGyro,
                                autoCreateReverse,
                                false
                            )
                        }
                    }
                ) {
                    Text("Save & Stop")
                }
            }
        )
    }
}

private fun getCardinalDirection(deg: Double): String {
    val d = (deg % 360.0 + 360.0) % 360.0
    return when {
        d >= 337.5 || d < 22.5 -> "N"
        d < 67.5 -> "NE"
        d < 112.5 -> "E"
        d < 157.5 -> "SE"
        d < 202.5 -> "S"
        d < 247.5 -> "SW"
        d < 292.5 -> "W"
        else -> "NW"
    }
}
