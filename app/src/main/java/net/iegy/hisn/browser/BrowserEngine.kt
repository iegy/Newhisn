package net.iegy.hisn.browser

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import net.iegy.hisn.tabs.PageError

/**
 * The seam between the app and the web rendering engine. Everything above this interface (tabs, UI, history,
 * private mode) is engine-agnostic; only [WebViewEngine] knows about android.webkit. A different engine can be
 * evaluated later by providing another implementation.
 */
interface BrowserEngine {
    fun createSession(callbacks: SessionCallbacks, desktopMode: Boolean): EngineSession

    /** Pauses/resumes engine-wide timers (call when the whole app goes to the background/foreground). */
    fun pauseAll()
    fun resumeAll()

    /** Clears cookies, site storage and the HTTP cache of this process's web data. */
    suspend fun clearWebsiteData()
}

interface EngineSession {
    val view: View
    fun load(url: String)
    fun reload()
    fun stop()
    fun goBack()
    fun goForward()
    fun setDesktopMode(enabled: Boolean)
    fun saveState(): Bundle?
    fun restoreState(state: Bundle): Boolean
    fun onPause()
    fun onResume()

    /** The view's Context delegates to the visible Activity (dialogs, pickers) and falls back to the app when none. */
    fun attachActivity(activity: Activity?)
    fun destroy()
}

class FileChooserRequest(
    val callback: ValueCallback<Array<Uri>>,
    val params: WebChromeClient.FileChooserParams,
)

/** What the user long-pressed. [linkUrl] and [imageUrl] are always http(s). */
data class LongPressTarget(val linkUrl: String?, val imageUrl: String?)

interface SessionCallbacks {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String, title: String, hadError: Boolean)
    fun onUrlChanged(url: String)
    fun onTitleChanged(title: String)
    fun onProgress(progress: Int)
    fun onNavigationState(canGoBack: Boolean, canGoForward: Boolean)
    fun onError(error: PageError)
    fun onBlockedCount(count: Int)

    /** Returns the session that should receive the new window's content, or null to refuse the popup. */
    fun onCreateWindow(): EngineSession?
    fun onCloseWindow()
    fun onExternalUrl(url: String)
    fun onDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, contentLength: Long)
    fun onLongPress(target: LongPressTarget)
    fun onFileChooser(request: FileChooserRequest)
    fun onWebPermission(origin: String, resources: List<String>, grant: (List<String>) -> Unit, deny: () -> Unit)
    fun onShowCustomView(view: View, onHidden: () -> Unit)
    fun onHideCustomView()
    fun onRenderProcessGone(didCrash: Boolean)
}
