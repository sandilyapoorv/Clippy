package com.clippy.forever

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.clippy.core.Fingerprint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ClipBackup {
    fun export(context: Context, uri: Uri) {
        val store = (context.applicationContext as ClippyApp).store
        val array = JSONArray()
        store.all().forEach { record ->
            val obj = JSONObject()
            obj.put("createdAt", record.createdAt)
            obj.put("kind", record.kind)
            obj.put("text", record.text ?: JSONObject.NULL)
            obj.put("mimeType", record.mimeType ?: JSONObject.NULL)
            obj.put("fingerprint", record.fingerprint)
            if (record.kind == "IMAGE" && record.imagePath != null) {
                val bytes = File(record.imagePath).takeIf { it.exists() }?.readBytes()
                if (bytes != null) {
                    obj.put("imageBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                }
            }
            array.put(obj)
        }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(array.toString().toByteArray(Charsets.UTF_8))
        } ?: error("Could not write backup")
    }

    fun import(context: Context, uri: Uri) {
        val store = (context.applicationContext as ClippyApp).store
        val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not read backup")
        val array = JSONArray(json)
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            val kind = obj.optString("kind", "TEXT")
            if (kind == "IMAGE" && obj.has("imageBase64")) {
                val bytes = Base64.decode(obj.getString("imageBase64"), Base64.NO_WRAP)
                val mime = obj.optString("mimeType", "image/*")
                store.insertImage(bytes, mime, Fingerprint.ofImage(bytes))
            } else {
                val text = obj.optString("text", "")
                if (text.isNotBlank()) {
                    store.insertText(text, Fingerprint.ofText(text))
                }
            }
        }
    }
}
