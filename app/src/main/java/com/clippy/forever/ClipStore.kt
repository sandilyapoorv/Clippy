package com.clippy.forever

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.clippy.core.ClipKind
import java.io.File

class ClipStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "clippy.db", null, 1) {
    private val imagesDir = File(context.applicationContext.filesDir, "clip-images").apply { mkdirs() }

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE clips (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                created_at INTEGER NOT NULL,
                kind TEXT NOT NULL,
                text TEXT,
                image_path TEXT,
                mime_type TEXT,
                fingerprint TEXT NOT NULL UNIQUE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_clips_created_at ON clips(created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun insertText(text: String, fingerprint: String): InsertResult {
        return insert(
            kind = ClipKind.TEXT.name,
            text = text,
            imagePath = null,
            mimeType = "text/plain",
            fingerprint = fingerprint,
        )
    }

    @Synchronized
    fun insertImage(bytes: ByteArray, mimeType: String, fingerprint: String): InsertResult {
        val extension = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("gif") -> "gif"
            else -> "jpg"
        }
        val file = File(imagesDir, "$fingerprint.$extension")
        if (!file.exists()) {
            file.writeBytes(bytes)
        }
        return insert(
            kind = ClipKind.IMAGE.name,
            text = null,
            imagePath = file.absolutePath,
            mimeType = mimeType,
            fingerprint = fingerprint,
        )
    }

    @Synchronized
    fun all(query: String = ""): List<ClipRecord> {
        val selection: String?
        val args: Array<String>?
        if (query.isBlank()) {
            selection = null
            args = null
        } else {
            selection = "text LIKE ?"
            args = arrayOf("%$query%")
        }
        readableDatabase.query(
            "clips",
            arrayOf("id", "created_at", "kind", "text", "image_path", "mime_type", "fingerprint"),
            selection,
            args,
            null,
            null,
            "created_at DESC",
        ).use { cursor ->
            val items = mutableListOf<ClipRecord>()
            while (cursor.moveToNext()) {
                items += ClipRecord(
                    id = cursor.getLong(0),
                    createdAt = cursor.getLong(1),
                    kind = cursor.getString(2),
                    text = cursor.getString(3),
                    imagePath = cursor.getString(4),
                    mimeType = cursor.getString(5),
                    fingerprint = cursor.getString(6),
                )
            }
            return items
        }
    }

    @Synchronized
    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM clips", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    @Synchronized
    fun delete(id: Long) {
        val record = all().firstOrNull { it.id == id }
        writableDatabase.delete("clips", "id = ?", arrayOf(id.toString()))
        record?.imagePath?.let { path -> File(path).delete() }
    }

    private fun insert(
        kind: String,
        text: String?,
        imagePath: String?,
        mimeType: String?,
        fingerprint: String,
    ): InsertResult {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.query(
                "clips",
                arrayOf("id"),
                "fingerprint = ?",
                arrayOf(fingerprint),
                null,
                null,
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return InsertResult(id = cursor.getLong(0), duplicate = true, failed = false)
                }
            }
            val values = ContentValues().apply {
                put("created_at", System.currentTimeMillis())
                put("kind", kind)
                put("text", text)
                put("image_path", imagePath)
                put("mime_type", mimeType)
                put("fingerprint", fingerprint)
            }
            val id = db.insertOrThrow("clips", null, values)
            if (id < 0) {
                return InsertResult(id = -1, duplicate = false, failed = true)
            }
            db.setTransactionSuccessful()
            return InsertResult(id = id, duplicate = false, failed = false)
        } catch (_: SQLiteConstraintException) {
            return InsertResult(id = -1, duplicate = true, failed = false)
        } catch (_: Exception) {
            return InsertResult(id = -1, duplicate = false, failed = true)
        } finally {
            db.endTransaction()
        }
    }

    data class InsertResult(val id: Long, val duplicate: Boolean, val failed: Boolean = false)
}
