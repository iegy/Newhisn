@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package net.iegy.hisn.ui.browser

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.iegy.hisn.BrowserHost
import net.iegy.hisn.R
import net.iegy.hisn.blocking.Allowlist
import net.iegy.hisn.browser.UrlResolver
import net.iegy.hisn.download.DownloadStart
import net.iegy.hisn.tabs.TabState
import net.iegy.hisn.ui.home.HomeScreen
import net.iegy.hisn.viewmodel.BrowserViewModel

/** Navigation callbacks the browser screen needs; kept as a plain holder so the screen has no NavController. */
class BrowserNav(
    val openTabs: () -> Unit,
    val openBookmarks: () -> Unit,
    val openHistory: () -> Unit,
    val openDownloads: () -> Unit,
    val openSettings: () -> Unit,
    val openAbout: () -> Unit,
)

@Composable
fun BrowserScreen(vm: BrowserViewModel, host: BrowserHost, nav: BrowserNav) {
    val model by vm.model.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bookmarked by vm.bookmarkedUrls.collectAsStateWithLifecycle()
    val tab = model.selected
    val isPrivate = vm.isPrivate
    val addressFocus = remember { FocusRequester() }

    BackHandler {
        when {
            host.handleBackInFullscreen() -> Unit
            tab == null -> host.exitApp()
            !tab.isHome && tab.canGoBack -> vm.tabs.goBack(tab.id)
            !tab.isHome && tab.parentId != null && model.tabs.any { it.id == tab.parentId } -> vm.tabs.close(tab.id)
            !tab.isHome -> vm.tabs.goHome(tab.id)
            else -> host.exitApp()
        }
    }

    // A private session with no tabs left is over: wipe it and go back to the regular browser.
    if (isPrivate && model.tabs.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(Unit) { host.closePrivateSession() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        if (tab != null) {
            AddressBar(
                tab = tab,
                isPrivate = isPrivate,
                focusRequester = addressFocus,
                onSubmit = { vm.tabs.navigate(tab.id, it) },
                onReload = { vm.tabs.reload(tab.id) },
                onStop = { vm.tabs.stop(tab.id) },
            )
            if (tab.isLoading && !tab.isHome) {
                LinearProgressIndicator(
                    progress = { tab.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            } else {
                Box(Modifier.fillMaxWidth().height(2.dp))
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                tab == null -> Unit
                tab.isHome -> HomeScreen(
                    vm = vm,
                    settings = settings,
                    onSearchClick = { addressFocus.requestFocus() },
                    onOpen = { vm.tabs.navigate(tab.id, it) },
                    nav = nav,
                    host = host,
                )
                else -> WebContent(vm = vm, tab = tab)
            }
        }

        if (tab != null) {
            BottomBar(
                tab = tab,
                tabCount = model.tabs.size,
                isPrivate = isPrivate,
                menu = {
                    PageMenu(
                        vm = vm,
                        host = host,
                        tab = tab,
                        bookmarked = tab.url in bookmarked,
                        blockingEnabled = settings.blockingEnabled,
                        siteAllowed = UrlResolver.hostOf(tab.url)?.let { Allowlist.isAllowed(settings.allowlist, it) } == true,
                        hasClosedTabs = model.closed.isNotEmpty(),
                        nav = nav,
                    )
                },
                onBack = {
                    if (!tab.isHome && tab.canGoBack) vm.tabs.goBack(tab.id)
                    else if (!tab.isHome) vm.tabs.goHome(tab.id)
                },
                onForward = { vm.tabs.goForward(tab.id) },
                onNewTab = { vm.tabs.openNewTab() },
                onTabs = nav.openTabs,
            )
        }
    }
}

@Composable
private fun AddressBar(
    tab: TabState,
    isPrivate: Boolean,
    focusRequester: FocusRequester,
    onSubmit: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val shown = if (tab.url == "about:blank") "" else tab.url
    var field by remember(tab.id, shown) { mutableStateOf(TextFieldValue(shown)) }
    var focused by remember { mutableStateOf(false) }
    val secure = UrlResolver.isSecure(tab.url)
    val insecure = !tab.isHome && UrlResolver.schemeOf(tab.url) == "http"

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.height(46.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (icon, tint, description) = when {
                isPrivate && tab.isHome -> Triple(Icons.Filled.VisibilityOff, MaterialTheme.colorScheme.primary, stringResource(R.string.private_mode))
                tab.isHome -> Triple(Icons.Filled.Search, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.address_hint))
                insecure -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.error, stringResource(R.string.not_secure))
                secure -> Triple(Icons.Filled.Lock, MaterialTheme.colorScheme.primary, stringResource(R.string.secure))
                else -> Triple(Icons.Filled.Info, MaterialTheme.colorScheme.onSurfaceVariant, "")
            }
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))

            Box(modifier = Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                val textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textDirection = TextDirection.Content,
                )
                if (field.text.isEmpty()) {
                    Text(
                        text = stringResource(if (isPrivate) R.string.address_hint_private else R.string.address_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        focusManager.clearFocus()
                        onSubmit(field.text)
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            focused = state.isFocused
                            if (state.isFocused) field = field.copy(selection = TextRange(0, field.text.length))
                        },
                )
            }

            if (focused && field.text.isNotEmpty()) {
                IconButton(onClick = { field = TextFieldValue("") }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear), modifier = Modifier.size(20.dp))
                }
            } else if (!tab.isHome) {
                if (tab.isLoading) {
                    IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_stop), modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconButton(onClick = onReload, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_reload), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    tab: TabState,
    tabCount: Int,
    isPrivate: Boolean,
    menu: @Composable () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNewTab: () -> Unit,
    onTabs: () -> Unit,
) {
    val tabsDescription = stringResource(R.string.action_tabs)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = !tab.isHome) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            IconButton(onClick = onForward, enabled = !tab.isHome && tab.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.action_forward))
            }
            IconButton(onClick = onNewTab) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_tab))
            }
            IconButton(onClick = onTabs) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .size(26.dp)
                        .semantics { contentDescription = tabsDescription },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (tabCount > 99) "99+" else tabCount.toString(),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            menu()
        }
    }
}

