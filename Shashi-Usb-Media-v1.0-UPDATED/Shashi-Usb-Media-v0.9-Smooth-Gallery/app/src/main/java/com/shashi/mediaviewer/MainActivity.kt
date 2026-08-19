package com.shashi.mediaviewer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.Locale

private val BG = Color(0xFF151515)
private val CARD = Color(0xFF363636)
private val BLUE = Color(0xFF2455A6)
private val ORANGE = Color(0xFFFF5722)
private const val PREFS = "shashi_media_v1"
private const val SSD_TREE = "ssd_tree_uri"

private enum class Room { HOME, INTERNAL, SSD, SOURCES }
private enum class Kind { PHOTO, VIDEO, MUSIC, DOC, OTHER }

private data class MediaEntry(
    val uri: Uri,
    val name: String,
    val mime: String,
    val kind: Kind,
    val size: Long,
    val external: Boolean
)

private data class Counts(
    val photos: Int = 0,
    val videos: Int = 0,
    val music: Int = 0,
    val docs: Int = 0,
    val other: Int = 0,
    val all: Int = 0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShashiMediaViewer() }
    }
}

@Composable
private fun ShashiMediaViewer() {
    val context = LocalContext.current
    val activity = context as Activity
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var room by remember { mutableStateOf(Room.HOME) }
    var category by remember { mutableStateOf<Kind?>(null) }
    var viewer by remember { mutableStateOf<MediaEntry?>(null) }
    var internal by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var ssd by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var ssdUri by remember { mutableStateOf(prefs.getString(SSD_TREE, null)?.let(Uri::parse)) }
    var search by remember { mutableStateOf("") }
    var grid by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    fun scanAll() {
        if (scanning) return
        scanning = true
        error = null
        scope.launch {
            try {
                internal = scanInternal(context)
                ssd = ssdUri?.let { scanExternalTree(context, it) }.orEmpty()
            } catch (t: Throwable) {
                error = t.message ?: "Scan failed"
            } finally {
                scanning = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { scanAll() }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            ssdUri = uri
            prefs.edit().putString(SSD_TREE, uri.toString()).apply()
            room = Room.SSD
            scanAll()
        }
    }

    BackHandler {
        when {
            viewer != null -> viewer = null
            category != null -> {
                category = null
                search = ""
            }
            room != Room.HOME -> room = Room.HOME
            else -> activity.finish()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) scanAll()
        else permissionLauncher.launch(missing.toTypedArray())
    }

    viewer?.let {
        MediaViewer(it) { viewer = null }
        return
    }

    category?.let { kind ->
        val source = when (room) {
            Room.SSD -> ssd
            Room.INTERNAL -> internal
            else -> internal + ssd
        }
        val shown = source
            .filter { it.kind == kind }
            .filter { it.name.contains(search, ignoreCase = true) }
        Browser(
            title = when (kind) {
                Kind.PHOTO -> "Photos"
                Kind.VIDEO -> "Videos"
                Kind.MUSIC -> "Music"
                Kind.DOC -> "Documents"
                Kind.OTHER -> "Other files"
            },
            files = shown,
            grid = grid,
            search = search,
            scanning = scanning,
            onSearch = { search = it },
            onToggle = { grid = !grid },
            onBack = { category = null; search = "" },
            onOpen = { viewer = it }
        )
        return
    }

    when (room) {
        Room.HOME -> Home(
            internal = internal,
            ssd = ssd,
            ssdUri = ssdUri,
            scanning = scanning,
            error = error,
            onInternal = { room = Room.INTERNAL },
            onSsd = { room = Room.SSD },
            onSources = { room = Room.SOURCES },
            onRefresh = ::scanAll,
            onCategory = { kind, targetRoom ->
                room = targetRoom
                category = kind
            }
        )
        Room.INTERNAL -> StorageRoom(
            title = "INTERNAL MEMORY",
            subtitle = "/storage/emulated/0",
            icon = Icons.Default.PhoneAndroid,
            files = internal,
            scanning = scanning,
            onBack = { room = Room.HOME },
            onRefresh = ::scanAll,
            onCategory = { category = it },
            onOpen = { viewer = it }
        )
        Room.SSD -> SsdRoom(
            context = context,
            uri = ssdUri,
            files = ssd,
            scanning = scanning,
            onBack = { room = Room.HOME },
            onChoose = { treeLauncher.launch(null) },
            onScan = ::scanAll,
            onForget = {
                ssdUri?.let {
                    try {
                        context.contentResolver.releasePersistableUriPermission(
                            it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                }
                ssdUri = null
                ssd = emptyList()
                prefs.edit().remove(SSD_TREE).apply()
            }
        )
        Room.SOURCES -> Sources(
            ssdConnected = ssdUri != null,
            onBack = { room = Room.HOME },
            onInternal = { room = Room.INTERNAL },
            onSsd = { room = Room.SSD },
            onChooseSsd = { treeLauncher.launch(null) }
        )
    }
}

@Composable
private fun Home(
    internal: List<MediaEntry>,
    ssd: List<MediaEntry>,
    ssdUri: Uri?,
    scanning: Boolean,
    error: String?,
    onInternal: () -> Unit,
    onSsd: () -> Unit,
    onSources: () -> Unit,
    onRefresh: () -> Unit,
    onCategory: (Kind, Room) -> Unit
) {
    val ic = count(internal)
    val ec = count(ssd)
    Scaffold(containerColor = BG) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Header(onSources, onRefresh, scanning)
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(12.dp)
            ) {
                item { Section("STORAGE") }
                item { StorageCard("Root / Device", "Android protected area", Icons.Default.Lock, ic.all, onInternal) }
                item { StorageCard("Internal Memory", "/storage/emulated/0", Icons.Default.PhoneAndroid, ic.all, onInternal) }
                item {
                    StorageCard(
                        "External SSD / USB",
                        if (ssdUri == null) "Not connected — tap to choose" else "Connected external storage",
                        Icons.Default.Usb,
                        ec.all,
                        onSsd
                    )
                }
                item { Spacer(Modifier.height(20.dp)); Section("MEDIA COLLECTIONS") }
                item { MediaCard("Photos", "Internal + external storage", Icons.Default.Image, ic.photos + ec.photos) { onCategory(Kind.PHOTO, Room.HOME) } }
                item { MediaCard("Videos", "Real video-frame thumbnails", Icons.Default.PlayCircle, ic.videos + ec.videos) { onCategory(Kind.VIDEO, Room.HOME) } }
                item { MediaCard("Music", "All audio files", Icons.Default.MusicNote, ic.music + ec.music) { onCategory(Kind.MUSIC, Room.HOME) } }
                item { MediaCard("Documents", "PDF, Office and text", Icons.Default.Description, ic.docs + ec.docs) { onCategory(Kind.DOC, Room.HOME) } }
                item { MediaCard("Other Files", "Other detected files", Icons.Default.InsertDriveFile, ic.other + ec.other) { onCategory(Kind.OTHER, Room.HOME) } }
                item { MediaCard("All Files", "Internal + SSD / USB", Icons.Default.Folder, ic.all + ec.all) { onCategory(Kind.OTHER, Room.HOME) } }
                item {
                    Spacer(Modifier.height(12.dp))
                    Button(onRefresh, Modifier.fillMaxWidth(), enabled = !scanning) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (scanning) "Scanning..." else "Deep Scan / Refresh")
                    }
                }
                if (error != null) item { Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
            }
        }
    }
}

