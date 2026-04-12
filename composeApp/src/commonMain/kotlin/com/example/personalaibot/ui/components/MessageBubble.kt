package com.example.personalaibot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.Message
import com.example.personalaibot.ui.theme.JarvisTheme

@Composable
fun MessageBubble(message: Message) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // JARVIS avatar dot
            Box(
                modifier = Modifier
                    .padding(end = 8.dp, top = 4.dp)
                    .size(28.dp)
                    .background(JarvisTheme.Cyan.copy(0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("J", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Surface(
            color = if (isUser) JarvisTheme.Cyan.copy(0.15f) else JarvisTheme.Card,
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser) {
                    Text(
                        "JARVIS",
                        color = JarvisTheme.Cyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                androidx.compose.foundation.text.selection.SelectionContainer {
                    RenderedMessage(
                        content = message.content,
                        color = if (isUser) Color.White else Color.White.copy(0.9f)
                    )
                }
            }
        }

        if (isUser) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp, top = 4.dp)
                    .size(28.dp)
                    .background(JarvisTheme.Cyan.copy(0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("U", color = JarvisTheme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RenderedMessage(content: String, color: Color) {
    val lines = content.lines()
    Column {
        lines.forEach { line ->
            val isTable = line.trim().startsWith("|")
            val annotatedString = buildAnnotatedString {
                var current = line
                while (current.contains("**")) {
                    val start = current.indexOf("**")
                    val end = current.indexOf("**", start + 2)
                    if (end != -1) {
                        append(current.substring(0, start))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(current.substring(start + 2, end))
                        }
                        current = current.substring(end + 2)
                    } else {
                        break
                    }
                }
                append(current)
            }

            Text(
                text = annotatedString,
                color = color,
                fontSize = if (isTable) 12.sp else 14.sp,
                fontFamily = if (isTable) FontFamily.Monospace else FontFamily.Default,
                lineHeight = if (isTable) 16.sp else 20.sp,
                modifier = Modifier.padding(vertical = if (isTable) 0.dp else 1.dp)
            )
        }
    }
}
