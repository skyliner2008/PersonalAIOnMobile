package com.example.personalaibot

import com.example.personalaibot.tools.trading.Candle
import com.example.personalaibot.tools.trading.SmcApiService
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmcP3LiquidityParityTest {
    private fun candles(): List<Candle> = listOf(
        Candle(100.0,101.0,99.0,100.5,1.0,1), Candle(100.5,102.0,100.0,101.0,1.0,2),
        Candle(101.0,103.0,100.5,102.0,1.0,3), Candle(102.0,102.5,99.5,100.0,1.0,4),
        Candle(100.0,101.0,98.0,99.0,1.0,5), Candle(99.0,102.0,98.5,101.0,1.0,6),
        Candle(101.0,103.0,100.0,102.0,1.0,7), Candle(102.0,102.2,99.0,100.0,1.0,8),
        Candle(100.0,101.0,97.0,98.0,1.0,9), Candle(98.0,100.0,97.5,99.0,1.0,10),
        Candle(99.0,101.0,98.0,100.0,1.0,11), Candle(100.0,104.0,99.0,103.0,1.0,12),
        Candle(103.0,104.0,101.0,102.0,1.0,13), Candle(102.0,103.0,100.0,101.0,1.0,14),
        Candle(101.0,102.0,99.0,100.0,1.0,15), Candle(100.0,101.0,98.0,99.0,1.0,16),
        Candle(99.0,100.0,97.0,98.0,1.0,17), Candle(98.0,99.0,96.0,97.0,1.0,18),
        Candle(97.0,98.0,95.0,96.0,1.0,19), Candle(96.0,97.0,94.0,95.0,1.0,20)
    )
    @Test fun lifecycle_is_deterministic() {
        val a=SmcApiService(HttpClient()).detectPineLiquidityZones(candles())
        val b=SmcApiService(HttpClient()).detectPineLiquidityZones(candles())
        assertEquals(a,b)
    }
    @Test fun output_is_bounded_per_side() {
        val out=SmcApiService(HttpClient()).detectPineLiquidityZones(candles(),maxN=5)
        assertTrue(out.count{it.isHigh}<=5); assertTrue(out.count{!it.isHigh}<=5)
    }
}
