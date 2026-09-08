package com.konstantyp.gymcal.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class MotivationQuote(
    val text: String,
    val author: String,
)

/**
 * ZenQuotes public API. Failures return null — UI hides the bar (BINDING quote-bar-A).
 */
object QuoteRepository {
    private const val URL_RANDOM = "https://zenquotes.io/api/random"

    suspend fun fetchRandom(): MotivationQuote? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(URL_RANDOM).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun parse(body: String): MotivationQuote? {
        val arr = JSONArray(body)
        if (arr.length() == 0) return null
        val obj = arr.getJSONObject(0)
        val q = obj.optString("q").trim()
        if (q.isEmpty()) return null
        val a = obj.optString("a").trim().ifEmpty { "ZenQuotes" }
        return MotivationQuote(text = q, author = a)
    }
}
