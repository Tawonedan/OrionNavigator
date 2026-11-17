package com.orion.helios

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.orion.helios.model.BuildingMap
import com.orion.helios.model.MapNode
import com.orion.helios.repository.MapRepository
import com.orion.helios.sensor.OrientationManager
import com.orion.helios.sensor.StepManager
import com.orion.helios.ui.screens.DashboardScreen
import com.orion.helios.ui.screens.GpsCoordinateScreen
import com.orion.helios.ui.screens.GraphInspectorScreen
import com.orion.helios.ui.screens.RouteTestScreen
import com.orion.helios.ui.screens.VisualizerScreen
import com.orion.helios.ui.screens.WalkRecordScreen
import com.orion.helios.ui.theme.HeliosTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

enum class AppScreen {
    DASHBOARD,
    WALK_RECORD,
    GRAPH_INSPECTOR,
    ROUTE_TEST,
    MAP_VISUALIZER,
    GPS_TOOL
}

class MainActivity : ComponentActivity() {

    private lateinit var mapRepository: MapRepository
    private lateinit var orientationManager: OrientationManager
    private lateinit var stepManager: StepManager

    private var compassHeadingState = mutableFloatStateOf(0f)
    private var gyroHeadingState = mutableFloatStateOf(0f)
    private var rawGyroHeadingState = mutableFloatStateOf(0f)
    private var stepCountState = mutableIntStateOf(0)
    private var isWalkingState = mutableStateOf(false)
    private var currentOriginNodeIdState = mutableStateOf<Int?>(null)
    private var currentScreenState = mutableStateOf(AppScreen.DASHBOARD)

    private var orientationJob: Job? = null
    private var stepJob: Job? = null

