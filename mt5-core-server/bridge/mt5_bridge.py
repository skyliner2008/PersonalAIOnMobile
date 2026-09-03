#!/usr/bin/env python3
import json
import math
import os
import sys
import threading
import time
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

try:
    import MetaTrader5 as mt5
except Exception as e:
    mt5 = None
    MT5_IMPORT_ERROR = str(e)
else:
    MT5_IMPORT_ERROR = ""

os.environ["SMC_CREDIT"] = "0"
try:
    import pandas as pd
    from smartmoneyconcepts import smc
except Exception as e:
    pd = None
    smc = None


def env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


HOST = env("MT5_BRIDGE_HOST", "127.0.0.1")
PORT = int(env("MT5_BRIDGE_PORT", "5001"))
TERMINAL_PATH = env("MT5_TERMINAL_PATH", "")
LOGIN = env("MT5_LOGIN", "")
PASSWORD = env("MT5_PASSWORD", "")
SERVER = env("MT5_SERVER", "")


def now_iso() -> str:
    # Timezone-aware UTC datetime (Python 3.12+ deprecates naive utcnow())
    return datetime.now(timezone.utc).replace(tzinfo=None).isoformat() + "Z"


def _ok(data):
    return {"success": True, "data": data}


def _err(message):
    return {"success": False, "error": str(message)}


# ── MT5 init caching ───────────────────────────────────────────────────────
# Previously `ensure_mt5` called `mt5.initialize()` on every request, which is
# slow and can collide with MT5's own locks under load. We now cache the
# initialization state for a short TTL and only retry when it falls stale or
# has previously failed. A lock prevents a thundering herd of concurrent
# initializations when the bridge is hit from multiple callers at once.
_MT5_STATE_LOCK = threading.Lock()
_MT5_INIT_STATE = {"ok": False, "message": "not_initialized", "checked_at": 0.0}
_MT5_OK_TTL_SEC = 30.0
_MT5_FAIL_RETRY_SEC = 3.0

# Serialize all MT5 API calls. The MetaTrader5 python module is NOT
# thread-safe — concurrent calls can corrupt internal state or silently drop
# returns. Even though HTTPServer (non-threading) processes one HTTP request
# at a time, other processes or the MT5 terminal's own callbacks could still
# race this module, so we hold a global ops lock for every mt5.* call made
# inside a request handler.
_MT5_OPS_LOCK = threading.RLock()


def ensure_mt5():
    if mt5 is None:
        return False, f"MetaTrader5 module missing: {MT5_IMPORT_ERROR}"

    now = time.monotonic()
    with _MT5_STATE_LOCK:
        ttl = _MT5_OK_TTL_SEC if _MT5_INIT_STATE["ok"] else _MT5_FAIL_RETRY_SEC
        if _MT5_INIT_STATE["checked_at"] > 0 and now - _MT5_INIT_STATE["checked_at"] < ttl:
            return _MT5_INIT_STATE["ok"], _MT5_INIT_STATE["message"]

        initialized = mt5.initialize(TERMINAL_PATH) if TERMINAL_PATH else mt5.initialize()
        if initialized:
            if LOGIN and PASSWORD and SERVER:
                authorized = mt5.login(int(LOGIN), password=PASSWORD, server=SERVER)
                if not authorized:
                    _MT5_INIT_STATE.update(
                        {"ok": False, "message": f"MT5 login failed: {mt5.last_error()}", "checked_at": now}
                    )
                    return False, _MT5_INIT_STATE["message"]
            _MT5_INIT_STATE.update({"ok": True, "message": "ok", "checked_at": now})
            return True, "ok"

        _MT5_INIT_STATE.update(
            {"ok": False, "message": f"MT5 initialize failed: {mt5.last_error()}", "checked_at": now}
        )
        return False, _MT5_INIT_STATE["message"]


def to_dict_namedtuple(obj):
    if obj is None:
        return None
    if hasattr(obj, "_asdict"):
        return obj._asdict()
    return dict(obj)


