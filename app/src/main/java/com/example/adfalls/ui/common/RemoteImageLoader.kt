package com.example.adfalls.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object RemoteImageLoader {
    private const val DISK_CACHE_DIR = "adfalls_image_cache"

    private val inFlightUrls = mutableSetOf<String>()
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(scope: CoroutineScope, imageView: ImageView, url: String?, placeholderColor: Int) {
        if (url.isNullOrBlank()) {
            imageView.tag = null
            imageView.setImageDrawable(ColorDrawable(placeholderColor))
            return
        }

        cache.get(url)?.let {
            imageView.tag = url
            imageView.setImageBitmap(it)
            return
        }

        val previousUrl = imageView.tag as? String
        imageView.tag = url
        if (previousUrl != url) {
            imageView.setImageDrawable(ColorDrawable(placeholderColor))
        }

        val context = imageView.context.applicationContext
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, url) }
            if (imageView.tag == url && bitmap != null) {
                cache.put(url, bitmap)
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    fun prefetch(scope: CoroutineScope, context: Context, url: String?) {
        if (url.isNullOrBlank() || cache.get(url) != null) return
        val appContext = context.applicationContext
        if (diskCacheFile(appContext, url).exists()) return
        synchronized(inFlightUrls) {
            if (!inFlightUrls.add(url)) return
        }
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadBitmap(appContext, url) }
            if (bitmap != null) {
                cache.put(url, bitmap)
            }
            synchronized(inFlightUrls) {
                inFlightUrls.remove(url)
            }
        }
    }

    private fun loadBitmap(context: Context, url: String): Bitmap? {
        diskCacheFile(context, url).takeIf { it.exists() }?.let { file ->
            BitmapFactory.decodeFile(file.absolutePath)?.let { return it }
            file.delete()
        }
        return fetchBitmap(context, url)
    }

    private fun fetchBitmap(context: Context, url: String): Bitmap? {
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_500
                readTimeout = 8_000
            }
            try {
                val bytes = connection.inputStream.use { it.readBytes() }
                diskCacheFile(context, url).writeBytes(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun diskCacheFile(context: Context, url: String): File {
        val directory = File(context.cacheDir, DISK_CACHE_DIR).apply { mkdirs() }
        return File(directory, "${url.sha256()}.img")
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
