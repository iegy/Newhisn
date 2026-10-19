package net.iegy.hisn

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.iegy.hisn.browser.UrlResolver

class MainActivity : BaseBrowserActivity() {
    override val isPrivateSession: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Keep the system splash only until the first tab exists (a few milliseconds); there is no timed delay.
        splash.setKeepOnScreenCondition { container.tabManager.model.value.tabs.isEmpty() }
        super.onCreate(savedInstanceState)
    }

    override fun onBeforeContent(intent: Intent) {
        handleViewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    /** Another app asked us to open a link: it gets its own tab (only plain http/https is accepted). */
    private fun handleViewIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val url = intent.dataString ?: return
        if (!UrlResolver.isWebUrl(url)) return
        // Consume so a configuration change does not reopen the same link.
        intent.action = null
        container.scope.launch {
            // On a cold start the saved tabs are still being restored; wait so the link is not lost.
            container.tabManager.model.first { it.tabs.isNotEmpty() }
            container.tabManager.openLinkInNewTab(url)
        }
    }

    override fun openPrivateWindow(url: String?) {
        val intent = Intent(this, PrivateActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(PrivateSnapshot.write(intent, container.settings.value, url))
    }

    override fun closePrivateSession() = Unit
}
