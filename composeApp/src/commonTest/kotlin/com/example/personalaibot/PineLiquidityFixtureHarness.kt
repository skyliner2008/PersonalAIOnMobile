package com.example.personalaibot

import com.example.personalaibot.tools.trading.Candle
import com.example.personalaibot.tools.trading.SmcApiService
import com.example.personalaibot.tools.trading.SmcLiquidityZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * P3.1 fixture contract for empirical Pine V11.29 parity.
 *
 * A fixture contains the exact candle stream exported from TradingView/Pine and
 * the reference liquidity snapshot after every bar. The harness replays the
 * same prefix on every bar and compares the Kotlin snapshot bar-by-bar.
 */
object PineLiquidityFixtureHarness {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Fixture(
        val name: String,
        val symbol: String,
        val timeframe: String,
        val params: Params = Params(),
        val candles: List<FixtureCandle>,
        val referenceSnapshots: List<ReferenceSnapshot>
    )

    @Serializable
    data class Params(
        val lookback: Int = 10,
        val thresholdPercent: Double = 0.1,
        val swingLen: Int = 10,
        val maxZones: Int = 50,
        val maxVisiblePerSide: Int = 5
    )

    @Serializable
    data class FixtureCandle(
        val timestamp: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double = 0.0
    )

    @Serializable
    data class ReferenceSnapshot(
        val barIndex: Int,
        val zones: List<ReferenceZone>
    )

    @Serializable
    data class ReferenceZone(
        val price: Double,
        val isHigh: Boolean,
        val strength: Int
    )

    data class Mismatch(
        val barIndex: Int,
        val expected: List<ReferenceZone>,
        val actual: List<ReferenceZone>
    )

    fun parse(content: String): Fixture = json.decodeFromString(content)

    fun compare(fixture: Fixture): List<Mismatch> {
        require(fixture.referenceSnapshots.size == fixture.candles.size) {
            "Fixture must contain exactly one reference snapshot per candle"
        }
        require(fixture.referenceSnapshots.map { it.barIndex } == fixture.candles.indices.toList()) {
            "Reference snapshots must cover every candle in ascending barIndex order"
        }

        val candles = fixture.candles.map { it.toCandle() }
        val api = SmcApiService(io.ktor.client.HttpClient())
        return fixture.referenceSnapshots.mapNotNull { reference ->
            val actual = api.detectPineLiquidityZones(
                candles = candles.subList(0, reference.barIndex + 1),
                lookback = fixture.params.lookback,
                threshold = fixture.params.thresholdPercent,
                swingLen = fixture.params.swingLen,
                maxZones = fixture.params.maxZones,
                maxN = fixture.params.maxVisiblePerSide
            ).toReferenceZones()

            if (actual == reference.zones) null
            else Mismatch(reference.barIndex, reference.zones, actual)
        }
    }

    fun assertMatches(fixture: Fixture) {
        val mismatches = compare(fixture)
        check(mismatches.isEmpty()) { formatMismatches(fixture, mismatches) }
    }

    private fun FixtureCandle.toCandle() = Candle(open, high, low, close, volume, timestamp)

    private fun List<SmcLiquidityZone>.toReferenceZones(): List<ReferenceZone> = map {
        ReferenceZone(it.price, it.isHigh, it.strength)
    }

    private fun formatMismatches(fixture: Fixture, mismatches: List<Mismatch>): String = buildString {
        append("Pine V11.29 fixture mismatch: ")
        append(fixture.name)
        append(" (")
        append(mismatches.size)
        append(" bars)\n")
        mismatches.take(20).forEach { mismatch ->
            append("bar=").append(mismatch.barIndex).append('\n')
            append("  expected=").append(mismatch.expected).append('\n')
            append("  actual=").append(mismatch.actual).append('\n')
        }
        if (mismatches.size > 20) append("... ").append(mismatches.size - 20).append(" more mismatches")
    }
}
