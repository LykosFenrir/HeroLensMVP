package com.herolens.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.herolens.app.BuildConfig
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private object PortraitLoader {
    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    @Volatile private var urls: Map<String, String>? = null

    suspend fun load(context: Context, heroId: String): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(heroId)?.let { return@withContext it }
        val cacheFile = File(File(context.cacheDir, "hero_templates").apply { mkdirs() }, "$heroId.png")
        val bitmap = if (cacheFile.exists()) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)
        } else {
            val url = urlMap(context)[heroId] ?: return@withContext null
            download(url, cacheFile)
        }
        bitmap?.also { memory.put(heroId, it) }
    }

    private fun urlMap(context: Context): Map<String, String> {
        urls?.let { return it }
        return synchronized(this) {
            urls ?: run {
                val json = context.assets.open("hero_portraits.json").bufferedReader().use { it.readText() }
                val source = JSONObject(json)
                source.keys().asSequence().associateWith { source.getString(it) }.also { urls = it }
            }
        }
    }

    private fun download(url: String, destination: File): Bitmap? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 18_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "HeroLens/${BuildConfig.VERSION_NAME}")
        return try {
            connection.inputStream.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            BitmapFactory.decodeFile(destination.absolutePath)
        } catch (_: Exception) {
            destination.delete()
            null
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
fun HeroPortrait(
    heroId: String,
    heroName: String,
    modifier: Modifier = Modifier.size(34.dp)
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, heroId) {
        value = PortraitLoader.load(context.applicationContext, heroId)
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "$heroName portrait",
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = heroName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
