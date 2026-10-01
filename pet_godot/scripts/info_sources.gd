extends Node
## Live tool results for the lab, fetched from the same free sources the app's tools use and shaped
## like what those tools return (so director.play_tool gets the same data it will get from the app):
##   device_weather                Open-Meteo            (DeviceControlExecutor.executeWeather)
##   trading_fundamental_analysis  TradingView scanner   (TradingApiService.getStockFundamentals)
##   trading_price                 TradingView scanner   (getBestEffortPrice: text values)
##   trading_technical_analysis    TradingView scanner   (getTechnicalAnalysis: text values + signal)
##   trading_top_gainers / losers  TradingView scanner   (scanTradingView rows)
##   trading_fear_greed            alternative.me        (getFearGreedIndex)
##   trading_crypto_overview       CoinGecko /global     (getCryptoGlobal)
## In the app the data comes from Kotlin over the bridge instead; this is for trying things on the PC.

const WEATHER_URL := "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s" \
	+ "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m" \
	+ "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto"
const SCAN_URL := "https://scanner.tradingview.com/%s/scan"
const SYMBOL_URL := "https://scanner.tradingview.com/symbol?symbol=%s&fields=%s"
const STOCK_COLUMNS := ["name", "description", "close", "change", "currency", "market_cap_basic", "price_earnings_ttm",
	"total_debt_fq", "net_debt_fq", "total_assets_fq", "total_liabilities_fq", "total_equity_fq", "debt_to_equity_fq",
	"total_revenue_ttm", "net_income_ttm", "free_cash_flow_ttm", "net_margin_ttm", "operating_margin_ttm",
	"return_on_equity_fq", "sector", "industry"]
const TA_COLUMNS := ["close", "change", "volume", "RSI", "MACD.macd", "MACD.signal", "BB.basis", "BB.width", "ATR", "ADX",
	"Recommend.All", "buy_signals", "sell_signals", "neutral_signals"]
const SCAN_COLUMNS := ["name", "description", "market_cap_basic", "close", "change", "change_abs", "volume", "RSI", "Recommend.All"]


func _stamp() -> String:
	return Time.get_datetime_string_from_system(false, true)


func weather(lat := 13.7563, lon := 100.5018, place := "กรุงเทพมหานคร") -> Dictionary:
	var w = await _request(WEATHER_URL % [lat, lon])
	if not (w is Dictionary and w.has("current")):
		return {}
	var c: Dictionary = w["current"]
	var d: Dictionary = w.get("daily", {})
	return {
		"location": place, "temp": c.get("temperature_2m"), "feels_like": c.get("apparent_temperature"),
		"humidity": c.get("relative_humidity_2m"), "precipitation": c.get("precipitation"),
		"weather_code": c.get("weather_code"), "wind": c.get("wind_speed_10m"), "is_day": c.get("is_day"),
		"max": _first(d, "temperature_2m_max"), "min": _first(d, "temperature_2m_min"),
		"rain_prob": _first(d, "precipitation_probability_max"), "source": "Open-Meteo", "fetched_at": _stamp(),
	}


func stock(ticker := "NASDAQ:MSFT") -> Dictionary:
	var market := "thailand" if ticker.begins_with("SET:") or ticker.begins_with("MAI:") else "america"
	var body := JSON.stringify({"symbols": {"tickers": [ticker]}, "columns": STOCK_COLUMNS})
	var r = await _request(SCAN_URL % market, HTTPClient.METHOD_POST, body, "Mozilla/5.0")
	if not (r is Dictionary and r.get("data", []).size() > 0):
		return {}
	var row: Array = r["data"][0]["d"]
	var v := {}
	for i in STOCK_COLUMNS.size():
		v[STOCK_COLUMNS[i]] = row[i] if i < row.size() else null
	var cash = null
	if v["total_debt_fq"] != null and v["net_debt_fq"] != null:
		cash = maxf(0.0, float(v["total_debt_fq"]) - float(v["net_debt_fq"]))
	return {
		"symbol": v["name"], "ticker": r["data"][0]["s"], "name": v["description"], "currency": v["currency"],
		"sector": v["sector"], "industry": v["industry"], "closePrice": v["close"], "changePct": v["change"],
		"marketCap": v["market_cap_basic"], "peTtm": v["price_earnings_ttm"], "totalDebt": v["total_debt_fq"],
		"netDebt": v["net_debt_fq"], "cashAndEquivalents": cash, "totalAssets": v["total_assets_fq"],
		"totalLiabilities": v["total_liabilities_fq"], "totalEquity": v["total_equity_fq"],
		"debtToEquity": v["debt_to_equity_fq"], "totalRevenueTtm": v["total_revenue_ttm"],
		"netIncomeTtm": v["net_income_ttm"], "freeCashFlowTtm": v["free_cash_flow_ttm"],
		"netMarginTtm": v["net_margin_ttm"], "operatingMarginTtm": v["operating_margin_ttm"],
		"returnOnEquity": v["return_on_equity_fq"], "source": "TradingView", "fetched_at": _stamp(),
	}


## The scanner's fields for one symbol, as text like the app keeps them ("N/A" when missing).
func _symbol_fields(tv_symbol: String, fields: Array) -> Dictionary:
	var r = await _request(SYMBOL_URL % [tv_symbol, ",".join(fields)], HTTPClient.METHOD_GET, "", "Mozilla/5.0")
	if not r is Dictionary:
		return {}
	var out := {}
	for f in fields:
		var v = r.get(f)
		out[f] = "N/A" if v == null else (v if v is String else ("%.4f" % float(v)).rstrip("0").rstrip("."))
	return out


