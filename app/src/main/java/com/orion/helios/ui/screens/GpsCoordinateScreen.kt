package com.orion.helios.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.orion.helios.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class GpsMode {
    ON_CLICK,
    REALTIME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsCoordinateScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var selectedMode by remember { mutableStateOf(GpsMode.REALTIME) }
    var decimalPlaces by remember { mutableIntStateOf(6) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var lastUpdatedTime by remember { mutableStateOf<String?>(null) }
    var isStreamingActive by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    @SuppressLint("MissingPermission")
    fun fetchSingleLocation() {
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }

        isLoading = true
        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc: Location? ->
                isLoading = false
                if (loc != null) {
                    currentLocation = loc
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    lastUpdatedTime = sdf.format(Date(loc.time))
                } else {
                    Toast.makeText(context, "Unable to get GPS location. Ensure GPS is enabled.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                isLoading = false
                Toast.makeText(context, "Location failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    DisposableEffect(selectedMode, hasPermission, isStreamingActive) {
        var callback: LocationCallback? = null

        if (hasPermission && selectedMode == GpsMode.REALTIME && isStreamingActive) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateDistanceMeters(0f)
                .setMinUpdateIntervalMillis(500L)
                .build()

            callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation
                    if (loc != null) {
                        currentLocation = loc
                        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        lastUpdatedTime = sdf.format(Date(loc.time))
                    }
                }
            }

            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
            } catch (_: SecurityException) {}
        }

        onDispose {
            callback?.let {
                fusedLocationClient.removeLocationUpdates(it)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GPS Coordinates", color = LightText) },
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
            if (!hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.LocationDisabled, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Location Permission Required", style = MaterialTheme.typography.titleMedium, color = LightText)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Precise location is required to read real-time GPS coordinates.", style = MaterialTheme.typography.bodySmall, color = DimText)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SolarGold)
                        ) {
                            Text("Grant Permission", color = DeepCharcoal)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Acquisition Mode", style = MaterialTheme.typography.labelMedium, color = DimText)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode == GpsMode.ON_CLICK,
                            onClick = { selectedMode = GpsMode.ON_CLICK },
                            label = { Text("Snapshot (By Click)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedMode == GpsMode.REALTIME,
                            onClick = { selectedMode = GpsMode.REALTIME },
                            label = { Text("Realtime (Continuous)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (selectedMode == GpsMode.REALTIME) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isStreamingActive) EmeraldGreen else CrimsonRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isStreamingActive) "Streaming Live" else "Stream Paused",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isStreamingActive) EmeraldGreen else CrimsonRed
                                )
                            }
                            TextButton(onClick = { isStreamingActive = !isStreamingActive }) {
                                Text(if (isStreamingActive) "Pause Stream" else "Resume Stream", color = CyanAccent)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Decimal Precision", style = MaterialTheme.typography.labelMedium, color = DimText)
                        Text("$decimalPlaces decimals", style = MaterialTheme.typography.bodyMedium, color = SolarGold, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = decimalPlaces.toFloat(),
                        onValueChange = { decimalPlaces = it.roundToInt() },
                        valueRange = 2f..8f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = SolarGold,
                            activeTrackColor = SolarGold,
                            inactiveTrackColor = CardSurfaceVariant
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(2, 4, 6, 8).forEach { d ->
                            TextButton(
                                onClick = { decimalPlaces = d },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("${d} dec", fontSize = 11.sp, color = if (decimalPlaces == d) SolarGold else DimText)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val formatString = "%.${decimalPlaces}f"
            val latStr = currentLocation?.latitude?.let { formatString.format(Locale.US, it) } ?: "--"
            val lngStr = currentLocation?.longitude?.let { formatString.format(Locale.US, it) } ?: "--"
            val combinedCoords = if (currentLocation != null) "$latStr, $lngStr" else ""

            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("COORDINATES", style = MaterialTheme.typography.labelSmall, color = DimText)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "LAT: $latStr",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        color = SolarGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "LNG: $lngStr",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    )

                    if (lastUpdatedTime != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Last fix: $lastUpdatedTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = DimText
                        )
                    }

                    if (currentLocation != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("GPS Coordinates", combinedCoords)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied: $combinedCoords", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DeepCharcoal),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = LightText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Coordinates", color = LightText)
                        }
                    }
                }
            }

            if (selectedMode == GpsMode.ON_CLICK) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { fetchSingleLocation() },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = SolarGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = DeepCharcoal, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = DeepCharcoal)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reveal Coordinates Now", color = DeepCharcoal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (currentLocation != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Telemetry", style = MaterialTheme.typography.labelMedium, color = DimText)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Accuracy", color = LightText)
                            Text("± ${"%.1f".format(currentLocation!!.accuracy)} m", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Altitude", color = LightText)
                            Text("${"%.1f".format(currentLocation!!.altitude)} m", color = LightText)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Speed", color = LightText)
                            Text("${"%.1f".format(currentLocation!!.speed * 3.6f)} km/h", color = LightText)
                        }
                    }
                }
            }
        }
    }
}

