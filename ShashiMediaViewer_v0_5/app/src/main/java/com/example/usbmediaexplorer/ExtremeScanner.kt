package com.example.usbmediaexplorer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import kotlin.coroutines.coroutineContext

class ExtremeScanner(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    suspend fun scan(
        tree: Uri,
        onFile: suspend (FileRecord) -> Unit,
        onStats: suspend (ScanStats) -> Unit
    ): ScanStats = withContext(Dispatchers.IO) {
        val queue = ArrayDeque<Uri>()
        val root = DocumentsContract.getTreeDocumentId(tree)
        queue.add(DocumentsContract.buildDocumentUriUsingTree(tree, root))
        var s = ScanStats()
        val start = System.nanoTime()
        var last = start

        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val parent = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getDocumentId(parent)
            )
            try {
                resolver.query(children, PROJECTION, null, null, null)?.use { c ->
                    val id = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val name = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mime = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val size = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val mod = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (c.moveToNext()) {
                        coroutineContext.ensureActive()
                        val docId = c.getString(id)
                        val n = c.getString(name) ?: ""
                        val m = c.getString(mime) ?: ""
                        val bytes = if (size >= 0 && !c.isNull(size)) c.getLong(size) else 0L
                        val changed = if (mod >= 0 && !c.isNull(mod)) c.getLong(mod) else 0L
                        val child = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                        if (m == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.addLast(child)
                            s = s.copy(folders = s.folders + 1)
                        } else {
                            val cat = classify(n, m)
                            onFile(FileRecord(child.toString(), parent.toString(), n, bytes, m, changed, cat))
                            s = s.copy(
                                files = s.files + 1, bytes = s.bytes + bytes.coerceAtLeast(0),
                                photos = s.photos + if (cat == Category.PHOTO) 1 else 0,
                                videos = s.videos + if (cat == Category.VIDEO) 1 else 0,
                                audio = s.audio + if (cat == Category.AUDIO) 1 else 0,
                                documents = s.documents + if (cat == Category.DOCUMENT) 1 else 0,
                                archives = s.archives + if (cat == Category.ARCHIVE) 1 else 0,
                                other = s.other + if (cat == Category.OTHER) 1 else 0
                            )
                        }
                        val sec = ((System.nanoTime() - start) / 1_000_000_000L).coerceAtLeast(1)
                        s = s.copy(filesPerSecond = s.files / sec)
                        if (System.nanoTime() - last > 200_000_000L) {
                            onStats(s); last = System.nanoTime()
                        }
                    }
                }
            } catch (_: SecurityException) {
                s = s.copy(inaccessible = s.inaccessible + 1)
            } catch (_: Exception) {
                s = s.copy(errors = s.errors + 1)
            }
        }
        onStats(s)
        s
    }

    private fun classify(name: String, mime: String): Category {
        val e = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") || e in PHOTO -> Category.PHOTO
            mime.startsWith("video/") || e in VIDEO -> Category.VIDEO
            mime.startsWith("audio/") || e in AUDIO -> Category.AUDIO
            mime.startsWith("text/") || e in DOCS -> Category.DOCUMENT
            e in ARCHIVE -> Category.ARCHIVE
            else -> Category.OTHER
        }
    }

    companion object {
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        private val PHOTO = setOf("jpg","jpeg","png","webp","gif","heic","heif","bmp","tif","tiff","dng","raw","cr2","nef","arw")
        private val VIDEO = setOf("mp4","mkv","mov","avi","webm","3gp","m4v","ts","mts","m2ts")
        private val AUDIO = setOf("mp3","wav","flac","aac","m4a","ogg","opus","wma","aiff")
        private val DOCS = setOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","rtf","csv","epub","odt","ods","odp","html","xml")
        private val ARCHIVE = setOf("zip","rar","7z","tar","gz","bz2","xz","iso","cab")
    }
}
