package com.example.personalaibot.automation.backtest

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BacktestResultStore — เก็บผล backtest ล่าสุดไว้ให้หน้าจอ Backtest อ่าน (in-memory)
 * tool trading_backtest push ผลเข้ามาทุกครั้งที่รันสำเร็จ — UI อ่านผ่าน StateFlow
 * (เก็บสูงสุด 20 runs ล่าสุด, หายเมื่อปิดแอป — ไม่ persist)
 */
object BacktestResultStore {

    data class Run(
        val result: BacktestResult,
        val atMs: Long,
        val strategyArg: String   // กลยุทธ์ที่สั่ง (all/smc/mix/เฉพาะตัว)
    )

    private val _runs = MutableStateFlow<List<Run>>(emptyList())
    val runs: StateFlow<List<Run>> = _runs.asStateFlow()

    fun add(result: BacktestResult, strategyArg: String) {
        val run = Run(result, kotlinx.datetime.Clock.System.now().toEpochMilliseconds(), strategyArg)
        _runs.value = (listOf(run) + _runs.value).take(20)
    }

    fun clear() { _runs.value = emptyList() }
}
