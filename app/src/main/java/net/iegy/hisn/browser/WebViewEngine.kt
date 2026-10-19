package net.iegy.hisn.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import net.iegy.hisn.blocking.BlockingEngine
import net.iegy.hisn.tabs.PageError
import net.iegy.hisn.tabs.PageErrorKind

class WebViewEngine(
    private val appContext: Context,
    private val blocking: BlockingEngine,
) : BrowserEngine {

    // Weak so a destroyed session can be collected; only used to reach the engine-wide pause/resume calls.
    private val live = java.util.WeakHashMap<WebViewSession, Boolean>()

    override fun createSession(callbacks: SessionCallbacks, desktopMode: Boolean): EngineSession =
        WebViewSession(appContext, blocking, callbacks, desktopMode).also { live[it] = true }

    private fun anyLiveView(): WebView? = live.keys.firstOrNull { !it.isDestroyed }?.webView

    // pauseTimers/resumeTimers are instance methods, but they act on every WebView in the process.
    override fun pauseAll() {
        anyLiveView()?.pauseTimers()
    }

    override fun resumeAll() {
        anyLiveView()?.resumeTimers()
    }

    override suspend fun clearWebsiteData() {
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies(null)
        cookies.flush()
        WebStorage.getInstance().deleteAllData()
        // clearCache is an instance method that affects the shared HTTP cache, so a short-lived view is enough.
        WebView(MutableContextWrapper(appContext)).apply {
            clearCache(true)
            clearFormData()
            destroy()
        }
    }
}

