package com.emotion.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Логиката за статовете е чиста и лесна за тестване. */
class NeedsTest {

    @Test
    fun `състоянието не се променя без изминало време`() {
        val before = Needs.Stats(80, 85, 90, false)
        assertEquals(before, Needs.decay(before, 0))
    }

    @Test
    fun `ситостта намалява по-бързо от енергията`() {
        val after = Needs.decay(Needs.Stats(100, 100, 100, false), 70)
        assertEquals(98, after.fullness)   // -70/25
        assertEquals(98, after.energy)     // -70/35
    }

    @Test
    fun `сънят възстановява енергия`() {
        val after = Needs.decay(Needs.Stats(80, 20, 60, true), 60)
        assertEquals(30, after.energy)
        assertTrue(after.sleeping)
    }

    @Test
    fun `статовете никога не излизат извън 0-100`() {
        val empty = Needs.decay(Needs.Stats(1, 1, 1, false), 60 * 24 * 7)
        assertEquals(0, empty.fullness)
        assertEquals(0, empty.energy)
        assertEquals(0, empty.mood)

        // сънят пълни енергията, но не над 100
        val full = Needs.decay(Needs.Stats(100, 95, 50, true), 60 * 24)
        assertEquals(100, full.energy)
        assertEquals(76, full.fullness)    // -1440/60
    }

    @Test
    fun `будният любимец не се събужда сам`() {
        assertFalse(Needs.decay(Needs.Stats(50, 50, 50, false), 10).sleeping)
    }
}