@Composable private fun Section(text: String) {
    Text(text, color = Color(0xFFBBC2E0), fontSize = 16.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(10.dp))
}

@Composable private fun Header(onSources: () -> Unit, onRefresh: () -> Unit, scanning: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(ORANGE).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Shashi-Usb-Media", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Fast • Smooth • Internal + SSD / USB", color = Color.White, fontSize = 12.sp)
        }
        IconButton(onSources) { Icon(Icons.Default.Storage, "Storage sources", tint = Color.White) }
        IconButton(onRefresh, enabled = !scanning) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) }
    }
}

@Composable private fun StorageCard(
    title: String, subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int, onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CARD),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(58.dp).background(BLUE, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.LightGray, fontSize = 13.sp)
                Text("$count files", color = Color(0xFFBDBDBD), fontSize = 11.sp)
            }
            if (title.contains("SSD")) Icon(Icons.Default.MoreVert, null, tint = Color.White)
        }
    }
}

@Composable private fun MediaCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, onClick: () -> Unit) {
    StorageCard(title, subtitle, icon, count, onClick)
}

@Composable private fun StorageRoom(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    files: List<MediaEntry>,
    scanning: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCategory: (Kind) -> Unit,
    onOpen: (MediaEntry) -> Unit
) {
    StorageRoomBase(title, subtitle, icon, files, scanning, onBack, onRefresh, onCategory, onOpen)
}

