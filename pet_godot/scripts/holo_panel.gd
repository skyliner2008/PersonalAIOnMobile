extends Node3D
## A hologram info panel the pet presents: a glass card that materialises from the bottom up, with
## live data on it. The scene director builds one per `panel` beat; params are the beat's fields
## laid over the scene's data (see scenes/SCHEMA.md, "Info scenes").
##
## Kinds:
##   weather   the app's weather model (location, temp, feels_like, humidity, wind, rain_prob, max,
##             min, weather_code, is_day, desc) with an animated 3D icon
##   balance   balance sheet from the app's StockFundamentalData: assets (cash + other) as one 3D
##             column, liabilities (debt + other) + equity as the other, and a legend
##   pie       3D donut chart: slices [{label, value, color?}]
##   bars      3D bar chart: bars [{label, value, color?}] (negative values hang below the line)
##   kpi       a grid of numbers: items [{label, value, sub?, good?}]
##   text      short markdown: # headings, **bold**, *italic*, - bullets, > quotes, `code`
## Common params: title, subtitle, size [w, h] in metres.

const CARD_SHADER := preload("res://shaders/holo_card.gdshader")
const PPM := 360.0                       # SubViewport pixels per metre (markdown cards)
const TEXT := Color(0.93, 0.97, 1.0)
const DIM := Color(0.62, 0.78, 0.9)
const ACCENT := Color(0.45, 0.9, 1.0)
const GOOD := Color(0.35, 0.95, 0.55)
const BAD := Color(1.0, 0.42, 0.42)
const PALETTE := [Color("4FA3FF"), Color("FF8A4C"), Color("A77BFF"), Color("3DDC97"), Color("FFC247"),
	Color("FF5A9E"), Color("5FD9E6")]
const CURRENCY := {"USD": "$", "THB": "฿", "EUR": "€", "JPY": "¥", "GBP": "£", "CNY": "¥"}

## WMO weather code -> [kind, Thai description] (same grouping as the app's weather tool)
const WMO := {
	0: ["clear", "ท้องฟ้าแจ่มใส"], 1: ["partly", "ท้องฟ้าโปร่ง มีเมฆเล็กน้อย"], 2: ["partly", "มีเมฆบางส่วน"],
	3: ["cloudy", "มีเมฆมาก"], 45: ["fog", "มีหมอก"], 48: ["fog", "หมอกน้ำแข็ง"],
	51: ["drizzle", "ฝนละออง"], 53: ["drizzle", "ฝนปรอยๆ"], 55: ["drizzle", "ฝนปรอยหนาแน่น"],
	56: ["drizzle", "ฝนละอองเยือกแข็ง"], 57: ["drizzle", "ฝนละอองเยือกแข็ง"],
	61: ["rain", "ฝนตกเล็กน้อย"], 63: ["rain", "ฝนตกปานกลาง"], 65: ["rain", "ฝนตกหนัก"],
	66: ["rain", "ฝนเยือกแข็ง"], 67: ["rain", "ฝนเยือกแข็งหนัก"],
	71: ["snow", "หิมะตกเล็กน้อย"], 73: ["snow", "หิมะตก"], 75: ["snow", "หิมะตกหนัก"], 77: ["snow", "เกล็ดหิมะ"],
	80: ["rain", "ฝนซู่"], 81: ["rain", "ฝนซู่ปานกลาง"], 82: ["rain", "ฝนซู่หนัก"],
	85: ["snow", "หิมะโปรย"], 86: ["snow", "หิมะโปรยหนัก"],
	95: ["thunder", "พายุฝนฟ้าคะนอง"], 96: ["thunder", "พายุฝนฟ้าคะนอง ลูกเห็บ"], 99: ["thunder", "พายุฝนฟ้าคะนอง ลูกเห็บหนัก"],
}

var kind := ""
var size := Vector2(2.1, 1.3)
var params := {}

var _card: MeshInstance3D
var _mat: ShaderMaterial
var _content := Node3D.new()             # labels and charts; they pop in once the card is there
var _on_reveal: Array[Callable] = []     # chart animations, started when the card has materialised
var _icon: Node3D                        # weather icon, animated in _process
var _icon_kind := ""
var _t := 0.0
var _home := Vector3.ZERO
var _opened := false
var _fonts := {}
var _text_view: SubViewport              # markdown cards: the text is drawn here and shown on the glass
var _text_label: RichTextLabel
var _text_pad := 0


# ---------------------------------------------------------------- data helpers (static)

