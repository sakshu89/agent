package com.ultron.app

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/** Lightweight Google web-result fetcher. It returns short public snippets, not a full page dump. */
class GoogleSearchClient {
    companion object {
        private const val TAG = "ITRA_WEB"
        private const val MAX_RESULTS = 6
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    fun search(query: String, onSuccess: (String) -> Unit, onError: (Throwable) -> Unit) {
        Thread {
            var outcome: Result<String>? = null
            try {
                val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val url = "https://www.google.com/search?q=$encoded&hl=en&gbv=1"
                val request = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36"
                    )
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                val results = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Google search HTTP ${response.code}")
                    }
                    extractSnippets(response.body?.string().orEmpty())
                }
                if (results.isBlank()) {
                    throw IllegalStateException("Google returned no readable search results.")
                }
                outcome = Result.success(results)
            } catch (t: Throwable) {
                Log.e(TAG, "Google search failed", t)
                outcome = Result.failure(t)
            }
            // Callbacks run outside the try block so a failure in the caller cannot trigger onError a second time.
            outcome?.fold(onSuccess, onError)
        }.start()
    }

    fun shutdown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun extractSnippets(html: String): String {
        val pattern = Pattern.compile(
            "<div[^>]*class=\\\"[^\\\"]*VwiC3b[^\\\"]*\\\"[^>]*>(.*?)</div>",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val matcher = pattern.matcher(html)
        val snippets = linkedSetOf<String>()
        while (matcher.find() && snippets.size < MAX_RESULTS) {
            val text = cleanHtml(matcher.group(1).orEmpty())
            if (text.length >= 25) snippets.add(text)
        }

        // Google's markup changes occasionally. Fall back to result blocks commonly
        // used by the mobile/basic renderer.
        if (snippets.isEmpty()) {
            val fallback = Pattern.compile(
                "<div[^>]*>(.*?)</div>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL
            )
            val fm = fallback.matcher(html)
            while (fm.find() && snippets.size < MAX_RESULTS) {
                val text = cleanHtml(fm.group(1).orEmpty())
                if (text.length in 35..500 && !text.contains("Google Search", ignoreCase = true)) {
                    snippets.add(text)
                }
            }
        }

        return snippets.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
    }

    private fun cleanHtml(value: String): String {
        return value
            .replace(Regex("<br\\s*/?>"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&nbsp;", " ")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
            }
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
