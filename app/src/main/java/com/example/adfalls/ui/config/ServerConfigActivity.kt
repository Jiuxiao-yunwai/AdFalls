package com.example.adfalls.ui.config

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.adfalls.R
import com.example.adfalls.config.ServerConfig
import com.example.adfalls.data.repository.AdRepository
import com.example.adfalls.ui.feed.MainActivity
import kotlinx.coroutines.launch

class ServerConfigActivity : ComponentActivity() {
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var errorText: TextView
    private lateinit var confirmButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.app_bg)
        window.navigationBarColor = getColor(R.color.app_bg)
        setContentView(R.layout.activity_server_config)

        hostInput = findViewById(R.id.server_host_input)
        portInput = findViewById(R.id.server_port_input)
        errorText = findViewById(R.id.server_config_error)
        confirmButton = findViewById(R.id.server_config_confirm)

        findViewById<View>(R.id.server_config_back).setOnClickListener { finish() }

        val (host, port) = ServerConfig.splitHostAndPort()
        hostInput.setText(host)
        portInput.setText(port)

        confirmButton.setOnClickListener {
            saveAndRestart()
        }
    }

    private fun saveAndRestart() {
        errorText.visibility = View.GONE
        confirmButton.isEnabled = false

        val result = runCatching {
            ServerConfig.saveBaseUrl(
                context = this,
                host = hostInput.text?.toString().orEmpty(),
                port = portInput.text?.toString().orEmpty()
            )
        }
        result.onFailure { error ->
            errorText.text = error.message ?: "服务器配置无效"
            errorText.visibility = View.VISIBLE
            confirmButton.isEnabled = true
            return
        }

        lifecycleScope.launch {
            runCatching {
                AdRepository.clearLocalAdData()
            }.onSuccess {
                Toast.makeText(this@ServerConfigActivity, "服务器已更新，正在重启", Toast.LENGTH_SHORT).show()
                restartApp()
            }.onFailure { error ->
                errorText.text = error.message ?: "本地广告数据清理失败"
                errorText.visibility = View.VISIBLE
                confirmButton.isEnabled = true
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finishAffinity()
    }
}
