package com.example.personalaibot.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.ui.theme.JarvisTheme
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/**
 * Compact banner shown above MT5 screens when the bridge / core-server is
 * unreachable.  The screen below still renders the cached MT5 view (so the
 * user is never staring at a blank UI), the banner just signals "this is
 * stale data + when we last saw fresh".
 *
 * 2026-04-30 (P6) — skyliner.jojo@gmail.com
 */
@Composable
fun OfflineBanner(
    serverOnline: Boolean,
    lastSyncMs: Long,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Hide the banner when sync is fresher than this. Default 30s. */
    freshThresholdMs: Long = 30_000L,
) {
    // Recompose every 30s so "5 min ago" stays accurate without being noisy.
    var nowMs by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(serverOnline, lastSyncMs) {
        while (true) {
            nowMs = Clock.System.now().toEpochMilliseconds()
            delay(30_000L)
        }
    }

    val ageMs = if (lastSyncMs <= 0L) Long.MAX_VALUE else (nowMs - lastSyncMs)
    val show = !serverOnline || (lastSyncMs > 0L && ageMs > freshThresholdMs)

    AnimatedVisibility(
        visible = show,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val tint = if (!serverOnline) JarvisTheme.Amber else JarvisTheme.Cyan
        val title = when {
            !serverOnline && lastSyncMs == 0L -> "Server offline — no cached data"
            !serverOnline -> "Server offline — showing cached data"
            else -> "Showing cached data"
        }
        val ageLabel = formatAge(ageMs)
        val subtitle = if (lastSyncMs > 0L) "Last synced: $ageLabel" else "Never synced"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (!serverOnline) Icons.Default.CloudOff else Icons.Default.Schedule,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = tint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                )
            }
            TextButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
                Text(" Retry", color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatAge(ageMs: Long): String {
    if (ageMs == Long.MAX_VALUE) return "never"
    if (ageMs < 0) return "just now"
    val sec = ageMs / 1000
    if (sec < 60) return "${sec}s ago"
    val min = sec / 60
    if (min < 60) return "${min} min ago"
    val hr = min / 60
    if (hr < 24) return "${hr}h ${min % 60}m ago"
    val days = hr / 24
    return "${days}d ago"
}
