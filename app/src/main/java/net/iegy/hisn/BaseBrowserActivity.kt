package net.iegy.hisn

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import net.iegy.hisn.browser.ExternalLinks
import net.iegy.hisn.browser.FileChooserRequest
import net.iegy.hisn.settings.LocaleHelper
import net.iegy.hisn.ui.BrowserRoot
import net.iegy.hisn.ui.theme.HisnTheme

/** Shared by the regular and the private window; the two differ only in which process they run in. */
abstract class BaseBrowserActivity : ComponentActivity(), BrowserHost {

    protected val container: AppContainer
        get() = (application as HisnApp).container

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var permissionCallback: ((Map<String, Boolean>) -> Unit)? = null
    private var customView: View? = null
    private var customViewHidden: (() -> Unit)? = null

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            fileCallback?.onReceiveValue(uris)
            fileCallback = null
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            permissionCallback?.invoke(result)
            permissionCallback = null
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container.tabManager.attachActivity(this)
        onBeforeContent(intent)
        setContent {
            HisnTheme(settings = container.settings, isPrivate = isPrivateSession) {
                BrowserRoot(container = container, host = this)
            }
        }
    }

    /** Hook for subclasses to react to the launch Intent before the UI is built. */
    protected open fun onBeforeContent(intent: Intent) = Unit

    override fun onResume() {
        super.onResume()
        container.tabManager.attachActivity(this)
        container.tabManager.onHostResumed()
    }

    override fun onPause() {
        container.tabManager.onHostPaused()
        super.onPause()
    }

    override fun onDestroy() {
        // Release the Activity reference held by every WebView's context wrapper so it can be collected.
        if (isFinishing || isDestroyed) container.tabManager.attachActivity(null)
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        super.onDestroy()
    }

    // ---- BrowserHost ----------------------------------------------------------------------------------------

    override fun launchFileChooser(request: FileChooserRequest) {
        fileCallback?.onReceiveValue(null)
        fileCallback = request.callback
        try {
            fileLauncher.launch(request.params.createIntent())
        } catch (e: ActivityNotFoundException) {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
        }
    }

    override fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun requestPermissions(permissions: Array<String>, onResult: (Map<String, Boolean>) -> Unit) {
        permissionCallback = onResult
        permissionLauncher.launch(permissions)
    }

    override fun showCustomView(view: View, onHidden: () -> Unit) {
        if (customView != null) {
            onHidden()
            return
        }
        customView = view
        customViewHidden = onHidden
        (window.decorView as FrameLayout).addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun hideCustomView() {
        val view = customView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        customView = null
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        customViewHidden?.invoke()
        customViewHidden = null
    }

    override fun handleBackInFullscreen(): Boolean {
        if (customView == null) return false
        hideCustomView()
        return true
    }

    override fun openExternal(url: String): Boolean {
        val intent = ExternalLinks.toIntent(url) ?: return false
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    override fun exitApp() {
        moveTaskToBack(true)
    }

    override fun recreateForLanguageChange() {
        recreate()
    }
}
