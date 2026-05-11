package com.projectmaidgroup.mobileaidomestic

import android.util.Base64
import java.nio.charset.StandardCharsets

internal data class VivoAigcAuth(
    val appKeyCandidates: List<String>,
) {
    val isValid: Boolean get() = appKeyCandidates.isNotEmpty()

    companion object {
        fun from(apiKey: String, appKey: String): VivoAigcAuth {
            val candidates = linkedSetOf<String>()

            appKey.trim().takeIf { it.isNotBlank() }?.let { candidates += it }

            val rawApiKey = apiKey.trim()
            if (rawApiKey.isNotBlank()) {
                val parts = rawApiKey.split("-", limit = 4)
                if (parts.size == 4 && parts[0] == "sk" && parts[1].contains("xuanji", ignoreCase = true)) {
                    val encodedOrRawAppKey = parts[3].trim()
                    decodeBase64Utf8(encodedOrRawAppKey)?.let { candidates += it }
                    candidates += encodedOrRawAppKey
                }

                candidates += rawApiKey
            }

            return VivoAigcAuth(appKeyCandidates = candidates.filter { it.isNotBlank() })
        }

        private fun decodeBase64Utf8(value: String): String? {
            return runCatching {
                String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8).trim()
            }.getOrNull()?.takeIf { it.isNotBlank() && !it.contains('\u0000') }
        }
    }
}