def list_symbols(limit=200):
    all_symbols = mt5.symbols_get() or []
    out = []
    for s in all_symbols[:limit]:
        sdict = s._asdict()
        sym = sdict.get("name", "")
        tick = mt5.symbol_info_tick(sym)
        tdict = tick._asdict() if tick else {}
        out.append(
            {
                "symbol": sym,
                "description": sdict.get("description", ""),
                "digits": sdict.get("digits", 0),
                "point": sdict.get("point", 0.0),
                "trade_mode": sdict.get("trade_mode", 0),
                "bid": tdict.get("bid", 0.0),
                "ask": tdict.get("ask", 0.0),
                "spread": (
                    (tdict.get("ask", 0.0) - tdict.get("bid", 0.0))
                    if tdict.get("ask") is not None and tdict.get("bid") is not None
                    else 0.0
                ),
            }
        )
    return out


def list_symbols_meta(limit=5000):
    """Return ALL symbols (no tick lookup) — fast metadata for search/validate."""
    all_symbols = mt5.symbols_get() or []
    out = []
    for s in all_symbols[:limit]:
        sdict = s._asdict()
        out.append({
            "symbol":      sdict.get("name", ""),
            "description": sdict.get("description", ""),
            "path":        sdict.get("path", ""),
            "digits":      sdict.get("digits", 0),
            "trade_mode":  sdict.get("trade_mode", 0),
        })
    return out


def get_tick_no_persist(symbol):
    """Fetch a tick without permanently adding the symbol to Market Watch.
    Records the original `.visible` state, selects if needed, fetches tick +
    symbol_info, then deselects iff we were the ones who added it.
    Symbols with open positions / charts will silently stay in Market Watch
    because MT5 refuses to remove them — that's the broker's safety net."""
    if not symbol:
        return None
    s_up = symbol.strip().upper()
    info_pre = mt5.symbol_info(s_up)
    was_visible = bool(info_pre and getattr(info_pre, "visible", False))
    if not info_pre:
        # Try fuzzy resolve (this MAY add to Market Watch — accept it once).
        s_up = resolve_symbol(symbol) or s_up
        info_pre = mt5.symbol_info(s_up)
        was_visible = bool(info_pre and getattr(info_pre, "visible", False))
    if info_pre is None:
        return None
    if not was_visible:
        if not mt5.symbol_select(s_up, True):
            return None
    tick = mt5.symbol_info_tick(s_up)
    info = mt5.symbol_info(s_up) or info_pre
    result = None
    if tick:
        result = {
            "symbol": s_up,
            "bid":    float(getattr(tick, "bid", 0.0) or 0.0),
            "ask":    float(getattr(tick, "ask", 0.0) or 0.0),
            "last":   float(getattr(tick, "last", 0.0) or 0.0),
            "spread": int(getattr(info, "spread", 0) or 0),
            "time":   int(getattr(tick, "time", 0) or 0),
            "digits": int(getattr(info, "digits", 0) or 0),
        }
    if not was_visible:
        try:
            mt5.symbol_select(s_up, False)
        except Exception:
            pass
    return result


def batch_ticks_no_persist(symbols):
    """Run get_tick_no_persist over a list and skip silent failures."""
    out = []
    for sym in symbols:
        try:
            t = get_tick_no_persist(sym)
            if t is not None:
                out.append(t)
        except Exception:
            pass
    return out


def set_symbols_selected(symbols, select=True):
    """Add (select=True) or remove (select=False) symbols from Market Watch.
    Returns a per-symbol map of success booleans. MT5 refuses to deselect a
    symbol that has an open position or active chart — that's expected."""
    out = {}
    for sym in symbols or []:
        if not sym:
            continue
        s_up = str(sym).strip().upper()
        try:
            ok = bool(mt5.symbol_select(s_up, bool(select)))
        except Exception as e:
            ok = False
        out[s_up] = ok
    return out


def list_market_watch():
    """List symbols currently visible in MT5 Market Watch."""
    all_symbols = mt5.symbols_get() or []
    return [s.name for s in all_symbols if getattr(s, "visible", False)]


def get_symbol_info(symbol):
    if not symbol:
        return None
    sym = resolve_symbol(symbol)
    if not sym:
        return None
    info = mt5.symbol_info(sym)
    return info._asdict() if info else None