@Composable private fun SsdRoom(
    context: Context,
    uri: Uri?,
    files: List<MediaEntry>,
    scanning: Boolean,
    onBack: () -> Unit,
    onChoose: () -> Unit,
    onScan: () -> Unit,
    onForget: () -> Unit
) {
    val name = uri?.let { DocumentFile.fromTreeUri(context, it)?.name } ?: "No SSD / USB selected"
    Column(Modifier.fillMaxSize().background(BG)) {
        TopBar("SSD / USB STORAGE", onBack)
        LazyColumn(Modifier.fillMaxSize().padding(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CARD), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Icon(Icons.Default.Usb, null, tint = Color.White, modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (uri == null) "Connect your SSD/USB and choose it below."
                            else "${files.size} files scanned on this external storage",
                            color = Color.LightGray, fontSize = 13.sp
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)); Button(onChoose, Modifier.fillMaxWidth()) { Icon(Icons.Default.Usb, null); Spacer(Modifier.size(8.dp)); Text("Choose SSD / USB") } }
            item { Spacer(Modifier.height(8.dp)); OutlinedButton(onScan, Modifier.fillMaxWidth(), enabled = uri != null && !scanning) { Icon(Icons.Default.Search, null); Spacer(Modifier.size(8.dp)); Text(if (scanning) "Scanning SSD..." else "Scan SSD") } }
            item { if (scanning) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) } }
            if (uri != null) {
                item { Spacer(Modifier.height(8.dp)); TextButton(onForget, Modifier.fillMaxWidth()) { Text("Forget this SSD / USB") } }
                item { Spacer(Modifier.height(14.dp)); Text("SSD MEDIA", color = Color(0xFFBBC2E0), fontSize = 16.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.height(8.dp)) }
                item { MediaCard("Photos", "Photos stored on SSD", Icons.Default.Image, files.count { it.kind == Kind.PHOTO }) { } }
                item { MediaCard("Videos", "Video thumbnails from SSD", Icons.Default.PlayCircle, files.count { it.kind == Kind.VIDEO }) { } }
                item { MediaCard("Music", "Music stored on SSD", Icons.Default.MusicNote, files.count { it.kind == Kind.MUSIC }) { } }
                item { MediaCard("Documents", "Documents stored on SSD", Icons.Default.Description, files.count { it.kind == Kind.DOC }) { } }
            }
        }
    }
}

