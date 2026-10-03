package com.heynex

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Minimal Groq API client for natural language understanding and general Q&A.
 */
class GroqApiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun ask(question: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Groq API key is empty"))
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", "You are Hey Nex, a helpful personal voice assistant for an Android phone. " +
                    "Answer concisely in the same language the user spoke, suitable for text-to-speech.")
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", question)
            })
        }

        val json = JSONObject().apply {
            put("model", "llama3-8b-8192")
            put("messages", messages)
            put("temperature", 0.7)
            put("max_tokens", 512)
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.w("GroqApiClient", "Groq error: ${response.code} - $body")
                Result.failure(IOException("Groq API error ${response.code}"))
            } else {
                val answer = JSONObject(body)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                Result.success(answer.trim())
            }
        } catch (e: UnknownHostException) {
            Result.failure(IOException("No internet connection"))
        } catch (e: Exception) {
            Log.e("GroqApiClient", "Request failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