## Adds the fields scenes use in conditions and captions: weather `kind`, `desc`, `hot`, `cold`,
## `rainy`, `night`; balance-sheet splits and percentages (`liabilitiesPct`, `equityPct`,
## `otherAssets`, `otherLiabilities`, `netCash`, `hasNetCash`); a currency symbol.
static func derive(data: Dictionary) -> Dictionary:
	var d := data.duplicate(true)
	if d.has("weather_code"):
		var w: Array = WMO.get(int(d["weather_code"]), ["cloudy", "สภาพอากาศปกติ"])
		d["kind"] = d.get("kind", w[0])
		d["desc"] = d.get("desc", w[1])
		d["night"] = int(d.get("is_day", 1)) == 0
		var temp := float(d.get("temp", 25.0))
		d["hot"] = temp >= 33.0
		d["cold"] = temp <= 18.0
		d["rainy"] = d["kind"] in ["drizzle", "rain", "thunder"]
		# the one thing worth saying about it, first match wins (scenes react to this)
		var k: String = d["kind"]
		var rain_prob := float(_num(d.get("rain_prob"), 0.0))
		if k == "thunder": d["situation"] = "thunder"
		elif d["rainy"]: d["situation"] = "rain"
		elif k == "snow": d["situation"] = "snow"
		elif d["hot"]: d["situation"] = "hot"
		elif d["cold"]: d["situation"] = "cold"
		elif rain_prob >= 50.0: d["situation"] = "rain_likely"
		elif k == "fog": d["situation"] = "fog"
		elif k in ["clear", "partly"]: d["situation"] = "nice"
		else: d["situation"] = "cloudy"
		if d["night"]: d["backdrop"] = "night"
		elif k == "snow": d["backdrop"] = "snow"
		elif k in ["clear", "partly"]: d["backdrop"] = "sunny"
		else: d["backdrop"] = "black"
	if d.has("totalAssets") and d["totalAssets"] != null:
		var a := float(d["totalAssets"])
		var l := float(_num(d.get("totalLiabilities"), 0.0))
		var e := float(_num(d.get("totalEquity"), a - l))
		var debt := float(_num(d.get("totalDebt"), 0.0))
		var cash = d.get("cashAndEquivalents")
		if cash == null and d.get("netDebt") != null:
			cash = maxf(0.0, debt - float(d["netDebt"]))
		cash = float(_num(cash, 0.0))
		d["cashAndEquivalents"] = cash
		d["otherAssets"] = maxf(0.0, a - cash)
		d["otherLiabilities"] = maxf(0.0, l - debt)
		d["liabilitiesPct"] = l / a * 100.0 if a > 0.0 else 0.0
		d["equityPct"] = e / a * 100.0 if a > 0.0 else 0.0
		d["netCash"] = cash - debt
		d["hasNetCash"] = cash > debt
	# trading_price: price / change / change_pct / prev_close / high_52w / low_52w (text values)
	if d.has("price") and d.has("change_pct"):
		d["up"] = numf(d["change_pct"]) >= 0.0
		d["move"] = numf(d["change_pct"])
		var hi = num(d.get("high_52w"))
		var lo = num(d.get("low_52w"))
		d["has_range"] = hi != null and lo != null and float(hi) > float(lo)
	# trading_technical_analysis: RSI, MACD.macd / MACD.signal, ADX, recommend_score, signal, buy/sell/neutral_signals
	if d.has("recommend_score") or d.has("RSI"):
		var rsi := numf(d.get("RSI"), 50.0)
		d["rsi_zone"] = "oversold" if rsi < 30.0 else ("overbought" if rsi > 70.0 else "neutral")
		d["score"] = numf(d.get("recommend_score", d.get("Recommend.All")))
		d["macd_bull"] = numf(d.get("MACD.macd")) >= numf(d.get("MACD.signal"))
		d["macd_word"] = "เหนือเส้นสัญญาณ" if d["macd_bull"] else "ใต้เส้นสัญญาณ"
		d["trend_strong"] = numf(d.get("ADX")) >= 25.0
		d["trend_word"] = "แรง (ADX ≥ 25)" if d["trend_strong"] else "อ่อน (ADX < 25)"
		d["day_up"] = numf(d.get("change")) >= 0.0
		d["signal_th"] = {"STRONG BUY": "ซื้อแรง", "BUY": "ซื้อ", "HOLD": "กลางๆ", "NEUTRAL": "กลางๆ", "SELL": "ขาย",
			"STRONG SELL": "ขายแรง"}.get(str(d.get("signal", "")).to_upper(), str(d.get("signal", "")))
	# trading_fear_greed: value, classification, trend "v0,v1,..." (newest first)
	if d.has("classification") and d.has("value"):
		var v := numf(d["value"])
		d["fg"] = v
		d["fg_zone"] = "extreme_fear" if v < 25 else ("fear" if v < 45 else ("neutral" if v <= 55 else ("greed" if v <= 75 else "extreme_greed")))
		d["fg_th"] = {"extreme_fear": "กลัวสุดขีด", "fear": "กลัว", "neutral": "กลางๆ", "greed": "โลภ", "extreme_greed": "โลภสุดขีด"}[d["fg_zone"]]
		var days := []
		var trend := str(d.get("trend", "")).split(",", false)
		for i in trend.size():                    # oldest first, today last
			var n = num(trend[trend.size() - 1 - i])
			if n != null:
				days.append({"label": "วันนี้" if i == trend.size() - 1 else "-%d" % (trend.size() - 1 - i), "value": n,
					"text": str(int(n)), "color": _fg_color(float(n)).to_html(false)})
		d["fg_days"] = days
	# trading_top_gainers / losers: a list of scanner rows (symbol, close, change, ...)
	if d.get("items") is Array and d["items"].size() > 0 and d["items"][0] is Dictionary and d["items"][0].has("change"):
		# one bar per coin: prefer the USDT pairs (the scanner also lists TRY / USDC / perpetual copies)
		var rows: Array = d["items"].filter(func(r): return str(r.get("symbol", "")).ends_with("USDT"))
		if rows.size() < 3:
			rows = d["items"]
		var bars := []
		var seen := {}
		for row in rows:
			var sym := str(row.get("symbol", row.get("name", "")))
			var label := sym.trim_suffix(".P").trim_suffix("USDT")
			if seen.has(label):
				continue
			seen[label] = true
			var ch := numf(row.get("change"))
			bars.append({"label": label, "value": ch, "text": "%+.1f%%" % ch, "color": "#3DDC97" if ch >= 0.0 else "#FF5A6E"})
			if bars.size() == 5:
				break
		d["movers"] = bars
		d["best_symbol"] = bars[0]["label"]
		d["best_change"] = bars[0]["text"]
		d["up"] = numf(d["items"][0].get("change")) >= 0.0
	# trading_crypto_overview (CoinGecko global)
	if d.has("btc_dominance"):
		var btc := numf(d["btc_dominance"])
		var eth := numf(d.get("eth_dominance"))
		d["dominance"] = [{"label": "BTC", "value": btc, "text": "", "color": "#FFB547"},
			{"label": "ETH", "value": eth, "text": "", "color": "#8FA4FF"},
			{"label": "อื่นๆ", "value": maxf(0.0, 100.0 - btc - eth), "text": "", "color": "#5FD9E6"}]
		d["up"] = numf(d.get("market_cap_change_24h")) >= 0.0
	# device_battery_status
	if d.has("level"):
		var level := numf(d["level"])
		d["low"] = level < 20.0
		d["full"] = level >= 95.0
		d["level01"] = level / 100.0
		d["charging_th"] = "กำลังชาร์จ ⚡" if bool(d.get("charging", false)) else "ไม่ได้ชาร์จ"
	d["currencySymbol"] = CURRENCY.get(str(d.get("currency", "USD")), "")
	return d


static func _fg_color(v: float) -> Color:
	if v < 25: return Color("FF4D5E")
	if v < 45: return Color("FF9A4C")
	if v <= 55: return Color("FFD24A")
	if v <= 75: return Color("9BE36B")
	return Color("3DDC97")


static func _num(v, fallback):
	return fallback if v == null else v


## A number from a tool value: numbers as they are; strings like "1,234.5", "+2.31%", "$12", "4085.2000"
## are parsed; "N/A", "-", "" and anything else give null. (The app's trading tools return text maps.)
static func num(v):
	if v is float or v is int:
		return float(v)
	if v is String:
		var s: String = str(v).strip_edges().replace(",", "").replace("%", "").replace("$", "").replace("฿", "").replace("+", "")
		if s.is_valid_float():
			return s.to_float()
	return null


static func numf(v, fallback := 0.0) -> float:
	var n = num(v)
	return fallback if n == null else float(n)


