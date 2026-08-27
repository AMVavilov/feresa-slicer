// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.modelimport

import java.util.Locale

/** Shared file-picker and Android intent policy for model documents Feresa can import. */
object ModelDocumentPolicy {
    const val GenericBinaryMimeType = "application/octet-stream"

    /**
     * MIME types passed to ACTION_OPEN_DOCUMENT. Keep broad fallbacks out of this list so the
     * document picker does not show unrelated files; providers with missing MIME information can
     * still hand a model to Feresa through the extension-gated ACTION_VIEW fallback in the manifest.
     */
    private val modelMimeTypes = linkedSetOf(
        "model/stl",
        "application/sla",
        "application/vnd.ms-pki.stl",
        "model/obj",
        "application/x-tgif",
        "model/3mf",
        "application/vnd.ms-3mfdocument",
        "application/vnd.ms-package.3dmanufacturing-3dmodel+xml",
        "application/x-3mf",
    )

    val pickerMimeTypes: Array<String>
        get() = modelMimeTypes.toTypedArray()

    val acceptedMimeTypes: Set<String>
        get() = modelMimeTypes.toSet()

    /**
     * Decides whether a document advertised by another app is a possible supported model.
     * ModelFileImporter remains the authority and validates the bytes before the file is used.
     */
    fun accepts(displayName: String?, mimeType: String?): Boolean {
        val normalizedName = displayName?.trim().orEmpty()
        val extension = normalizedName
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
        if (extension.isNotEmpty()) return extension in ModelFileImporter.acceptedExtensions

        val normalizedMime = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
            .orEmpty()
        return normalizedMime in modelMimeTypes
    }
}

/** Android-free projection used to deterministically select URI candidates from an incoming Intent. */
internal object IncomingModelUriSelection {
    const val ActionView = "android.intent.action.VIEW"
    const val ActionSend = "android.intent.action.SEND"
    const val ActionSendMultiple = "android.intent.action.SEND_MULTIPLE"

    fun select(
        action: String?,
        dataUri: String?,
        streamUris: List<String>,
        clipDataUris: List<String>,
    ): List<String> {
        val candidates = when (action) {
            ActionView -> buildList {
                dataUri?.let(::add)
                addAll(clipDataUris)
            }
            ActionSend, ActionSendMultiple -> buildList {
                addAll(streamUris)
                addAll(clipDataUris)
                dataUri?.let(::add)
            }
            else -> emptyList()
        }
        return candidates
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
    }
}
