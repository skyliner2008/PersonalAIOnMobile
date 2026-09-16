package com.skyliner2008.jarvis.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skyliner2008.jarvis.controller.SpecialistSessionController
import com.skyliner2008.jarvis.data.LiveSpecialistService

/**
 * หน้าแปลภาษาแบบเรียลไทม์ — gemini-3.5-live-translate-preview
 *
 * ขอบเขตตามความสามารถจริงของโมเดล:
 * - แปลเสียงเป็นเสียง 70+ ภาษา แบบสตรีมต่อเนื่อง (ไม่รอให้พูดจบประโยค)
 * - เห็นทั้งข้อความต้นทางและคำแปลพร้อมกัน และได้ยินเสียงคำแปลด้วย
 * - เลือก "ภาษาปลายทาง" ได้อย่างเดียว ภาษาต้นทางโมเดลตรวจเอง
 * - ไม่รองรับ tool / คำสั่งระบบ (เป็นล่ามล้วน ไม่ใช่ผู้ช่วย)
 */
@Composable
fun TranslateScreen(
    controller: SpecialistSessionController,
    onClose: () -> Unit
) {
    val segments by controller.segments.collectAsState()
    val state by controller.state.collectAsState()
    val isPaused by controller.isPaused.collectAsState()
    val level by controller.audioLevel.collectAsState()
    val mode by controller.mode.collectAsState()
    val target by controller.targetLanguage.collectAsState()
    val echo by controller.echoTargetLanguage.collectAsState()

    val listState = rememberLazyListState()
    LaunchedEffect(segments.size) {
        if (segments.isNotEmpty()) listState.animateScrollToItem(segments.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.skyliner2008.jarvis.ui.theme.JarvisTheme.Dark)
            .padding(16.dp)
    ) {
        Text(
            "🌐 แปลภาษาสด",
            color = com.skyliner2008.jarvis.ui.theme.JarvisTheme.Cyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        StatusLine(state = state, isPaused = isPaused, running = mode != null, level = level)

        Spacer(Modifier.height(10.dp))
        Text("แปลเป็นภาษา", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TRANSLATE_LANGUAGES) { lang ->
                val selected = lang.code == target
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected) com.skyliner2008.jarvis.ui.theme.JarvisTheme.Cyan
                            else com.skyliner2008.jarvis.ui.theme.JarvisTheme.Card
                        )
                        .clickable(enabled = mode == null) { controller.setTargetLanguage(lang.code) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        lang.label,
                        color = if (selected) com.skyliner2008.jarvis.ui.theme.JarvisTheme.Dark else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = echo,
                onCheckedChange = { controller.setEchoTargetLanguage(it) },
                enabled = mode == null
            )
            Spacer(Modifier.size(8.dp))
            Column {
                Text(
                    "พูดตามเมื่อผู้พูดใช้ภาษาปลายทางอยู่แล้ว",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
                if (echo) {
                    // เปิดโหมดนี้แล้วเสียงที่ออกลำโพงจะถูกไมค์รับกลับ แล้ววนพูดซ้ำได้ — เตือนให้ใช้หูฟัง
                    Text(
                        "⚠️ ใช้หูฟังด้วย ไม่งั้นเสียงแปลจากลำโพงจะย้อนเข้าไมค์แล้ววนซ้ำ",
                        color = com.skyliner2008.jarvis.ui.theme.JarvisTheme.Amber,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(com.skyliner2008.jarvis.ui.theme.JarvisTheme.Surface)
                .border(
                    1.dp,
                    com.skyliner2008.jarvis.ui.theme.JarvisTheme.Cyan.copy(alpha = 0.25f),
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            if (segments.isEmpty()) {
                Text(
                    "เลือกภาษาปลายทางแล้วกดเริ่มแปล\nพูดได้ต่อเนื่องเลย ไม่ต้องรอให้จบประโยค — คำแปลจะออกเป็นเสียงและข้อความพร้อมกัน",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(segments) { seg ->
                        val isTranslated = seg.kind == LiveSpecialistService.TranscriptKind.TRANSLATED
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isTranslated) com.skyliner2008.jarvis.ui.theme.JarvisTheme.Cyan.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .padding(8.dp)
                        ) {
                            Text(
                                if (isTranslated) "คำแปล" else "ได้ยิน",
                                color = com.skyliner2008.jarvis.ui.theme.JarvisTheme.Cyan.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                            Text(
                                seg.text,
                                color = if (isTranslated) Color.White else Color.White.copy(alpha = 0.8f),
                                fontSize = if (isTranslated) 16.sp else 14.sp,
                                fontWeight = if (isTranslated) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (mode == null) {
                Button(
                    onClick = { controller.startTranslate() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.skyliner2008.jarvis.ui.theme.JarvisTheme.Cyan
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "● เริ่มแปล",
                        color = com.skyliner2008.jarvis.ui.theme.JarvisTheme.Dark,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = { if (isPaused) controller.resume() else controller.pause() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) com.skyliner2008.jarvis.ui.theme.JarvisTheme.Green
                        else com.skyliner2008.jarvis.ui.theme.JarvisTheme.Amber
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (isPaused) "▶ แปลต่อ" else "❚❚ พัก",
                        color = com.skyliner2008.jarvis.ui.theme.JarvisTheme.Dark,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { controller.stop(summarize = true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.skyliner2008.jarvis.ui.theme.JarvisTheme.Red
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text("■ หยุด", color = Color.White, fontWeight = FontWeight.Bold) }
            }
            OutlinedButton(onClick = {
                controller.stop(summarize = false)
                onClose()
            }) { Text("ปิด", color = Color.White.copy(alpha = 0.8f)) }
        }
    }
}

internal data class TranslateLanguage(val code: String, val label: String)

/** ภาษาที่ใช้บ่อย — โมเดลรองรับ 70+ ภาษา เลือกเฉพาะที่ใช้จริงมาแสดงเป็นปุ่ม */
internal val TRANSLATE_LANGUAGES = listOf(
    TranslateLanguage("en", "อังกฤษ"),
    TranslateLanguage("th", "ไทย"),
    TranslateLanguage("ja", "ญี่ปุ่น"),
    TranslateLanguage("zh", "จีน"),
    TranslateLanguage("ko", "เกาหลี"),
    TranslateLanguage("vi", "เวียดนาม"),
    TranslateLanguage("id", "อินโดนีเซีย"),
    TranslateLanguage("ms", "มาเลย์"),
    TranslateLanguage("de", "เยอรมัน"),
    TranslateLanguage("fr", "ฝรั่งเศส"),
    TranslateLanguage("es", "สเปน"),
    TranslateLanguage("ru", "รัสเซีย"),
    TranslateLanguage("ar", "อาหรับ"),
    TranslateLanguage("hi", "ฮินดี")
)
