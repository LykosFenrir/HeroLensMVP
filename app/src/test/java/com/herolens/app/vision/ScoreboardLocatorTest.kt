package com.herolens.app.vision

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreboardLocatorTest {
    @Test
    fun locatesBlueAndRedPanelsAtArbitraryFramePosition() {
        val width = 640
        val height = 360
        val rgba = ByteArray(width * height * 4) { 0x28 }

        fun fill(left: Int, top: Int, right: Int, bottom: Int, r: Int, g: Int, b: Int) {
            for (y in top until bottom) {
                for (x in left until right) {
                    val index = (y * width + x) * 4
                    rgba[index] = r.toByte()
                    rgba[index + 1] = g.toByte()
                    rgba[index + 2] = b.toByte()
                    rgba[index + 3] = 0xff.toByte()
                }
            }
        }

        fill(180, 90, 510, 170, 30, 145, 225)
        fill(190, 190, 520, 270, 205, 42, 65)
        val region = ScoreboardLocator.locate(ScoreboardFrame(rgba, width, height))
        assertNotNull(region)
        region!!
        assertTrue(region.allyPanel.centerY < region.enemyPanel.centerY)
        assertTrue(region.bounds.width > 0.40f)
        assertTrue(region.confidence > 0.20f)
    }
}
