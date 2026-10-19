@file:OptIn(ExperimentalMaterial3Api::class)

package net.iegy.hisn.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.iegy.hisn.R
import net.iegy.hisn.download.DownloadItem
import net.iegy.hisn.download.DownloadStart
import net.iegy.hisn.download.DownloadStatus
import net.iegy.hisn.ui.components.BackTopBar
import net.iegy.hisn.ui.components.EmptyState
import net.iegy.hisn.ui.home.Letter
import net.iegy.hisn.viewmodel.DownloadsViewModel

@Composable
fun DownloadsScreen(vm: DownloadsViewModel, onBack: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun open(item: DownloadItem) {
        val uri = vm.contentUri(item)
        if (uri == null) {
            Toast.makeText(context, R.string.dl_file_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, item.entity.mimeType ?: context.contentResolver.getType(uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.dl_no_app_to_open, Toast.LENGTH_SHORT).show()
        }
    }

    fun share(item: DownloadItem) {
        val uri = vm.contentUri(item) ?: run {
            Toast.makeText(context, R.string.dl_file_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType(item.entity.mimeType ?: context.contentResolver.getType(uri) ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars)) {
        BackTopBar(title = stringResource(R.string.title_downloads), onBack = onBack)
        Text(
            text = stringResource(R.string.downloads_location),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (items.isEmpty()) {
            EmptyState(stringResource(R.string.downloads_empty))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.entity.id }) { item ->
                    DownloadRow(
                        item = item,
                        onOpen = { open(item) },
                        onShare = { share(item) },
                        onCancel = { vm.remove(item, deleteFile = false) },
                        onRetry = {
                            val message = when (val r = vm.retry(item)) {
                                is DownloadStart.Started -> context.getString(R.string.msg_download_started, r.fileName)
                                is DownloadStart.Failed -> context.getString(r.messageRes)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        },
                        onRemove = { vm.remove(item, deleteFile = false) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Letter(item.entity.fileName, 40)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.entity.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            when (item.status) {
                DownloadStatus.PENDING, DownloadStatus.RUNNING, DownloadStatus.PAUSED -> {
                    if (item.total > 0) {
                        LinearProgressIndicator(progress = { (item.bytes.toFloat() / item.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    val sizeText = if (item.total > 0)
                        "${Formatter.formatShortFileSize(context, item.bytes)} / ${Formatter.formatShortFileSize(context, item.total)}"
                    else Formatter.formatShortFileSize(context, item.bytes)
                    Text(
                        text = item.errorRes?.let { stringResource(it) } ?: sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DownloadStatus.SUCCESS -> Text(
                    text = stringResource(R.string.dl_completed) + if (item.total > 0) " · " + Formatter.formatShortFileSize(context, item.total) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                DownloadStatus.FAILED -> Text(
                    text = stringResource(item.errorRes ?: R.string.dl_err_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                DownloadStatus.MISSING -> Text(
                    text = stringResource(R.string.dl_removed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (item.status) {
            DownloadStatus.PENDING, DownloadStatus.RUNNING, DownloadStatus.PAUSED ->
                IconButton(onClick = onCancel) { Icon(Icons.Filled.Cancel, contentDescription = stringResource(R.string.action_cancel)) }
            DownloadStatus.SUCCESS -> {
                IconButton(onClick = onOpen) { Icon(Icons.Filled.OpenInBrowser, contentDescription = stringResource(R.string.action_open)) }
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share)) }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.dl_remove_from_list)) }
            }
            DownloadStatus.FAILED -> {
                IconButton(onClick = onRetry) { Icon(Icons.Filled.Replay, contentDescription = stringResource(R.string.action_retry)) }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.dl_remove_from_list)) }
            }
            DownloadStatus.MISSING ->
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.dl_remove_from_list)) }
        }
    }
}