def resolve_symbol(symbol):
    """
    Smart Symbol Resolver:
    1. Try exact match (and select it)
    2. Try fuzzy match in broker's full list (useful for prefixes/suffixes like BTCUSD.m)
    Returns the resolved symbol name or None.
    """
    if not symbol:
        return None
    s_up = symbol.strip().upper()

    # Try exact match first
    if mt5.symbol_select(s_up, True):
        return s_up

    # If not found, search in all broker symbols (check name and description)
    all_syms = mt5.symbols_get() or []
    matches = []
    for s in all_syms:
        name = s.name.upper()
        desc = getattr(s, 'description', '').upper()
        if s_up in name or s_up in desc:
            matches.append(s.name)

    if not matches:
        # One last try: if query is "BTC", search for "XBT" (common variant)
        if s_up == "BTC" or s_up == "BTCUSD":
            matches = [s.name for s in all_syms if "XBT" in s.name.upper()]

    # If only one match, it's highly likely the one we want
    if len(matches) == 1:
        target = matches[0]
        if mt5.symbol_select(target, True):
            return target

    # If multiple, try to find the "best" one (shortest suffix or exact name match among list)
    # This covers cases like "EURUSD" vs "EURUSD.m" (shortest name is often the primary)
    matches.sort(key=len)
    for target in matches:
        if mt5.symbol_select(target, True):
            return target

    return None


def search_symbols(query, limit=20):
    """
    Fuzzy search broker symbols by name + description.
    Returns matches sorted by relevance score:
      3 — exact name match (case-insensitive)
      2 — name starts with query or query is substring of name
      1 — description contains query
    Also returns tick data (bid/ask) for top matches only.
    """
    q = query.strip().upper()
    all_symbols = mt5.symbols_get() or []

    scored = []
    for s in all_symbols:
        sdict    = s._asdict()
        sym      = sdict.get("name", "")
        desc     = sdict.get("description", "")
        sym_up   = sym.upper()
        desc_up  = desc.upper()

        score = 0
        if sym_up == q:
            score = 3
        elif sym_up.startswith(q) or q in sym_up:
            score = 2
        elif q in desc_up:
            score = 1

        if score > 0:
            scored.append((score, sym, desc, sdict))

    # Sort: score desc, then name asc
    scored.sort(key=lambda x: (-x[0], x[1]))

    out = []
    for score, sym, desc, sdict in scored[:limit]:
        tick  = mt5.symbol_info_tick(sym)
        tdict = tick._asdict() if tick else {}
        out.append({
            "symbol":      sym,
            "description": desc,
            "path":        sdict.get("path", ""),
            "digits":      sdict.get("digits", 0),
            "trade_mode":  sdict.get("trade_mode", 0),
            "bid":         tdict.get("bid", 0.0),
            "ask":         tdict.get("ask", 0.0),
            "score":       score,
            "exact":       score == 3,
        })
    return out


def list_positions(symbol=""):
    rows = mt5.positions_get(symbol=symbol) if symbol else mt5.positions_get()
    rows = rows or []
    return [_enrich_trade_row(r._asdict()) for r in rows]


def list_orders(symbol=""):
    rows = mt5.orders_get(symbol=symbol) if symbol else mt5.orders_get()
    rows = rows or []
    return [_enrich_trade_row(r._asdict()) for r in rows]


def _enrich_trade_row(row):
    """Add a human-readable `side` to each position/order/deal row."""
    if not isinstance(row, dict):
        return row
    side_existing = str(row.get("side", "")).strip().lower()
    if side_existing in ("buy", "sell"):
        row["side"] = side_existing.upper()
        return row
    type_code = row.get("type")
    if isinstance(type_code, (int, float)):
        row["side"] = "BUY" if int(type_code) % 2 == 0 else "SELL"
    elif isinstance(type_code, str):
        t = type_code.strip().lower()
        if "buy" in t or t == "0":
            row["side"] = "BUY"
        elif "sell" in t or t == "1":
            row["side"] = "SELL"
    return row


def list_history(limit=200, symbol=""):
    # V26.1 (2026-05-15): Use a wider date range. MT5 history timestamps can be
    # sensitive to the broker's timezone vs system time. Providing a `date_to`
    # slightly in the future (tomorrow) ensures we don't miss the most recent
    # deals if the broker time is ahead of local system time.
    date_to = datetime.now() + timedelta(days=1)
    date_from = date_to - timedelta(days=60)
    deals = mt5.history_deals_get(date_from, date_to) or []
    out = [_enrich_trade_row(d._asdict()) for d in deals]
    if symbol:
        out = [d for d in out if str(d.get("symbol", "")).upper() == symbol.upper()]
    return out[-int(limit):]