## 4085.2 -> "4,085.20"; small prices keep more decimals (0.000123 -> "0.000123").
static func group(x: float, decimals := -1) -> String:
	if decimals < 0:
		var a := absf(x)
		decimals = 2 if a >= 1.0 else (4 if a >= 0.01 else 6)
	var s := ("%." + str(decimals) + "f") % absf(x)
	var whole := s.get_slice(".", 0)
	var frac := s.get_slice(".", 1) if decimals > 0 else ""
	var out := ""
	while whole.length() > 3:
		out = "," + whole.right(3) + out
		whole = whole.left(whole.length() - 3)
	out = whole + out
	return ("-" if x < 0.0 else "") + out + ("." + frac if frac != "" else "")


## 619000000000 -> "$619.0B" (T / B / M / K)
static func money(v, symbol := "$") -> String:
	if v == null:
		return "–"
	var x := float(v)
	var sign := "-" if x < 0.0 else ""
	x = absf(x)
	for unit in [[1e12, "T"], [1e9, "B"], [1e6, "M"], [1e3, "K"]]:
		if x >= unit[0]:
			return "%s%s%.1f%s" % [sign, symbol, x / unit[0], unit[1]]
	return "%s%s%.0f" % [sign, symbol, x]


## 12.34 -> "12.3%"; under 1 % keeps two decimals (0.17 -> "0.17%") so it matches the tool's own text.
static func pct(v, signed := false) -> String:
	if v == null:
		return "–"
	var x := float(v)
	var digits := 2 if absf(x) < 1.0 else 1
	return ("%+." + str(digits) + "f%%" if signed else "%." + str(digits) + "f%%") % x


## Short markdown -> RichTextLabel BBCode.
static func md_to_bbcode(md: String) -> String:
	var out := PackedStringArray()
	for raw in md.replace("\r", "").split("\n"):
		var line := raw.strip_edges(false, true).replace("[", "[lb]")
		var stripped := line.strip_edges()
		if stripped.begins_with("### "):
			line = "[font_size=34][b][color=#7fe3ff]%s[/color][/b][/font_size]" % _md_inline(stripped.substr(4))
		elif stripped.begins_with("## "):
			line = "[font_size=40][b][color=#7fe3ff]%s[/color][/b][/font_size]" % _md_inline(stripped.substr(3))
		elif stripped.begins_with("# "):
			line = "[font_size=46][b][color=#9fefff]%s[/color][/b][/font_size]" % _md_inline(stripped.substr(2))
		elif stripped.begins_with("- ") or stripped.begins_with("* ") or stripped.begins_with("• "):
			line = "[indent][color=#6fe0ff]•[/color] %s[/indent]" % _md_inline(stripped.substr(2))
		elif stripped.begins_with("> "):
			line = "[i][color=#a9c4d8]%s[/color][/i]" % _md_inline(stripped.substr(2))
		else:
			line = _md_inline(line)
		out.append(line)
	return "\n".join(out)


static func _md_inline(s: String) -> String:
	var rules := [
		["\\*\\*(.+?)\\*\\*", "[b]$1[/b]"], ["__(.+?)__", "[b]$1[/b]"],
		["(?<![\\w*])\\*(?!\\s)(.+?)\\*(?!\\w)", "[i]$1[/i]"], ["(?<![\\w_])_(?!\\s)(.+?)_(?!\\w)", "[i]$1[/i]"],
		["~~(.+?)~~", "[s]$1[/s]"], ["`(.+?)`", "[code][color=#ffd27a]$1[/color][/code]"],
		["(?<![\\w.])\\+(\\d[\\d.,]*%)", "[color=#5ff08c]+$1[/color]"], ["(?<![\\w.])-(\\d[\\d.,]*%)", "[color=#ff6b6b]-$1[/color]"],
	]
	for r in rules:
		var re := RegEx.create_from_string(r[0])
		s = re.sub(s, r[1], true)
	return s


# ---------------------------------------------------------------- building

func setup(k: String, p: Dictionary) -> void:
	kind = k
	params = p
	var sz = p.get("size")
	if sz is Array and sz.size() == 2:
		size = Vector2(float(sz[0]), float(sz[1]))
	else:
		size = {"weather": Vector2(2.1, 1.5), "balance": Vector2(2.1, 1.6), "pie": Vector2(2.1, 1.45),
			"bars": Vector2(2.1, 1.45), "kpi": Vector2(2.1, 0.9), "text": Vector2(1.9, 0.8),
			"gauge": Vector2(2.1, 1.45), "price": Vector2(2.1, 1.45)}.get(k, Vector2(2.0, 1.2))
	add_child(_content)
	match k:
		"weather": _build_weather(p)
		"balance": _build_balance(p)
		"pie": _build_pie(p)
		"bars": _build_bars(p)
		"kpi": _build_kpi(p)
		"gauge": _build_gauge(p)
		"price": _build_price(p)
		_: _build_text(p)
	_make_card()


func _make_card() -> void:
	_card = MeshInstance3D.new()
	var quad := QuadMesh.new()
	quad.size = size
	_card.mesh = quad
	_mat = ShaderMaterial.new()
	_mat.shader = CARD_SHADER
	_mat.set_shader_parameter("size", size)
	_mat.set_shader_parameter("reveal", 0.0)
	_card.material_override = _mat
	add_child(_card)
	move_child(_card, 0)


func _font(weight := 600) -> SystemFont:
	if not _fonts.has(weight):
		var f := SystemFont.new()
		f.font_names = PackedStringArray(["Leelawadee UI", "Noto Sans Thai", "Tahoma", "Segoe UI Symbol", "Segoe UI Emoji", "sans-serif"])
		f.font_weight = weight
		_fonts[weight] = f
	return _fonts[weight]


## A line of text on the card. `align` -1 = its left edge at x, 0 = centred, 1 = its right edge at x.
func _label(text: String, x: float, y: float, height: float, color := TEXT, align := 0, weight := 600,
		z := 0.02) -> Label3D:
	var l := Label3D.new()
	l.text = text
	l.font = _font(weight)
	l.font_size = 64
	l.pixel_size = height / 64.0
	l.modulate = color
	l.outline_size = 10
	l.outline_modulate = Color(0.0, 0.05, 0.1, 0.55)
	l.render_priority = 6
	l.outline_render_priority = 5
	l.double_sided = true
	_place_label(l, x, align)
	l.position.y = y
	l.position.z = z
	_content.add_child(l)
	return l


func _place_label(l: Label3D, x: float, align: int) -> void:
	var w := (l.font as Font).get_string_size(l.text, HORIZONTAL_ALIGNMENT_LEFT, -1, l.font_size).x * l.pixel_size
	l.position.x = x + w * 0.5 * float(-align)