/** One WebView = one tab's engine session. */
@SuppressLint("SetJavaScriptEnabled")
internal class WebViewSession(
    appContext: Context,
    private val blocking: BlockingEngine,
    private val callbacks: SessionCallbacks,
    desktopMode: Boolean,
) : EngineSession {

    private val contextWrapper = MutableContextWrapper(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Read on WebView's IO threads in shouldInterceptRequest, hence volatile.
    @Volatile private var pageHost: String? = null
    @Volatile private var hadError = false
    private val blockedCount = AtomicInteger(0)
    private val blockedFlushPending = AtomicBoolean(false)
    private val mobileUserAgent: String = WebSettings.getDefaultUserAgent(appContext)
    private var destroyed = false
    val isDestroyed: Boolean get() = destroyed

    val webView: WebView = WebView(contextWrapper)
    override val view: View get() = webView

    init {
        configure(desktopMode)
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            callbacks.onDownload(url, userAgent, contentDisposition, mimeType, contentLength)
        }
        webView.setOnLongClickListener { handleLongPress() }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
    }

    private fun configure(desktopMode: Boolean) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Local files and content providers are never reachable from web content.
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Popups are only honoured for real user gestures (see Chrome.onCreateWindow).
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            // Location is not requested by this app, so geolocation is not offered to pages.
            setGeolocationEnabled(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = if (desktopMode) UserAgents.desktop(mobileUserAgent) else null
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                WebSettingsCompat.setSafeBrowsingEnabled(this, true)
            }
        }
        webView.isVerticalScrollBarEnabled = true
        webView.overScrollMode = View.OVER_SCROLL_NEVER
    }

    override fun load(url: String) {
        if (destroyed) return
        webView.loadUrl(url)
    }

    override fun reload() {
        if (!destroyed) webView.reload()
    }

    override fun stop() {
        if (!destroyed) webView.stopLoading()
    }

    override fun goBack() {
        if (!destroyed && webView.canGoBack()) webView.goBack()
    }

    override fun goForward() {
        if (!destroyed && webView.canGoForward()) webView.goForward()
    }

    override fun setDesktopMode(enabled: Boolean) {
        if (destroyed) return
        webView.settings.userAgentString = if (enabled) UserAgents.desktop(mobileUserAgent) else null
        webView.reload()
    }

    override fun saveState(): Bundle? {
        if (destroyed) return null
        val bundle = Bundle()
        return if (webView.saveState(bundle) != null) bundle else null
    }

    override fun restoreState(state: Bundle): Boolean =
        !destroyed && webView.restoreState(state) != null

    override fun onPause() {
        if (!destroyed) webView.onPause()
    }

    override fun onResume() {
        if (!destroyed) webView.onResume()
    }

    override fun attachActivity(activity: Activity?) {
        contextWrapper.baseContext = activity ?: contextWrapper.baseContext.applicationContext
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.setDownloadListener(null)
        webView.setOnLongClickListener(null)
        webView.destroy()
    }

    private fun publishNavigationState() {
        callbacks.onNavigationState(webView.canGoBack(), webView.canGoForward())
    }

    private fun scheduleBlockedFlush() {
        if (blockedFlushPending.compareAndSet(false, true)) {
            mainHandler.postDelayed({
                blockedFlushPending.set(false)
                if (!destroyed) callbacks.onBlockedCount(blockedCount.get())
            }, 300)
        }
    }

    private fun handleLongPress(): Boolean {
        val result = webView.hitTestResult
        val extra = result.extra?.takeIf { UrlResolver.isWebUrl(it) } ?: return false
        return when (result.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                callbacks.onLongPress(LongPressTarget(linkUrl = extra, imageUrl = null))
                true
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                callbacks.onLongPress(LongPressTarget(linkUrl = null, imageUrl = extra))
                true
            }
            else -> false
        }
    }

    private inner class Client : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return when {
                UrlResolver.isWebUrl(url) -> false
                url == "about:blank" -> false
                UrlResolver.isExternalScheme(url) -> {
                    callbacks.onExternalUrl(url)
                    true
                }
                // javascript:, file:, content:, data:, etc. are never navigated to from a page.
                else -> true
            }
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (request.isForMainFrame) return null
            val host = request.url.host
            if (!blocking.shouldBlock(host, pageHost)) return null
            blockedCount.incrementAndGet()
            scheduleBlockedFlush()
            return WebResourceResponse(
                "text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)),
            )
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            hadError = false
            val newHost = UrlResolver.hostOf(url)
            if (newHost != pageHost) {
                blockedCount.set(0)
                callbacks.onBlockedCount(0)
            }
            pageHost = newHost
            callbacks.onPageStarted(url)
            publishNavigationState()
        }

        override fun onPageFinished(view: WebView, url: String) {
            callbacks.onPageFinished(url, view.title.orEmpty(), hadError)
            publishNavigationState()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (UrlResolver.isWebUrl(url)) {
                pageHost = UrlResolver.hostOf(url)
                callbacks.onUrlChanged(url)
            }
            publishNavigationState()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val description = error.description?.toString().orEmpty()
            // ERR_ABORTED fires when a navigation turns into a download or is superseded; not a failure.
            if (description.contains("ERR_ABORTED")) return
            hadError = true
            val kind = when (error.errorCode) {
                ERROR_HOST_LOOKUP -> PageErrorKind.DNS
                ERROR_TIMEOUT -> PageErrorKind.TIMEOUT
                ERROR_CONNECT, ERROR_IO -> PageErrorKind.CONNECTION
                ERROR_FAILED_SSL_HANDSHAKE -> PageErrorKind.SSL
                else -> PageErrorKind.GENERIC
            }
            callbacks.onError(PageError(kind, request.url.toString(), error.errorCode, description))
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // Certificate validation is never bypassed: the load is cancelled and the user sees an error page.
            handler.cancel()
            hadError = true
            callbacks.onError(PageError(PageErrorKind.SSL, error.url.orEmpty(), error.primaryError))
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            callbacks.onRenderProcessGone(detail.didCrash())
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            callbacks.onProgress(newProgress)
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            callbacks.onTitleChanged(title.orEmpty())
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            // Scripted popups without a user gesture are refused outright.
            if (!isUserGesture) return false
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val target = callbacks.onCreateWindow() as? WebViewSession ?: return false
            transport.webView = target.webView
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            callbacks.onCloseWindow()
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            callbacks.onFileChooser(FileChooserRequest(filePathCallback, fileChooserParams))
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val supported = request.resources.filter {
                it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                    it == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                    it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
            }
            if (supported.isEmpty()) {
                request.deny()
                return
            }
            callbacks.onWebPermission(
                origin = request.origin.toString(),
                resources = supported,
                grant = { granted -> request.grant(granted.toTypedArray()) },
                deny = { request.deny() },
            )
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: android.webkit.GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            callbacks.onShowCustomView(view) { callback.onCustomViewHidden() }
        }

        override fun onHideCustomView() {
            callbacks.onHideCustomView()
        }

        override fun getDefaultVideoPoster(): Bitmap? = null
    }
}
