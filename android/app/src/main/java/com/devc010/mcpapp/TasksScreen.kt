package com.devc010.mcpapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Status colors ────────────────────────────────────────────────
private val statusColors = mapOf(
    "pending"           to Color(0xFF78909C),
    "queued"            to Color(0xFF78909C),
    "picked_up"         to Color(0xFF29B6F6),
    "analyzing"         to Color(0xFF29B6F6),
    "awaiting_approval" to Color(0xFFFFA726),
    "approved"          to Color(0xFF66BB6A),
    "applying"          to Color(0xFF66BB6A),
    "done"              to Color(0xFF4CAF50),
    "rejected"          to Color(0xFFEF5350),
    "failed"            to Color(0xFFEF5350),
    "timeout"           to Color(0xFFEF5350),
)

private val statusLabels = mapOf(
    "pending"           to "Pending",
    "queued"            to "Queued",
    "picked_up"         to "Picked Up",
    "analyzing"         to "Analyzing…",
    "awaiting_approval" to "Needs Approval",
    "approved"          to "Approved",
    "applying"          to "Applying…",
    "done"              to "Done ✓",
    "rejected"          to "Rejected",
    "failed"            to "Failed",
    "timeout"           to "Timed Out",
)

@Composable
fun TasksScreen(
    viewModel: MainViewModel,
    onApproveTask: (taskId: String, action: String) -> Unit
) {
    val tasks by viewModel.taskFeed.collectAsState()

    LaunchedEffect(Unit) { viewModel.startTaskStream() }
    DisposableEffect(Unit) { onDispose { viewModel.stopTaskStream() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1117))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Task Feed",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            // Live indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
            )
            Spacer(Modifier.width(6.dp))
            Text("Live", fontSize = 12.sp, color = Color(0xFF4CAF50))
        }

        HorizontalDivider(color = Color(0xFF1E2130))

        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No tasks yet", color = Color(0xFF546E7A), fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Send a task from the DevMCP app", color = Color(0xFF37474F), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(task = task, onApproveTask = onApproveTask)
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: TaskDetail, onApproveTask: (taskId: String, action: String) -> Unit) {
    var expanded by remember { mutableStateOf(task.status == "awaiting_approval") }
    val statusColor by animateColorAsState(
        targetValue = statusColors[task.status] ?: Color(0xFF78909C),
        animationSpec = tween(500),
        label = "statusColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B27)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top row: status badge + project name
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(status = task.status, color = statusColor)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = task.projectName.ifBlank { task.id },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = Color(0xFF546E7A)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = task.description,
                fontSize = 13.sp,
                color = Color(0xFF90A4AE),
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            // Expanded details
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF1E2130))
                Spacer(Modifier.height(10.dp))

                // Target path
                if (task.targetPath.isNotBlank()) {
                    LabelValue("Target", task.targetPath)
                    Spacer(Modifier.height(6.dp))
                }

                // Status history timeline
                if (task.statusHistory.isNotEmpty()) {
                    Text("Timeline", fontSize = 12.sp, color = Color(0xFF546E7A), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    task.statusHistory.forEach { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColors[entry.status] ?: Color(0xFF546E7A))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${statusLabels[entry.status] ?: entry.status}  ${entry.at.take(19).replace("T", " ")}",
                                fontSize = 12.sp,
                                color = Color(0xFF78909C),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Diff preview
                if (!task.diffPreview.isNullOrBlank() && task.status == "awaiting_approval") {
                    Text("Preview", fontSize = 12.sp, color = Color(0xFF546E7A), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D1117))
                            .border(1.dp, Color(0xFF1E2130), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = task.diffPreview.take(800) + if (task.diffPreview.length > 800) "\n…" else "",
                            fontSize = 11.sp,
                            color = Color(0xFF90A4AE),
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // Approve / Reject buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onApproveTask(task.id, "approve") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Approve", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { onApproveTask(task.id, "reject") },
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB71C1C)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null,
                                tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reject", fontSize = 13.sp, color = Color(0xFFEF5350))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = statusLabels[status] ?: status,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row {
        Text("$label: ", fontSize = 12.sp, color = Color(0xFF546E7A), fontWeight = FontWeight.Medium)
        Text(value, fontSize = 12.sp, color = Color(0xFF90A4AE), fontFamily = FontFamily.Monospace,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
