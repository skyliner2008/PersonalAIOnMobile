package com.example.personalaibot

import com.example.personalaibot.automation.smc.SmcWallBuilder
import com.example.personalaibot.automation.smc.SmcWallKind
import com.example.personalaibot.automation.smc.SmcOrderBlock
import com.example.personalaibot.automation.smc.Fvg
import com.example.personalaibot.automation.smc.LiquidityZone
import com.example.personalaibot.tools.trading.Candle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmcWallsTest {
    private val candles = List(40) { i -> Candle(100.0, 101.0, 99.0, 100.0, 1.0, i.toLong()) }

    @Test fun builds_ob_and_fvg_walls_and_confluence() {
        val obs = listOf(SmcOrderBlock(true, 101.0, 99.0, 30, hasFvg = true))
        val fvgs = listOf(Fvg(true, 101.2, 99.8, 31))
        val liq = listOf(LiquidityZone(100.0, 29, false, confluenceStars = 3))
        val (walls, scores) = SmcWallBuilder.build(candles, obs, emptyList(), fvgs, liq, 100.0, 1.0)
        assertEquals(2, walls.size)
        assertTrue(walls.any { it.kind == SmcWallKind.ORDER_BLOCK })
        assertTrue(walls.any { it.kind == SmcWallKind.FVG })
        assertTrue(walls.first().confluence >= 2)
        assertTrue(scores.first { it.side == "BULL" }.score >= 2)
    }

    @Test fun wall_order_is_deterministic() {
        val obs = listOf(SmcOrderBlock(true, 101.0, 99.0, 30, hasFvg = true))
        val fvgs = listOf(Fvg(true, 101.2, 99.8, 31))
        val a = SmcWallBuilder.build(candles, obs, emptyList(), fvgs, emptyList(), 100.0, 1.0).first
        val b = SmcWallBuilder.build(candles, obs, emptyList(), fvgs, emptyList(), 100.0, 1.0).first
        assertEquals(a, b)
    }
}
