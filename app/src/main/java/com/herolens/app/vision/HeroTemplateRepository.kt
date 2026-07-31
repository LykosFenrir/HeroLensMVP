package com.herolens.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class HeroTemplateRepository(private val context: Context) {
    suspend fun load(onProgress: (String) -> Unit): TemplateLoadResult = withContext(Dispatchers.IO) {
        val json = context.assets.open("hero_portraits.json").bufferedReader().use { it.readText() }
        val source = JSONObject(json)
        val ids = source.keys().asSequence().toList().sorted()
        val cacheDir = File(context.cacheDir, "hero_templates").apply { mkdirs() }
        val signatures = linkedMapOf<String, ImageSignature>()
        val failures = mutableListOf<String>()

        ids.forEachIndexed { index, heroId ->
            onProgress("Loading hero portraits ${index + 1}/${ids.size}")
            runCatching {
                val cached = File(cacheDir, "$heroId.png")
                val bitmap = if (cached.exists()) {
                    BitmapFactory.decodeFile(cached.absolutePath)
                } else {
                    downloadBitmap(source.getString(heroId), cached)
                } ?: error("Unable to decode portrait")
                signatures[heroId] = bitmapSignature(bitmap)
                bitmap.recycle()
            }.onFailure {
                failures += "$heroId: ${it.message ?: "download failed"}"
            }
        }
        TemplateLoadResult(signatures, failures)
    }

    private fun downloadBitmap(url: String, destination: File): Bitmap? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "HeroLens/0.5")
        return try {
            connection.inputStream.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            BitmapFactory.decodeFile(destination.absolutePath)
        } finally {
            connection.disconnect()
        }
    }

    private fun bitmapSignature(bitmap: Bitmap): ImageSignature {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val rgba = ByteArray(pixels.size * 4)
        pixels.forEachIndexed { index, color ->
            val offset = index * 4
            rgba[offset] = ((color shr 16) and 0xff).toByte()
            rgba[offset + 1] = ((color shr 8) and 0xff).toByte()
            rgba[offset + 2] = (color and 0xff).toByte()
            rgba[offset + 3] = ((color ushr 24) and 0xff).toByte()
        }
        return SignatureMath.signature(rgba, bitmap.width, bitmap.height)
    }
}

data class TemplateLoadResult(
    val signatures: Map<String, ImageSignature>,
    val failures: List<String>
)
