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
        connection.setRequestProperty("User-Agent", "HeroLens/0.6")
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

    private fun signatureCacheFile(): File =
        File(context.filesDir, "hero-signatures-${HeroCatalog.DATA_VERSION}-v6.bin")

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
        const val CACHE_MAGIC = "HEROLENS_SIGNATURE_V6"
    }
}

data class TemplateLoadResult(
    val signatures: Map<String, ImageSignature>,
    val failures: List<String>
)
