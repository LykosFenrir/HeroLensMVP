package com.herolens.app.vision

import android.graphics.Bitmap

fun Bitmap.toScoreboardFrame(): ScoreboardFrame {
    val source = if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    val rgba = ByteArray(pixels.size * 4)
    pixels.forEachIndexed { index, color ->
        val offset = index * 4
        rgba[offset] = ((color shr 16) and 0xff).toByte()
        rgba[offset + 1] = ((color shr 8) and 0xff).toByte()
        rgba[offset + 2] = (color and 0xff).toByte()
        rgba[offset + 3] = ((color ushr 24) and 0xff).toByte()
    }
    return ScoreboardFrame(rgba, source.width, source.height)
}

fun ScoreboardFrame.toBitmap(): Bitmap {
    val colors = IntArray(width * height)
    for (index in colors.indices) {
        val offset = index * 4
        val r = rgbaBytes[offset].toInt() and 0xff
        val g = rgbaBytes[offset + 1].toInt() and 0xff
        val b = rgbaBytes[offset + 2].toInt() and 0xff
        val a = rgbaBytes[offset + 3].toInt() and 0xff
        colors[index] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
}
