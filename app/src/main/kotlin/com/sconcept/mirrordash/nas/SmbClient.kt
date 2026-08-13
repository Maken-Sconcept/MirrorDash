package com.sconcept.mirrordash.nas

import com.sconcept.mirrordash.nas.model.SmbFileItem
import com.sconcept.mirrordash.nas.model.SmbShare
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.util.Properties

/**
 * Thin wrapper around jcifs-ng for browsing an SMB share. Ported from BerthierOptions
 * unchanged - this has no dependency on Android UI, Config, or anything else worth rewriting.
 * All calls perform blocking network I/O and must run off the main thread.
 */
object SmbClient {

    private const val CONNECT_TIMEOUT_MS = "7000"
    private const val RESPONSE_TIMEOUT_MS = "15000"
    private const val SO_TIMEOUT_MS = "15000"

    private fun buildContext(share: SmbShare): CIFSContext {
        val props = Properties()
        props.setProperty("jcifs.smb.client.minVersion", "SMB202")
        props.setProperty("jcifs.smb.client.maxVersion", "SMB311")
        props.setProperty("jcifs.smb.client.connTimeout", CONNECT_TIMEOUT_MS)
        props.setProperty("jcifs.smb.client.responseTimeout", RESPONSE_TIMEOUT_MS)
        props.setProperty("jcifs.smb.client.soTimeout", SO_TIMEOUT_MS)
        val baseContext = BaseContext(PropertyConfiguration(props))
        val auth = NtlmPasswordAuthenticator(
            share.domain.ifBlank { null },
            share.username.ifBlank { null },
            share.password.ifBlank { null },
        )
        return baseContext.withCredentials(auth)
    }

    /** Lists the contents of [path] (relative to the share root, "" for the root itself). */
    @Throws(Exception::class)
    fun list(share: SmbShare, path: String): List<SmbFileItem> {
        val context = buildContext(share)
        val normalizedPath = SmbPaths.normalizeRelativePath(path)
        val url = if (normalizedPath.isBlank()) {
            share.rootUrl()
        } else {
            share.rootUrl() + normalizedPath + "/"
        }
        val root = SmbFile(url, context)
        return root.listFiles()
            ?.map { f ->
                SmbFileItem(
                    name = f.name.trimEnd('/'),
                    url = f.canonicalPath,
                    isDirectory = f.isDirectory,
                    sizeBytes = if (f.isDirectory) 0L else f.length(),
                )
            }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .orEmpty()
    }

    /** Throws if the connection/credentials are bad; used as a "Test connection" check. */
    @Throws(Exception::class)
    fun testConnection(share: SmbShare) {
        list(share, "")
    }

    /** Opens a readable stream for the file at [url] (as returned by [list]'s [SmbFileItem.url]). */
    @Throws(Exception::class)
    fun openStream(share: SmbShare, url: String): java.io.InputStream {
        val context = buildContext(share)
        return SmbFile(url, context).inputStream
    }
}
