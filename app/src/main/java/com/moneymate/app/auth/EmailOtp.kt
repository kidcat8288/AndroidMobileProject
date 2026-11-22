package com.moneymate.app.auth

import com.moneymate.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

interface EmailOtpService {
    suspend fun sendCode(email: String): String?
    suspend fun verifyCode(email: String, code: String): String?
}
class SupabaseEmailOtp(
    private val baseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/'),
    private val apiKey: String = BuildConfig.SUPABASE_ANON_KEY
) : EmailOtpService {

    override suspend fun sendCode(email: String): String? =
        post("/auth/v1/otp", JSONObject().put("email", email).put("create_user", true)) { status, message ->
            when (status) {
                429 -> "Too many codes requested. Wait a minute and try again."
                else -> message ?: "Couldn't send the code. Try again."
            }
        }

    override suspend fun verifyCode(email: String, code: String): String? =
        post("/auth/v1/verify", JSONObject().put("type", "email").put("email", email).put("token", code)) { status, message ->
            when (status) {
                429 -> "Too many attempts. Wait a minute and try again."
                in 400..499 -> "That code is wrong or has expired."
                else -> message ?: "Couldn't check the code. Try again."
            }
        }

    private suspend fun post(path: String, body: JSONObject, onError: (Int, String?) -> String): String? {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            return "Email codes aren't set up. Add supabase.url and supabase.anonKey to local.properties."
        }
        return withContext(Dispatchers.IO) {
            try {
                val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("apikey", apiKey)
                }
                try {
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                    val status = connection.responseCode
                    if (status in 200..299) {
                        connection.inputStream.close() // session not needed
                        null
                    } else {
                        val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        onError(status, serverMessage(text))
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: IOException) {
                "No connection to the email service. Check your internet and try again."
            }
        }
    }

    private fun serverMessage(text: String): String? = runCatching {
        val json = JSONObject(text)
        listOf("msg", "error_description", "message").firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() } }
    }.getOrNull()
}
