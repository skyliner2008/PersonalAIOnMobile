from jesse.strategies import Strategy
import jesse.indicators as ta
from jesse import utils


class BBSqueezeTrend(Strategy):
    """
    Trend-following Bollinger Band squeeze strategy.
    Trading timeframe: 30m. Anchor timeframe: 4h.

    Entry: On the 4h anchor, Bollinger Bands (bb_period, bb_dev) compress
    inside Keltner Channels (bb_period, kc_mult) - "squeeze on". When the
    bands expand back outside the Keltner channel - "squeeze fires" - enter
    on the first 30m candle of the new anchor bar in the direction of the
    linear regression slope of anchor closes (slope_period). The squeeze must
    have persisted for at least min_squeeze_bars before the fire, and ADX(14)
    on the anchor must exceed adx_threshold.

    Entry signal validated via Rule Significance Test (p = 0.0, 2000 sims,
    ETH-USDT 30m/4h anchor, 2025-01-01 -> 2026-07-16).

    Risk: 3% of available margin per trade, sized off the initial 4h ATR stop.

    Exits: initial stop at entry -/+ atr_stop_mult * ATR(14, 4h); afterwards
    an ATR trailing stop ratchets from the highest 30m close (long) / lowest
    30m close (short) seen since entry, distance atr_trail_mult * ATR(14, 4h).
    """

    anchor_tf = "4h"

    def hyperparameters(self):
        return [
            {'name': 'bb_period', 'type': int, 'min': 15, 'max': 30, 'default': 29},
            {'name': 'bb_dev', 'type': float, 'min': 1.5, 'max': 2.5, 'default': 1.82},
            {'name': 'kc_mult', 'type': float, 'min': 1.0, 'max': 2.0, 'default': 1.56},
            {'name': 'slope_period', 'type': int, 'min': 10, 'max': 40, 'default': 11},
            {'name': 'adx_threshold', 'type': float, 'min': 15, 'max': 35, 'default': 19.11},
            {'name': 'min_squeeze_bars', 'type': int, 'min': 1, 'max': 8, 'default': 4},
            {'name': 'atr_stop_mult', 'type': float, 'min': 1.5, 'max': 3.5, 'default': 1.74},
            {'name': 'atr_trail_mult', 'type': float, 'min': 2.0, 'max': 4.5, 'default': 3.71},
        ]

    @property
    def anchor(self):
        return self.get_candles(self.exchange, self.symbol, self.anchor_tf)

    @property
    def _bb(self):
        return ta.bollinger_bands(
            self.anchor,
            period=self.hp['bb_period'],
            devup=self.hp['bb_dev'],
            devdn=self.hp['bb_dev'],
            sequential=True,
        )

    @property
    def _kc(self):
        return ta.keltner(
            self.anchor,
            period=self.hp['bb_period'],
            multiplier=self.hp['kc_mult'],
            matype=1,
            sequential=True,
        )

    @property
    def _atr(self) -> float:
        return ta.atr(self.anchor, period=14)

    @property
    def _adx(self) -> float:
        return ta.adx(self.anchor, period=14)

    def _adx_ok(self) -> bool:
        return self._adx >= self.hp['adx_threshold']

    def _squeeze_fired(self) -> bool:
        bb = self._bb
        kc = self._kc
        squeeze_on = (bb.upperband < kc.upperband) & (bb.lowerband > kc.lowerband)
        if not bool(squeeze_on[-2]) or bool(squeeze_on[-1]):
            return False
        n = self.hp['min_squeeze_bars']
        return bool(squeeze_on[-(n + 1):-1].all())

    def _slope(self) -> float:
        return ta.linearreg_slope(self.anchor, period=self.hp['slope_period'])

    def before(self):
        if self._squeeze_fired():
            self.vars['pending_fire_ts'] = int(self.anchor[-1, 0])
        else:
            self.vars['pending_fire_ts'] = None

    def should_long(self) -> bool:
        pending = self.vars.get('pending_fire_ts')
        consumed = self.vars.get('consumed_fire_ts')
        return pending is not None and pending != consumed and self._adx_ok() and self._slope() > 0

    def should_short(self) -> bool:
        pending = self.vars.get('pending_fire_ts')
        consumed = self.vars.get('consumed_fire_ts')
        return pending is not None and pending != consumed and self._adx_ok() and self._slope() < 0

    def _risk_qty(self, entry_price: float, stop_price: float) -> float:
        risk_qty = utils.risk_to_qty(self.available_margin, 3, entry_price, stop_price, self.fee_rate)
        leverage = getattr(self, 'leverage', 1)
        max_qty = utils.size_to_qty(self.available_margin * leverage * 0.95, entry_price, fee_rate=self.fee_rate)
        return min(risk_qty, max_qty)

    def go_long(self):
        entry = self.price
        stop = entry - self.hp['atr_stop_mult'] * self._atr
        qty = self._risk_qty(entry, stop)
        if qty <= 0:
            return
        self.buy = qty, entry
        self.vars['planned_stop'] = stop
        self.vars['consumed_fire_ts'] = self.vars['pending_fire_ts']

    def go_short(self):
        entry = self.price
        stop = entry + self.hp['atr_stop_mult'] * self._atr
        qty = self._risk_qty(entry, stop)
        if qty <= 0:
            return
        self.sell = qty, entry
        self.vars['planned_stop'] = stop
        self.vars['consumed_fire_ts'] = self.vars['pending_fire_ts']

    def should_cancel_entry(self) -> bool:
        return False

    def on_open_position(self, order):
        self.vars['peak'] = self.price
        self.vars['trough'] = self.price
        stop = self.vars['planned_stop']
        if self.is_long:
            self.stop_loss = self.position.qty, stop
        elif self.is_short:
            self.stop_loss = self.position.qty, stop

    def update_position(self):
        if self.is_long:
            self.vars['peak'] = max(self.vars['peak'], self.close)
            new_stop = self.vars['peak'] - self.hp['atr_trail_mult'] * self._atr
            if new_stop > self.average_stop_loss:
                self.stop_loss = self.position.qty, new_stop
        elif self.is_short:
            self.vars['trough'] = min(self.vars['trough'], self.close)
            new_stop = self.vars['trough'] + self.hp['atr_trail_mult'] * self._atr
            if new_stop < self.average_stop_loss:
                self.stop_loss = self.position.qty, new_stop

    def update_chart(self) -> None:
        # Bollinger Bands on the candle chart (4h anchor values)
        bb = ta.bollinger_bands(
            self.anchor,
            period=self.hp['bb_period'],
            devup=self.hp['bb_dev'],
            devdn=self.hp['bb_dev'],
        )
        self.add_line_to_candle_chart('bb_upper', bb.upperband, color='rgba(41, 128, 185, 0.7)')
        self.add_line_to_candle_chart('bb_middle', bb.middleband, color='rgba(127, 140, 141, 0.7)')
        self.add_line_to_candle_chart('bb_lower', bb.lowerband, color='rgba(41, 128, 185, 0.7)')

        # Keltner Channels on the candle chart (4h anchor values)
        kc = ta.keltner(
            self.anchor,
            period=self.hp['bb_period'],
            multiplier=self.hp['kc_mult'],
            matype=1,
        )
        self.add_line_to_candle_chart('kc_upper', kc.upperband, color='rgba(230, 126, 34, 0.7)')
        self.add_line_to_candle_chart('kc_lower', kc.lowerband, color='rgba(230, 126, 34, 0.7)')

        # ADX and threshold on a separate panel
        adx = self._adx
        self.add_extra_line_chart('adx', 'adx14', adx, color='orange')
        self.add_horizontal_line_to_extra_chart('adx', 'threshold', self.hp['adx_threshold'], 'red', line_width=1)

        # Linear regression slope on a separate panel
        slope = self._slope()
        self.add_extra_line_chart('slope', 'slope', slope, color='blue')
        self.add_horizontal_line_to_extra_chart('slope', 'zero', 0, 'gray', line_width=1)

        # ATR on a separate panel
        atr = self._atr
        self.add_extra_line_chart('atr', 'atr14', atr, color='purple')