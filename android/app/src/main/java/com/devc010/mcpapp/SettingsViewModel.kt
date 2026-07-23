package com.devc010.mcpapp

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class ConnectionStatus {
    IDLE, LOADING, SUCCESS, ERROR
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences = application.getSharedPreferences("SettingsPrefs", Context.MODE_PRIVATE)

    private val _tailscaleIp = MutableStateFlow(sharedPreferences.getString("tailscale_ip", "") ?: "")
    val tailscaleIp: StateFlow<String> = _tailscaleIp.asStateFlow()

    private val _groqApiKey = MutableStateFlow(sharedPreferences.getString("groq_api_key", "") ?: "")
    val groqApiKey: StateFlow<String> = _groqApiKey.asStateFlow()

    private val _zaiApiKey = MutableStateFlow(sharedPreferences.getString("zai_api_key", "") ?: "")
    val zaiApiKey: StateFlow<String> = _zaiApiKey.asStateFlow()

    private val _devmcpApiKey = MutableStateFlow(sharedPreferences.getString("devmcp_api_key", "") ?: "")
    val devmcpApiKey: StateFlow<String> = _devmcpApiKey.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    fun updateTailscaleIp(ip: String) {
        _tailscaleIp.value = ip
        sharedPreferences.edit().putString("tailscale_ip", ip).apply()
    }

    fun updateGroqApiKey(key: String) {
        _groqApiKey.value = key
        sharedPreferences.edit().putString("groq_api_key", key).apply()
    }

    fun updateZaiApiKey(key: String) {
        _zaiApiKey.value = key
        sharedPreferences.edit().putString("zai_api_key", key).apply()
    }

    fun updateDevmcpApiKey(key: String) {
        _devmcpApiKey.value = key
        sharedPreferences.edit().putString("devmcp_api_key", key).apply()
    }

    fun testConnection() {
        if (_tailscaleIp.value.isBlank()) {
            _connectionStatus.value = ConnectionStatus.ERROR
            return
        }
        
        _connectionStatus.value = ConnectionStatus.LOADING
        
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = URL("http://${_tailscaleIp.value}:8000/health")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.setRequestProperty("X-DevMCP-Key", _devmcpApiKey.value)

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                        if (responseStr.contains("\"status\"") && responseStr.contains("\"online\"")) {
                            ConnectionStatus.SUCCESS
                        } else {
                            ConnectionStatus.ERROR
                        }
                    } else {
                        ConnectionStatus.ERROR
                    }
                } catch (e: Exception) {
                    ConnectionStatus.ERROR
                }
            }
            _connectionStatus.value = result
        }
    }
    
    fun resetConnectionStatus() {
        _connectionStatus.value = ConnectionStatus.IDLE
    }
}