func price(tv_symbol := "BINANCE:BTCUSDT") -> Dictionary:
	var f: Dictionary = await _symbol_fields(tv_symbol, ["close", "change", "price_52_week_high", "price_52_week_low"])
	var close := float(f.get("close", "0")) if str(f.get("close", "N/A")).is_valid_float() else 0.0
	if close <= 0.0:
		return {}
	var pct := float(f["change"]) if str(f["change"]).is_valid_float() else 0.0
	var prev := close / (1.0 + pct / 100.0) if pct != -100.0 else close
	var ex := tv_symbol.get_slice(":", 0)
	return {
		"symbol": tv_symbol.get_slice(":", 1), "canonical_symbol": tv_symbol.get_slice(":", 1),
		"price": "%.4f" % close, "change": "%.4f" % (close - prev), "change_pct": "%.2f%%" % pct,
		"prev_close": "%.4f" % prev, "high_52w": f.get("price_52_week_high", "N/A"), "low_52w": f.get("price_52_week_low", "N/A"),
		"currency": "USD", "market_state": "LIVE | TV:%s" % ex, "direction": "UP" if pct >= 0.0 else "DOWN",
		"source": "TV:%s" % ex, "fetched_at": _stamp(),
	}


func technical(tv_symbol := "BINANCE:BTCUSDT") -> Dictionary:
	var f: Dictionary = await _symbol_fields(tv_symbol, TA_COLUMNS)
	if f.is_empty() or f.get("close", "N/A") == "N/A":
		return {}
	var rec := float(f["Recommend.All"]) if str(f["Recommend.All"]).is_valid_float() else 0.0
	f["symbol"] = tv_symbol.get_slice(":", 1)
	f["signal"] = "STRONG BUY" if rec >= 0.5 else ("BUY" if rec >= 0.1 else ("HOLD" if rec >= -0.1 else ("SELL" if rec >= -0.5 else "STRONG SELL")))
	f["recommend_score"] = "%.3f" % rec
	f["interval"] = "1D"
	f["source"] = "TradingView"
	f["fetched_at"] = _stamp()
	return f


## trading_top_gainers (up) / trading_top_losers: scanner rows sorted by today's % change.
func movers(up := true, exchange := "BINANCE", limit := 30) -> Array:
	var body := JSON.stringify({
		"filter": [{"left": "exchange", "operation": "equal", "right": exchange}, {"left": "volume", "operation": "greater", "right": 0}],
		"columns": SCAN_COLUMNS, "sort": {"sortBy": "change", "sortOrder": "desc" if up else "asc"}, "range": [0, limit],
	})
	var r = await _request(SCAN_URL % "crypto", HTTPClient.METHOD_POST, body, "Mozilla/5.0")
	if not (r is Dictionary and r.has("data")):
		return []
	var rows := []
	for item in r["data"]:
		var row := {"symbol": str(item["s"]).get_slice(":", 1)}
		for i in SCAN_COLUMNS.size():
			var v = item["d"][i] if i < item["d"].size() else null
			row[SCAN_COLUMNS[i]] = "N/A" if v == null else (v if v is String else ("%.4f" % float(v)).rstrip("0").rstrip("."))
		rows.append(row)
	return rows


func fear_greed(days := 7) -> Dictionary:
	var r = await _request("https://api.alternative.me/fng/?limit=%d&format=json" % days)
	if not (r is Dictionary and r.get("data", []).size() > 0):
		return {}
	var trend := []
	for item in r["data"]:
		trend.append(str(item.get("value", "")))
	return {"value": str(r["data"][0]["value"]), "classification": str(r["data"][0]["value_classification"]),
		"trend": ",".join(trend), "fetched_at": _stamp()}


func crypto_overview() -> Dictionary:
	var r = await _request("https://api.coingecko.com/api/v3/global", HTTPClient.METHOD_GET, "", "curl/8.0")
	if not (r is Dictionary and r.has("data")):
		return {}
	var d: Dictionary = r["data"]
	return {
		"total_market_cap_usd": str(d["total_market_cap"]["usd"]), "total_volume_24h_usd": str(d["total_volume"]["usd"]),
		"btc_dominance": str(d["market_cap_percentage"]["btc"]), "eth_dominance": str(d["market_cap_percentage"]["eth"]),
		"market_cap_change_24h": str(d["market_cap_change_percentage_24h_usd"]),
		"active_cryptocurrencies": str(d["active_cryptocurrencies"]), "markets": str(d["markets"]), "fetched_at": _stamp(),
	}


## One live result by tool name (what the lab's ข้อมูล tab calls). {} / [] when the source didn't answer.
func fetch(tool: String):
	match tool:
		"device_weather": return await weather()
		"trading_fundamental_analysis": return await stock()
		"trading_price": return await price()
		"trading_technical_analysis": return await technical()
		"trading_top_gainers": return await movers(true)
		"trading_top_losers": return await movers(false)
		"trading_fear_greed": return await fear_greed()
		"trading_crypto_overview": return await crypto_overview()
	return {}


func _first(d: Dictionary, key: String):
	var a = d.get(key)
	return a[0] if a is Array and a.size() > 0 else null


func _request(url: String, method := HTTPClient.METHOD_GET, body := "", agent := "JARVIS-Android/1.0"):
	var http := HTTPRequest.new()
	http.timeout = 20.0
	add_child(http)
	var headers := PackedStringArray(["User-Agent: " + agent, "Content-Type: application/json"])
	if http.request(url, headers, method, body) != OK:
		http.queue_free()
		return null
	var res: Array = await http.request_completed          # [result, code, headers, body]
	http.queue_free()
	if res[0] != HTTPRequest.RESULT_SUCCESS or res[1] != 200:
		push_warning("info request failed (%s, HTTP %s): %s" % [res[0], res[1], url])
		return null
	return JSON.parse_string((res[3] as PackedByteArray).get_string_from_utf8())