func _title(p: Dictionary) -> float:
	var top := size.y * 0.5 - 0.13
	if str(p.get("title", "")) != "":
		_label(str(p["title"]), -size.x * 0.5 + 0.1, top, 0.12, TEXT, -1, 700)
	if str(p.get("subtitle", "")) != "":
		_label(str(p["subtitle"]), size.x * 0.5 - 0.1, top - 0.005, 0.075, DIM, 1, 500)
	return top


func _chart_material(c: Color) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = 0.35
	m.emission_enabled = true
	m.emission = c
	m.emission_energy_multiplier = 0.35
	m.cull_mode = BaseMaterial3D.CULL_DISABLED
	return m


func _dot(x: float, y: float, c: Color, r := 0.035) -> void:
	var m := MeshInstance3D.new()
	var s := SphereMesh.new()
	s.radius = r
	s.height = r * 2.0
	m.mesh = s
	m.material_override = _chart_material(c)
	m.position = Vector3(x, y, 0.04)
	_content.add_child(m)


func _color(item: Dictionary, i: int) -> Color:
	var c = item.get("color")
	return Color(c) if c is String and c != "" else PALETTE[i % PALETTE.size()]


# ---------------------------------------------------------------- weather

func _build_weather(p: Dictionary) -> void:
	_title({"title": p.get("title", p.get("location", "")), "subtitle": p.get("subtitle", "วันนี้")})
	var temp := float(p.get("temp", 0.0))
	var big := _label("%d°C" % roundi(temp), 0.38, 0.27, 0.42, TEXT, 0, 700)
	_on_reveal.append(func():
		var t := create_tween()
		t.tween_method(func(v: float): big.text = "%d°C" % roundi(v), temp - 12.0, temp, 0.9).set_trans(Tween.TRANS_EXPO).set_ease(Tween.EASE_OUT))
	_label(str(p.get("desc", "")), 0.38, 0.0, 0.1, ACCENT, 0, 600)
	_label("สูงสุด %d° · ต่ำสุด %d°" % [roundi(float(p.get("max", temp))), roundi(float(p.get("min", temp)))],
		0.38, -0.13, 0.075, DIM, 0, 500)
	var chips := [
		["รู้สึกเหมือน", "%d°C" % roundi(float(p.get("feels_like", temp)))],
		["ความชื้น", "%d%%" % roundi(float(p.get("humidity", 0)))],
		["ลม", "%d กม./ชม." % roundi(float(p.get("wind", 0)))],
		["โอกาสฝน", "%d%%" % roundi(float(p.get("rain_prob", 0)))],
	]
	var cw := (size.x - 0.25) / 2.0
	for i in chips.size():
		var cx := -size.x * 0.5 + 0.1 + cw * 0.5 + (cw + 0.05) * (i % 2)
		var cy := -0.33 - 0.21 * floorf(i / 2.0)
		_chip(cx, cy, cw, 0.17)
		_label(chips[i][0], cx - cw * 0.5 + 0.06, cy, 0.08, Color(0.75, 0.88, 0.97), -1, 600, 0.03)
		_label(chips[i][1], cx + cw * 0.5 - 0.06, cy, 0.09, TEXT, 1, 700, 0.03)
	_icon = _weather_icon(str(p.get("kind", "clear")), bool(p.get("night", false)))
	_icon.position = Vector3(-0.55, 0.2, 0.16)
	_content.add_child(_icon)


func _chip(x: float, y: float, w: float, h: float) -> void:
	var m := MeshInstance3D.new()
	var q := QuadMesh.new()
	q.size = Vector2(w, h)
	m.mesh = q
	var mat := ShaderMaterial.new()
	mat.shader = CARD_SHADER
	mat.set_shader_parameter("size", Vector2(w, h))
	mat.set_shader_parameter("radius", 0.05)
	mat.set_shader_parameter("tint", Color(0.2, 0.45, 0.65, 0.35))
	mat.set_shader_parameter("rim_color", Color(0.3, 0.6, 0.8))
	m.material_override = mat
	m.position = Vector3(x, y, 0.01)
	_content.add_child(m)


func _weather_icon(k: String, night: bool) -> Node3D:
	_icon_kind = k
	var root := Node3D.new()
	var sunny := k in ["clear", "partly"]
	if sunny and not night:
		var sun := Node3D.new()
		sun.name = "Sun"
		var core := MeshInstance3D.new()
		var s := SphereMesh.new()
		s.radius = 0.16
		s.height = 0.32
		core.mesh = s
		var m := StandardMaterial3D.new()
		m.albedo_color = Color(1.0, 0.8, 0.25)
		m.emission_enabled = true
		m.emission = Color(1.0, 0.72, 0.15)
		m.emission_energy_multiplier = 2.2
		core.material_override = m
		sun.add_child(core)
		var rays := Node3D.new()
		rays.name = "Rays"
		for i in 10:
			var r := MeshInstance3D.new()
			var b := BoxMesh.new()
			b.size = Vector3(0.035, 0.1, 0.03)
			r.mesh = b
			r.material_override = m
			var a := TAU * i / 10.0
			r.position = Vector3(sin(a) * 0.25, cos(a) * 0.25, 0.0)
			r.rotation.z = -a
			rays.add_child(r)
		sun.add_child(rays)
		if k == "partly":
			sun.position = Vector3(-0.1, 0.12, -0.05)
		root.add_child(sun)
	elif sunny and night:
		var moon := Label3D.new()
		moon.text = "☾"
		moon.font = _font(400)
		moon.font_size = 128
		moon.pixel_size = 0.45 / 128.0
		moon.modulate = Color(1.0, 0.93, 0.6)
		moon.name = "Moon"
		root.add_child(moon)
		for i in 3:
			var st := Label3D.new()
			st.text = "✦"
			st.font = _font(400)
			st.font_size = 64
			st.pixel_size = 0.07 / 64.0
			st.modulate = Color(1.0, 0.95, 0.7)
			st.position = Vector3([-0.22, 0.2, 0.25][i], [0.18, 0.22, -0.1][i], 0.02)
			root.add_child(st)
	if k != "clear":
		var dark := k in ["rain", "thunder"]
		var cloud := _cloud(Color(0.55, 0.6, 0.68) if dark else Color(0.93, 0.95, 0.98))
		cloud.name = "Cloud"
		cloud.position = Vector3(0.06, -0.04, 0.08) if k == "partly" else Vector3(0.0, 0.05, 0.0)
		root.add_child(cloud)
		if k in ["drizzle", "rain", "thunder", "snow"]:
			cloud.add_child(_precip(k == "snow", k == "drizzle"))
		if k == "thunder":
			var bolt := Label3D.new()
			bolt.name = "Bolt"
			bolt.text = "⚡"
			bolt.font = _font(400)
			bolt.font_size = 96
			bolt.pixel_size = 0.3 / 96.0
			bolt.position = Vector3(0.05, -0.22, 0.12)
			root.add_child(bolt)
		if k == "fog":
			for i in 3:
				var band := MeshInstance3D.new()
				var c := CapsuleMesh.new()
				c.radius = 0.035
				c.height = 0.5 - 0.08 * i
				band.mesh = c
				band.rotation.z = PI * 0.5
				var fm := StandardMaterial3D.new()
				fm.albedo_color = Color(0.85, 0.9, 0.95, 0.75)
				fm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
				band.material_override = fm
				band.position = Vector3(0.04 * i, -0.2 - 0.09 * i, 0.12)
				band.name = "Fog%d" % i
				root.add_child(band)
	root.scale = Vector3.ONE * 1.15
	return root


