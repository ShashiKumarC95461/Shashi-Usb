@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
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
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult

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

            treeUri?.let { uri ->
                merged = merged + scanTree(context, uri)
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
                    DocumentFile
                        .fromSingleUri(context, uri)
                        ?.name
                        ?: "Cloud file"

                val mime =
                    context.contentResolver.getType(uri)
                        ?: "/"

                viewer = MediaFile(
                    uri = uri,
                    name = name,
                    mime = mime,
                    type = categoryFromMime(mime)
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


        val missing =
            needed.filter {

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


    viewer?.let { file ->

        Viewer(file) {
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
            category = cat,
            files = shown,
            grid = grid,
            search = search,
            scanning = scanning,
            onSearch = {
                search = it
            },
            onToggle = {
                grid = !grid
            },
            onBack = {
                category = null
                search = ""
            },
            onOpen = {
                viewer = it
            }
        )

        return
    }


    if (sourceScreen) {

        SourceScreen(
            onBack = {
                sourceScreen = false
            },

            onInternal = {

                sourceScreen = false
                treeUri = null

                scan()
            },

            onUsb = {
                treeLauncher.launch(null)
            },

            onCloud = {

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
                            text = "Shashi-Usb-Media",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Fast • Smooth Gallery • Deep Scan",
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
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Sources",
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
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
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

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),

            contentPadding =
                PaddingValues(
                    vertical = 10.dp
                )
        ) {


            item {

                Text(
                    text = "Internal storage",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )


                Spacer(
                    modifier = Modifier.height(8.dp)
                )


                if (scanning) {

                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                }


                error?.let {

                    Text(
                        text = "Scan error: $it",
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp
                    )
                }
            }


            item {

                HomeCard(
                    title = "Photos",
                    count = counts.photos,
                    color = Color(0xFF15155A),
                    icon = Icons.Default.Image
                ) {

                    category = Category.PHOTOS
                }
            }


            item {

                HomeCard(
                    title = "Photos and Videos",
                    count = counts.photos + counts.videos,
                    color = Color(0xFF401044),
                    icon = Icons.Default.GridView
                ) {

                    category = Category.ALL
                }
            }


            item {

                HomeCard(
                    title = "Videos",
                    count = counts.videos,
                    color = Color(0xFF4A1111),
                    icon = Icons.Default.PlayArrow
                ) {

                    category = Category.VIDEOS
                }
            }


            item {

                HomeCard(
                    title = "Music",
                    count = counts.music,
                    color = Color(0xFF0E3A0E),
                    icon = Icons.Default.MusicNote
                ) {

                    category = Category.MUSIC
                }
            }


            item {

                HomeCard(
                    title = "Docs",
                    count = counts.docs,
                    color = Color(0xFF333333),
                    icon = Icons.Default.Description
                ) {

                    category = Category.DOCS
                }
            }


            item {

                HomeCard(
                    title = "File Manager",
                    count = counts.all,
                    color = Color(0xFF333311),
                    icon = Icons.Default.Folder
                ) {

                    category = Category.ALL
                }
            }


            item {

                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                Button(
                    onClick = {
                        scan()
                    },

                    enabled = !scanning,

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )


                    Spacer(
                        modifier = Modifier.size(8.dp)
                    )


                    Text(
                        text =
                            if (scanning)
                                "Scanning..."
                            else
                                "Deep Scan / Refresh"
                    )
                }
            }


            item {

                Spacer(
                    modifier = Modifier.height(8.dp)
                )


                OutlinedButton(
                    onClick = {
                        sourceScreen = true
                    },

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null
                    )


                    Spacer(
                        modifier = Modifier.size(8.dp)
                    )


                    Text(
                        text = "Select Source / USB / Cloud"
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

        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable {
                    onClick()
                },

        shape =
            RoundedCornerShape(8.dp),

        colors =
            CardDefaults.cardColors(
                containerColor = color
            )
    ) {

        Row(

            modifier =
                Modifier.padding(14.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )


            Spacer(
                modifier = Modifier.size(14.dp)
            )


            Column {

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp
                )


                Text(
                    text = count.toString(),
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

        containerColor =
            Color(0xFF303030),

        topBar = {

            TopAppBar(

                title = {

                    Column {

                        Text(
                            text = "Shashi-Usb-Media",
                            color = Color.White
                        )

                        Text(
                            text = "Select Source",
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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
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

        Column(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
        ) {

            SourceRow(
                title = "Internal storage",
                subtitle = "/storage/emulated/0",
                icon = Icons.Default.Storage,
                onClick = onInternal
            )


            SourceRow(
                title = "USB drive / folder",
                subtitle = "Choose an accessible USB or storage folder",
                icon = Icons.Default.Usb,
                onClick = onUsb
            )


            SourceRow(
                title = "Cloud storage",
                subtitle = "Google Drive, OneDrive, Dropbox and other providers",
                icon = Icons.Default.Cloud,
                onClick = onCloud
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

        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                }
                .padding(18.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(50.dp)
        )


        Spacer(
            modifier = Modifier.size(18.dp)
        )


        Column {

            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp
            )


            Text(
                text = subtitle,
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

    val listState =
        rememberLazyListState()

    val gridState =
        rememberLazyGridState()


    Scaffold(

        containerColor = Dark,

        topBar = {

            TopAppBar(

                title = {

                    Text(
                        text = "${category.label} (${files.size})",
                        color = Color.White
                    )
                },


                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },


                actions = {

                    IconButton(
                        onClick = onToggle
                    ) {

                        Icon(
                            imageVector =
                                if (grid)
                                    Icons.Default.List
                                else
                                    Icons.Default.GridView,

                            contentDescription = "Toggle view",

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


        Column(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                    modifier = Modifier.fillMaxWidth()
                )
            }


            if (files.isEmpty()) {

                Box(

                    modifier =
                        Modifier.fillMaxSize(),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text = "No files found",
                        color = Color.White
                    )
                }

            } else if (grid) {

                LazyVerticalGrid(

                    columns =
                        GridCells.Adaptive(112.dp),

                    state = gridState,

                    modifier =
                        Modifier.fillMaxSize(),

                    contentPadding =
                        PaddingValues(5.dp)
                ) {

                    items(
                        items = files,
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

                    modifier =
                        Modifier.fillMaxSize(),

                    contentPadding =
                        PaddingValues(bottom = 20.dp)
                ) {

                    items(
                        items = files,
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

        modifier =
            Modifier
                .padding(3.dp)
                .clickable {
                    onOpen()
                },

        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color(0xFF242424)
            )
    ) {

        Column {

            Box(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp),

                contentAlignment =
                    Alignment.Center
            ) {

                if (file.type == Category.PHOTOS) {

                    AsyncImage(

                        model = file.uri,

                        contentDescription = file.name,

                        modifier =
                            Modifier.fillMaxSize(),

                        contentScale =
                            ContentScale.Crop
                    )

                } else if (file.type == Category.VIDEOS) {

                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )


                    Text(
                        text = "VIDEO",
                        color = Color.LightGray,
                        fontSize = 10.sp,

                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(4.dp)
                    )

                } else {

                    Icon(

                        imageVector =
                            if (file.type == Category.MUSIC)
                                Icons.Default.MusicNote
                            else
                                Icons.Default.Description,

                        contentDescription = null,

                        tint = Color.White,

                        modifier =
                            Modifier.size(48.dp)
                    )
                }
            }


            Text(

                text = file.name,

                color = Color.White,

                fontSize = 11.sp,

                maxLines = 2,

                overflow =
                    TextOverflow.Ellipsis,

                modifier =
                    Modifier.padding(5.dp)
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

        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onOpen()
                }
                .padding(
                    horizontal = 14.dp,
                    vertical = 11.dp
                ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(

            imageVector =
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

            contentDescription = null,

            tint = Color.White,

            modifier =
                Modifier.size(38.dp)
        )


        Spacer(
            modifier = Modifier.size(12.dp)
        )


        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(

                text = file.name,

                color = Color.White,

                fontSize = 15.sp,

                maxLines = 2,

                overflow =
                    TextOverflow.Ellipsis
            )


            Text(

                text =
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

                        text = file.name,

                        color = Color.White,

                        maxLines = 1,

                        overflow =
                            TextOverflow.Ellipsis
                    )
                },


                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
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

    ) { padding ->


        Box(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),

            contentAlignment =
                Alignment.Center
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
fun SmoothPhotoViewer(
    uri: Uri
) {

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

        modifier =
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
                                .coerceIn(
                                    1f,
                                    5f
                                )

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

        contentAlignment =
            Alignment.Center
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

            contentScale =
                ContentScale.Fit
        )


        if (scale > 1.01f) {

            Text(

                text = "${(scale * 100).toInt()}%",

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
fun SmoothVideoViewer(
    uri: Uri
) {

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

        factory = { viewContext ->

            PlayerView(viewContext).apply {

                this.player = player

                useController = true
            }
        },

        modifier =
            Modifier.fillMaxSize()
    )
}


@Composable
fun OpenExternal(
    file: MediaFile
) {

    val context =
        LocalContext.current


    Button(

        onClick = {

            val intent =
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

                context.startActivity(intent)

            } catch (_: Exception) {
            }
        }

    ) {

        Text(
            text = "Open file"
        )
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
                    ?.use { cursor ->


                        val id =
                            cursor.getColumnIndex(
                                MediaStore.MediaColumns._ID
                            )


                        val name =
                            cursor.getColumnIndex(
                                MediaStore.MediaColumns.DISPLAY_NAME
                            )


                        val mime =
                            cursor.getColumnIndex(
                                MediaStore.MediaColumns.MIME_TYPE
                            )


                        val size =
                            cursor.getColumnIndex(
                                MediaStore.MediaColumns.SIZE
                            )


                        if (id < 0 || name < 0) {
                            return@use
                        }


                        while (cursor.moveToNext()) {

                            out += MediaFile(

                                uri =
                                    Uri.withAppendedPath(
                                        uri,
                                        cursor
                                            .getLong(id)
                                            .toString()
                                    ),

                                name =
                                    cursor
                                        .getString(name)
                                        ?: "Unknown",

                                mime =
                                    if (mime >= 0)
                                        cursor.getString(mime)
                                            ?: ""
                                    else
                                        "",

                                type = type,

                                size =
                                    if (size >= 0)
                                        cursor.getLong(size)
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
            )
                ?: return@withContext emptyList()


        val out =
            ArrayList<MediaFile>()


        fun walk(
            dir: DocumentFile
        ) {

            val children =

                try {
                    dir.listFiles()
                } catch (_: Exception) {
                    emptyArray()
                }


            for (file in children) {

                try {

                    if (file.isDirectory) {

                        walk(file)

                    } else if (file.isFile) {

                        val mime =
                            file.type ?: "/"


                        out += MediaFile(

                            uri = file.uri,

                            name =
                                file.name
                                    ?: "Unknown",

                            mime = mime,

                            type =
                                categoryFromMime(
                                    mime
                                ),

                            size =
                                file.length()
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
): Counts =

    Counts(

        photos =
            files.count {
                it.type == Category.PHOTOS
            },

        videos =
            files.count {
                it.type == Category.VIDEOS
            },

        music =
            files.count {
                it.type == Category.MUSIC
            },

        docs =
            files.count {
                it.type == Category.DOCS
            },

        all =
            files.size
    )
