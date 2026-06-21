package com.plushledger.update

internal object UpdateSourceSelector {
    fun order(urls: List<String>, preference: String): List<String> {
        val valid = urls
            .map(String::trim)
            .filter { it.startsWith("https://") }
            .distinct()

        return when (preference) {
            "GitHub 优先" -> valid.sortedByDescending(::isGitHubSource)
            else -> valid.sortedBy(::isGitHubSource)
        }
    }

    private fun isGitHubSource(url: String): Boolean = runCatching {
        val host = java.net.URI(url).host.orEmpty().lowercase()
        host == "github.com" || host.endsWith(".githubusercontent.com")
    }.getOrDefault(false)
}

internal fun totalDownloadBytes(
    contentRange: String?,
    contentLength: Long,
    existingBytes: Long,
    appending: Boolean
): Long {
    val rangeTotal = contentRange
        ?.substringAfterLast('/', missingDelimiterValue = "")
        ?.toLongOrNull()
    if (rangeTotal != null && rangeTotal > 0L) return rangeTotal
    if (contentLength <= 0L) return -1L
    return if (appending) existingBytes + contentLength else contentLength
}
