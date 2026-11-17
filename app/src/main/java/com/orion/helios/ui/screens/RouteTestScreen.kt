package com.orion.helios.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orion.helios.model.BuildingMap
import com.orion.helios.navigation.Dijkstra
import com.orion.helios.navigation.RouteResult
import com.orion.helios.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteTestScreen(
    buildingMap: BuildingMap,
    onNavigateBack: () -> Unit
) {
    val destinationNodes = remember(buildingMap.nodes) { buildingMap.nodes.filter { it.isDestination } }
    var startNodeId by remember { mutableStateOf(buildingMap.nodes.firstOrNull()?.id) }
    var destNodeId by remember { mutableStateOf(destinationNodes.firstOrNull()?.id ?: buildingMap.nodes.lastOrNull()?.id) }

    var routeResult by remember { mutableStateOf<RouteResult?>(null) }
    var attemptedCalc by remember { mutableStateOf(false) }

    val dijkstra = remember(buildingMap) {
        val nodeMap = buildingMap.nodes.associateBy { it.id }
        Dijkstra(nodeMap, buildingMap.edges)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Route Simulator", color = LightText) },
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
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Origin & Destination", style = MaterialTheme.typography.titleMedium, color = LightText)
                    Spacer(modifier = Modifier.height(12.dp))

                    var startExpanded by remember { mutableStateOf(false) }
                    val startNode = buildingMap.nodes.find { it.id == startNodeId }

                    Text("Start Node:", style = MaterialTheme.typography.labelSmall, color = DimText)
                    OutlinedCard(
                        onClick = { startExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(startNode?.let { "#${it.id}: ${it.name}" } ?: "Select Start", color = LightText)
                        }
                    }
                    DropdownMenu(expanded = startExpanded, onDismissRequest = { startExpanded = false }) {
                        buildingMap.nodes.forEach { node ->
                            DropdownMenuItem(
                                text = { Text("#${node.id}: ${node.name}") },
                                onClick = {
                                    startNodeId = node.id
                                    startExpanded = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    var destExpanded by remember { mutableStateOf(false) }
                    val destNode = buildingMap.nodes.find { it.id == destNodeId }

                    Text("Destination:", style = MaterialTheme.typography.labelSmall, color = DimText)
                    OutlinedCard(
                        onClick = { destExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(destNode?.let { "#${it.id}: ${it.name}" } ?: "Select Destination", color = LightText)
                        }
                    }
                    DropdownMenu(expanded = destExpanded, onDismissRequest = { destExpanded = false }) {
                        buildingMap.nodes.forEach { node ->
                            DropdownMenuItem(
                                text = { Text("#${node.id}: ${node.name} ${if (node.isDestination) "(Room)" else ""}") },
                                onClick = {
                                    destNodeId = node.id
                                    destExpanded = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            attemptedCalc = true
                            if (startNodeId != null && destNodeId != null) {
                                routeResult = dijkstra.findPath(startNodeId!!, destNodeId!!)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SolarGold),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AltRoute, contentDescription = null, tint = DeepCharcoal)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Calculate Shortest Route", color = DeepCharcoal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (attemptedCalc) {
                val res = routeResult
                if (res != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Path Found: ${res.totalSteps} steps total",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Traversing ${res.pathNodes.size} nodes (${res.segments.size} segments)",
                                style = MaterialTheme.typography.bodySmall,
                                color = LightText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(res.segments) { index, segment ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardSurface),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SolarGold,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = segment.turnDirection,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = LightText
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Walk ${segment.steps} steps towards ${segment.toNode.name} (heading ${segment.degree.roundToInt()} deg)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = DimText
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonRed)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "No reachable path found between these nodes. Check for missing edges or one-way corridor dead ends.",
                                color = CrimsonRed,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

