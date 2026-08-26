// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.modelimport

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

data class ExternalModelOpenRequest(
    val uri: Uri,
    val displayName: String?,
    val mimeType: String?,
)

/** Process-local hand-off between MainActivity intent delivery and the model workspace. */
object ExternalModelOpenRequests {
    private val pending = Channel<ExternalModelOpenRequest>(capacity = 32)

    val requests: Flow<ExternalModelOpenRequest> = pending.receiveAsFlow()

    internal fun enqueue(request: ExternalModelOpenRequest): Boolean = pending.trySend(request).isSuccess
}

object IncomingModelIntentRouter {
    /** Returns the number of supported documents queued for import. */
    fun route(context: Context, intent: Intent?): Int {
        intent ?: return 0
        val candidates = IncomingModelUriSelection.select(
            action = intent.action,
            dataUri = intent.data?.toString(),
            streamUris = intent.streamUris().map(Uri::toString),
            clipDataUris = buildList {
                val clipData = intent.clipData ?: return@buildList
                repeat(clipData.itemCount) { index ->
                    clipData.getItemAt(index).uri?.toString()?.let(::add)
                }
            },
        )

        val resolver = context.contentResolver
        var acceptedCount = 0
        candidates.forEach { serializedUri ->
            val uri = Uri.parse(serializedUri)
            if (uri.scheme != "content" && uri.scheme != "file") return@forEach

            val displayName = resolver.queryDisplayName(uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')
            val mimeType = runCatching { resolver.getType(uri) }.getOrNull() ?: intent.type
            if (!ModelDocumentPolicy.accepts(displayName, mimeType)) return@forEach

            val queued = ExternalModelOpenRequests.enqueue(
                ExternalModelOpenRequest(
                    uri = uri,
                    displayName = displayName,
                    mimeType = mimeType,
                ),
            )
            if (queued) acceptedCount += 1
        }
        return acceptedCount
    }
}

private fun android.content.ContentResolver.queryDisplayName(uri: Uri): String? = runCatching {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
    }
}.getOrNull()

private fun Intent.streamUris(): List<Uri> {
    @Suppress("DEPRECATION")
    val value = extras?.get(Intent.EXTRA_STREAM)
    return when (value) {
        is Uri -> listOf(value)
        is ArrayList<*> -> value.filterIsInstance<Uri>()
        is Array<*> -> value.filterIsInstance<Uri>()
        else -> emptyList()
    }
}
