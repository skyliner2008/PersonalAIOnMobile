package com.skyliner2008.jarvis

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import com.skyliner2008.jarvis.ui.component.avatar.DynamicPropAnimation
import com.skyliner2008.jarvis.ui.component.avatar.DynamicVectorProp
import com.skyliner2008.jarvis.ui.component.avatar.PropPosition
import com.skyliner2008.jarvis.ui.component.avatar.RobotFaceState
import com.skyliner2008.jarvis.ui.component.avatar.parseHexColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SvgPathTest {
    @Test
    fun testSvgPathParser() {
        val pathString = "M 10 20 L 30 40 Z"
        val nodes = PathParser().parsePathString(pathString).toNodes()
        assertTrue(nodes.isNotEmpty())
        assertEquals(3, nodes.size)
    }

    @Test
    fun testComplexSvgPath() {
        // Hat-like shape with cubic bezier and quadratic curves
        val cowboyHatPath = "M 4 20 Q 12 18 20 20 C 18 16 16 12 15 8 C 14 6 10 6 9 8 C 8 12 6 16 4 20 Z"
        val nodes = PathParser().parsePathString(cowboyHatPath).toNodes()
        assertTrue(nodes.isNotEmpty())
        assertTrue(nodes.size >= 5)
    }

    @Test
    fun testParseHexColor() {
        assertEquals(Color(0xFFFFD700), parseHexColor("#FFD700"))
        assertEquals(Color(0xFF00E5FF), parseHexColor("00E5FF"))
        assertEquals(Color(0xFFFFFFFF), parseHexColor("#FFF"))
        assertEquals(Color(0xFF000000), parseHexColor("#000"))
        // Fallback on invalid hex
        val fallback = Color(0xFF123456)
        assertEquals(fallback, parseHexColor("not_a_color", fallback))
    }

    @Test
    fun testDynamicVectorPropModel() {
        val prop = DynamicVectorProp(
            id = "cowboy_hat",
            name = "cowboy_hat",
            svgPath = "M 4 20 L 20 20 Z",
            fillColor = "#8B4513",
            position = PropPosition.FOREHEAD,
            sizeDp = 64f,
            animation = DynamicPropAnimation.FLOAT_BOB
        )
        assertEquals("cowboy_hat", prop.id)
        assertEquals(PropPosition.FOREHEAD, prop.position)
        assertEquals(DynamicPropAnimation.FLOAT_BOB, prop.animation)
    }

    @Test
    fun testRobotFaceStateFromArgsWithSvg() {
        val args = mapOf(
            "emotion" to "happy",
            "svg_path" to "M 10 10 L 20 20 Z",
            "prop_name" to "crown",
            "prop_color" to "#FFD700",
            "prop_position" to "FOREHEAD",
            "prop_anim" to "PULSE",
            "prop_size" to "56"
        )
        val state = RobotFaceState.fromArgs(args)
        assertEquals(1, state.customProps.size)
        val prop = state.customProps[0]
        assertEquals("crown", prop.name)
        assertEquals("#FFD700", prop.fillColor)
        assertEquals(PropPosition.FOREHEAD, prop.position)
        assertEquals(DynamicPropAnimation.PULSE, prop.animation)
        assertEquals(56f, prop.sizeDp)
    }

    @Test
    fun testPetCustomPropStorePersistenceAndLookup() {
        val store = com.skyliner2008.jarvis.pet.PetCustomPropStore
        store.clearCustomProps()
        assertTrue(store.savedCustomProps.value.isEmpty())

        val eyepatch = DynamicVectorProp(
            id = "pirate_eyepatch",
            name = "pirate_eyepatch",
            svgPath = "M 5 5 C 10 5 15 10 15 15 C 15 20 10 25 5 25 Z",
            fillColor = "#1A1A1A",
            position = PropPosition.LEFT_EYE,
            sizeDp = 0f, // Auto-fit to eye diameter
            animation = DynamicPropAnimation.STATIC
        )
        store.saveCustomProp(eyepatch)

        assertEquals(1, store.savedCustomProps.value.size)
        val found = store.findPropByNameOrId("pirate_eyepatch")
        assertNotNull(found)
        assertEquals("pirate_eyepatch", found.name)
        assertEquals(PropPosition.LEFT_EYE, found.position)
        assertEquals(0f, found.sizeDp)

        // Case-insensitive lookup
        val foundUpper = store.findPropByNameOrId("PIRATE_EYEPATCH")
        assertNotNull(foundUpper)

        // Delete from store
        store.deleteCustomProp("pirate_eyepatch")
        assertTrue(store.savedCustomProps.value.isEmpty())
        kotlin.test.assertNull(store.findPropByNameOrId("pirate_eyepatch"))
    }

    @Test
    fun testPetModeControllerPropReuseByName() {
        var currentState = com.skyliner2008.jarvis.ui.component.avatar.AvatarState()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        val controller = com.skyliner2008.jarvis.pet.PetModeController(
            scope = scope,
            avatarStateProvider = { currentState },
            onUpdateAvatarState = { currentState = it }
        )

        // 1. First time: Add custom prop with SVG Path (AI dreams up and creates a monocle)
        val cmdWithSvg = "CUSTOM_PROP|action=add|name=detective_monocle|svg_path=M 10 10 C 20 10 20 20 10 20 Z|position=right_eye|size=0|animation=static"
        controller.updateRobotFace(cmdWithSvg)

        assertEquals(1, currentState.faceState.customProps.size)
        val equipped = currentState.faceState.customProps.first()
        assertEquals("detective_monocle", equipped.name)
        assertEquals(PropPosition.RIGHT_EYE, equipped.position)
        assertEquals(0f, equipped.sizeDp)

        // Verify it was persisted in PetCustomPropStore
        val stored = com.skyliner2008.jarvis.pet.PetCustomPropStore.findPropByNameOrId("detective_monocle")
        assertNotNull(stored)
        assertEquals("detective_monocle", stored.name)

        // 2. Remove prop from robot's face
        controller.removeCustomProp("detective_monocle")
        assertTrue(currentState.faceState.customProps.isEmpty())

        // 3. Later conversation: AI reuses previously created prop simply by name without svg_path!
        val cmdReuseByName = "CUSTOM_PROP|action=add|name=detective_monocle"
        controller.updateRobotFace(cmdReuseByName)

        assertEquals(1, currentState.faceState.customProps.size)
        val reused = currentState.faceState.customProps.first()
        assertEquals("detective_monocle", reused.name)
        assertEquals(PropPosition.RIGHT_EYE, reused.position)
        assertEquals("M 10 10 C 20 10 20 20 10 20 Z", reused.svgPath)

        // Clean up
        com.skyliner2008.jarvis.pet.PetCustomPropStore.deleteCustomProp("detective_monocle")
    }
}