func _cloud(c: Color) -> Node3D:
	var cloud := Node3D.new()
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = 0.8
	m.emission_enabled = true
	m.emission = c
	m.emission_energy_multiplier = 0.25
	for puff in [[Vector3(0, 0.04, 0), 0.16], [Vector3(-0.17, -0.03, 0.02), 0.11], [Vector3(0.18, -0.02, 0), 0.12],
			[Vector3(-0.07, 0.11, -0.03), 0.11], [Vector3(0.09, 0.1, 0.02), 0.12], [Vector3(0, -0.06, 0.07), 0.12]]:
		var p := MeshInstance3D.new()
		var s := SphereMesh.new()
		s.radius = puff[1]
		s.height = puff[1] * 1.8
		p.mesh = s
		p.material_override = m
		p.position = puff[0]
		cloud.add_child(p)
	return cloud


func _precip(snow: bool, light: bool) -> CPUParticles3D:
	var p := CPUParticles3D.new()
	var q := QuadMesh.new()
	q.size = Vector2(0.03, 0.03) if snow else Vector2(0.018, 0.09)
	var m := StandardMaterial3D.new()
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.albedo_color = Color(1, 1, 1, 0.95) if snow else Color(0.5, 0.78, 1.0, 0.95)
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.billboard_mode = BaseMaterial3D.BILLBOARD_ENABLED if snow else BaseMaterial3D.BILLBOARD_FIXED_Y
	if snow:
		m.albedo_texture = preload("res://scripts/effects.gd").soft_dot()
	q.material = m
	p.mesh = q
	p.amount = 14 if light else 26
	p.lifetime = 0.9 if snow else 0.4
	p.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
	p.emission_box_extents = Vector3(0.2, 0.01, 0.06)
	p.direction = Vector3.DOWN
	p.spread = 8.0 if snow else 0.0
	p.gravity = Vector3(0, -0.6 if snow else -4.0, 0)
	p.initial_velocity_min = 0.25 if snow else 0.9
	p.initial_velocity_max = 0.4 if snow else 1.2
	p.position = Vector3(0, -0.1, 0.02)
	p.local_coords = true
	return p


# ---------------------------------------------------------------- balance sheet

func _build_balance(p: Dictionary) -> void:
	var sym: String = p.get("currencySymbol", "$")
	_title({"title": p.get("title", "งบดุล · %s" % p.get("symbol", "")),
		"subtitle": p.get("subtitle", "ไตรมาสล่าสุด · %s" % p.get("currency", ""))})
	var a := float(p.get("totalAssets", 0.0))
	var l := float(p.get("totalLiabilities", 0.0))
	var e := float(p.get("totalEquity", 0.0))
	var parts := [
		["เงินสดและเทียบเท่า", float(p.get("cashAndEquivalents", 0.0)), Color("3DDC97"), 0],
		["สินทรัพย์อื่น", float(p.get("otherAssets", a)), Color("4FA3FF"), 0],
		["หนี้สินที่มีดอกเบี้ย", float(p.get("totalDebt", 0.0)), Color("FF5A6E"), 1],
		["หนี้สินอื่น", float(p.get("otherLiabilities", l)), Color("FF9A4C"), 1],
		["ส่วนของผู้ถือหุ้น", e, Color("A77BFF"), 1],
	]
	var base := -size.y * 0.5 + 0.28
	var full := size.y - 0.74
	var unit := full / maxf(a, l + e) if maxf(a, l + e) > 0.0 else 0.0     # metres per currency unit
	var cols := [-0.74, -0.22]
	var stacks := [0.0, 0.0]
	var pivots := [Node3D.new(), Node3D.new()]
	for c in 2:
		pivots[c].position = Vector3(cols[c], base, 0.12)
		pivots[c].scale = Vector3(1.0, 0.001, 1.0)
		_content.add_child(pivots[c])
	for part in parts:
		var hgt: float = part[1] * unit
		if hgt <= 0.0:
			continue
		var c: int = part[3]
		var m := MeshInstance3D.new()
		var b := BoxMesh.new()
		b.size = Vector3(0.34, maxf(hgt - 0.008, 0.002), 0.18)
		m.mesh = b
		m.material_override = _chart_material(part[2])
		m.position = Vector3(0, stacks[c] + hgt * 0.5, 0)
		pivots[c].add_child(m)
		stacks[c] += hgt
	_label(money(a, sym), cols[0], base + a * unit + 0.08, 0.085, TEXT, 0, 700, 0.14)
	_label(money(l + e, sym), cols[1], base + (l + e) * unit + 0.08, 0.085, TEXT, 0, 700, 0.14)
	_label("สินทรัพย์", cols[0], base - 0.1, 0.072, DIM, 0, 600, 0.14)
	_label("หนี้สิน+ทุน", cols[1], base - 0.1, 0.072, DIM, 0, 600, 0.14)
	_label("=", (cols[0] + cols[1]) * 0.5, base + full * 0.45, 0.16, ACCENT, 0, 700, 0.14)
	for c in 2:
		var piv: Node3D = pivots[c]
		_on_reveal.append(func():
			var t := piv.create_tween()
			t.tween_interval(0.15 * c)
			t.tween_property(piv, "scale:y", 1.0, 0.8).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT))
	# legend
	var y := size.y * 0.5 - 0.34
	for part in parts:
		_dot(0.08, y, part[2], 0.04)
		_label(part[0], 0.15, y, 0.075, TEXT, -1, 600)
		var share: float = part[1] / a * 100.0 if a > 0.0 else 0.0
		_label("%s · %s" % [money(part[1], sym), pct(share)], size.x * 0.5 - 0.08, y - 0.09, 0.07, DIM, 1, 600)
		y -= 0.225


