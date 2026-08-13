package com.sconcept.mirrordash.nas

import com.sconcept.mirrordash.nas.model.SmbShare

/** Ported from BerthierOptions helpers/SmbPaths.kt (unchanged logic - path normalization
 * shouldn't need to know about UI toolkit or settings storage). */
object SmbPaths {

    fun normalizeRelativePath(path: String): String {
        return path
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("/")
    }

    fun segments(path: String): List<String> {
        val normalized = normalizeRelativePath(path)
        return if (normalized.isBlank()) emptyList() else normalized.split('/')
    }

    fun parentPath(path: String): String {
        return segments(path).dropLast(1).joinToString("/")
    }

    fun childPath(parent: String, child: String): String {
        val cleanParent = normalizeRelativePath(parent)
        val cleanChild = normalizeRelativePath(child)
        return listOf(cleanParent, cleanChild)
            .filter { it.isNotBlank() }
            .joinToString("/")
    }

    fun displayPath(share: SmbShare, relativePath: String = ""): String {
        val host = share.host.trim().trim('/')
        val shareName = share.share.trim().trim('/')
        val baseParts = listOf(host, shareName).filter { it.isNotBlank() }
        val base = if (baseParts.isEmpty()) "smb://" else "smb://${baseParts.joinToString("/")}"
        val normalizedRelativePath = normalizeRelativePath(relativePath)
        return if (normalizedRelativePath.isBlank()) base else "$base/$normalizedRelativePath"
    }

    fun relativePathFromUrl(share: SmbShare, url: String): String {
        val rootUrl = share.rootUrl()
        if (url.startsWith(rootUrl, ignoreCase = true)) {
            return normalizeRelativePath(url.substring(rootUrl.length).trim('/'))
        }

        val afterScheme = url.substringAfter("://", url).replace('\\', '/')
        val hostlessPath = afterScheme.substringAfter('/', "")
        val urlSegments = hostlessPath
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (urlSegments.isEmpty()) {
            return ""
        }

        val shareSegments = normalizeRelativePath(share.share)
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (shareSegments.isNotEmpty() && urlSegments.size >= shareSegments.size) {
            val matchesShareRoot = shareSegments.indices.all { index ->
                urlSegments[index].equals(shareSegments[index], ignoreCase = true)
            }
            if (matchesShareRoot) {
                return normalizeRelativePath(urlSegments.drop(shareSegments.size).joinToString("/"))
            }
        }

        val lastShareSegment = shareSegments.lastOrNull()
        if (lastShareSegment != null) {
            val shareIndex = urlSegments.indexOfFirst { it.equals(lastShareSegment, ignoreCase = true) }
            if (shareIndex >= 0) {
                return normalizeRelativePath(urlSegments.drop(shareIndex + 1).joinToString("/"))
            }
        }

        return normalizeRelativePath(hostlessPath.trim('/'))
    }

    fun relativePathFromUserInput(share: SmbShare, input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""

        val normalizedDisplayRoot = displayPath(share)
        if (trimmed.startsWith(normalizedDisplayRoot, ignoreCase = true)) {
            return normalizeRelativePath(trimmed.removePrefix(normalizedDisplayRoot).trim('/'))
        }

        val withoutScheme = trimmed.substringAfter("://", trimmed)
        val withoutLeadingSlash = withoutScheme.trimStart('/')
        val shareRoot = normalizeRelativePath(share.share)
        val hostRoot = normalizeRelativePath(share.host)
        val normalizedInput = normalizeRelativePath(withoutLeadingSlash)

        return when {
            normalizedInput.equals(shareRoot, ignoreCase = true) -> ""
            normalizedInput.startsWith("$shareRoot/", ignoreCase = true) ->
                normalizeRelativePath(normalizedInput.removePrefix("$shareRoot/"))
            hostRoot.isNotBlank() && normalizedInput.startsWith("$hostRoot/$shareRoot/", ignoreCase = true) ->
                normalizeRelativePath(normalizedInput.removePrefix("$hostRoot/$shareRoot/"))
            hostRoot.isNotBlank() && normalizedInput.equals("$hostRoot/$shareRoot", ignoreCase = true) -> ""
            else -> normalizeRelativePath(trimmed.trim('/'))
        }
    }
}
