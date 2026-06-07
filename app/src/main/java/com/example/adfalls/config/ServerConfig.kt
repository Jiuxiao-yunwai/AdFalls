package com.example.adfalls.config

import android.content.Context
import android.content.SharedPreferences
import com.example.adfalls.BuildConfig

object ServerConfig {
    private const val PREFS_NAME = "server_config"
    private const val KEY_API_BASE_URL = "api_base_url"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getBaseUrl(): String {
        val configured = prefs?.getString(KEY_API_BASE_URL, null)
        return normalizeBaseUrl(configured ?: BuildConfig.ADFALLS_API_BASE_URL)
    }

    fun saveBaseUrl(context: Context, host: String, port: String): String {
        initialize(context)
        val normalized = normalizeHostAndPort(host, port)
        prefs?.edit()?.putString(KEY_API_BASE_URL, normalized)?.apply()
        return normalized
    }

    fun splitHostAndPort(baseUrl: String = getBaseUrl()): Pair<String, String> {
        val withoutScheme = baseUrl
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        val host = withoutScheme.substringBefore(':')
        val port = withoutScheme.substringAfter(':', "8000")
        return host to port
    }

    fun normalizeHostAndPort(host: String, port: String): String {
        val cleanHost = host
            .trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
            .substringBefore('/')
            .substringBefore(':')
        require(cleanHost.isNotBlank()) { "请输入服务器地址" }

        val cleanPort = port.trim()
        val portNumber = cleanPort.toIntOrNull()
        require(portNumber != null && portNumber in 1..65535) { "请输入 1-65535 之间的端口" }

        return "http://$cleanHost:$portNumber"
    }

    private fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "http://$trimmed"
        }
    }
}
