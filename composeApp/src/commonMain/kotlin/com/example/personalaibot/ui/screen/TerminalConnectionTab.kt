package com.example.personalaibot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.Mt5ClientRuntimeInfo
import com.example.personalaibot.ui.theme.JarvisTheme

// ─── Connection Tab ──────────────────────────────────────────────────────────

@Composable
internal fun TerminalConnectionTab(
    endpointInput: String,
    isConnected: Boolean,
    terminalFeed: List<String>,
    clients: List<Mt5ClientRuntimeInfo>,
    clientsLoading: Boolean,
    selectedClientExe: String,
    onEndpointChange: (String) -> Unit,
    onConnectToggle: () -> Unit,
    onSelectClient: (String) -> Unit,
    onRefreshClients: () -> Unit,
    onStartClient: () -> Unit,
    onStopClient: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Endpoint + connect toggle
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Bridge Endpoint", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JarvisTextField(
                        value = endpointInput,
                        onValueChange = onEndpointChange,
                        label = "Base URL",
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onConnectToggle,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) NegativeRed.copy(alpha = 0.2f) else JarvisTheme.Cyan,
                            contentColor = if (isConnected) NegativeRed else JarvisTheme.Dark
                        ),
                        modifier = Modifier.height(54.dp)
                    ) {
                        Icon(
                            if (isConnected) Icons.Default.LinkOff else Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isConnected) "Disconnect" else "Connect",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // MT5 clients
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MT5 Clients", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRefreshClients, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (clientsLoading) JarvisTheme.Cyan.copy(alpha = 0.5f) else JarvisTheme.Cyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onStartClient, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = BuyGreen, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onStopClient, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = SellRed, modifier = Modifier.size(20.dp))
                    }
                }
                if (clients.isEmpty()) {
                    Text(
                        if (clientsLoading) "Scanning..." else "No MT5 client found",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                } else {
                    clients.forEach { client ->
                        ClientRow(
                            client = client,
                            selected = selectedClientExe.equals(client.exePath, ignoreCase = true),
                            onSelect = { onSelectClient(client.exePath) }
                        )
                    }
                }
            }
        }

        // Server terminal feed
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = JarvisTheme.Cyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Server Terminal", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${terminalFeed.size} lines", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
                }
                Surface(
                    color = TerminalSurface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 240.dp),
                ) {
                    if (terminalFeed.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No server output yet", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            reverseLayout = true,
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(terminalFeed.asReversed()) { line ->
                                val color = when {
                                    line.contains("[ERR", ignoreCase = true) ||
                                    line.contains("ERROR", ignoreCase = true) -> Color(0xFFFF5252)
                                    line.contains("[WARN", ignoreCase = true) -> Color(0xFFFFB74D)
                                    line.contains("[OK", ignoreCase = true) ||
                                    line.contains("SUCCESS", ignoreCase = true) -> Color(0xFF69F0AE)
                                    else -> Color(0xFF9EA7C0)
                                }
                                Text(
                                    line,
                                    color = color,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Client Row ──────────────────────────────────────────────────────────────

@Composable
private fun ClientRow(
    client: Mt5ClientRuntimeInfo,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) JarvisTheme.Cyan.copy(alpha = 0.12f) else PanelSurfaceHi)
            .border(1.dp, if (selected) JarvisTheme.Cyan else OutlineSubtle, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (client.running) BuyGreen else NegativeRed),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                client.exePath.substringAfterLast('\\').substringAfterLast('/'),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                client.exePath,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = JarvisTheme.Cyan, modifier = Modifier.size(14.dp))
        }
    }
}
