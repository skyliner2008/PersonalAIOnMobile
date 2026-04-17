package com.example.personalaibot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.db.AlertJob
import com.example.personalaibot.ui.theme.JarvisTheme

@Composable
fun AutomationScreen(
    jobs: List<AlertJob>,
    onDelete: (Long) -> Unit,
    onUpdateInterval: (Long, Long) -> Unit
) {
    if (jobs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(JarvisTheme.Dark),
            contentAlignment = Alignment.Center
        ) {
            Text("No cron jobs yet", color = Color.White.copy(alpha = 0.7f))
        }
    } else {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(JarvisTheme.Dark),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(jobs.size) { index ->
                val job = jobs[index]
                CronJobCard(
                    job = job,
                    onDelete = { onDelete(job.id) },
                    onUpdateInterval = { interval -> onUpdateInterval(job.id, interval) }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CronJobCard(
    job: AlertJob,
    onDelete: () -> Unit,
    onUpdateInterval: (Long) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(JarvisTheme.Cyan.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(job.symbol.take(8), color = JarvisTheme.Cyan, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    if (job.is_triggered == 1L) "TRIGGERED" else "ACTIVE",
                    color = if (job.is_triggered == 1L) Color(0xFFFF5252) else Color(0xFF00C853),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(job.name, color = Color.White, fontWeight = FontWeight.SemiBold)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = JarvisTheme.Cyan)
                Text(
                    text = " Every ${job.interval_minutes} minutes",
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Interval", tint = Color.White.copy(alpha = 0.8f))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF6B6B))
                }
            }

            if (job.last_value != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                    Text(" Last value: ${job.last_value}", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                }
            }
        }
    }

    if (showEditDialog) {
        IntervalEditDialog(
            currentValue = job.interval_minutes,
            onDismiss = { showEditDialog = false },
            onConfirm = {
                onUpdateInterval(it)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun IntervalEditDialog(
    currentValue: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var textValue by remember { mutableStateOf(currentValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisTheme.Surface,
        title = { Text("Update interval", color = Color.White) },
        text = {
            Column {
                Text("Minutes (1-1440)", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { if (it.all(Char::isDigit)) textValue = it },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = JarvisTheme.Cyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newVal = textValue.toLongOrNull() ?: currentValue
                    onConfirm(newVal.coerceIn(1, 1440))
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisTheme.Cyan)
            ) {
                Text("Save", color = JarvisTheme.Dark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}