# ── Candles / Rates ────────────────────────────────────────────────────────
# `mt5-core-server` expects a /candles endpoint for its tracking pipeline.
# Previously this was missing in the bridge, which forced every call from
# the Node server (POST /api/mt5/tracking/run-once, GET /api/mt5/candles)
# to fail with 502. This implementation returns an array of candle objects
# with standard `t/o/h/l/c/v` keys that the Node tracking service already
# understands via parseCandles().
_TIMEFRAME_MAP = {}


def _init_timeframe_map():
    if _TIMEFRAME_MAP or mt5 is None:
        return
    entries = {
        "m1": "TIMEFRAME_M1",
        "m2": "TIMEFRAME_M2",
        "m3": "TIMEFRAME_M3",
        "m5": "TIMEFRAME_M5",
        "m10": "TIMEFRAME_M10",
        "m15": "TIMEFRAME_M15",
        "m30": "TIMEFRAME_M30",
        "h1": "TIMEFRAME_H1",
        "h2": "TIMEFRAME_H2",
        "h3": "TIMEFRAME_H3",
        "h4": "TIMEFRAME_H4",
        "h6": "TIMEFRAME_H6",
        "h8": "TIMEFRAME_H8",
        "h12": "TIMEFRAME_H12",
        "d1": "TIMEFRAME_D1",
        "w1": "TIMEFRAME_W1",
        "mn1": "TIMEFRAME_MN1",
    }
    for key, attr in entries.items():
        value = getattr(mt5, attr, None)
        if value is not None:
            _TIMEFRAME_MAP[key] = value
            # accept common "1m", "1d", "1w" style aliases
            if key.startswith(("m", "h", "d", "w")) and len(key) > 1:
                _TIMEFRAME_MAP[key[1:] + key[0]] = value
    if "d1" in _TIMEFRAME_MAP:
        _TIMEFRAME_MAP["d"] = _TIMEFRAME_MAP["d1"]
        _TIMEFRAME_MAP["1d"] = _TIMEFRAME_MAP["d1"]
    if "w1" in _TIMEFRAME_MAP:
        _TIMEFRAME_MAP["w"] = _TIMEFRAME_MAP["w1"]
        _TIMEFRAME_MAP["1w"] = _TIMEFRAME_MAP["w1"]


def resolve_timeframe(raw):
    _init_timeframe_map()
    if not raw:
        return _TIMEFRAME_MAP.get("m15")
    normalized = str(raw).strip().lower().replace(" ", "")
    if normalized in _TIMEFRAME_MAP:
        return _TIMEFRAME_MAP[normalized]
    # fallback: accept just a number -> treat as minutes
    if normalized.isdigit():
        return _TIMEFRAME_MAP.get("m" + normalized)
    return None


def list_candles(symbol, timeframe, count):
    if not symbol:
        raise ValueError("symbol is required")
    tf = resolve_timeframe(timeframe)
    if tf is None:
        raise ValueError(f"unsupported timeframe: {timeframe}")
    count = max(1, min(int(count or 300), 5000))
    sym = resolve_symbol(symbol)
    if not sym:
        raise ValueError(f"cannot select symbol: {symbol}")

    # Retry loop for copy_rates_from_pos (MT5 history can be finicky)
    rates = None
    for attempt in range(3):
        rates = mt5.copy_rates_from_pos(sym, tf, 0, count)
        if rates is not None:
            break
        if attempt < 2:
            time.sleep(0.5)

    if rates is None:
        raise ValueError(f"copy_rates_from_pos returned None: {mt5.last_error()}")
    out = []
    for row in rates:
        out.append(
            {
                "t": int(row["time"]),
                "o": float(row["open"]),
                "h": float(row["high"]),
                "l": float(row["low"]),
                "c": float(row["close"]),
                "v": float(row["tick_volume"]) if "tick_volume" in row.dtype.names else 0.0,
            }
        )
    return out


