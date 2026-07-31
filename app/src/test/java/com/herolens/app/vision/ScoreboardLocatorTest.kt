package com.herolens.app.vision

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreboardLocatorTest {
    @Test
    fun locatesPairedBlueAndRedPanels() {
        val width = 320
        val height = 180
        val pixels = ByteArray(width * height * 4) { index -> if (index % 4 == 3) 0xff.toByte() else 24 }
        fill(pixels, width, 70, 30, 250, 78, 30, 155, 225)
        fill(pixels, width, 72, 112, 252, 160, 210, 45, 58)
        val region = ScoreboardLocator.locate(ScoreboardFrame(pixels, width, height))
        assertNotNull(region)
    }

    @Test
    fun searchExplainsWhenOnlyOneTeamColourIsPresent() {
        val width = 320
        val height = 180
        val pixels = ByteArray(width * height * 4) { index -> if (index % 4 == 3) 0xff.toByte() else 24 }
        fill(pixels, width, 70, 30, 250, 78, 30, 155, 225)
        val result = ScoreboardLocator.search(ScoreboardFrame(pixels, width, height))
        assertNull(result.region)
        assertEquals(ScoreboardSearchState.NOT_FOUND, result.state)
    }

    @Test
    fun rejectsSingleColoredPanel() {
        val width = 320
        val height = 180
        val pixels = ByteArray(width * height * 4) { index -> if (index % 4 == 3) 0xff.toByte() else 24 }
        fill(pixels, width, 70, 30, 250, 78, 30, 155, 225)
        assertNull(ScoreboardLocator.locate(ScoreboardFrame(pixels, width, height)))
    }

    private fun fill(
        pixels: ByteArray,
        width: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        red: Int,
        green: Int,
        blue: Int
    ) {
        for (y in top until bottom) for (x in left until right) {
            val i = (y * width + x) * 4
            pixels[i] = red.toByte()
            pixels[i + 1] = green.toByte()
            pixels[i + 2] = blue.toByte()
            pixels[i + 3] = 0xff.toByte()
        }
    }
}