# ---------------------------------------------------------------- pie / donut

func _build_pie(p: Dictionary) -> void:
	var sym: String = p.get("currencySymbol", "$")
	_title(p)
	var slices: Array = p.get("slices", [])
	var total := 0.0
	for s in slices:
		total += maxf(0.0, float(_num(s.get("value"), 0.0)))
	if total <= 0.0:
		return
	var donut := Node3D.new()
	donut.position = Vector3(-0.46, -0.1, 0.16)
	donut.rotation.x = deg_to_rad(-18.0)
	_content.add_child(donut)
	var meshes: Array = []
	var a0 := 0.0
	var biggest := 0
	for i in slices.size():
		if float(_num(slices[i].get("value"), 0.0)) > float(_num(slices[biggest].get("value"), 0.0)):
			biggest = i
	for i in slices.size():
		var frac := maxf(0.0, float(_num(slices[i].get("value"), 0.0))) / total
		var m := MeshInstance3D.new()
		m.material_override = _chart_material(_color(slices[i], i))
		donut.add_child(m)
		var mid := a0 + frac * TAU * 0.5
		if i == biggest:                  # the largest slice stands out a little
			m.position = Vector3(sin(mid), cos(mid), 0.0) * 0.04
		meshes.append([m, a0, a0 + frac * TAU])
		a0 += frac * TAU
	var draw := func(progress: float):
		var limit := progress * TAU
		for item in meshes:
			var from: float = item[1]
			var to: float = minf(item[2], limit)
			(item[0] as MeshInstance3D).mesh = _donut_slice(0.24, 0.47, 0.15, from, to) if to > from + 0.001 else null
	draw.call(0.0)
	_on_reveal.append(func():
		create_tween().tween_method(draw, 0.0, 1.0, 0.9).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT))
	if str(p.get("center", "")) != "":
		_label(str(p["center"]), -0.46, -0.1, 0.085, TEXT, 0, 700, 0.24)
	var y := size.y * 0.5 - 0.36
	var step := minf(0.26, (size.y - 0.55) / maxf(1.0, slices.size()))
	for i in slices.size():
		var v := float(_num(slices[i].get("value"), 0.0))
		_dot(0.12, y, _color(slices[i], i), 0.04)
		_label(str(slices[i].get("label", "")), 0.19, y, 0.075, TEXT, -1, 600)
		var shown: String = str(slices[i].get("text", money(v, sym) if p.get("money", true) else str(v)))
		var share := pct(v / total * 100.0)
		_label(share if shown == "" else "%s · %s" % [shown, share], size.x * 0.5 - 0.08, y - 0.09, 0.07, DIM, 1, 600)
		y -= step


## One slice of a donut facing +Z (angles clockwise from 12 o'clock), as a closed solid.
func _donut_slice(r0: float, r1: float, depth: float, a0: float, a1: float) -> ArrayMesh:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var n := maxi(2, int(ceil((a1 - a0) / TAU * 64.0)))
	var gap := 0.012                               # a hairline between slices
	var b0 := a0 + gap / r1
	var b1 := maxf(b0 + 0.001, a1 - gap / r1)
	var zf := depth * 0.5
	var pt := func(r: float, a: float, z: float) -> Vector3: return Vector3(sin(a) * r, cos(a) * r, z)
	for i in n:
		var t0 := lerpf(b0, b1, float(i) / n)
		var t1 := lerpf(b0, b1, float(i + 1) / n)
		for face in [[zf, Vector3(0, 0, 1)], [-zf, Vector3(0, 0, -1)]]:
			var z: float = face[0]
			_quad(st, face[1], pt.call(r0, t0, z), pt.call(r1, t0, z), pt.call(r1, t1, z), pt.call(r0, t1, z))
		var o0 := Vector3(sin(t0), cos(t0), 0)
		var o1 := Vector3(sin(t1), cos(t1), 0)
		_quad(st, (o0 + o1).normalized(), pt.call(r1, t0, zf), pt.call(r1, t0, -zf), pt.call(r1, t1, -zf), pt.call(r1, t1, zf))
		_quad(st, -(o0 + o1).normalized(), pt.call(r0, t0, zf), pt.call(r0, t0, -zf), pt.call(r0, t1, -zf), pt.call(r0, t1, zf))
	for e in [[b0, -1.0], [b1, 1.0]]:
		var a: float = e[0]
		var nrm: Vector3 = Vector3(cos(a), -sin(a), 0) * float(e[1])
		_quad(st, nrm, pt.call(r0, a, zf), pt.call(r1, a, zf), pt.call(r1, a, -zf), pt.call(r0, a, -zf))
	return st.commit()


func _quad(st: SurfaceTool, n: Vector3, a: Vector3, b: Vector3, c: Vector3, d: Vector3) -> void:
	for v in [a, b, c, a, c, d]:
		st.set_normal(n)
		st.add_vertex(v)


# ---------------------------------------------------------------- bars

func _build_bars(p: Dictionary) -> void:
	var sym: String = p.get("currencySymbol", "$")
	_title(p)
	var bars: Array = p.get("bars", [])
	if bars.is_empty():
		return
	var hi := 0.0
	var lo := 0.0
	for b in bars:
		var v := float(_num(b.get("value"), 0.0))
		hi = maxf(hi, v)
		lo = minf(lo, v)
	var area := size.y - 0.8
	var unit := area / maxf(hi - lo, 1e-9)                  # metres per value unit
	var base := -size.y * 0.5 + 0.28 + (-lo) * unit
	var span := size.x - 0.4
	var slot := span / bars.size()
	var w := minf(0.32, slot * 0.55)
	var line := MeshInstance3D.new()
	var lb := BoxMesh.new()
	lb.size = Vector3(span + 0.1, 0.006, 0.2)
	line.mesh = lb
	line.material_override = _chart_material(Color(0.5, 0.75, 0.9))
	line.position = Vector3(0, base, 0.12)
	_content.add_child(line)
	for i in bars.size():
		var v := float(_num(bars[i].get("value"), 0.0))
		var x := -span * 0.5 + slot * (i + 0.5)
		var piv := Node3D.new()
		piv.position = Vector3(x, base, 0.12)
		piv.scale = Vector3(1.0, 0.001, 1.0)
		_content.add_child(piv)
		var m := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(w, maxf(absf(v) * unit, 0.004), 0.16)
		m.mesh = bm
		m.material_override = _chart_material(_color(bars[i], i))
		m.position = Vector3(0, v * unit * 0.5, 0)
		piv.add_child(m)
		var shown: String = str(bars[i].get("text", money(v, sym) if p.get("money", true) else str(v)))
		var vy := base + v * unit + (0.08 if v >= 0.0 else -0.08)
		_label(shown, x, vy, minf(0.085, slot * 0.26), TEXT, 0, 700, 0.14)
		_label(str(bars[i].get("label", "")), x, -size.y * 0.5 + 0.14, 0.07, DIM, 0, 600)
		var delay := 0.12 * i
		_on_reveal.append(func():
			var t := piv.create_tween()
			t.tween_interval(delay)
			t.tween_property(piv, "scale:y", 1.0, 0.7).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT))