def get_smc_analysis(symbol, timeframe, count):
    if pd is None or smc is None:
        raise ValueError("pandas or smartmoneyconcepts not installed in bridge")
        
    candles = list_candles(symbol, timeframe, count)
    if not candles:
        raise ValueError("No candles returned")
        
    df = pd.DataFrame(candles)
    # smartmoneyconcepts expects: open, high, low, close, volume
    df.rename(columns={'o': 'open', 'h': 'high', 'l': 'low', 'c': 'close', 'v': 'volume'}, inplace=True)
    
    fvg = smc.fvg(df)
    hl = smc.swing_highs_lows(df, swing_length=10)
    bos = smc.bos_choch(df, hl)
    ob = smc.ob(df, hl)
    liq = smc.liquidity(df, hl)
    
    def get_recent(series_or_df, col_filter, n=3):
        valid = series_or_df[series_or_df[col_filter] != 0].dropna(subset=[col_filter])
        if not valid.empty:
            records = valid.tail(n).to_dict('records')
            for r in records:
                for k, v in r.items():
                    if isinstance(v, float) and math.isnan(v):
                        r[k] = None
            return records
        return []

    return {
        "fvg": get_recent(fvg, 'FVG', 5),
        "bos": get_recent(bos, 'BOS', 3),
        "choch": get_recent(bos, 'CHOCH', 3),
        "ob": get_recent(ob, 'OB', 3),
        "liquidity": get_recent(liq, 'Liquidity', 3)
    }


def send_order(payload):
    symbol = str(payload.get("symbol", "")).upper()
    # Accept `side`, `action`, or uppercase/lowercase variants.
    raw_side = payload.get("side") or payload.get("action") or ""
    side = str(raw_side).strip().lower()
    if side in ("long",):
        side = "buy"
    elif side in ("short",):
        side = "sell"
    volume = float(payload.get("volume", 0) or 0)
    sl = payload.get("sl")
    tp = payload.get("tp")
    comment = str(payload.get("comment", "JARVIS"))

    if not symbol or volume <= 0:
        return _err("symbol and positive volume are required")

    sym = resolve_symbol(symbol)
    if not sym:
        return _err(f"cannot select symbol: {symbol}")
    symbol = sym  # Use the resolved symbol name for the rest of the order logic

    tick = mt5.symbol_info_tick(symbol)
    if tick is None:
        return _err(f"cannot read tick for symbol: {symbol}")

    if side in ("buy", "long"):
        order_type = mt5.ORDER_TYPE_BUY
        price = tick.ask
    elif side in ("sell", "short"):
        order_type = mt5.ORDER_TYPE_SELL
        price = tick.bid
    else:
        return _err("side/action must be BUY or SELL")

    req = {
        "action": mt5.TRADE_ACTION_DEAL,
        "symbol": symbol,
        "volume": volume,
        "type": order_type,
        "price": price,
        "deviation": int(payload.get("deviation", 20)),
        "magic": int(payload.get("magic", 20260420)),
        "comment": comment,
        "type_time": mt5.ORDER_TIME_GTC,
    }
    if sl is not None and str(sl).strip() != "":
        req["sl"] = float(sl)
    if tp is not None and str(tp).strip() != "":
        req["tp"] = float(tp)

    result = _try_order_send_with_filling(req, symbol)
    if result is None:
        return _err(f"order_send failed: {mt5.last_error()}")
    if hasattr(result, "retcode") and result.retcode != getattr(mt5, "TRADE_RETCODE_DONE", 10009):
        return _err(f"order_send retcode={result.retcode} ({getattr(result, 'comment', '')})")
    return _ok(result._asdict())


def _try_order_send_with_filling(req, symbol):
    """Some brokers reject ORDER_FILLING_IOC or ORDER_FILLING_FOK.
    We infer the allowed filling modes from symbol_info when possible, otherwise
    try a sensible order (IOC → FOK → RETURN)."""
    filling_modes = _preferred_filling_modes(symbol)
    last_result = None
    for mode in filling_modes:
        attempt = dict(req)
        attempt["type_filling"] = mode
        last_result = mt5.order_send(attempt)
        if last_result is None:
            continue
        retcode = getattr(last_result, "retcode", None)
        if retcode == getattr(mt5, "TRADE_RETCODE_DONE", 10009):
            return last_result
        # Retcode 10030 = "Unsupported filling mode" — try the next mode.
        if retcode != 10030:
            return last_result
    return last_result


