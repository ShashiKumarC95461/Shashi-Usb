package com.example.usbmediaexplorer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

class FileStore(context: Context) : SQLiteOpenHelper(context, "usb_files.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE files(" +
            "uri TEXT PRIMARY KEY, parentUri TEXT, name TEXT, size INTEGER, mime TEXT, " +
            "modified INTEGER, category TEXT)"
        )
        db.execSQL("CREATE INDEX idx_name ON files(name)")
        db.execSQL("CREATE INDEX idx_category ON files(category)")
        db.execSQL("CREATE INDEX idx_parent ON files(parentUri)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    @Synchronized
    fun upsert(file: FileRecord) {
        val v = ContentValues().apply {
            put("uri", file.uri); put("parentUri", file.parentUri); put("name", file.name)
            put("size", file.size); put("mime", file.mime); put("modified", file.modified)
            put("category", file.category.name)
        }
        writableDatabase.insertWithOnConflict("files", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun search(query: String, limit: Int = 500): List<FileRecord> {
        val out = ArrayList<FileRecord>()
        val q = "%${query.replace("%", "\%").replace("_", "\_")}%"
        readableDatabase.query(
            "files", null, "name LIKE ? ESCAPE '\\'", arrayOf(q),
            null, null, "name COLLATE NOCASE ASC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) out += row(c)
        }
        return out
    }

    @Synchronized
    fun byCategory(category: Category, limit: Int = 500): List<FileRecord> {
        val out = ArrayList<FileRecord>()
        readableDatabase.query("files", null, "category=?", arrayOf(category.name),
            null, null, "name COLLATE NOCASE ASC", limit.toString()).use { c ->
            while (c.moveToNext()) out += row(c)
        }
        return out
    }

    @Synchronized
    fun clear() { writableDatabase.delete("files", null, null) }

    private fun row(c: android.database.Cursor) = FileRecord(
        c.getString(c.getColumnIndexOrThrow("uri")),
        c.getString(c.getColumnIndexOrThrow("parentUri")),
        c.getString(c.getColumnIndexOrThrow("name")),
        c.getLong(c.getColumnIndexOrThrow("size")),
        c.getString(c.getColumnIndexOrThrow("mime")),
        c.getLong(c.getColumnIndexOrThrow("modified")),
        Category.valueOf(c.getString(c.getColumnIndexOrThrow("category")))
    )
}