@Composable private fun StorageRoomBase(
    title: String, subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    files: List<MediaEntry>,
    scanning: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCategory: (Kind) -> Unit,
    onOpen: (MediaEntry) -> Unit
) {
    val c = count(files)
    Column(Modifier.fillMaxSize().background(BG)) {
        TopBar(title, onBack)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
            item { StorageCard(title, subtitle, icon, c.all, {}) }
            item { Spacer(Modifier.height(16.dp)); Section("MEDIA") }
            item { MediaCard("Photos", "Images", Icons.Default.Image, c.photos) { onCategory(Kind.PHOTO) } }
            item { MediaCard("Videos", "Real thumbnails", Icons.Default.PlayCircle, c.videos) { onCategory(Kind.VIDEO) } }
            item { MediaCard("Music", "Audio", Icons.Default.MusicNote, c.music) { onCategory(Kind.MUSIC) } }
            item { MediaCard("Documents", "PDF / Office / text", Icons.Default.Description, c.docs) { onCategory(Kind.DOC) } }
            item { MediaCard("Other Files", "Unclassified", Icons.Default.InsertDriveFile, c.other) { onCategory(Kind.OTHER) } }
            item { Spacer(Modifier.height(12.dp)); Button(onRefresh, Modifier.fillMaxWidth(), enabled = !scanning) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.size(8.dp)); Text(if(scanning) "Scanning..." else "Refresh this storage") } }
        }
    }
}

@Composable private fun Sources(ssdConnected: Boolean, onBack:()->Unit, onInternal:()->Unit, onSsd:()->Unit, onChooseSsd:()->Unit) {
    Column(Modifier.fillMaxSize().background(BG)) {
        TopBar("STORAGE SOURCES", onBack)
        SourceLine("Internal Memory", "/storage/emulated/0", Icons.Default.PhoneAndroid, onInternal)
        SourceLine("External SSD / USB", if(ssdConnected) "Connected — open SSD room" else "Not connected", Icons.Default.Usb, onSsd)
        SourceLine("Choose SSD / USB", "Grant persistent access to the external drive", Icons.Default.AddToDrive, onChooseSsd)
    }
}

@Composable private fun SourceLine(title:String, subtitle:String, icon:androidx.compose.ui.graphics.vector.ImageVector, onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(18.dp), verticalAlignment=Alignment.CenterVertically) {
        Icon(icon, null, tint=Color.White, modifier=Modifier.size(44.dp))
        Spacer(Modifier.size(16.dp))
        Column { Text(title, color=Color.White, fontSize=19.sp); Text(subtitle, color=Color.LightGray, fontSize=13.sp) }
    }
}

@Composable private fun TopBar(title:String, onBack:()->Unit) {
    Row(Modifier.fillMaxWidth().background(ORANGE).padding(5.dp), verticalAlignment=Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back", tint=Color.White) }
        Text(title, color=Color.White, fontSize=19.sp, fontWeight=FontWeight.Bold)
    }
}

@Composable private fun Browser(title:String, files:List<MediaEntry>, grid:Boolean, search:String, scanning:Boolean, onSearch:(String)->Unit, onToggle:()->Unit, onBack:()->Unit, onOpen:(MediaEntry)->Unit) {
    Column(Modifier.fillMaxSize().background(BG)) {
        TopBar("$title (${files.size})", onBack)
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            OutlinedTextField(search, onSearch, Modifier.weight(1f).padding(8.dp), singleLine=true, label={Text("Search")})
            IconButton(onToggle) { Icon(if(grid) Icons.Default.List else Icons.Default.GridView, "View", tint=Color.White) }
        }
        if(scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(files.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { Text("No files found", color=Color.White) }
        else if(grid) LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.fillMaxSize(), contentPadding=PaddingValues(5.dp)) {
            items(files, key={it.uri.toString()}) { f -> Tile(f) { onOpen(f) } }
        } else LazyColumn(Modifier.fillMaxSize()) {
            items(files, key={it.uri.toString()}) { f -> RowFile(f) { onOpen(f) } }
        }
    }
}

