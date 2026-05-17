package com.example.personalaibot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.personalaibot.data.SymbolCategory
import com.example.personalaibot.data.SymbolCategoryClassifier
import com.example.personalaibot.tools.trading.BrokerSymbolCache
import com.example.personalaibot.tools.trading.Mt5SymbolInfo
import com.example.personalaibot.ui.theme.JarvisTheme
import kotlinx.coroutines.launch

/**
 * SymbolPickerDialog
 * ──────────────────
 * Full-screen dialog for selecting a trading symbol from the broker's catalogue.
 *
 * Features:
 *  • Search box — instant filter on symbol code and description
 *  • Grouped list — Metals / Crypto / Indices / Stocks / Forex / Other
 *  • ⭐ Favorite toggle — persisted per broker via BrokerSymbolCache
 *  • Favorites section pinned at top when non-empty
 *  • Loading state while symbols are fetched from cache
 *  • Empty-state message when no symbols match query
 *
 * 2026-05-01 — skyliner.jojo@gmail.com
 */
@Composable
fun SymbolPickerDialog(
    brokerId: String,
    onSymbolSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // ── State ──────────────────────────────────────────────────────────────────
    var query by remember { mutableStateOf("") }
    var allSymbols by remember { mutableStateOf<List<Mt5SymbolInfo>>(emptyList()) }
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }

    // ── Load on open ───────────────────────────────────────────────────────────
    LaunchedEffect(brokerId) {
        loading = true
        allSymbols = BrokerSymbolCache.loadAll(brokerId)
        favorites = BrokerSymbolCache.favorites(brokerId).toSet()
        loading = false
    }

    // ── Derived filtered + grouped data ───────────────────────────────────────
    val filtered: List<Mt5SymbolInfo> = remember(allSymbols, query) {
        val q = query.trim().uppercase()
        if (q.isEmpty()) allSymbols
        else allSymbols.filter {
            it.symbol.uppercase().contains(q) || it.description.uppercase().contains(q)
        }
    }

    val grouped: Map<SymbolCategory, List<Mt5SymbolInfo>> = remember(filtered) {
        filtered.groupBy { SymbolCategoryClassifier.classify(it.symbol) }
            .toSortedMap(compareBy { it.ordinal })
    }

    val favoritesInList: List<Mt5SymbolInfo> = remember(filtered, favorites) {
        filtered.filter { it.symbol.uppercase() in favorites }
            .sortedBy { it.symbol }
    }

    // ── Category emoji icons ───────────────────────────────────────────────────
    fun categoryIcon(cat: SymbolCategory) = when (cat) {
        SymbolCategory.METALS  -> "🥇"
        SymbolCategory.CRYPTO  -> "₿"
        SymbolCategory.INDICES -> "📊"
        SymbolCategory.STOCKS  -> "📈"
        SymbolCategory.FOREX   -> "💱"
        SymbolCategory.OTHER   -> "🔹"
    }

    fun categoryColor(cat: SymbolCategory) = when (cat) {
        SymbolCategory.METALS  -> Color(0xFFFFD700)
        SymbolCategory.CRYPTO  -> Color(0xFF00E5FF)
        SymbolCategory.INDICES -> Color(0xFF7C4DFF)
        SymbolCategory.STOCKS  -> Color(0xFF22C55E)
        SymbolCategory.FOREX   -> Color(0xFF29B6F6)
        SymbolCategory.OTHER   -> Color(0xFF607D8B)
    }

    // ── Render ─────────────────────────────────────────────────────────────────
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = JarvisTheme.Surface,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header bar ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JarvisTheme.Card)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Select Symbol",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = JarvisTheme.Cyan, fontSize = 14.sp)
                    }
                }

                // ── Search box ─────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(JarvisTheme.Card)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(JarvisTheme.Cyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    "🔍  Search symbol or name…",
                                    color = Color(0xFF666680),
                                    fontSize = 14.sp,
                                )
                            }
                            inner()
                        },
                    )
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(20.dp)
                                .clickable { query = "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✕", color = Color(0xFF888899), fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF303050))

                // ── Symbol list ────────────────────────────────────────────────
                when {
                    loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = JarvisTheme.Cyan, strokeWidth = 2.dp)
                                Spacer(Modifier.height(12.dp))
                                Text("Loading symbols…", color = Color(0xFF888899), fontSize = 12.sp)
                            }
                        }
                    }

                    filtered.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (allSymbols.isEmpty())
                                    "No symbols in cache.\nConnect to broker and sync."
                                else
                                    "No symbols match \"$query\"",
                                color = Color(0xFF666680),
                                fontSize = 13.sp,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            // Favorites section (pinned at top when non-empty)
                            if (favoritesInList.isNotEmpty()) {
                                symbolSection(
                                    header = "⭐  Favorites",
                                    headerColor = JarvisTheme.Amber,
                                    symbols = favoritesInList,
                                    favorites = favorites,
                                    onSelect = { sym ->
                                        keyboard?.hide()
                                        onSymbolSelected(sym)
                                    },
                                    onToggleFav = { sym ->
                                        scope.launch {
                                            BrokerSymbolCache.toggleFavorite(brokerId, sym)
                                            favorites = BrokerSymbolCache.favorites(brokerId).toSet()
                                        }
                                    },
                                )
                            }

                            // Categorized sections
                            grouped.forEach { (cat, list) ->
                                symbolSection(
                                    header = "${categoryIcon(cat)}  ${cat.displayName}",
                                    headerColor = categoryColor(cat),
                                    symbols = list,
                                    favorites = favorites,
                                    onSelect = { sym ->
                                        keyboard?.hide()
                                        onSymbolSelected(sym)
                                    },
                                    onToggleFav = { sym ->
                                        scope.launch {
                                            BrokerSymbolCache.toggleFavorite(brokerId, sym)
                                            favorites = BrokerSymbolCache.favorites(brokerId).toSet()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Section builder ────────────────────────────────────────────────────────────

private fun LazyListScope.symbolSection(
    header: String,
    headerColor: Color,
    symbols: List<Mt5SymbolInfo>,
    favorites: Set<String>,
    onSelect: (String) -> Unit,
    onToggleFav: (String) -> Unit,
) {
    item(key = "header_$header") {
        Text(
            text = header,
            color = headerColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        )
    }

    items(
        items = symbols,
        key = { it.symbol + header },   // prefix prevents key clash between sections
    ) { info ->
        SymbolRow(
            info = info,
            isFav = info.symbol.uppercase() in favorites,
            onSelect = { onSelect(info.symbol) },
            onToggleFav = { onToggleFav(info.symbol) },
        )
    }
}

// ── Single symbol row ──────────────────────────────────────────────────────────

@Composable
private fun SymbolRow(
    info: Mt5SymbolInfo,
    isFav: Boolean,
    onSelect: () -> Unit,
    onToggleFav: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.symbol,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (info.description.isNotBlank()) {
                Text(
                    text = info.description,
                    color = Color(0xFF888899),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }

        // Bid price (if available)
        if (info.bid > 0.0) {
            Text(
                text = formatPrice(info.bid, info.digits),
                color = Color(0xFF888899),
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        // Favorite star
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(onClick = onToggleFav),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isFav) "⭐" else "☆",
                fontSize = 16.sp,
                color = if (isFav) JarvisTheme.Amber else Color(0xFF555566),
            )
        }
    }
    HorizontalDivider(
        color = Color(0xFF1E1E30),
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

private fun formatPrice(price: Double, digits: Int): String {
    return if (digits in 0..8) "%.${digits}f".format(price)
    else price.toString()
}
