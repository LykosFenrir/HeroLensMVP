package com.herolens.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.herolens.app.vision.HeroDetection
import com.herolens.app.vision.NormalizedRect
import com.herolens.app.vision.ScoreboardFrame
import com.herolens.app.vision.ScoreboardLayout
import com.herolens.app.vision.ScoreboardRegion
import com.herolens.app.vision.SignatureMath
import com.herolens.app.vision.TeamSide
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Opt-in local dataset collector for future detector training.
 *
 * Only the located scoreboard region and portrait cells are saved. The full camera
 * frame and the surrounding room are never written to disk. Nothing is uploaded.
 */
class DatasetCollector(private val context: Context) {
    private val root = File(context.filesDir, "training_samples")
    private val exportRoot = File(context.cacheDir, "dataset_exports")

    fun sampleCount(): Int = root.listFiles()?.count { it.isDirectory } ?: 0

    fun sizeBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clear() {
        root.deleteRecursively()
    }

    fun saveReviewedSample(
        frame: ScoreboardFrame,
        region: ScoreboardRegion,
        detections: List<HeroDetection>,
        layout: ScoreboardLayout,
        platform: InputPlatform,
        displayType: DisplayType,
        scanConfidence: Int,
        source: String = "scan_burst"
    ): DatasetSaveResult {
        val timestamp = System.currentTimeMillis()
        val session = File(root, "sample_$timestamp")
        if (!session.mkdirs() && !session.isDirectory) {
            return DatasetSaveResult(false, null, "Could not create the local sample folder")
        }
        File(root, ".nomedia").apply { if (!exists()) writeText("") }

        return runCatching {
            val scoreboardRect = region.bounds.expandForPrivacyCrop()
            val scoreboard = SignatureMath.crop(frame, scoreboardRect)
            writeJpeg(scoreboard.rgba, scoreboard.width, scoreboard.height, File(session, "scoreboard.jpg"), 91)

            val labels = JSONArray()
            detections.sortedWith(compareBy<HeroDetection> { it.team.ordinal }.thenBy { it.slot }).forEach { detection ->
                val rect = detection.bounds ?: return@forEach
                val crop = SignatureMath.crop(frame, rect.expandCell())
                val teamName = detection.team.name.lowercase()
                val heroName = detection.heroId ?: "unknown"
                val filename = "${teamName}_${detection.slot + 1}_${sanitize(heroName)}.jpg"
                writeJpeg(crop.rgba, crop.width, crop.height, File(session, filename), 94)
                labels.put(
                    JSONObject()
                        .put("team", teamName)
                        .put("slot", detection.slot)
                        .put("heroId", detection.heroId ?: JSONObject.NULL)
                        .put("confidence", detection.confidence.toDouble())
                        .put("file", filename)
                        .put("bounds", rect.toJson())
                )
            }

            val metadata = JSONObject()
                .put("schema", 1)
                .put("appVersion", "0.7.0")
                .put("timestamp", timestamp)
                .put("source", source)
                .put("platform", platform.name)
                .put("displayType", displayType.name)
                .put("layout", layout.name)
                .put("scanConfidence", scanConfidence)
                .put("frameWidth", frame.width)
                .put("frameHeight", frame.height)
                .put("scoreboardBounds", scoreboardRect.toJson())
                .put("labels", labels)
                .put("privacy", "Only the located scoreboard and portrait crops are stored locally. No upload is performed.")
            File(session, "labels.json").writeText(metadata.toString(2))
            DatasetSaveResult(true, session, "Saved locally")
        }.getOrElse { throwable ->
            session.deleteRecursively()
            DatasetSaveResult(false, null, throwable.message ?: "Could not save the training sample")
        }
    }

    fun createShareIntent(): Intent? {
        if (sampleCount() == 0) return null
        exportRoot.mkdirs()
        exportRoot.listFiles()?.forEach { if (it.isFile) it.delete() }
        val output = File(exportRoot, "HeroLens-training-samples-${System.currentTimeMillis()}.zip")
        ZipOutputStream(FileOutputStream(output)).use { zip ->
            root.walkTopDown().filter { it.isFile && it.name != ".nomedia" }.forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(relative))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "HeroLens opt-in training samples")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writeJpeg(rgba: ByteArray, width: Int, height: Int, file: File, quality: Int) {
        require(width > 0 && height > 0)
        val pixels = IntArray(width * height)
        for (index in pixels.indices) {
            val source = index * 4
            val r = rgba[source].toInt() and 0xff
            val g = rgba[source + 1].toInt() and 0xff
            val b = rgba[source + 2].toInt() and 0xff
            val a = rgba[source + 3].toInt() and 0xff
            pixels[index] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(70, 100), stream))
        }
        bitmap.recycle()
    }

    private fun NormalizedRect.expandForPrivacyCrop(): NormalizedRect = NormalizedRect(
        (left - width * 0.04f).coerceIn(0f, 1f),
        (top - height * 0.05f).coerceIn(0f, 1f),
        (right + width * 0.04f).coerceIn(0f, 1f),
        (bottom + height * 0.05f).coerceIn(0f, 1f)
    )

    private fun NormalizedRect.expandCell(): NormalizedRect = NormalizedRect(
        (left - width * 0.10f).coerceIn(0f, 1f),
        (top - height * 0.08f).coerceIn(0f, 1f),
        (right + width * 0.10f).coerceIn(0f, 1f),
        (bottom + height * 0.08f).coerceIn(0f, 1f)
    )

    private fun NormalizedRect.toJson(): JSONObject = JSONObject()
        .put("left", left.toDouble())
        .put("top", top.toDouble())
        .put("right", right.toDouble())
        .put("bottom", bottom.toDouble())

    private fun sanitize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9_-]+"), "_")
}

data class DatasetSaveResult(
    val saved: Boolean,
    val directory: File?,
    val message: String
)
