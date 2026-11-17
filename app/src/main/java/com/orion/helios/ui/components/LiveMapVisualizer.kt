package com.orion.helios.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orion.helios.model.BuildingMap
import com.orion.helios.ui.theme.*
import kotlin.math.*

@Composable
fun LiveMapVisualizer(
    buildingMap: BuildingMap,
    modifier: Modifier = Modifier,
    stepLengthCm: Float = 75f,
    highlightNodeId: Int? = null,
    defaultHeight: Dp = 340.dp
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var textSizeSp by remember { mutableFloatStateOf(8.5f) }
    var useGyroHeading by remember { mutableStateOf(false) }
    var useArrivalHeading by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    val currentHeight = if (isExpanded) 520.dp else defaultHeight

    val nodePositions = remember(buildingMap, stepLengthCm, useGyroHeading, useArrivalHeading) {
        calculateGraphLayout(buildingMap, stepLengthCm, useGyroHeading, useArrivalHeading)
    }

    val textMeasurer = rememberTextMeasurer()

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (buildingMap.nodes.isNotEmpty()) EmeraldGreen else DimText)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Live Map Visualizer",
                            style = MaterialTheme.typography.titleSmall,
                            color = LightText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${buildingMap.nodes.size} Nodes • ${buildingMap.edges.size} Edges",
                        style = MaterialTheme.typography.labelSmall,
                        color = DimText
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasArrival = remember(buildingMap) { buildingMap.edges.any { it.arrivalDegree != null } }
                    if (hasArrival) {
                        IconButton(
                            onClick = { useArrivalHeading = !useArrivalHeading },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.CompareArrows,
                                contentDescription = "Toggle Departure/Arrival Angle",
                                tint = if (useArrivalHeading) CyanAccent else SolarGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    val hasGyro = remember(buildingMap) { buildingMap.edges.any { it.gyro != null } }
                    if (hasGyro) {
                        IconButton(
                            onClick = { useGyroHeading = !useGyroHeading },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.SyncAlt,
                                contentDescription = "Toggle Compass/Gyro",
                                tint = if (useGyroHeading) CyanAccent else SolarGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            scale = 1.0f
                            panOffset = Offset.Zero
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.CenterFocusStrong,
                            contentDescription = "Recenter View",
                            tint = SolarGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Toggle Expanded View",
                            tint = LightText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF14171D))
                    .border(1.dp, Color(0xFF2B313D), RoundedCornerShape(12.dp))
            ) {
                if (buildingMap.nodes.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            tint = DimText,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No recorded nodes yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LightText,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Walk and save a node above to build your live graph",
                            style = MaterialTheme.typography.labelSmall,
                            color = DimText
                        )
                    }
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectCanvasGestures { centroid, pan, zoom ->
                                    val newScale = (scale * zoom).coerceIn(0.1f, 15f)
                                    panOffset = (panOffset - centroid) * (newScale / scale) + centroid + pan
                                    scale = newScale
                                }
                            }
                    ) {
                        val canvasWidth = size.width
                        val canvasHeightPx = size.height
                        val centerCanvas = Offset(canvasWidth / 2f, canvasHeightPx / 2f)

                        withTransform({
                            translate(centerCanvas.x + panOffset.x, centerCanvas.y + panOffset.y)
                            scale(scale, scale, pivot = Offset.Zero)
                        }) {
                            val gridSize = 100f
                            val gridRange = 25
                            for (i in -gridRange..gridRange) {
                                val coord = i * gridSize
                                drawLine(
                                    color = Color(0xFF1F242D),
                                    start = Offset(coord, -gridRange * gridSize),
                                    end = Offset(coord, gridRange * gridSize),
                                    strokeWidth = 1f
                                )
                                drawLine(
                                    color = Color(0xFF1F242D),
                                    start = Offset(-gridRange * gridSize, coord),
                                    end = Offset(gridRange * gridSize, coord),
                                    strokeWidth = 1f
                                )
                            }

                            val drawnLineSegments = mutableSetOf<String>()
                            buildingMap.edges.forEach { edge ->
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

                            buildingMap.edges.forEach { edge ->
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

                            buildingMap.nodes.forEach { node ->
                                val pos = nodePositions[node.id] ?: Offset.Zero
                                val isHighlighted = highlightNodeId == node.id
                                val nodeRadius = if (isHighlighted) 24f else 20f

                                if (isHighlighted) {
                                    drawCircle(
                                        color = EmeraldGreen.copy(alpha = 0.35f),
                                        radius = nodeRadius + 10f,
                                        center = pos
                                    )
                                    drawCircle(
                                        color = EmeraldGreen,
                                        radius = nodeRadius + 4f,
                                        center = pos,
                                        style = Stroke(width = 2.5f)
                                    )
                                }

                                val nodeBg = if (isHighlighted) EmeraldGreen else if (node.isDestination) SolarGold else CyanAccent
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
                                    style = TextStyle(fontSize = 11.sp, color = nodeBg, fontWeight = FontWeight.Bold)
                                )
                                drawText(
                                    textLayoutResult = idResult,
                                    topLeft = Offset(pos.x - idResult.size.width / 2f, pos.y - idResult.size.height / 2f)
                                )

                                val nameResult = textMeasurer.measure(
                                    text = if (isHighlighted) "${node.name} (Origin)" else node.name,
                                    style = TextStyle(
                                        fontSize = (textSizeSp + 1.5f).sp,
                                        color = if (isHighlighted) EmeraldGreen else LightText,
                                        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium
                                    )
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
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.92f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.TextFields, contentDescription = null, tint = SolarGold, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Text: ${"%.1f".format(textSizeSp)}sp",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LightText,
                                    fontWeight = FontWeight.SemiBold
                                )
                                FilledTonalIconButton(
                                    onClick = { if (textSizeSp > 5f) textSizeSp = (textSizeSp - 1f).coerceAtLeast(5f) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease Text Size", modifier = Modifier.size(12.dp))
                                }
                                FilledTonalIconButton(
                                    onClick = { if (textSizeSp < 18f) textSizeSp = (textSizeSp + 1f).coerceAtMost(18f) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase Text Size", modifier = Modifier.size(12.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .height(16.dp)
                                        .width(1.dp)
                                        .background(DimText.copy(alpha = 0.4f))
                                )

                                Text(
                                    text = "Zoom: ${(scale * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DimText
                                )
                                FilledTonalIconButton(
                                    onClick = { scale = (scale * 1.25f).coerceAtMost(15f) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(12.dp))
                                }
                                FilledTonalIconButton(
                                    onClick = { scale = (scale / 1.25f).coerceAtLeast(0.1f) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

suspend fun PointerInputScope.detectCanvasGestures(
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val panChange = event.calculatePan()
            val zoomChange = event.calculateZoom()
            val centroid = event.calculateCentroid(useCurrent = false)

            if (panChange != Offset.Zero || zoomChange != 1f) {
                onGesture(centroid, panChange, zoomChange)
            }
            event.changes.forEach { change ->
                if (change.positionChanged()) {
                    change.consume()
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

fun calculateGraphLayout(
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