@Composable private fun Tile(f:MediaEntry, onOpen:()->Unit) {
    Card(Modifier.padding(3.dp).clickable(onClick=onOpen), colors=CardDefaults.cardColors(containerColor=CARD)) {
        Column {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment=Alignment.Center) {
                when(f.kind) {
                    Kind.PHOTO -> AsyncImage(f.uri, f.name, Modifier.fillMaxSize(), contentScale=ContentScale.Crop)
                    Kind.VIDEO -> VideoThumbnail(f)
                    Kind.MUSIC -> Icon(Icons.Default.MusicNote, null, tint=Color.White, modifier=Modifier.size(48.dp))
                    else -> Icon(Icons.Default.Description, null, tint=Color.White, modifier=Modifier.size(48.dp))
                }
            }
            Text(f.name, color=Color.White, fontSize=11.sp, maxLines=2, overflow=TextOverflow.Ellipsis, modifier=Modifier.padding(5.dp))
        }
    }
}

@Composable private fun RowFile(f:MediaEntry, onOpen:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onOpen).padding(10.dp), verticalAlignment=Alignment.CenterVertically) {
        when(f.kind) {
            Kind.PHOTO -> AsyncImage(f.uri, f.name, Modifier.size(58.dp), contentScale=ContentScale.Crop)
            Kind.VIDEO -> Box(Modifier.size(58.dp)) { VideoThumbnail(f) }
            Kind.MUSIC -> Icon(Icons.Default.MusicNote, null, tint=Color.White, modifier=Modifier.size(38.dp))
            else -> Icon(Icons.Default.Description, null, tint=Color.White, modifier=Modifier.size(38.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, color=Color.White, maxLines=2, overflow=TextOverflow.Ellipsis)
            Text(formatSize(f.size), color=Color.Gray, fontSize=11.sp)
        }
    }
}

@Composable private fun VideoThumbnail(f:MediaEntry) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, f.uri) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, f.uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val timeUs = if (duration > 0) (duration * 1000L / 4L).coerceAtMost(1_000_000L) else 0L
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }
    if(bitmap != null) {
        Box(Modifier.fillMaxSize()) {
            Image(bitmap!!.asImageBitmap(), f.name, Modifier.fillMaxSize(), contentScale=ContentScale.Crop)
            Icon(Icons.Default.PlayCircle, null, tint=Color.White, modifier=Modifier.size(42.dp).align(Alignment.Center))
        }
    } else {
        Icon(Icons.Default.PlayCircle, f.name, tint=Color.White, modifier=Modifier.size(48.dp))
    }
}

@Composable private fun MediaViewer(f:MediaEntry, onBack:()->Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        TopBar(f.name, onBack)
        Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) {
            when(f.kind) {
                Kind.PHOTO -> PhotoViewer(f.uri)
                Kind.VIDEO -> VideoPlayer(f.uri)
                else -> OpenExternal(f)
            }
        }
    }
}

@Composable private fun PhotoViewer(uri:Uri) {
    var scale by remember(uri) { mutableStateOf(1f) }
    var x by remember(uri) { mutableStateOf(0f) }
    var y by remember(uri) { mutableStateOf(0f) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(uri) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    x += pan.x; y += pan.y
                }
            }
            .pointerInput(uri) {
                detectTapGestures(onDoubleTap = {
                    if(scale > 1.05f) { scale=1f; x=0f; y=0f } else scale=2f
                })
            },
        contentAlignment=Alignment.Center
    ) {
        AsyncImage(uri, null, Modifier.fillMaxSize().graphicsLayer { scaleX=scale; scaleY=scale; translationX=x; translationY=y }, contentScale=ContentScale.Fit)
    }
}

@Composable private fun VideoPlayer(uri:Uri) {
    val context=LocalContext.current
    val player=remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady=true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory={ PlayerView(it).apply { this.player=player; useController=true; controllerShowTimeoutMs=3000 } }, modifier=Modifier.fillMaxSize())
}

@Composable private fun OpenExternal(f:MediaEntry) {
    val context=LocalContext.current
    Button(onClick={
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(f.uri, f.mime.ifBlank{"*/*"})
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_:Exception) {}
    }) { Text("Open file") }
}