def _preferred_filling_modes(symbol):
    modes = []
    info = mt5.symbol_info(symbol)
    allowed = getattr(info, "filling_mode", None) if info else None
    # filling_mode is a bitmask; infer preferred order from it.
    sym_flag_fok = getattr(mt5, "SYMBOL_FILLING_FOK", 1)
    sym_flag_ioc = getattr(mt5, "SYMBOL_FILLING_IOC", 2)
    order_ioc = getattr(mt5, "ORDER_FILLING_IOC", 1)
    order_fok = getattr(mt5, "ORDER_FILLING_FOK", 0)
    order_return = getattr(mt5, "ORDER_FILLING_RETURN", 2)
    if allowed is not None:
        if allowed & sym_flag_ioc:
            modes.append(order_ioc)
        if allowed & sym_flag_fok:
            modes.append(order_fok)
    if not modes:
        modes = [order_ioc, order_fok, order_return]
    # ensure RETURN is always the last fallback
    if order_return not in modes:
        modes.append(order_return)
    return modes


def close_position(payload):
    ticket = payload.get("ticket")
    symbol = str(payload.get("symbol", "")).strip().upper()
    volume_override = payload.get("volume")

    # Treat empty string / None uniformly.
    ticket_str = "" if ticket is None else str(ticket).strip()

    if not symbol and not ticket_str:
        return _err("At least one of `ticket` or `symbol` is required")

    positions = list_positions(symbol=symbol) if symbol else list_positions()
    if ticket_str:
        positions = [p for p in positions if str(p.get("ticket")) == ticket_str]

    if not positions:
        return _err("position not found")

    pos = positions[0]
    symbol = pos["symbol"]
    volume = min(float(volume_override), float(pos["volume"])) if volume_override is not None else float(pos["volume"])
    ptype = int(pos["type"])

    tick = mt5.symbol_info_tick(symbol)
    if tick is None:
        return _err(f"cannot read tick for symbol: {symbol}")

    if ptype == mt5.ORDER_TYPE_BUY:
        close_type = mt5.ORDER_TYPE_SELL
        price = tick.bid
    else:
        close_type = mt5.ORDER_TYPE_BUY
        price = tick.ask

    req = {
        "action": mt5.TRADE_ACTION_DEAL,
        "symbol": symbol,
        "position": int(pos["ticket"]),
        "volume": volume,
        "type": close_type,
        "price": price,
        "deviation": int(payload.get("deviation", 20)),
        "magic": int(payload.get("magic", 20260420)),
        "comment": "JARVIS_CLOSE",
        "type_time": mt5.ORDER_TIME_GTC,
    }

    result = _try_order_send_with_filling(req, symbol)
    if result is None:
        return _err(f"close order_send failed: {mt5.last_error()}")
    if hasattr(result, "retcode") and result.retcode != getattr(mt5, "TRADE_RETCODE_DONE", 10009):
        return _err(f"close retcode={result.retcode} ({getattr(result, 'comment', '')})")
    return _ok(result._asdict())


