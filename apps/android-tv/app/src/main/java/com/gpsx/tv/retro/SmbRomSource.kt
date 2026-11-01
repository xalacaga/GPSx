// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.retro

import java.io.FilterInputStream
import java.io.InputStream
import java.util.Properties
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbFile

/** NAS account; [toString] never shows the password. */
class SmbCredentials(val user: String, val password: String) {
    override fun toString(): String = "SmbCredentials(user=$user)"
}

/**
 * [RomSource] over SMB 2/3 with jcifs-ng (LGPL-2.1). With [credentials] the share is opened with
 * that NAS account (a `DOMAIN\user` name carries its domain); without, as guest, then anonymously
 * if the server refuses guests. Every path is built child by child from the share URL, so folder
 * names are never parsed as URLs.
 */
class SmbRomSource(private val shareUrl: String, private val credentials: SmbCredentials? = null) : RomSource {
    private val base: CIFSContext by lazy {
        val properties =
            Properties().apply {
                setProperty("jcifs.smb.client.minVersion", "SMB202")
                setProperty("jcifs.smb.client.maxVersion", "SMB311")
                setProperty("jcifs.smb.client.connTimeout", "5000")
                setProperty("jcifs.smb.client.responseTimeout", "10000")
                setProperty("jcifs.smb.client.soTimeout", "15000")
            }
        BaseContext(PropertyConfiguration(properties))
    }

    @Volatile private var context: CIFSContext? = null

    override fun list(path: List<String>): List<RomFile> =
        withContext { cifs ->
            resolve(cifs, path, directory = true).use { folder ->
                folder.listFiles().map { child ->
                    child.use {
                        val directory = it.isDirectory
                        RomFile(it.name.trimEnd('/'), directory, if (directory) 0 else it.length())
                    }
                }
            }
        }

    override fun open(path: List<String>): InputStream =
        withContext { cifs ->
            val file = resolve(cifs, path, directory = false)
            try {
                object : FilterInputStream(file.openInputStream()) {
                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            file.close()
                        }
                    }
                }
            } catch (failure: Exception) {
                file.close()
                throw failure
            }
        }

    private fun resolve(cifs: CIFSContext, path: List<String>, directory: Boolean): SmbFile {
        var current = SmbFile(shareUrl, cifs)
        path.forEachIndexed { index, name ->
            val last = index == path.lastIndex
            val next = SmbFile(current, if (last && !directory) name else "$name/")
            current.close()
            current = next
        }
        return current
    }

    private fun <T> withContext(block: (CIFSContext) -> T): T {
        context?.let { return block(it) }
        credentials?.let { account ->
            val domain = account.user.substringBefore('\\', "")
            val user = account.user.substringAfter('\\')
            val signedIn = base.withCredentials(NtlmPasswordAuthenticator(domain, user, account.password))
            return block(signedIn).also { context = signedIn }
        }
        return try {
            val guest = base.withGuestCrendentials()
            block(guest).also { context = guest }
        } catch (_: SmbAuthException) {
            val anonymous = base.withAnonymousCredentials()
            block(anonymous).also { context = anonymous }
        }
    }
}
