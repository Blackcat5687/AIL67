package com.notekeep.local.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File
import java.security.MessageDigest

/**
 * Persists note background images inside the app's own storage instead of relying on a
 * `content://` URI (whose read permission can be revoked or can simply stop resolving once the
 * source app/file changes). Every background image a user picks - or restores from a backup -
 * ends up as a private file:// copy under filesDir/backgrounds, so it survives independently of
 * the original picker URI.
 *
 * Files are named by the SHA-256 hash of their own bytes rather than a random UUID. This means
 * picking the same picture again (even from a different content:// uri, e.g. re-selected from the
 * gallery, or re-imported from a backup) always resolves to the exact same file on disk instead of
 * creating a fresh duplicate copy - which is what previously made "recently used backgrounds" show
 * the same image several times.
 */
object ImageStore {

    private const val DIR_NAME = "backgrounds"

    private fun dir(context: Context): File {
        val d = File(context.filesDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Copies the content:// (or any resolvable) URI into permanent app storage. Returns a file:// URI string, or null on failure.
     * If a file with identical content already exists (same hash), that existing file is reused instead of writing a duplicate. */
    fun persist(context: Context, sourceUri: Uri): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() } ?: return null
            persistBytes(context, bytes, guessExtension(context, sourceUri))
        } catch (e: Exception) {
            null
        }
    }

    /** Writes raw image bytes into app storage, reusing an existing file if identical bytes were already stored. */
    private fun persistBytes(context: Context, bytes: ByteArray, extension: String): String? {
        return try {
            val hash = sha256(bytes)
            val existing = dir(context).listFiles()?.firstOrNull { it.nameWithoutExtension == hash }
            if (existing != null) {
                return Uri.fromFile(existing).toString()
            }
            val outFile = File(dir(context), "$hash.$extension")
            outFile.writeBytes(bytes)
            Uri.fromFile(outFile).toString()
        } catch (e: Exception) {
            null
        }
    }

    /** True if this uri string already points at our own private storage (nothing to copy). */
    fun isOwnedFile(context: Context, uriString: String): Boolean {
        return try {
            val path = Uri.parse(uriString).path ?: return false
            File(path).canonicalPath.startsWith(dir(context).canonicalPath)
        } catch (e: Exception) {
            false
        }
    }

    /** Reads an owned/any file:// or content:// uri into base64, for embedding in a JSON backup. */
    fun readAsBase64(context: Context, uriString: String): String? {
        return try {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                Base64.encodeToString(input.readBytes(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Writes base64 image data (from a backup file) into private storage. Returns a file:// URI string, or null on failure.
     * Reuses an existing file if the same image content is already stored (e.g. re-importing the same backup, or a
     * background that was also used by another note), instead of writing another duplicate copy. */
    fun writeFromBase64(context: Context, base64: String, extension: String): String? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            persistBytes(context, bytes, extension)
        } catch (e: Exception) {
            null
        }
    }

    /** Deletes every private background file that isn't referenced by any of the given still-in-use uri strings. Safe no-op for uris we don't own. */
    fun pruneUnused(context: Context, inUseUriStrings: Collection<String>) {
        val inUsePaths = inUseUriStrings.mapNotNull { runCatching { Uri.parse(it).path }.getOrNull() }.toSet()
        dir(context).listFiles()?.forEach { file ->
            if (file.absolutePath !in inUsePaths) {
                file.delete()
            }
        }
    }

    private fun guessExtension(context: Context, uri: Uri): String {
        val type = context.contentResolver.getType(uri)
        return when {
            type?.contains("png") == true -> "png"
            type?.contains("webp") == true -> "webp"
            type?.contains("gif") == true -> "gif"
            else -> "jpg"
        }
    }
}
