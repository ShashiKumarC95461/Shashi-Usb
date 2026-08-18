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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

    fun scan() {
        scope.launch {

            scanning = true
            error = null

            try {

                val media =
                    scanMediaStore(context)

                val usb =
                    treeUri?.let {
                        scanTree(context, it)
                    } ?: emptyList()

                val combined =
                    (media + usb)
                        .distinctBy {
                            it.uri.toString()
                        }

                files = combined
                counts = makeCounts(combined)

            } catch (e: Exception) {

                error =
                    e.message ?: "Unable to scan media"

            } finally {

                scanning = false
            }
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

                    context.contentResolver
                        .takePersistableUriPermission(
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
                        ?: "File"

                val mime =
                    context.contentResolver
                        .getType(uri)
                        ?: "/"

                viewer =
                    MediaFile(
                        uri = uri,
                        name = name,
                        mime = mime,
                        type = categoryFromMime(mime)
                    )
            }
        }

    LaunchedEffect(Unit) {

        val permissions =
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
            permissions.filter {

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

        Viewer(
            file = file,
            onBack = {
                viewer = null
            }
        )

        return
    }

    category?.let { selectedCategory ->

        val shown =
            files
                .filter {
                    selectedCategory == Category.ALL ||
                            it.type == selectedCategory
                }
                .filter {
                    it.name.contains(
                        search,
                        ignoreCase = true
                    )
                }

        CategoryBrowser(
            category = selectedCategory,
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
                        "application/*"
                    )
                )
            }
        )

        return
    }

    Scaffold(
        containerColor = Dark
    ) { padding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
        ) {

            Header(
                scanning = scanning,
                onSources = {
                    sourceScreen = true
                },
                onRefresh = {
                    if (!scanning) {
                        scan()
                    }
                }
            )

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                contentPadding =
                    PaddingValues(
                        top = 12.dp,
                        bottom = 24.dp
                    )
            ) {

                item {

                    Text(
                        text = "Internal storage",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    if (scanning) {

                        LinearProgressIndicator(
                            modifier =
                                Modifier.fillMaxWidth()
                        )

                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )
                    }

                    error?.let {

                        Text(
                            text = "Scan error: $it",
                            color =
                                Color(0xFFFF8A80),
                            fontSize = 12.sp
                        )

                        Spacer(
                            modifier =
                                Modifier.height(6.dp)
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
                        count =
                            counts.photos +
                                    counts.videos,
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
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Button(
                        onClick = {
                            if (!scanning) {
                                scan()
                            }
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                        enabled = !scanning
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.Search,
                            contentDescription = null
                        )

                        Spacer(
                            modifier =
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
                        modifier =
                            Modifier.height(8.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            sourceScreen = true
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.Usb,
                            contentDescription = null
                        )

                        Spacer(
                            modifier =
                                Modifier.size(8.dp)
                        )

                        Text(
                            "Select USB / Storage Source"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Header(
    scanning: Boolean,
    onSources: () -> Unit,
    onRefresh: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Orange)
                .padding(
                    horizontal = 14.dp,
                    vertical = 10.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = "Shashi-Usb-Media",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Fast • Smooth Gallery • Deep Scan",
                color = Color.White,
                fontSize = 12.sp
            )
        }

        IconButton(
            onClick = onSources
        ) {

            Icon(
                imageVector =
                    Icons.Default.Storage,
                contentDescription = "Sources",
                tint = Color.White
            )
        }

        IconButton(
            onClick = onRefresh,
            enabled = !scanning
        ) {

            Icon(
                imageVector =
                    Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = Color.White
            )
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
            RoundedCornerShape(10.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = color
            )
    ) {

        Row(
            modifier =
                Modifier.padding(15.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier =
                    Modifier.size(42.dp)
            )

            Spacer(
                modifier =
                    Modifier.size(14.dp)
            )

            Column {

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "$count files",
                    color = Color.LightGray,
                    fontSize = 13.sp
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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFF303030))
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Orange)
                    .padding(8.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBack
            ) {

                Icon(
                    imageVector =
                        Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Column {

                Text(
                    text = "Shashi-Usb-Media",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Select Source",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

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
            title = "Cloud / other files",
            subtitle = "Open files from another storage provider",
            icon = Icons.Default.Folder,
            onClick = onCloud
        )
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
            modifier =
                Modifier.size(46.dp)
        )

        Spacer(
            modifier =
                Modifier.size(18.dp)
        )

        Column {

            Text(
                text = title,
                color = Color.White,
                fontSize = 19.sp
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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Dark)
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Orange)
                    .padding(6.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBack
            ) {

                Icon(
                    imageVector =
                        Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text =
                    "${category.label} (${files.size})",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier.weight(1f)
            )

            IconButton(
                onClick = onToggle
            ) {

                Icon(
                    imageVector =
                        if (grid)
                            Icons.Default.List
                        else
                            Icons.Default.GridView,
                    contentDescription =
                        "Change view",
                    tint = Color.White
                )
            }
        }

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
                modifier =
                    Modifier.fillMaxWidth()
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
                    GridCells.Adaptive(
                        minSize = 112.dp
                    ),
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
                ) { file ->

                    GalleryTile(
                        file = file,
                        onOpen = {
                            onOpen(file)
                        }
                    )
                }
            }

        } else {

            LazyColumn(
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
                ) { file ->

                    FileRow(
                        file = file,
                        onOpen = {
                            onOpen(file)
                        }
                    )
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

                when (file.type) {

                    Category.PHOTOS -> {

                        AsyncImage(
                            model = file.uri,
                            contentDescription =
                                file.name,
                            modifier =
                                Modifier.fillMaxSize(),
                            contentScale =
                                ContentScale.Crop
                        )
                    }

                    Category.VIDEOS -> {

                        Icon(
                            imageVector =
                                Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier =
                                Modifier.size(48.dp)
                        )
                    }

                    Category.MUSIC -> {

                        Icon(
                            imageVector =
                                Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier =
                                Modifier.size(48.dp)
                        )
                    }

                    else -> {

                        Icon(
                            imageVector =
                                Icons.Default.Description,
                            contentDescription = null,
                            tint = Color.White,
                            modifier =
                                Modifier.size(48.dp)
                        )
                    }
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
            modifier =
                Modifier.size(12.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
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
                    if (file.size > 0)
                        formatSize(file.size)
                    else
                        file.mime,
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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(6.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBack
            ) {

                Icon(
                    imageVector =
                        Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = file.name,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
                modifier =
                    Modifier.weight(1f)
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize(),
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
                text =
                    "${(scale * 100).toInt()}%",
                color = Color.White,
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter
                        )
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

            ExoPlayer
                .Builder(context)
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
                controllerShowTimeoutMs = 3000
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

    val context = LocalContext.current

    Button(
        onClick = {

            val intent =
                Intent(Intent.ACTION_VIEW).apply {

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

        Text("Open file")
    }
}

suspend fun scanMediaStore(
    context: Context
): List<MediaFile> =
    withContext(Dispatchers.IO) {

        val result =
            ArrayList<MediaFile>()

        fun query(
            uri: Uri,
            category: Category
        ) {

            try {

                val projection =
                    arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.MIME_TYPE,
                        MediaStore.MediaColumns.SIZE
                    )

                context.contentResolver
                    .query(
                        uri,
                        projection,
                        null,
                        null,
                        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                    )
                    ?.use { cursor ->

                        val idIndex =
                            cursor.getColumnIndex(
                                MediaStore.MediaColumns._ID
                            )

                        val nameIndex =
                            cursor.getColumnIndex(
                                MediaStore.MediaColumns.DISPLAY_NAME
                            )

                        val mimeIndex =
                            cursor.getColumnIndex(
                                MediaStore.MediaColumns.MIME_TYPE
                            )

                        val sizeIndex =
                            cursor.getColumnIndex(
                                MediaStore.MediaColumns.SIZE
                            )

                        if (idIndex < 0 ||
                            nameIndex < 0
                        ) {
                            return@use
                        }

                        while (
                            cursor.moveToNext()
                        ) {

                            val id =
                                cursor.getLong(
                                    idIndex
                                )

                            val name =
                                cursor.getString(
                                    nameIndex
                                ) ?: "Unknown"

                            val mime =
                                if (mimeIndex >= 0)
                                    cursor.getString(
                                        mimeIndex
                                    ) ?: ""
                                else
                                    ""

                            val size =
                                if (sizeIndex >= 0)
                                    cursor.getLong(
                                        sizeIndex
                                    )
                                else
                                    0L

                            result +=
                                MediaFile(
                                    uri =
                                        Uri.withAppendedPath(
                                            uri,
                                            id.toString()
                                        ),
                                    name = name,
                                    mime = mime,
                                    type = category,
                                    size = size
                                )
                        }
                    }

            } catch (_: Exception) {
            }
        }

        query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            Category.PHOTOS
        )

        query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            Category.VIDEOS
        )

        query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            Category.MUSIC
        )

        result
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

        val result =
            ArrayList<MediaFile>()

        fun walk(
            directory: DocumentFile
        ) {

            val children =
                try {
                    directory.listFiles()
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

                        result +=
                            MediaFile(
                                uri = file.uri,
                                name =
                                    file.name
                                        ?: "Unknown",
                                mime = mime,
                                type =
                                    categoryFromMime(
                                        mime
                                    ),
                                size = file.length()
                            )
                    }

                } catch (_: Exception) {
                }
            }
        }

        walk(root)

        result
    }

fun categoryFromMime(
    mime: String
): Category {

    val value =
        mime.lowercase(Locale.US)

    return when {

        value.startsWith("image/") ->
            Category.PHOTOS

        value.startsWith("video/") ->
            Category.VIDEOS

        value.startsWith("audio/") ->
            Category.MUSIC

        value == "application/pdf" ||
                value.startsWith("text/") ||
                value.contains("word") ||
                value.contains("excel") ||
                value.contains("spreadsheet") ||
                value.contains("powerpoint") ||
                value.contains("presentation") ->
            Category.DOCS

        else ->
            Category.ALL
    }
}

fun makeCounts(
    files: List<MediaFile>
): Counts {

    return Counts(
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
        all = files.size
    )
}

fun formatSize(
    bytes: Long
): String {

    if (bytes <= 0L) {
        return "0 B"
    }

    val units =
        arrayOf(
            "B",
            "KB",
            "MB",
            "GB",
            "TB"
        )

    var value =
        bytes.toDouble()

    var index = 0

    while (
        value >= 1024 &&
        index < units.lastIndex
    ) {

        value /= 1024
        index++
    }

    return if (index == 0) {

        "${value.toInt()} ${units[index]}"

    } else {

        String.format(
            Locale.US,
            "%.1f %s",
            value,
            units[index]
        )
    }
}
