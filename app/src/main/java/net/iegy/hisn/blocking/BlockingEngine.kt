package net.iegy.hisn.blocking

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface FilterUpdate {
    data class Updated(val rules: Int) : FilterUpdate
    data object Failed : FilterUpdate
}

/**
 * Request-level ad/tracker blocking for WebView, based on domain rules.
 *
 * Decisions run on WebView's request thread, so everything read there is a volatile immutable snapshot and a
 * decision is a few hash lookups: no disk, no network, no locks. Rules come from the bundled starter list or from
 * the cached downloaded list. Downloading only happens on explicit user action or the weekly background refresh,
 * never while a page is loading, and a failed or implausibly small download never replaces working rules.
 *
 * Limits (also documented in the README): this blocks network requests by domain. It does not do cosmetic
 * (element-hiding) filtering and cannot block ads served from the same domain as the content.
 */
class BlockingEngine(private val context: Context) {
    @Volatile private var matcher: DomainMatcher = DomainMatcher.EMPTY
    @Volatile private var enabled: Boolean = true
    @Volatile private var allowlist: Set<String> = emptySet()

    private val _ruleCount = MutableStateFlow(0)
    val ruleCount: StateFlow<Int> = _ruleCount.asStateFlow()

    private val cacheFile: File
        get() = File(File(context.filesDir, "filters").apply { mkdirs() }, CACHE_NAME)

    fun configure(enabled: Boolean, allowlist: Set<String>) {
        this.enabled = enabled
        this.allowlist = allowlist
    }

    /** Loads the cached list if it is present and plausible, otherwise the bundled starter list. */
    suspend fun load() = withContext(Dispatchers.IO) {
        val cached = runCatching {
            val file = cacheFile
            if (file.exists()) file.useLines { DomainMatcher.parse(it) } else null
        }.getOrNull()?.takeIf { it.size >= MIN_CACHED_RULES }

        val loaded = cached ?: context.assets.open(ASSET).bufferedReader().useLines { DomainMatcher.parse(it) }
        matcher = loaded
        _ruleCount.value = loaded.size
    }

    /** True if the subresource request should be answered with an empty response. Never called for main frames. */
    fun shouldBlock(requestHost: String?, pageHost: String?): Boolean {
        if (!enabled || requestHost.isNullOrEmpty()) return false
        if (pageHost != null) {
            if (Allowlist.isAllowed(allowlist, pageHost)) return false
            if (SitePolicy.isSameSite(requestHost, pageHost)) return false
        }
        return matcher.isBlocked(requestHost)
    }

    /**
     * Downloads the public hosts list over HTTPS (normal TLS validation) into a temp file, validates it, and only
     * then swaps it in. Returns [FilterUpdate.Failed] on any problem, leaving the current rules untouched.
     */
    suspend fun update(): FilterUpdate = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        val temp = File(cacheFile.parentFile, "$CACHE_NAME.tmp")
        try {
            connection = (URL(SOURCE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "HISN-Browser-FilterUpdate")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext FilterUpdate.Failed

            var total = 0L
            connection.inputStream.use { input ->
                temp.outputStream().use { out ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) throw IOException("Filter list too large")
                        out.write(buffer, 0, read)
                    }
                }
            }

            val parsed = temp.useLines { DomainMatcher.parse(it) }
            if (parsed.size < MIN_UPDATE_RULES) return@withContext FilterUpdate.Failed

            if (!temp.renameTo(cacheFile)) {
                temp.copyTo(cacheFile, overwrite = true)
            }
            matcher = parsed
            _ruleCount.value = parsed.size
            FilterUpdate.Updated(parsed.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FilterUpdate.Failed
        } finally {
            connection?.disconnect()
            temp.delete()
        }
    }

    companion object {
        private const val ASSET = "filters/default_hosts.txt"
        private const val CACHE_NAME = "hosts.txt"

        /** StevenBlack/hosts (MIT licence): a unified, regularly maintained hosts list. See docs/FILTER_LISTS.md. */
        const val SOURCE_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"

        private const val MAX_BYTES = 16L * 1024 * 1024
        private const val MIN_UPDATE_RULES = 5_000
        private const val MIN_CACHED_RULES = 1_000
    }
}
