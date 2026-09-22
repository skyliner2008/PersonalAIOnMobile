package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.pet.HoloHandGesture
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme
import com.skyliner2008.jarvis.ui.component.avatar.ForegroundEffect
import com.skyliner2008.jarvis.ui.component.avatar.PropType
import com.skyliner2008.jarvis.ui.component.avatar.RiveAvatarMapper
import com.skyliner2008.jarvis.ui.component.avatar.RiveBg
import com.skyliner2008.jarvis.ui.component.avatar.RiveFace
import com.skyliner2008.jarvis.ui.component.avatar.RiveFg
import com.skyliner2008.jarvis.ui.component.avatar.RiveProp
import com.skyliner2008.jarvis.ui.component.avatar.RiveReact
import com.skyliner2008.jarvis.ui.component.avatar.RiveState
import com.skyliner2008.jarvis.ui.component.avatar.RobotFaceState
import com.skyliner2008.jarvis.ui.component.avatar.effectiveEmotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RiveAvatarBindingTest {

    @Test
    fun `every emotion maps into the Rive contract ranges`() {
        for (emotion in AvatarEmotion.entries) {
            val inputs = RiveAvatarMapper.plan(AvatarState(emotion = emotion)).inputs
            assertTrue(inputs.face in 0..33, "$emotion face=${inputs.face}")
            assertTrue(inputs.prop in 0..55, "$emotion prop=${inputs.prop}")
            assertTrue(inputs.bg in 0..10, "$emotion bg=${inputs.bg}")
            assertTrue(inputs.fg in 0..11, "$emotion fg=${inputs.fg}")
            assertTrue(inputs.state in 0..19, "$emotion state=${inputs.state}")
        }
    }

    @Test
    fun `eye style overrides the emotion like the Canvas engine`() {
        val state = AvatarState(
            emotion = AvatarEmotion.IDLE,
            faceState = RobotFaceState(eyeStyleName = "heart")
        )
        assertEquals(AvatarEmotion.LOVE, state.effectiveEmotion())
        assertEquals(RiveFace.LOVE, RiveAvatarMapper.plan(state).inputs.face)
    }

    @Test
    fun `AI props go to Rive when it has them and to Compose otherwise`() {
        val state = AvatarState(faceState = RobotFaceState(propsRaw = "book,sunglasses,crown"))
        val plan = RiveAvatarMapper.plan(state)
        assertEquals(RiveProp.SUNGLASSES, plan.inputs.prop)
        assertFalse(PropType.SUNGLASSES in plan.composeProps)
        assertTrue(PropType.BOOK in plan.composeProps)
        assertTrue(PropType.CROWN in plan.composeProps)
    }

    @Test
    fun `emotion accessories Rive lacks are drawn by Compose`() {
        val plan = RiveAvatarMapper.plan(AvatarState(emotion = AvatarEmotion.WORKING))
        assertTrue(PropType.LAPTOP in plan.composeProps)
        assertEquals(RiveFace.FOCUSED, plan.inputs.face)
    }

    @Test
    fun `backgrounds and foregrounds split between Rive and Compose`() {
        val love = RiveAvatarMapper.plan(AvatarState(faceState = RobotFaceState(backgroundName = "love_bg")))
        assertEquals(RiveBg.BOKEH_HEARTS, love.inputs.bg)
        assertEquals(BackgroundTheme.DEFAULT, love.composeBackground)

        val sakura = RiveAvatarMapper.plan(
            AvatarState(emotion = AvatarEmotion.LOVE, faceState = RobotFaceState(backgroundName = "sakura"))
        )
        assertEquals(RiveBg.NONE, sakura.inputs.bg)
        assertEquals(BackgroundTheme.SAKURA, sakura.composeBackground)

        val confetti = RiveAvatarMapper.plan(AvatarState(faceState = RobotFaceState(foregroundName = "confetti_tumble")))
        assertEquals(RiveFg.CONFETTI, confetti.inputs.fg)
        assertEquals(ForegroundEffect.NONE, confetti.composeForeground)
    }

    @Test
    fun `operational emotions drive the state channel`() {
        fun stateOf(s: AvatarState) = RiveAvatarMapper.plan(s).inputs.state
        assertEquals(RiveState.LISTENING, stateOf(AvatarState(emotion = AvatarEmotion.LISTENING)))
        assertEquals(RiveState.THINKING, stateOf(AvatarState(emotion = AvatarEmotion.THINKING)))
        assertEquals(RiveState.ASLEEP, stateOf(AvatarState(emotion = AvatarEmotion.SLEEPING)))
        assertEquals(RiveState.DIZZY, stateOf(AvatarState(isDizzy = true)))
        assertEquals(RiveState.SPEAKING, stateOf(AvatarState(emotion = AvatarEmotion.HAPPY, isSpeaking = true)))
        assertEquals(RiveState.IDLE, stateOf(AvatarState()))
        assertEquals(
            RiveState.PLAY_BOUNCE,
            stateOf(AvatarState(faceState = RobotFaceState(eyeTrickName = "ping_pong_bounce")))
        )
    }

    @Test
    fun `speaking keeps the face and turns the voice bar on`() {
        val inputs = RiveAvatarMapper.plan(
            AvatarState(emotion = AvatarEmotion.HAPPY, isSpeaking = true, audioLevel = 1.7f)
        ).inputs
        assertEquals(RiveFace.HAPPY, inputs.face)
        assertTrue(inputs.isSpeaking)
        assertEquals(1f, inputs.audioLevel)
    }

    @Test
    fun `touch reactions follow the state machine escalation`() {
        assertEquals(RiveReact.POKE_L, RiveAvatarMapper.reactFor(HoloHandGesture.POKE, true, AvatarEmotion.HAPPY))
        assertEquals(RiveReact.POKE_R, RiveAvatarMapper.reactFor(HoloHandGesture.POKE, false, AvatarEmotion.HAPPY))
        assertEquals(RiveReact.POKE_ANNOYED, RiveAvatarMapper.reactFor(HoloHandGesture.POKE, true, AvatarEmotion.POUT))
        assertEquals(RiveReact.POKE_ANGRY, RiveAvatarMapper.reactFor(HoloHandGesture.POKE, false, AvatarEmotion.ANGRY))
        assertEquals(RiveReact.HEAD_PAT, RiveAvatarMapper.reactFor(HoloHandGesture.STROKE, false, AvatarEmotion.LOVE))
        assertEquals(RiveReact.CHIN_SCRATCH, RiveAvatarMapper.reactFor(HoloHandGesture.CHIN_SCRATCH, false, AvatarEmotion.LOVE))
    }
}
