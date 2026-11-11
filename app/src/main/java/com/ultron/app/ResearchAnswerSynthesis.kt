package com.ultron.app

/** Lightweight local synthesis for web text gathered from accessibility/OCR. */
object ResearchAnswerSynthesis {
    fun summarize(query: String, chunks: List<String>): String {
        val lines = chunks.flatMap { it.lines() }
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length >= 25 }
            .filterNot { isChromeNoise(it) }
            .distinct()
        if (lines.isEmpty()) return ""
        val selected = lines
            .sortedWith(compareByDescending<String> { relevance(it, query) }.thenBy { it.length })
            .take(6)
            .filter { it.length <= 420 }
        if (selected.isEmpty()) return ""
        return "Here’s what I found about $query: " + selected.joinToString(" ")
    }

    private fun relevance(line: String, query: String): Int {
        val q = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        val l = line.lowercase()
        return q.count { l.contains(it) } * 10 + if (l.contains("is ") || l.contains("are ") || l.contains("because")) 3 else 0
    }

    private fun isChromeNoise(s: String): Boolean {
        val x = s.lowercase()
        return x in setOf("sign in", "images", "videos", "news", "shopping", "maps", "more", "tools", "settings") ||
            x.startsWith("https://") || x.startsWith("http://")
    }
}