def modify_position(payload):
    """Modify SL/TP of an existing open position using TRADE_ACTION_SLTP.

    Accepts one of:
      - ticket        → direct lookup by ticket
      - symbol        → first matching open position on that symbol
    plus any combination of:
      - sl            → new stop loss price (0 or empty to leave unchanged)
      - tp            → new take profit price (0 or empty to leave unchanged)

    Passing an empty/null sl or tp leaves the existing value; pass "0" to clear.
    """
    ticket_raw = payload.get("ticket")
    symbol = str(payload.get("symbol", "")).strip().upper()
    ticket_str = "" if ticket_raw is None else str(ticket_raw).strip()

    if not symbol and not ticket_str:
        return _err("At least one of `ticket` or `symbol` is required")

    positions = list_positions(symbol=symbol) if symbol else list_positions()
    if ticket_str:
        positions = [p for p in positions if str(p.get("ticket")) == ticket_str]

    if not positions:
        return _err("position not found")

    pos = positions[0]
    sym = str(pos.get("symbol", "")).upper()
    ticket = int(pos.get("ticket"))
    cur_sl = float(pos.get("sl", 0.0) or 0.0)
    cur_tp = float(pos.get("tp", 0.0) or 0.0)

    def _coerce(value, default):
        if value is None:
            return default
        text = str(value).strip()
        if text == "":
            return default
        try:
            return float(text)
        except Exception:
            return default

    new_sl = _coerce(payload.get("sl"), cur_sl)
    new_tp = _coerce(payload.get("tp"), cur_tp)

    if math.isclose(new_sl, cur_sl, rel_tol=1e-5, abs_tol=1e-5) and math.isclose(new_tp, cur_tp, rel_tol=1e-5, abs_tol=1e-5):
        return _ok({"unchanged": True, "ticket": ticket, "sl": cur_sl, "tp": cur_tp})

    req = {
        "action": mt5.TRADE_ACTION_SLTP,
        "symbol": sym,
        "position": ticket,
        "sl": new_sl,
        "tp": new_tp,
        "magic": int(payload.get("magic", 20260420)),
        "comment": str(payload.get("comment", "JARVIS_MODIFY")),
    }
    result = mt5.order_send(req)
    if result is None:
        return _err(f"modify order_send failed: {mt5.last_error()}")
    if hasattr(result, "retcode") and result.retcode != getattr(mt5, "TRADE_RETCODE_DONE", 10009):
        return _err(f"modify retcode={result.retcode} ({getattr(result, 'comment', '')})")
    out = result._asdict()
    out["ticket"] = ticket
    out["new_sl"] = new_sl
    out["new_tp"] = new_tp
    return _ok(out)


