package com.example.studygate

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// DataStore 扩展
val Context.dataStore by preferencesDataStore(name = "settings")

object SettingsHelper {
    val KEY_BASE_URL = stringPreferencesKey("base_url")
    val KEY_API_KEY = stringPreferencesKey("api_key")
    val KEY_MODEL = stringPreferencesKey("model")
    val KEY_SESSIONS = stringPreferencesKey("sessions_json")
}

// 网络请求客户端
object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun chat(baseUrl: String, apiKey: String, model: String, userPrompt: String): String {
        // 构造 OpenAI 兼容的请求体
        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            }))
        }.toString()

        val request = Request.Builder()
            // 确保 baseUrl 结尾带 /
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                // 解析返回的 JSON，提取 content
                val jsonResponse = JSONObject(bodyStr)
                val content = jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                content
            } else {
                "请求失败: ${response.code}\n$bodyStr"
            }
        } catch (e: Exception) {
            "网络错误: ${e.message}"
        }
    }
}