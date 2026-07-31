package com.herolens.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.herolens.app.core.HeroCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class HeroTemplateRepository(private val context: Context) {
    suspend fun load(onProgress: (String) -> Unit): TemplateLoadResult = withContext(Dispatchers.IO) {
        readSignatureCache()?.let { cached ->
            onProgress("Recognition model ready · ${cached.size} heroes")
            return@withContext TemplateLoadResult(cached, emptyList())
        }

        val json = context.assets.open("hero_portraits.json").bufferedReader().use { it.readText() }
        val source = JSONObject(json)
        val ids = source.keys().asSequence().toList().sorted()
        val cacheDir = File(context.cacheDir, "hero_templates").apply { mkdirs() }
        val completed = AtomicInteger(0)
        val semaphore = Semaphore(6)

        val results = coroutineScope {
            ids.map { heroId ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                    val result = runCatching {
                        val cached = File(cacheDir, "$heroId.png")
                        val bitmap = if (cached.exists()) {
                            BitmapFactory.decodeFile(cached.absolutePath)
                        } else {
                            downloadBitmap(source.getString(heroId), cached)
                        } ?: error("Unable to decode portrait")
                        val signature = bitmapSignature(bitmap)
                        bitmap.recycle()
                        heroId to signature
                    }
                    val count = completed.incrementAndGet()
                    onProgress("Preparing recognition $count/${ids.size}")
                    heroId to result
                    }
                }
            }.awaitAll()
        }

        val signatures = linkedMapOf<String, ImageSignature>()
        val failures = mutableListOf<String>()
        results.forEach { (heroId, result) ->
            result.onSuccess { (_, signature) -> signatures[heroId] = signature }
                .onFailure { failures += "$heroId: ${it.message ?: "download failed"}" }
        }
        if (signatures.isNotEmpty()) writeSignatureCache(signatures)
        TemplateLoadResult(signatures, failures)
    }

    fun clearModelCache() {
        signatureCacheFile().delete()
        File(context.cacheDir, "hero_templates").deleteRecursively()
    }

    private fun downloadBitmap(url: String, destination: File): Bitmap? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 18_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "HeroLens/0.6.1")
        val temporary = File(destination.parentFile, "${destination.name}.part")
        return try {
            connection.inputStream.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            BitmapFactory.decodeFile(destination.absolutePath)
        } catch (throwable: Throwable) {
            temporary.delete()
            destination.delete()
            throw throwable
        } finally {
            connection.disconnect()
        }
    }

    private fun bitmapSignature(bitmap: Bitmap): ImageSignature {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        pixels.forEachIndexed { index, color ->
            val alpha = (color ushr 24) and 0xff
            if (alpha >= 24) {
                val x = index % width
                val y = index / width
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }

        if (maxX < minX || maxY < minY) {
            minX = 0
            minY = 0
            maxX = width - 1
            maxY = height - 1
        }

        val padX = ((maxX - minX + 1) * 0.04f).toInt()
        val padY = ((maxY - minY + 1) * 0.04f).toInt()
        minX = (minX - padX).coerceAtLeast(0)
        minY = (minY - padY).coerceAtLeast(0)
        maxX = (maxX + padX).coerceAtMost(width - 1)
        maxY = (maxY + padY).coerceAtMost(height - 1)

        val cropWidth = maxX - minX + 1
        val cropHeight = maxY - minY + 1
        val rgba = ByteArray(cropWidth * cropHeight * 4)
        var destination = 0
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val color = pixels[y * width + x]
                val alpha = (color ushr 24) and 0xff
                val red = (color shr 16) and 0xff
                val green = (color shr 8) and 0xff
                val blue = color and 0xff

                // Composite transparent margins onto neutral gray. This makes the
                // portrait signature less sensitive to the scoreboard's team color.
                rgba[destination] = ((red * alpha + 128 * (255 - alpha)) / 255).toByte()
                rgba[destination + 1] = ((green * alpha + 128 * (255 - alpha)) / 255).toByte()
                rgba[destination + 2] = ((blue * alpha + 128 * (255 - alpha)) / 255).toByte()
                rgba[destination + 3] = 0xff.toByte()
                destination += 4
            }
        }
        return SignatureMath.signature(rgba, cropWidth, cropHeight)
    }

    private fun signatureCacheFile(): File =
        File(context.filesDir, "hero-signatures-${HeroCatalog.DATA_VERSION}-v6_1-tv.bin")

    private fun readSignatureCache(): Map<String, ImageSignature>? {
        val file = signatureCacheFile()
        if (!file.exists()) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                require(input.readUTF() == CACHE_MAGIC)
                val count = input.readInt().coerceIn(0, 100)
                buildMap {
                    repeat(count) {
                        val heroId = input.readUTF()
                        val luminance = input.readFloatArray()
                        val edges = input.readFloatArray()
                        val histogram = input.readFloatArray()
                        val hash = input.readLong()
                        put(heroId, ImageSignature(luminance, edges, histogram, hash))
                    }
                }
            }
        }.getOrElse {
            file.delete()
            null
        }
    }

    private fun writeSignatureCache(signatures: Map<String, ImageSignature>) {
        val destination = signatureCacheFile()
        val temporary = File(destination.parentFile, "${destination.name}.part")
        runCatching {
            DataOutputStream(temporary.outputStream().buffered()).use { output ->
                output.writeUTF(CACHE_MAGIC)
                output.writeInt(signatures.size)
                signatures.toSortedMap().forEach { (heroId, signature) ->
                    output.writeUTF(heroId)
                    output.writeFloatArray(signature.luminance)
                    output.writeFloatArray(signature.edges)
                    output.writeFloatArray(signature.colorHistogram)
                    output.writeLong(signature.hash)
                }
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
        }.onFailure { temporary.delete() }
    }

    private fun DataInputStream.readFloatArray(): FloatArray {
        val size = readInt().coerceIn(0, 4096)
        return FloatArray(size) { readFloat() }
    }

    private fun DataOutputStream.writeFloatArray(values: FloatArray) {
        writeInt(values.size)
        values.forEach { writeFloat(it) }
    }

    private companion object {
        const val CACHE_MAGIC = "HEROLENS_SIGNATURE_V6_1_TV"
    }
}

data class TemplateLoadResult(
    val signatures: Map<String, ImageSignature>,
    val failures: List<String>
)
