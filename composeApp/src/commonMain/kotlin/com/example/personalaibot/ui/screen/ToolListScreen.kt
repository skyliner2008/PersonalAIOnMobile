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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.ui.theme.JarvisTheme

@Composable
fun ToolListScreen() {
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }
    var expandedItem by remember { mutableStateOf<String?>(null) }

    val filtered = remember(selectedCategory) {
        ALL_TOOLS.filter { selectedCategory == null || it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisTheme.Dark)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CategoryChip(
                    label = "All (${ALL_TOOLS.size})",
                    color = JarvisTheme.Cyan,
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null; expandedItem = null }
                )
            }
            items(ToolCategory.entries) { category ->
                val count = ALL_TOOLS.count { it.category == category }
                CategoryChip(
                    label = "${category.label} ($count)",
                    color = category.color,
                    selected = selectedCategory == category,
                    onClick = {
                        selectedCategory = if (selectedCategory == category) null else category
                        expandedItem = null
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.name }) { tool ->
                ToolCard(
                    tool = tool,
                    expanded = expandedItem == tool.name,
                    onClick = {
                        expandedItem = if (expandedItem == tool.name) null else tool.name
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, if (selected) color else color.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .background(if (selected) color.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) color else color.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ToolCard(
    tool: ToolInfo,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tool.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    tool.category.label,
                    color = tool.category.color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                tool.shortDesc,
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!expanded) {
                Text(
                    "Tool ID: ${tool.name}",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tool ID: ${tool.name}",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("Capabilities", color = tool.category.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                tool.capabilities.forEach { cap ->
                    Text("- $cap", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("When User Says", color = tool.category.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                tool.examplePrompts.forEach { ex ->
                    Text("- $ex", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }
            }
        }
    }
}
