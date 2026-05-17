---
name: MT5 UI redesign 2026-04-20
description: Rewrote TradingTerminalScreen.kt (Compose Multiplatform) with a Jarvis-style dashboard — Overview/Trade/History/Settings/Connection tabs, hero PnL, margin level bar, symbol quick-select, volume stepper, side-badged position cards.
type: project
originSessionId: 776b71ae-233f-4e43-add7-9e532865f100
---
Rewrote `composeApp/src/commonMain/kotlin/com/example/personalaibot/ui/screen/TradingTerminalScreen.kt` (501 → 1631 lines). The outer `@Composable fun TradingTerminalScreen(...)` signature is unchanged — App.kt / JarvisViewModel wiring still works without edits.

**Non-obvious design contracts worth remembering:**

1. Tabs reordered to `Overview → Trade → History → Settings → Connection`. Default lands on Overview (dashboard) rather than Connection. Old code defaulted to Connection.

2. `Mt5TradeItem.side` is now expected to be a readable string `"BUY"` / `"SELL"` (the Node + Python normalization from the 2026-04-20 backend fix). The new UI uses `row.side.equals("BUY", ignoreCase = true)` to drive color/icon. If backend normalization is ever removed, the cards will collapse to all SELL-styled — do NOT revert `normalizeTradeSide` without updating this UI.

3. KMP gotcha: this is `commonMain`, so `System.currentTimeMillis()` does NOT compile. Use `kotlinx.datetime.Clock.System.now().toEpochMilliseconds()` (already imported). Same rule for any future time-based helpers in this file.

4. Visual vocabulary matches the rest of Jarvis: `JarvisTheme.Dark` background, `JarvisTheme.Card` panels, `JarvisTheme.Cyan` accent, `RoundedCornerShape(14.dp)` on cards (same as `AutomationScreen.kt`), 11–12sp body text, `letterSpacing = 2.sp` on uppercase section headers.

5. New semantic colors introduced as private file-level vals (not in `JarvisTheme` object): `BuyGreen = 0xFF00E676`, `SellRed = 0xFFFF5252`, `PositiveGreen = 0xFFB9F6CA`, `NegativeRed = 0xFFFF8A80`, `OutlineSubtle = 0xFF2A3247`. Kept file-local because these are trading-semantic, not global theme tokens.

6. Quick-symbol chips are a hardcoded list: `XAUUSD, EURUSD, GBPUSD, USDJPY, BTCUSD, US30, NAS100, XAGUSD` (private `QuickSymbols` val). Symbol is still freely editable in the text field; chips are a convenience.

7. Margin level thresholds: ≥500% healthy (green), ≥150% caution (cyan), <150% at-risk (red). Progress bar ratio is `marginLevelPct / 1000.0` clamped to 0.1–1.0 so the bar stays visually meaningful at high levels.

8. Compose Multiplatform version is 1.7.3 / Kotlin 2.1.20 — `LinearProgressIndicator(progress = { value })` lambda form is supported. `enum.entries.toList()` works natively.

**Why:** the old UI was a plain `FilterChip` row + bullet-list AccountTab, no visual hierarchy, no per-side color coding, no margin health, no symbol shortcuts. User asked for it to be "beautiful, easy to use, organized, and consistent with other screens".

**How to apply:** When adding new trading-related UI, reuse the primitives in this file (`MetricCard`, `SoftButton`, `JarvisTextField`, `StatChip`, `EmptyState`, `KvRow`, `PositionCard`). Keep the `TradingTerminalScreen(...)` outer signature stable — App.kt passes 30+ params positionally and any rename breaks the build.
