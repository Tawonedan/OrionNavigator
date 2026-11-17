package com.orion.helios.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.orion.helios.model.BuildingMap
import com.orion.helios.navigation.GraphValidator
import com.orion.helios.ui.theme.*

@Composable
fun DashboardScreen(
    buildingMap: BuildingMap,
    onUpdateBuildingName: (String) -> Unit,
    onStartWalkRecord: () -> Unit,
    onOpenGraphInspector: () -> Unit,
    onOpenRouteTest: () -> Unit,
    onOpenVisualizer: () -> Unit,
    onOpenGpsTool: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onResetMap: () -> Unit
) {
    var showEditNameDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(buildingMap.buildingName) }
    var showResetDialog by remember { mutableStateOf(false) }

    val issues = remember(buildingMap) { GraphValidator.validate(buildingMap) }
    val destinationCount = remember(buildingMap) { buildingMap.nodes.count { it.isDestination } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HELIOS",
                    style = MaterialTheme.typography.labelMedium,
                    color = SolarGold
                )
                Text(
                    text = buildingMap.buildingName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = LightText
                )
            }
            IconButton(onClick = {
                tempName = buildingMap.buildingName
                showEditNameDialog = true
            }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Name", tint = SolarGold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricBadge(
                label = "Nodes",
                value = "${buildingMap.nodes.size}",
                modifier = Modifier.weight(1f)
            )
            MetricBadge(
                label = "Destinations",
                value = "$destinationCount",
                modifier = Modifier.weight(1f)
            )
            MetricBadge(
                label = "Edges",
                value = "${buildingMap.edges.size}",
                modifier = Modifier.weight(1f)
            )
        }

        if (issues.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${issues.size} graph issue(s) detected (e.g. orphan nodes or one-way edges)",
                        style = MaterialTheme.typography.bodySmall,
                        color = LightText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            onClick = onStartWalkRecord,
            colors = CardDefaults.cardColors(containerColor = SolarGold),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Walk & Record",
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepCharcoal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Simultaneous gyro and compass mapper",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeepCharcoal.copy(alpha = 0.8f)
                    )
                }
                Icon(
                    Icons.Default.DirectionsWalk,
                    contentDescription = null,
                    tint = DeepCharcoal,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        DashboardActionCard(
            title = "Graph Inspector",
            subtitle = "View and manually edit nodes and corridor edges",
            icon = Icons.Default.List,
            onClick = onOpenGraphInspector
        )

        Spacer(modifier = Modifier.height(12.dp))

        DashboardActionCard(
            title = "Test Route (Dijkstra)",
            subtitle = "Simulate Orion navigation pathfinding on this map",
            icon = Icons.Default.AltRoute,
            onClick = onOpenRouteTest
        )

        Spacer(modifier = Modifier.height(12.dp))

        DashboardActionCard(
            title = "JSON Map Visualizer",
            subtitle = "Draw connecting edges and navigate with pinch and drag",
            icon = Icons.Default.Map,
            onClick = onOpenVisualizer
        )

        Spacer(modifier = Modifier.height(12.dp))

        DashboardActionCard(
            title = "GPS Coordinates",
            subtitle = "On-click snapshot and realtime live GPS coordinates",
            icon = Icons.Default.MyLocation,
            onClick = onOpenGpsTool
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onExportJson,
                colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, tint = CyanAccent)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export JSON", color = LightText)
            }

            Button(
                onClick = onImportJson,
                colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, tint = SolarGold)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import JSON", color = LightText)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Reset Map to Blank", color = CrimsonRed)
        }
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Building Name") },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Building & Floor Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tempName.isNotBlank()) {
                        onUpdateBuildingName(tempName.trim())
                        showEditNameDialog = false
                    }
                }) {
                    Text("Save", color = SolarGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Map") },
            text = { Text("Are you sure you want to clear all nodes and edges? This will create a clean new map.") },
            confirmButton = {
                TextButton(onClick = {
                    onResetMap()
                    showResetDialog = false
                }) {
                    Text("Reset", color = CrimsonRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MetricBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = SolarGold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = DimText)
        }
    }
}

@Composable
private fun DashboardActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(CardSurfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = SolarGold, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = LightText)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = DimText)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DimText)
        }
    }
}