# ---------------------------------------------------------------- gauge (RSI, fear & greed, rating)

## A 3D half-dial: coloured zones from `min` to `max`, a springy needle to `value`, the number under it.
## zones: [{to, color, label?}]  text: shown instead of the number  label: line under the number
func _build_gauge(p: Dictionary) -> void:
	_title(p)
	var lo := numf(p.get("min"), 0.0)
	var hi := numf(p.get("max"), 100.0)
	var value := clampf(numf(p.get("value"), lo), lo, hi)
	var center := Vector3(0.0, -0.3, 0.14)
	var dial := Node3D.new()
	dial.position = center
	_content.add_child(dial)
	var angle := func(v: float) -> float: return -PI * 0.5 + (v - lo) / maxf(hi - lo, 1e-9) * PI
	var zones: Array = p.get("zones", [{"to": hi, "color": "#4FA3FF"}])
	var from := lo
	for z in zones:
		var to := numf(z.get("to"), hi)
		var m := MeshInstance3D.new()
		m.mesh = _donut_slice(0.47, 0.72, 0.1, angle.call(from), angle.call(to))
		m.material_override = _chart_material(Color(str(z.get("color", "#4FA3FF"))))
		dial.add_child(m)
		if str(z.get("label", "")) != "":            # zone name just outside the arc
			var mid: float = angle.call((from + to) * 0.5)
			var lp := Vector2(sin(mid), cos(mid)) * 0.86
			_label(str(z["label"]), lp.x, center.y + lp.y, 0.056, DIM, 0, 600, 0.16)
		from = to
	var pivot := Node3D.new()
	pivot.position = Vector3(0, 0, 0.08)
	dial.add_child(pivot)
	var needle := MeshInstance3D.new()
	var nb := BoxMesh.new()
	nb.size = Vector3(0.035, 0.6, 0.03)
	needle.mesh = nb
	needle.position = Vector3(0, 0.3, 0)
	needle.material_override = _chart_material(Color(0.95, 0.97, 1.0))
	pivot.add_child(needle)
	var hub := MeshInstance3D.new()
	var hs := SphereMesh.new()
	hs.radius = 0.07
	hs.height = 0.14
	hub.mesh = hs
	hub.material_override = _chart_material(Color(0.95, 0.97, 1.0))
	pivot.add_child(hub)
	pivot.rotation.z = -angle.call(lo)
	var target: float = -angle.call(value)
	_on_reveal.append(func():
		pivot.create_tween().tween_property(pivot, "rotation:z", target, 1.2).set_trans(Tween.TRANS_ELASTIC).set_ease(Tween.EASE_OUT))
	var shown := str(p.get("text", "")) if str(p.get("text", "")) != "" else (str(roundi(value)) if absf(hi - lo) > 5.0 else "%.2f" % value)
	_label(shown, 0.0, center.y - 0.2, 0.16, TEXT, 0, 800, 0.2)
	if str(p.get("label", "")) != "":
		var lc = p.get("label_color")
		_label(str(p["label"]), 0.0, center.y - 0.36, 0.09, Color(str(lc)) if lc is String and lc != "" else ACCENT, 0, 700, 0.2)
	_label(str(p.get("min_text", _short(lo))), -0.6, center.y - 0.07, 0.06, DIM, 0, 500, 0.16)
	_label(str(p.get("max_text", _short(hi))), 0.6, center.y - 0.07, 0.06, DIM, 0, 500, 0.16)


func _short(v: float) -> String:
	return str(roundi(v)) if absf(v - roundf(v)) < 1e-6 else "%.1f" % v


# ---------------------------------------------------------------- price card

## trading_price: big price, the day's move in colour, previous close, and the 52-week range with a marker.
func _build_price(p: Dictionary) -> void:
	var sym: String = p.get("currencySymbol", "$")
	_title({"title": p.get("title", str(p.get("symbol", ""))), "subtitle": p.get("subtitle", str(p.get("market_state", "")))})
	var price := numf(p.get("price"))
	var move := numf(p.get("change_pct"))
	var change := numf(p.get("change"))
	var up := move >= 0.0
	var col := GOOD if up else BAD
	var big := _label(sym + group(price), 0.0, 0.3, 0.3, TEXT, 0, 800)
	_on_reveal.append(func():
		var t := create_tween()
		t.tween_method(func(v: float): big.text = sym + group(v), price * (0.985 if up else 1.015), price, 0.9) \
			.set_trans(Tween.TRANS_EXPO).set_ease(Tween.EASE_OUT))
	_label("%s %s%s (%+.2f%%)" % ["▲" if up else "▼", "+" if change >= 0.0 else "-", group(absf(change)), move],
		0.0, 0.07, 0.1, col, 0, 700)
	var prev = num(p.get("prev_close"))
	if prev != null:
		_label("ปิดก่อนหน้า %s%s" % [sym, group(float(prev))], 0.0, -0.07, 0.075, DIM, 0, 500)
	var hi = num(p.get("high_52w"))
	var lo = num(p.get("low_52w"))
	if hi != null and lo != null and float(hi) > float(lo):
		var y := -0.34
		var w := 1.6
		var bar := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(w, 0.05, 0.05)
		bar.mesh = bm
		bar.material_override = _chart_material(Color(0.35, 0.55, 0.75))
		bar.position = Vector3(0, y, 0.1)
		_content.add_child(bar)
		var t := clampf((price - float(lo)) / (float(hi) - float(lo)), 0.0, 1.0)
		var mark := MeshInstance3D.new()
		var ms := SphereMesh.new()
		ms.radius = 0.06
		ms.height = 0.12
		mark.mesh = ms
		mark.material_override = _chart_material(col)
		mark.position = Vector3(-w * 0.5, y, 0.14)
		_content.add_child(mark)
		_on_reveal.append(func():
			mark.create_tween().tween_property(mark, "position:x", -w * 0.5 + w * t, 1.0).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT))
		_label("ต่ำสุด 52 สัปดาห์", -w * 0.5, y - 0.1, 0.055, DIM, -1, 500)
		_label("สูงสุด", w * 0.5, y - 0.1, 0.055, DIM, 1, 500)
		_label(sym + group(float(lo)), -w * 0.5, y + 0.1, 0.06, TEXT, -1, 600)
		_label(sym + group(float(hi)), w * 0.5, y + 0.1, 0.06, TEXT, 1, 600)
	else:
		var src := str(p.get("source", ""))
		if src != "":
			_label("แหล่งข้อมูล %s" % src, 0.0, -0.3, 0.065, DIM, 0, 500)
	if str(p.get("fetched_at", "")) != "":
		_label(str(p["fetched_at"]), 0.0, -size.y * 0.5 + 0.1, 0.05, DIM, 0, 500)


