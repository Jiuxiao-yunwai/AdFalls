package com.example.adfalls.cache

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object RemoteVideoCache {
    private const val DISK_CACHE_DIR = "adfalls_video_cache"
    private val inFlightUrls = mutableSetOf<String>()

    fun playableUrl(context: Context, url: String?): String? {
        if (url.isNullOrBlank() || isLocalUrl(url)) return url
        val file = diskCacheFile(context.applicationContext, url)
        return if (file.exists() && file.length() > 0L) Uri.fromFile(file).toString() else url
    }

    fun prefetch(scope: CoroutineScope, context: Context, url: String?) {
        if (url.isNullOrBlank() || isLocalUrl(url)) return
        val appContext = context.applicationContext
        val file = diskCacheFile(appContext, url)
        if (file.exists() && file.length() > 0L) return
        synchronized(inFlightUrls) {
            if (!inFlightUrls.add(url)) return
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                downloadToCache(appContext, url)
            }
            synchronized(inFlightUrls) {
                inFlightUrls.remove(url)
            }
        }
    }

    private fun downloadToCache(context: Context, url: String) {
        runCatching {
            val target = diskCacheFile(context, url)
            val temporary = File(target.parentFile, "${target.name}.tmp")
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3_000
                readTimeout = 20_000
            }
            try {
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (temporary.length() > 0L) {
                    if (target.exists()) target.delete()
                    temporary.renameTo(target)
                } else {
                    temporary.delete()
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun isLocalUrl(url: String): Boolean {
        return url.startsWith("file:", ignoreCase = true) ||
            url.startsWith("content:", ignoreCase = true) ||
            url.startsWith("android.resource:", ignoreCase = true)
    }

    private fun diskCacheFile(context: Context, url: String): File {
        val directory = File(context.cacheDir, DISK_CACHE_DIR).apply { mkdirs() }
        return File(directory, "${url.sha256()}.mp4")
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
