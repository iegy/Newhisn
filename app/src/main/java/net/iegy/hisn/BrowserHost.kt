package net.iegy.hisn

import android.view.View
import net.iegy.hisn.browser.FileChooserRequest

/** Activity-level capabilities the Compose UI needs but cannot perform itself. */
interface BrowserHost {
    val isPrivateSession: Boolean

    fun launchFileChooser(request: FileChooserRequest)
    fun hasPermission(permission: String): Boolean
    fun requestPermissions(permissions: Array<String>, onResult: (Map<String, Boolean>) -> Unit)

    fun showCustomView(view: View, onHidden: () -> Unit)
    fun hideCustomView()

    /** If a fullscreen video is showing, closes it and returns true (so Back is consumed). */
    fun handleBackInFullscreen(): Boolean

    /** Hands a vetted external link to another app. Returns false if no app can handle it. */
    fun openExternal(url: String): Boolean

    /** Opens the separate private-browsing window (its own task and process). */
    fun openPrivateWindow(url: String? = null)

    /** Private process only: wipes all private data, returns to the regular browser and ends the process. */
    fun closePrivateSession()

    /** Sends the app to the background (Back on the start page). */
    fun exitApp()

    fun recreateForLanguageChange()
}