@Composable
private fun PageMenu(
    vm: BrowserViewModel,
    host: BrowserHost,
    tab: TabState,
    bookmarked: Boolean,
    blockingEnabled: Boolean,
    siteAllowed: Boolean,
    hasClosedTabs: Boolean,
    nav: BrowserNav,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var open by remember { mutableStateOf(false) }
    val isPrivate = vm.isPrivate
    val isPage = !tab.isHome
    val siteHost = UrlResolver.hostOf(tab.url)
    val canBlock = isPage && UrlResolver.isWebUrl(tab.url) && siteHost != null

    fun close() { open = false }

    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu))
    }
    DropdownMenu(expanded = open, onDismissRequest = ::close) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_new_tab)) },
            leadingIcon = { Icon(Icons.Filled.Add, null) },
            onClick = { close(); vm.tabs.openNewTab() },
        )
        if (!isPrivate) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_new_private_tab)) },
                leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) },
                onClick = { close(); host.openPrivateWindow() },
            )
        }
        if (hasClosedTabs) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_reopen_tab)) },
                leadingIcon = { Icon(Icons.Filled.Replay, null) },
                onClick = { close(); vm.tabs.reopenClosed() },
            )
        }
        if (isPage) {
            HorizontalDivider()
            if (!isPrivate && UrlResolver.isWebUrl(tab.url)) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (bookmarked) R.string.action_remove_bookmark else R.string.action_add_bookmark)) },
                    leadingIcon = { Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null) },
                    onClick = { close(); vm.toggleBookmark(tab.url, tab.title) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_share)) },
                leadingIcon = { Icon(Icons.Filled.Share, null) },
                onClick = {
                    close()
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tab.url)
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_copy_link)) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = {
                    close()
                    clipboard.setText(AnnotatedString(tab.url))
                    Toast.makeText(context, R.string.msg_link_copied, Toast.LENGTH_SHORT).show()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_desktop_site)) },
                leadingIcon = { Icon(Icons.Filled.DesktopWindows, null) },
                trailingIcon = { Checkbox(checked = tab.desktopMode, onCheckedChange = null) },
                onClick = { close(); vm.tabs.setDesktopMode(tab.id, !tab.desktopMode) },
            )
            if (canBlock && blockingEnabled) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.menu_site_blocking))
                            val subtitle = if (siteAllowed) stringResource(R.string.menu_site_blocking_off)
                            else pluralStringResource(R.plurals.menu_blocked_count, tab.blockedCount, tab.blockedCount)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = { Icon(Icons.Filled.Shield, null) },
                    trailingIcon = { Switch(checked = !siteAllowed, onCheckedChange = null) },
                    onClick = {
                        // Switch off for this site, then reload so the change is visible immediately.
                        vm.setSiteBlocking(siteHost.orEmpty(), blockingOn = siteAllowed)
                        close()
                        vm.tabs.reload(tab.id)
                    },
                )
            }
            if (UrlResolver.isDirectMedia(tab.url)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_download_file)) },
                    leadingIcon = { Icon(Icons.Filled.Download, null) },
                    onClick = {
                        close()
                        val message = when (val r = vm.enqueueDownload(tab.url, null)) {
                            is DownloadStart.Started -> context.getString(R.string.msg_download_started, r.fileName)
                            is DownloadStart.Failed -> context.getString(r.messageRes)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_start_page)) },
                leadingIcon = { Icon(Icons.Filled.Home, null) },
                onClick = { close(); vm.tabs.goHome(tab.id) },
            )
        }
        HorizontalDivider()
        if (!isPrivate) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_bookmarks)) },
                leadingIcon = { Icon(Icons.Filled.Bookmark, null) },
                onClick = { close(); nav.openBookmarks() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_history)) },
                leadingIcon = { Icon(Icons.Filled.History, null) },
                onClick = { close(); nav.openHistory() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_downloads)) },
                leadingIcon = { Icon(Icons.Filled.Download, null) },
                onClick = { close(); nav.openDownloads() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                onClick = { close(); nav.openSettings() },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_about)) },
                leadingIcon = { Icon(Icons.Filled.Info, null) },
                onClick = { close(); nav.openAbout() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_close_private)) },
                leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) },
                onClick = { close(); host.closePrivateSession() },
            )
        }
    }
}
