package com.clippy.forever

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.clippy.core.ClipKind
import java.io.File

class ClipStore(context: Context) : SQLiteOpenHelper(context, "clippy.db", null, 1) {
    private val imagesDir = File(context.filesDir, "clip-images").apply { mkdirs() }

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

    fun insertText(text: String, fingerprint: String): InsertResult {
        return insert(
            kind = ClipKind.TEXT.name,
            text = text,
            imagePath = null,
            mimeType = "text/plain",
            fingerprint = fingerprint,
        )
    }

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

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM clips", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

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
        val values = ContentValues().apply {
            put("created_at", System.currentTimeMillis())
            put("kind", kind)
            put("text", text)
            put("image_path", imagePath)
            put("mime_type", mimeType)
            put("fingerprint", fingerprint)
        }
        return try {
            val id = writableDatabase.insertOrThrow("clips", null, values)
            InsertResult(id = id, duplicate = false)
        } catch (_: Exception) {
            InsertResult(id = -1, duplicate = true)
        }
    }

    data class InsertResult(val id: Long, val duplicate: Boolean)
}