class Handler(BaseHTTPRequestHandler):
    def _send(self, status_code, body_obj):
        raw = json.dumps(body_obj, ensure_ascii=False).encode("utf-8")
        try:
            self.send_response(status_code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            # CORS: the Node mt5-core-server is the intended caller, but allowing
            # preflight makes ad-hoc curl / mobile-direct debugging painless.
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key")
            self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            self.end_headers()
            self.wfile.write(raw)
        except (ConnectionAbortedError, ConnectionResetError, BrokenPipeError) as e:
            # Client (Node bridge / Kotlin app) closed the connection before we
            # finished writing — typically an HTTP timeout on their side.
            # Swallow silently; stack traces pollute logs and carry no info.
            sys.stderr.write(f"[mt5-bridge] client closed connection: {type(e).__name__}\n")

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def _json_body(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0:
            return {}
        raw = self.rfile.read(length).decode("utf-8")
        if not raw:
            return {}
        return json.loads(raw)

    def _route_path(self):
        p = urlparse(self.path).path
        return p.replace("/api/mt5", "", 1) if p.startswith("/api/mt5") else p

    def do_GET(self):
        # Serialize all MT5 terminal access — module is not thread-safe.
        with _MT5_OPS_LOCK:
            ok, msg = ensure_mt5()
            parsed = urlparse(self.path)
            path = self._route_path()
            qs = parse_qs(parsed.query)

            if path == "/health":
                self._send(
                    200,
                    _ok(
                        {
                            "bridge": "mt5-python-bridge",
                            "time": now_iso(),
                            "mt5Ready": ok,
                            "message": msg,
                        }
                    ),
                )
                return

            if not ok:
                self._send(500, _err(msg))
                return

            try:
                if path == "/account":
                    acc = mt5.account_info()
                    self._send(200, _ok(to_dict_namedtuple(acc)))
                    return
                if path == "/symbols":
                    limit = int((qs.get("limit") or ["200"])[0])
                    self._send(200, _ok(list_symbols(limit=limit)))
                    return
                if path == "/symbol-search":
                    query = (qs.get("q") or qs.get("query") or [""])[0].strip()
                    limit = int((qs.get("limit") or ["20"])[0])
                    if not query:
                        self._send(400, _err("Missing query param: q"))
                        return
                    self._send(200, _ok(search_symbols(query, limit=limit)))
                    return
                if path == "/symbols-meta":
                    limit = int((qs.get("limit") or ["5000"])[0])
                    self._send(200, _ok(list_symbols_meta(limit=limit)))
                    return
                if path == "/positions":
                    symbol = (qs.get("symbol") or [""])[0]
                    self._send(200, _ok(list_positions(symbol=symbol)))
                    return
                if path == "/orders":
                    symbol = (qs.get("symbol") or [""])[0]
                    self._send(200, _ok(list_orders(symbol=symbol)))
                    return
                if path == "/history":
                    symbol = (qs.get("symbol") or [""])[0]
                    limit = int((qs.get("limit") or ["200"])[0])
                    self._send(200, _ok(list_history(limit=limit, symbol=symbol)))
                    return
                if path == "/symbol_info":
                    symbol = (qs.get("symbol") or [""])[0]
                    if not symbol:
                        self._send(400, _err("Missing symbol param"))
                        return
                    info = get_symbol_info(symbol)
                    if info is None:
                        self._send(404, _err(f"Symbol not found: {symbol}"))
                    else:
                        self._send(200, _ok(info))
                    return
                if path == "/market-watch":
                    self._send(200, _ok(list_market_watch()))
                    return
                if path in ("/candles", "/rates"):
                    symbol = (qs.get("symbol") or [""])[0]
                    timeframe = (qs.get("timeframe") or ["M15"])[0]
                    count = int((qs.get("count") or ["300"])[0])
                    self._send(200, _ok(list_candles(symbol, timeframe, count)))
                    return
                if path == "/smc":
                    symbol = (qs.get("symbol") or [""])[0]
                    timeframe = (qs.get("timeframe") or ["H1"])[0]
                    count = int((qs.get("count") or ["300"])[0])
                    if not symbol:
                        self._send(400, _err("Missing symbol param"))
                        return
                    self._send(200, _ok(get_smc_analysis(symbol, timeframe, count)))
                    return

                self._send(404, _err(f"not found: {path}"))
            except Exception as e:
                self._send(500, _err(f"{e}"))

    def do_POST(self):
        # Serialize all MT5 terminal access — module is not thread-safe.
        with _MT5_OPS_LOCK:
            ok, msg = ensure_mt5()
            path = self._route_path()
            if not ok:
                self._send(500, _err(msg))
                return
            try:
                body = self._json_body()
                if path == "/order":
                    result = send_order(body)
                    self._send(200 if result.get("success") else 400, result)
                    return
                if path == "/close":
                    result = close_position(body)
                    self._send(200 if result.get("success") else 400, result)
                    return
                if path in ("/modify", "/position/modify", "/positions/modify"):
                    result = modify_position(body)
                    self._send(200 if result.get("success") else 400, result)
                    return
                if path == "/ticks_lite":
                    symbols = body.get("symbols", [])
                    if not isinstance(symbols, list):
                        self._send(400, _err("symbols must be a list"))
                        return
                    self._send(200, _ok(batch_ticks_no_persist(symbols)))
                    return
                if path in ("/symbols/select", "/symbol_select"):
                    symbols = body.get("symbols", [])
                    select = bool(body.get("select", True))
                    if not isinstance(symbols, list):
                        self._send(400, _err("symbols must be a list"))
                        return
                    self._send(200, _ok(set_symbols_selected(symbols, select)))
                    return
                if path == "/snapshots_bulk":
                    symbols = body.get("symbols", [])
                    if not isinstance(symbols, list):
                        self._send(400, _err("symbols must be a list"))
                        return
                    out = {}
                    for sym in symbols:
                        resolved = resolve_symbol(sym)
                        if not resolved:
                            out[sym] = {"error": "not found"}
                            continue
                        tick = mt5.symbol_info_tick(resolved)
                        out[sym] = {
                            "tick": tick._asdict() if tick else None,
                            "positions": list_positions(resolved),
                            "orders": list_orders(resolved),
                            "info": get_symbol_info(resolved)
                        }
                    self._send(200, _ok(out))
                    return
                self._send(404, _err(f"not found: {path}"))
            except Exception as e:
                self._send(500, _err(f"{e}"))

    def log_message(self, format, *args):
        sys.stdout.write("[mt5-bridge] " + (format % args) + "\n")


def main():
    server = HTTPServer((HOST, PORT), Handler)
    print(f"[mt5-bridge] running on http://{HOST}:{PORT}")
    server.serve_forever()


if __name__ == "__main__":
    main()
