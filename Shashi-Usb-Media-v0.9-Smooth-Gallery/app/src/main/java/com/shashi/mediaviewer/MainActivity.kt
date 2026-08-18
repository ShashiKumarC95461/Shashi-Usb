package com.shashi.mediaviewer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

import coil.compose.AsyncImage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.Locale

private val Orange = Color(0xFFFF5722)
private val Dark = Color(0xFF111111)

data class MediaFile(
    val uri: Uri,
    val name: String,
    val mime: String,
    val type: Category,
    val size: Long = 0L
)

enum class Category(val label: String) {
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    MUSIC("Music"),
    DOCS("Docs"),
    ALL("File Manager")
}

data class Counts(
    val photos: Int = 0,
    val videos: Int = 0,
    val music: Int = 0,
    val docs: Int = 0,
    val all: Int = 0
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShashiUsbMediaApp()
        }
    }
}

@Composable
fun ShashiUsbMediaApp() {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var files by remember {
        mutableStateOf<List<MediaFile>>(emptyList())
    }

    var counts by remember {
        mutableStateOf(Counts())
    }

    var scanning by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var category by remember {
        mutableStateOf<Category?>(null)
    }

    var viewer by remember {
        mutableStateOf<MediaFile?>(null)
    }

    var sourceScreen by remember {
        mutableStateOf(false)
    }

    var search by remember {
        mutableStateOf("")
    }

    var grid by remember {
        mutableStateOf(true)
    }

    var treeUri by remember {
        mutableStateOf<Uri?>(null)
    }

    fun scan() = scope.launch {

        scanning = true
        error = null

        try {

            val base = scanMediaStore(context)

            var merged = base

            treeUri?.let {
                merged += scanTree(context, it)
            }

            files = merged.distinctBy {
                it.uri.toString()
            }

            counts = makeCounts(files)

        } catch (t: Throwable) {

            error = t.message ?: "Scan failed"

        } finally {

            scanning = false
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            scan()
        }

    val treeLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->

            if (uri != null) {

                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }

                treeUri = uri
                sourceScreen = false
                scan()
            }
        }

    val cloudLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                val name =
                    DocumentFile.fromSingleUri(context, uri)?.name
                        ?: "Cloud file"

                val mime =
                    context.contentResolver.getType(uri)
                        ?: "/"

                viewer = MediaFile(
                    uri,
                    name,
                    mime,
                    categoryFromMime(mime)
                )
            }
        }

    LaunchedEffect(Unit) {

        val needed =
            if (Build.VERSION.SDK_INT >= 33) {

                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )

            } else {

                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(
                context,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {

            permissionLauncher.launch(
                missing.toTypedArray()
            )

        } else {

            scan()
        }
    }

    viewer?.let {

        Viewer(it) {
            viewer = null
        }

        return
    }

    category?.let { cat ->

        val shown =
            files
                .asSequence()
                .filter {
                    cat == Category.ALL || it.type == cat
                }
                .filter {
                    it.name.contains(search, true)
                }
                .toList()

        CategoryBrowser(
            cat,
            shown,
            grid,
            search,
            scanning,
            { search = it },
            { grid = !grid },
            {
                category = null
                search = ""
            },
            {
                viewer = it
            }
        )

        return
    }

    if (sourceScreen) {

        SourceScreen(
            { sourceScreen = false },
            {
                sourceScreen = false
                treeUri = null
                scan()
            },
            {
                treeLauncher.launch(null)
            },
            {
                cloudLauncher.launch(
                    arrayOf(
                        "image/*",
                        "video/*",
                        "audio/*",
                        "application/pdf",
                        "text/*",
                        "application/zip",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.*"
                    )
                )
            }
        )

        return
    }

    Scaffold(

        containerColor = Dark,

        topBar = {

            TopAppBar(

                title = {

                    Column {

                        Text(
                            "Shashi-Usb-Media",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Fast • Smooth Gallery • Deep Scan",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                },

                actions = {

                    IconButton(
                        onClick = {
                            sourceScreen = true
                        }
                    ) {

                        Icon(
                            Icons.Default.Storage,
                            "Sources",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            scan()
                        },
                        enabled = !scanning
                    ) {

                        Icon(
                            Icons.Default.Refresh,
                            "Refresh",
                            tint = Color.White
                        )
                    }
                },

                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Orange
                    )
            )
        }

    ) { padding ->

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),

            contentPadding =
                PaddingValues(vertical = 10.dp)
        ) {

            item {

                Text(
                    "Internal storage",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                if (scanning) {

                    LinearProgressIndicator(
                        Modifier.fillMaxWidth()
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )
                }

                error?.let {

                    Text(
                        "Scan error: $it",
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp
                    )
                }
            }

            item {

                HomeCard(
                    "Photos",
                    counts.photos,
                    Color(0xFF15155A),
                    Icons.Default.Image
                ) {
                    category = Category.PHOTOS
                }
            }

            item {

                HomeCard(
                    "Photos and Videos",
                    counts.photos + counts.videos,
                    Color(0xFF401044),
                    Icons.Default.GridView
                ) {
                    category = Category.ALL
                }
            }

            item {

                HomeCard(
                    "Videos",
                    counts.videos,
                    Color(0xFF4A1111),
                    Icons.Default.PlayArrow
                ) {
                    category = Category.VIDEOS
                }
            }

            item {

                HomeCard(
                    "Music",
                    counts.music,
                    Color(0xFF0E3A0E),
                    Icons.Default.MusicNote
                ) {
                    category = Category.MUSIC
                }
            }

            item {

                HomeCard(
                    "Docs",
                    counts.docs,
                    Color(0xFF333333),
                    Icons.Default.Description
                ) {
                    category = Category.DOCS
                }
            }

            item {

                HomeCard(
                    "File Manager",
                    counts.all,
                    Color(0xFF333311),
                    Icons.Default.Folder
                ) {
                    category = Category.ALL
                }
            }

            item {

                Spacer(
                    Modifier.height(10.dp)
                )

                Button(
                    onClick = {
                        scan()
                    },
                    enabled = !scanning,
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Icon(
                        Icons.Default.Search,
                        null
                    )

                    Spacer(
                        Modifier.size(8.dp)
                    )

                    Text(
                        if (scanning)
                            "Scanning..."
                        else
                            "Deep Scan / Refresh"
                    )
                }
            }

            item {

                Spacer(
                    Modifier.height(8.dp)
                )

                OutlinedButton(
                    onClick = {
                        sourceScreen = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Icon(
                        Icons.Default.Usb,
                        null
                    )

                    Spacer(
                        Modifier.size(8.dp)
                    )

                    Text(
                        "Select Source / USB / Cloud"
                    )
                }
            }
        }
    }
}

@Composable
fun HomeCard(
    title: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {

    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                onClick()
            },

        shape = RoundedCornerShape(8.dp),

        colors =
            CardDefaults.cardColors(
                containerColor = color
            )
    ) {

        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                icon,
                null,
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )

            Spacer(
                Modifier.size(14.dp)
            )

            Column {

                Text(
                    title,
                    color = Color.White,
                    fontSize = 20.sp
                )

                Text(
                    count.toString(),
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SourceScreen(
    onBack: () -> Unit,
    onInternal: () -> Unit,
    onUsb: () -> Unit,
    onCloud: () -> Unit
) {

    Scaffold(

        containerColor = Color(0xFF303030),

        topBar = {

            TopAppBar(

                title = {

                    Column {

                        Text(
                            "Shashi-Usb-Media",
                            color = Color.White
                        )

                        Text(
                            "Select Source",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            Icons.Default.ArrowBack,
                            "Back",
                            tint = Color.White
                        )
                    }
                },

                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Orange
                    )
            )
        }

    ) { p ->

        Column(
            Modifier
                .fillMaxSize()
                .padding(p)
        ) {

            SourceRow(
                "Internal storage",
                "/storage/emulated/0",
                Icons.Default.Storage,
                onInternal
            )

            SourceRow(
                "USB drive / folder",
                "Choose an accessible USB or storage folder",
                Icons.Default.Usb,
                onUsb
            )

            SourceRow(
                "Cloud storage",
                "Google Drive, OneDrive, Dropbox and other providers",
                Icons.Default.Cloud,
                onCloud
            )
        }
    }
}

@Composable
fun SourceRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {

    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(18.dp),

        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            icon,
            null,
            tint = Color.White,
            modifier = Modifier.size(50.dp)
        )

        Spacer(
            Modifier.size(18.dp)
        )

        Column {

            Text(
                title,
                color = Color.White,
                fontSize = 20.sp
            )

            Text(
                subtitle,
                color = Color.LightGray,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun CategoryBrowser(
    category: Category,
    files: List<MediaFile>,
    grid: Boolean,
    search: String,
    scanning: Boolean,
    onSearch: (String) -> Unit,
    onToggle: () -> Unit,
    onBack: () -> Unit,
    onOpen: (MediaFile) -> Unit
) {

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    Scaffold(

        containerColor = Dark,

        topBar = {

            TopAppBar(

                title = {

                    Text(
                        "${category.label} (${files.size})",
                        color = Color.White
                    )
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            Icons.Default.ArrowBack,
                            "Back",
                            tint = Color.White
                        )
                    }
                },

                actions = {

                    IconButton(
                        onClick = onToggle
                    ) {

                        Icon(
                            if (grid)
                                Icons.Default.List
                            else
                                Icons.Default.GridView,
                            "Toggle view",
                            tint = Color.White
                        )
                    }
                },

                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Orange
                    )
            )
        }

    ) { p ->

        Column(
            Modifier
                .fillMaxSize()
                .padding(p)
        ) {

            OutlinedTextField(
                value = search,
                onValueChange = onSearch,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                singleLine = true,
                label = {
                    Text("Search files")
                }
            )

            if (scanning) {

                LinearProgressIndicator(
                    Modifier.fillMaxWidth()
                )
            }

            if (files.isEmpty()) {

                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        "No files found",
                        color = Color.White
                    )
                }

            } else if (grid) {

                LazyVerticalGrid(
                    GridCells.Adaptive(112.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(5.dp)
                ) {

                    items(
                        files,
                        key = {
                            it.uri.toString()
                        }
                    ) {

                        GalleryTile(it) {
                            onOpen(it)
                        }
                    }
                }

            } else {

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(bottom = 20.dp)
                ) {

                    items(
                        files,
                        key = {
                            it.uri.toString()
                        }
                    ) {

                        FileRow(it) {
                            onOpen(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryTile(
    file: MediaFile,
    onOpen: () -> Unit
) {

    Card(
        Modifier
            .padding(3.dp)
            .clickable {
                onOpen()
            },

        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF242424)
            )
    ) {

        Column {

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),

                contentAlignment = Alignment.Center
            ) {

                if (file.type == Category.PHOTOS) {

                    AsyncImage(
                        model = file.uri,
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                } else if (file.type == Category.VIDEOS) {

                    Icon(
                        Icons.Default.PlayCircle,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )

                    Text(
                        "VIDEO",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(4.dp)
                    )

                } else {

                    Icon(
                        if (file.type == Category.MUSIC)
                            Icons.Default.MusicNote
                        else
                            Icons.Default.Description,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Text(
                file.name,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(5.dp)
            )
        }
    }
}

@Composable
fun FileRow(
    file: MediaFile,
    onOpen: () -> Unit
) {

    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                onOpen()
            }
            .padding(
                horizontal = 14.dp,
                vertical = 11.dp
            ),

        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            when (file.type) {
                Category.PHOTOS ->
                    Icons.Default.Image

                Category.VIDEOS ->
                    Icons.Default.PlayCircle

                Category.MUSIC ->
                    Icons.Default.MusicNote

                Category.DOCS ->
                    Icons.Default.Description

                Category.ALL ->
                    Icons.Default.InsertDriveFile
            },

            null,

            tint = Color.White,

            modifier = Modifier.size(38.dp)
        )

        Spacer(
            Modifier.size(12.dp)
        )

        Column(
            Modifier.weight(1f)
        ) {

            Text(
                file.name,
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                file.mime.ifBlank {
                    file.type.label
                },
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun Viewer(
    file: MediaFile,
    onBack: () -> Unit
) {

    Scaffold(

        containerColor = Color.Black,

        topBar = {

            TopAppBar(

                title = {

                    Text(
                        file.name,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            Icons.Default.ArrowBack,
                            "Back",
                            tint = Color.White
                        )
                    }
                },

                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black
                    )
            )
        }

    ) { p ->

        Box(
            Modifier
                .fillMaxSize()
                .padding(p),

            contentAlignment = Alignment.Center
        ) {

            when (file.type) {

                Category.PHOTOS ->
                    SmoothPhotoViewer(file.uri)

                Category.VIDEOS ->
                    SmoothVideoViewer(file.uri)

                else ->
                    OpenExternal(file)
            }
        }
    }
}

@Composable
fun SmoothPhotoViewer(uri: Uri) {

    var scale by remember(uri) {
        mutableStateOf(1f)
    }

    var offsetX by remember(uri) {
        mutableStateOf(0f)
    }

    var offsetY by remember(uri) {
        mutableStateOf(0f)
    }

    Box(

        Modifier
            .fillMaxSize()
            .background(Color.Black)

            .pointerInput(uri) {

                detectTransformGestures {
                        _,
                        pan,
                        zoom,
                        _ ->

                    scale =
                        (scale * zoom)
                            .coerceIn(1f, 5f)

                    offsetX += pan.x
                    offsetY += pan.y
                }
            }

            .pointerInput(uri) {

                detectTapGestures(
                    onDoubleTap = {

                        if (scale > 1.05f) {

                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f

                        } else {

                            scale = 2f
                        }
                    }
                )
            },

        contentAlignment = Alignment.Center
    ) {

        AsyncImage(

            model = uri,

            contentDescription = null,

            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },

            contentScale = ContentScale.Fit
        )

        if (scale > 1.01f) {

            Text(
                "${(scale * 100).toInt()}%",
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(18.dp)
            )
        }
    }
}

@Composable
fun SmoothVideoViewer(uri: Uri) {

    val context = LocalContext.current

    val player =
        remember(uri) {

            ExoPlayer.Builder(context)
                .build()
                .apply {

                    setMediaItem(
                        MediaItem.fromUri(uri)
                    )

                    prepare()

                    playWhenReady = true
                }
        }

    DisposableEffect(player) {

        onDispose {
            player.release()
        }
    }

    AndroidView(

        factory = {

            PlayerView(it).apply {

                this.player = player
                useController = true
            }
        },

        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun OpenExternal(file: MediaFile) {

    val context = LocalContext.current

    Button(

        onClick = {

            val i =
                Intent(
                    Intent.ACTION_VIEW
                ).apply {

                    setDataAndType(
                        file.uri,
                        file.mime.ifBlank {
                            "/"
                        }
                    )

                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

            try {

                context.startActivity(i)

            } catch (_: Exception) {
            }
        }

    ) {

        Text("Open file")
    }
}

suspend fun scanMediaStore(
    context: Context
): List<MediaFile> =
    withContext(Dispatchers.IO) {

        val out =
            ArrayList<MediaFile>(4096)

        fun query(
            uri: Uri,
            type: Category,
            projection: Array<String>
        ) {

            try {

                context.contentResolver
                    .query(
                        uri,
                        projection,
                        null,
                        null,
                        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                    )
                    ?.use { c ->

                        val id =
                            c.getColumnIndex(
                                MediaStore.MediaColumns._ID
                            )

                        val name =
                            c.getColumnIndex(
                                MediaStore.MediaColumns.DISPLAY_NAME
                            )

                        val mime =
                            c.getColumnIndex(
                                MediaStore.MediaColumns.MIME_TYPE
                            )

                        val size =
                            c.getColumnIndex(
                                MediaStore.MediaColumns.SIZE
                            )

                        if (id < 0 || name < 0) {
                            return@use
                        }

                        while (c.moveToNext()) {

                            out += MediaFile(

                                Uri.withAppendedPath(
                                    uri,
                                    c.getLong(id).toString()
                                ),

                                c.getString(name)
                                    ?: "Unknown",

                                if (mime >= 0)
                                    c.getString(mime) ?: ""
                                else
                                    "",

                                type,

                                if (size >= 0)
                                    c.getLong(size)
                                else
                                    0L
                            )
                        }
                    }

            } catch (_: Exception) {
            }
        }

        query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            Category.PHOTOS,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.SIZE
            )
        )

        query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            Category.VIDEOS,
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.SIZE
            )
        )

        query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            Category.MUSIC,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE
            )
        )

        out
    }

suspend fun scanTree(
    context: Context,
    treeUri: Uri
): List<MediaFile> =
    withContext(Dispatchers.IO) {

        val root =
            DocumentFile.fromTreeUri(
                context,
                treeUri
            ) ?: return@withContext emptyList()

        val out =
            ArrayList<MediaFile>()

        fun walk(dir: DocumentFile) {

            val children =
                try {
                    dir.listFiles()
                } catch (_: Exception) {
                    emptyArray()
                }

            for (f in children) {

                try {

                    if (f.isDirectory) {

                        walk(f)

                    } else if (f.isFile) {

                        val mime =
                            f.type ?: "/"

                        out += MediaFile(
                            f.uri,
                            f.name ?: "Unknown",
                            mime,
                            categoryFromMime(mime),
                            f.length()
                        )
                    }

                } catch (_: Exception) {
                }
            }
        }

        walk(root)

        out
    }

fun categoryFromMime(
    mime: String
): Category {

    val m =
        mime.lowercase(Locale.US)

    return when {

        m.startsWith("image/") ->
            Category.PHOTOS

        m.startsWith("video/") ->
            Category.VIDEOS

        m.startsWith("audio/") ->
            Category.MUSIC

        m == "application/pdf" ||
                m.startsWith("text/") ||
                m.contains("word") ||
                m.contains("excel") ||
                m.contains("spreadsheet") ||
                m.contains("powerpoint") ->
            Category.DOCS

        else ->
            Category.ALL
    }
}

fun makeCounts(
    files: List<MediaFile>
) =
    Counts(
        files.count {
            it.type == Category.PHOTOS
        },

        files.count {
            it.type == Category.VIDEOS
        },

        files.count {
            it.type == Category.MUSIC
        },

        files.count {
            it.type == Category.DOCS
        },

        files.size
    )