# ---------------------------------------------------------------- kpi grid

func _build_kpi(p: Dictionary) -> void:
	var items: Array = p.get("items", [])
	var cols := int(p.get("columns", mini(3, maxi(1, items.size()))))
	var rows := int(ceil(items.size() / float(maxi(cols, 1))))
	var has_title: bool = str(p.get("title", "")) != ""
	if not p.has("size"):
		size = Vector2(size.x, (0.24 if has_title else 0.06) + rows * 0.36 + 0.08)
	var top := _title(p) if has_title else size.y * 0.5 + 0.07
	var cw := (size.x - 0.2) / cols
	for i in items.size():
		var it: Dictionary = items[i]
		var cx := -size.x * 0.5 + 0.1 + cw * (i % cols + 0.5)
		var cy := top - 0.24 - 0.36 * floorf(i / float(cols))
		_chip(cx, cy - 0.02, cw - 0.05, 0.32)
		_label(str(it.get("label", "")), cx, cy + 0.08, 0.065, DIM, 0, 500, 0.03)
		var good = it.get("good")                   # true / false (or "true" / "false" text), null = plain
		var vc := TEXT
		if good != null and str(good) != "":
			vc = GOOD if str(good).to_lower() in ["true", "1", "yes"] else BAD
		_label(str(it.get("value", "")), cx, cy - 0.04, 0.11, vc, 0, 700, 0.03)
		if str(it.get("sub", "")) != "":
			_label(str(it["sub"]), cx, cy - 0.13, 0.055, DIM, 0, 500, 0.03)


# ---------------------------------------------------------------- markdown text

func _build_text(p: Dictionary) -> void:
	var md := str(p.get("text", p.get("markdown", "")))
	if p.get("title", "") != "":
		md = "# %s\n%s" % [p["title"], md]
	var vp := SubViewport.new()
	vp.transparent_bg = true
	vp.disable_3d = true
	vp.gui_disable_input = true
	vp.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	var pad := 30
	var w := int(size.x * PPM)
	vp.size = Vector2i(w, 64)
	var rtl := RichTextLabel.new()
	rtl.bbcode_enabled = true
	rtl.fit_content = true
	rtl.scroll_active = false
	rtl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	rtl.position = Vector2(pad, pad)
	rtl.size = Vector2(w - pad * 2, 10)
	rtl.add_theme_font_override("normal_font", _font(500))
	rtl.add_theme_font_override("bold_font", _font(800))
	var italic := _font(500).duplicate() as SystemFont
	italic.font_italic = true
	rtl.add_theme_font_override("italics_font", italic)
	rtl.add_theme_font_override("mono_font", _font(600))
	for key in ["normal_font_size", "bold_font_size", "italics_font_size", "mono_font_size"]:
		rtl.add_theme_font_size_override(key, 30)
	rtl.add_theme_color_override("default_color", TEXT)
	rtl.add_theme_constant_override("line_separation", 6)
	rtl.text = md_to_bbcode(md)
	vp.add_child(rtl)
	add_child(vp)
	_text_view = vp
	_text_label = rtl
	_text_pad = pad


# ---------------------------------------------------------------- show / hide

## Materialise: the card sweeps up from the bottom, then the labels and charts appear.
func open(dur := 0.5) -> void:
	_home = position
	_opened = true
	if _text_view:                       # markdown: size the card to the text first
		await get_tree().process_frame
		await get_tree().process_frame
		var hpx := int(_text_label.get_content_height()) + _text_pad * 2
		_text_view.size = Vector2i(_text_view.size.x, hpx)
		size = Vector2(size.x, hpx / PPM)
		(_card.mesh as QuadMesh).size = size
		_mat.set_shader_parameter("size", size)
		_mat.set_shader_parameter("content", _text_view.get_texture())
		_mat.set_shader_parameter("has_content", true)
	_content.scale = Vector3(1.0, 0.001, 1.0)
	var t := create_tween()
	t.tween_method(func(v: float): _mat.set_shader_parameter("reveal", v), 0.0, 1.0, dur).set_trans(Tween.TRANS_SINE)
	t.parallel().tween_property(_content, "scale:y", 1.0, dur * 0.6).set_delay(dur * 0.5).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	t.tween_callback(func():
		for f in _on_reveal:
			f.call())


func close(dur := 0.35) -> void:
	var t := create_tween().set_parallel(true)
	t.tween_property(self, "scale", Vector3(1.08, 0.02, 1.0), dur).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_IN)
	t.tween_method(func(v: float): _mat.set_shader_parameter("fade", v), 1.0, 0.0, dur)
	t.chain().tween_callback(queue_free)


func _process(delta: float) -> void:
	_t += delta
	if _opened:
		position.y = _home.y + sin(_t * 1.3) * 0.015            # a gentle float
	if _icon == null:
		return
	var sun := _icon.get_node_or_null("Sun")
	if sun:
		(sun.get_node("Rays") as Node3D).rotation.z -= delta * 0.6
		sun.scale = Vector3.ONE * (1.0 + sin(_t * 2.4) * 0.04)
	var cloud := _icon.get_node_or_null("Cloud") as Node3D
	if cloud:
		cloud.position.x = (0.06 if _icon_kind == "partly" else 0.0) + sin(_t * 0.9) * 0.03
	var bolt := _icon.get_node_or_null("Bolt") as Label3D
	if bolt:
		bolt.visible = fmod(_t, 1.6) < 0.18 or (fmod(_t, 1.6) > 0.3 and fmod(_t, 1.6) < 0.4)
	for i in 3:
		var f := _icon.get_node_or_null("Fog%d" % i) as Node3D
		if f:
			f.position.x = 0.04 * i + sin(_t * 0.7 + i) * 0.05
	var moon := _icon.get_node_or_null("Moon") as Node3D
	if moon:
		moon.rotation.z = sin(_t * 0.8) * 0.08
