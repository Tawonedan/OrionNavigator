package com.orion.helios.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orion.helios.model.BuildingMap
import com.orion.helios.model.MapEdge
import com.orion.helios.model.MapNode
import com.orion.helios.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphInspectorScreen(
    buildingMap: BuildingMap,
    compassHeading: Float,
    gyroHeading: Float,
    onAddNode: (name: String, isDestination: Boolean) -> Unit,
    onUpdateNode: (MapNode) -> Unit,
    onDeleteNode: (nodeId: Int) -> Unit,
    onAddEdge: (from: Int, to: Int, steps: Int, degree: Double, gyro: Double?, autoReverse: Boolean) -> Unit,
    onDeleteEdge: (from: Int, to: Int) -> Unit,
    onUpdateEdgeDegree: (from: Int, to: Int, newDegree: Double, newGyro: Double?) -> Unit,
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Nodes (${buildingMap.nodes.size})", "Edges (${buildingMap.edges.size})")

    var showAddNodeDialog by remember { mutableStateOf(false) }
    var showAddEdgeDialog by remember { mutableStateOf(false) }
    var editingNode by remember { mutableStateOf<MapNode?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Graph Inspector", color = LightText) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = LightText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepCharcoal)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) showAddNodeDialog = true else showAddEdgeDialog = true
                },
                containerColor = SolarGold,
                contentColor = DeepCharcoal
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
        containerColor = DeepCharcoal
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CardSurface,
                contentColor = SolarGold
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(buildingMap.nodes, key = { it.id }) { node ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${node.id}. ${node.name}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = LightText
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (node.isDestination) "Destination Room" else "Corridor Junction",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (node.isDestination) SolarGold else DimText
                                    )
                                }
                                Row {
                                    IconButton(onClick = { editingNode = node }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = CyanAccent)
                                    }
                                    IconButton(onClick = { onDeleteNode(node.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonRed)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val nodeMap = remember(buildingMap.nodes) { buildingMap.nodes.associateBy { it.id } }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(buildingMap.edges) { edge ->
                        val fromNodeName = nodeMap[edge.from]?.name ?: "ID ${edge.from}"
                        val toNodeName = nodeMap[edge.to]?.name ?: "ID ${edge.to}"

                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$fromNodeName -> $toNodeName",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = LightText
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val dep = edge.departureDegree ?: edge.degree
                                        val arrPart = if (edge.arrivalDegree != null) " | Arr: ${edge.arrivalDegree.roundToInt()} deg" else ""
                                        val gyroPart = if (edge.gyro != null) " | Gyro: ${edge.gyro.roundToInt()} deg" else ""
                                        Text(
                                            text = "${edge.steps} steps | Dep: ${dep.roundToInt()} deg$gyroPart$arrPart",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SolarGold
                                        )
                                    }
                                    IconButton(onClick = { onDeleteEdge(edge.from, edge.to) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonRed)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        onUpdateEdgeDegree(edge.from, edge.to, compassHeading.toDouble(), gyroHeading.toDouble())
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                                ) {
                                    Icon(Icons.Default.CompassCalibration, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Aim Phone & Set Heading (C: ${compassHeading.roundToInt()} deg | G: ${gyroHeading.roundToInt()} deg)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddNodeDialog) {
        var name by remember { mutableStateOf("") }
        var isDest by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddNodeDialog = false },
            title = { Text("Add Node Manually") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Node Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isDest, onCheckedChange = { isDest = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Is Destination Room")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        onAddNode(name.trim(), isDest)
                        showAddNodeDialog = false
                    }
                }) {
                    Text("Add", color = SolarGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    editingNode?.let { node ->
        var editName by remember { mutableStateOf(node.name) }
        var editIsDest by remember { mutableStateOf(node.isDestination) }

        AlertDialog(
            onDismissRequest = { editingNode = null },
            title = { Text("Edit Node #${node.id}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Node Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editIsDest, onCheckedChange = { editIsDest = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Is Destination Room")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotBlank()) {
                        onUpdateNode(node.copy(name = editName.trim(), isDestination = editIsDest))
                        editingNode = null
                    }
                }) {
                    Text("Save", color = SolarGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingNode = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddEdgeDialog) {
        var fromId by remember { mutableStateOf(buildingMap.nodes.firstOrNull()?.id ?: 0) }
        var toId by remember { mutableStateOf(buildingMap.nodes.getOrNull(1)?.id ?: 0) }
        var stepsText by remember { mutableStateOf("10") }
        var degreeText by remember { mutableStateOf("${compassHeading.roundToInt()}") }
        var gyroText by remember { mutableStateOf("${gyroHeading.roundToInt()}") }
        var autoReverse by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddEdgeDialog = false },
            title = { Text("Add Edge Manually") },
            text = {
                Column {
                    Text("From Node ID: $fromId", color = LightText)
                    Text("To Node ID: $toId", color = LightText)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = stepsText,
                        onValueChange = { stepsText = it },
                        label = { Text("Step Count") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = degreeText,
                        onValueChange = { degreeText = it },
                        label = { Text("Compass Degree (0-359)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gyroText,
                        onValueChange = { gyroText = it },
                        label = { Text("Gyro Degree (0-359)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoReverse, onCheckedChange = { autoReverse = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create reverse edge")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = stepsText.toIntOrNull() ?: 10
                    val d = degreeText.toDoubleOrNull() ?: 0.0
                    val g = gyroText.toDoubleOrNull()
                    onAddEdge(fromId, toId, s, d, g, autoReverse)
                    showAddEdgeDialog = false
                }) {
                    Text("Add Edge", color = SolarGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEdgeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