private suspend fun scanInternal(context:Context):List<MediaEntry> = withContext(Dispatchers.IO) {
    val out=ArrayList<MediaEntry>()
    fun query(base:Uri, forced:Kind) {
        try {
            context.contentResolver.query(
                base,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE),
                null,null,"${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { c ->
                val id=c.getColumnIndex(MediaStore.MediaColumns._ID)
                val name=c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mime=c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val size=c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                while(c.moveToNext()) {
                    val n=c.getString(name).orEmpty()
                    val m=if(mime>=0)c.getString(mime).orEmpty() else ""
                    out += MediaEntry(Uri.withAppendedPath(base,c.getLong(id).toString()),n,m,detectKind(n,m,forced),if(size>=0)c.getLong(size) else 0L,false)
                }
            }
        } catch (_:Exception) {}
    }
    query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Kind.PHOTO)
    query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Kind.VIDEO)
    query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Kind.MUSIC)
    out
}

private suspend fun scanExternalTree(context:Context, tree:Uri):List<MediaEntry> = withContext(Dispatchers.IO) {
    val root=DocumentFile.fromTreeUri(context,tree) ?: return@withContext emptyList()
    val out=ArrayList<MediaEntry>()
    val queue=ArrayDeque<DocumentFile>()
    queue.add(root)
    while(queue.isNotEmpty()) {
        val dir=queue.removeFirst()
        val children=try{dir.listFiles()}catch(_:Exception){emptyArray()}
        for(f in children) {
            try {
                if(f.isDirectory) queue.addLast(f)
                else if(f.isFile) {
                    val n=f.name.orEmpty()
                    val m=f.type.orEmpty()
                    out += MediaEntry(f.uri,n,m,detectKind(n,m,null),f.length(),true)
                }
            } catch (_:Exception) {}
        }
    }
    out
}

private fun detectKind(name:String,mime:String,forced:Kind?):Kind {
    val n=name.lowercase(Locale.US)
    val m=mime.lowercase(Locale.US)
    return when {
        m.startsWith("image/") || n.endsWithAny(".jpg",".jpeg",".png",".webp",".gif",".heic",".heif",".bmp",".tif",".tiff") -> Kind.PHOTO
        m.startsWith("video/") || n.endsWithAny(".mp4",".mkv",".mov",".avi",".webm",".3gp",".m4v",".ts",".mts",".m2ts",".wmv",".flv") -> Kind.VIDEO
        m.startsWith("audio/") || n.endsWithAny(".mp3",".m4a",".aac",".wav",".flac",".ogg",".opus",".wma") -> Kind.MUSIC
        m == "application/pdf" || m.startsWith("text/") || m.contains("word") || m.contains("excel") || m.contains("powerpoint") || n.endsWithAny(".pdf",".doc",".docx",".xls",".xlsx",".ppt",".pptx",".txt",".csv") -> Kind.DOC
        else -> forced ?: Kind.OTHER
    }
}

private fun String.endsWithAny(vararg suffixes:String)=suffixes.any{endsWith(it)}

private fun count(files:List<MediaEntry>)=Counts(
    photos=files.count{it.kind==Kind.PHOTO},
    videos=files.count{it.kind==Kind.VIDEO},
    music=files.count{it.kind==Kind.MUSIC},
    docs=files.count{it.kind==Kind.DOC},
    other=files.count{it.kind==Kind.OTHER},
    all=files.size
)

private fun formatSize(bytes:Long):String {
    if(bytes<=0)return "0 B"
    val units=arrayOf("B","KB","MB","GB","TB")
    var value=bytes.toDouble()
    var index=0
    while(value>=1024 && index<units.lastIndex){value/=1024;index++}
    return if(index==0) "${value.toInt()} B" else String.format(Locale.US,"%.1f %s",value,units[index])
}
