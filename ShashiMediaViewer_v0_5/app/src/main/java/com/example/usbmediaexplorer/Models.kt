package com.example.usbmediaexplorer

enum class Category { PHOTO, VIDEO, AUDIO, DOCUMENT, ARCHIVE, OTHER }

data class FileRecord(
    val uri: String,
    val parentUri: String,
    val name: String,
    val size: Long,
    val mime: String,
    val modified: Long,
    val category: Category
)

data class ScanStats(
    val files: Long = 0,
    val folders: Long = 0,
    val bytes: Long = 0,
    val photos: Long = 0,
    val videos: Long = 0,
    val audio: Long = 0,
    val documents: Long = 0,
    val archives: Long = 0,
    val other: Long = 0,
    val inaccessible: Long = 0,
    val errors: Long = 0,
    val filesPerSecond: Long = 0
)