    private val exportJsonLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(mapRepository.toJsonString().toByteArray())
                }
                Toast.makeText(this, "Map exported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importJsonLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                val sb = StringBuilder()
                contentResolver.openInputStream(it)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            sb.append(line).append('\n')
                            line = reader.readLine()
                        }
                    }
                }
                val success = mapRepository.loadFromJson(sb.toString())
                if (success) {
                    Toast.makeText(this, "Map imported successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to parse JSON map", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startSensors()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mapRepository = MapRepository(this)
        orientationManager = OrientationManager(this)
        stepManager = StepManager(this)

        requestNeededPermissions()

        setContent {
            HeliosTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val buildingMap by mapRepository.mapState.collectAsState()
                    val currentScreen by currentScreenState
                    val compassHeading by compassHeadingState
                    val gyroHeading by gyroHeadingState
                    val rawGyroHeading by rawGyroHeadingState
                    val stepCount by stepCountState
                    val isWalking by isWalkingState
                    val currentOriginNodeId by currentOriginNodeIdState

                    when (currentScreen) {
                        AppScreen.DASHBOARD -> {
                            DashboardScreen(
                                buildingMap = buildingMap,
                                onUpdateBuildingName = { mapRepository.updateBuildingName(it) },
                                onStartWalkRecord = { currentScreenState.value = AppScreen.WALK_RECORD },
                                onOpenGraphInspector = { currentScreenState.value = AppScreen.GRAPH_INSPECTOR },
                                onOpenRouteTest = { currentScreenState.value = AppScreen.ROUTE_TEST },
                                onOpenVisualizer = { currentScreenState.value = AppScreen.MAP_VISUALIZER },
                                onOpenGpsTool = { currentScreenState.value = AppScreen.GPS_TOOL },
                                onExportJson = { exportJsonLauncher.launch("Map.json") },
                                onImportJson = { importJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                                onResetMap = { mapRepository.resetMap() }
                            )
                        }

                        AppScreen.WALK_RECORD -> {
                            WalkRecordScreen(
                                buildingMap = buildingMap,
                                compassHeading = compassHeading,
                                gyroHeading = gyroHeading,
                                rawGyroHeading = rawGyroHeading,
                                stepCount = stepCount,
                                onCalibrateGyroZero = { raw ->
                                    orientationManager.calibrateGyroZero(raw)
                                    Toast.makeText(this, "Gyro calibrated to 0 deg", Toast.LENGTH_SHORT).show()
                                },
                                onAlignGyroToCompass = { raw, compass ->
                                    orientationManager.alignGyroToCompass(raw, compass)
                                    Toast.makeText(this, "Gyro aligned to compass", Toast.LENGTH_SHORT).show()
                                },
                                onStartWalking = { originId, customName, isDest ->
                                    val actualOriginId = if (originId == null) {
                                        val firstNode = mapRepository.addNode(customName ?: "Origin", isDest)
                                        firstNode.id
                                    } else originId

                                    currentOriginNodeIdState.value = actualOriginId
                                    stepCountState.intValue = 0
                                    isWalkingState.value = true
                                },
                                onStepCountAdjusted = { delta ->
                                    val next = stepCountState.intValue + delta
                                    stepCountState.intValue = if (next < 0) 0 else next
                                },
                                onCancelWalking = {
                                    isWalkingState.value = false
                                    stepCountState.intValue = 0
                                },
                                onSaveArrival = { name, isDest, steps, depDeg, depGyro, arrDeg, arrGyro, autoReverse, continueWalking ->
                                    val originId = currentOriginNodeIdState.value ?: 1
                                    val newNode = mapRepository.addNode(name, isDest)
                                    mapRepository.addEdge(
                                        from = originId,
                                        to = newNode.id,
                                        steps = steps,
                                        degree = depDeg,
                                        gyro = depGyro,
                                        departureDegree = depDeg,
                                        departureGyro = depGyro,
                                        arrivalDegree = arrDeg,
                                        arrivalGyro = arrGyro,
                                        autoCreateReverse = autoReverse
                                    )

                                    if (continueWalking) {
                                        currentOriginNodeIdState.value = newNode.id
                                        stepCountState.intValue = 0
                                        isWalkingState.value = false
                                        Toast.makeText(this, "$name saved. Aim phone at next node & tap Start Walking", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isWalkingState.value = false
                                        currentOriginNodeIdState.value = null
                                        stepCountState.intValue = 0
                                    }
                                },
                                isWalking = isWalking,
                                currentOriginNodeId = currentOriginNodeId,
                                onNavigateBack = { currentScreenState.value = AppScreen.DASHBOARD }
                            )
                        }

                        AppScreen.GRAPH_INSPECTOR -> {
                            GraphInspectorScreen(
                                buildingMap = buildingMap,
                                compassHeading = compassHeading,
                                gyroHeading = gyroHeading,
                                onAddNode = { name, isDest -> mapRepository.addNode(name, isDest) },
                                onUpdateNode = { mapRepository.updateNode(it) },
                                onDeleteNode = { mapRepository.deleteNode(it) },
                                onAddEdge = { from, to, steps, degree, gyro, autoRev ->
                                    mapRepository.addEdge(
                                        from = from,
                                        to = to,
                                        steps = steps,
                                        degree = degree,
                                        gyro = gyro,
                                        departureDegree = degree,
                                        departureGyro = gyro,
                                        arrivalDegree = degree,
                                        arrivalGyro = gyro,
                                        autoCreateReverse = autoRev
                                    )
                                },
                                onDeleteEdge = { from, to -> mapRepository.deleteEdge(from, to) },
                                onUpdateEdgeDegree = { from, to, newDeg, newGyro ->
                                    val edge = buildingMap.edges.find { it.from == from && it.to == to }
                                    if (edge != null) {
                                        mapRepository.addEdge(
                                            from = from,
                                            to = to,
                                            steps = edge.steps,
                                            degree = newDeg,
                                            gyro = newGyro,
                                            departureDegree = newDeg,
                                            departureGyro = newGyro,
                                            arrivalDegree = edge.arrivalDegree,
                                            arrivalGyro = edge.arrivalGyro,
                                            autoCreateReverse = false
                                        )
                                    }
                                },
                                onNavigateBack = { currentScreenState.value = AppScreen.DASHBOARD }
                            )
                        }

                        AppScreen.ROUTE_TEST -> {
                            RouteTestScreen(
                                buildingMap = buildingMap,
                                onNavigateBack = { currentScreenState.value = AppScreen.DASHBOARD }
                            )
                        }

                        AppScreen.MAP_VISUALIZER -> {
                            VisualizerScreen(
                                currentMap = buildingMap,
                                onNavigateBack = { currentScreenState.value = AppScreen.DASHBOARD }
                            )
                        }

                        AppScreen.GPS_TOOL -> {
                            GpsCoordinateScreen(
                                onNavigateBack = { currentScreenState.value = AppScreen.DASHBOARD }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            startSensors()
        }
    }

    private fun startSensors() {
        orientationJob?.cancel()
        orientationJob = lifecycleScope.launch {
            orientationManager.getDualOrientationFlow().collectLatest { data ->
                compassHeadingState.floatValue = data.compass
                gyroHeadingState.floatValue = data.gyro
                rawGyroHeadingState.floatValue = data.rawGyro
            }
        }

        stepJob?.cancel()
        stepJob = lifecycleScope.launch {
            stepManager.getStepEventsFlow().collectLatest {
                if (isWalkingState.value) {
                    stepCountState.intValue += 1
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        orientationJob?.cancel()
        stepJob?.cancel()
    }
}
