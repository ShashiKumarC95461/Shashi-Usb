package com.example.usbmediaexplorer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(context: ComponentActivity) {
    val scope = rememberCoroutineScope()
    val store = remember { FileStore(context) }
    var stats by remember { mutableStateOf(ScanStats()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FileRecord>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf("Ready — connect USB / OTG") }
    var category by remember { mutableStateOf<Category?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        job?.cancel()
        store.clear()
        results = emptyList()
        stats = ScanStats()
        scanning = true
        message = "Extreme deep scan + live index…"
        job = scope.launch {
            try {
                ExtremeScanner(context).scan(uri,
                    onFile = { store.upsert(it) },
                    onStats = { stats = it })
                message = "Complete — ${stats.files} files indexed"
            } catch (_: kotlinx.coroutines.CancellationException) {
                message = "Scan stopped"
            } finally { scanning = false }
        }
    }

    LaunchedEffect(query, category, scanning) {
        results = if (category != null && query.isBlank()) store.byCategory(category!!)
        else store.search(query)
    }

    MaterialTheme {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("Shashi Media Viewer") },
                actions = {
                    TextButton(onClick = {
                        if (scanning) job?.cancel() else picker.launch(null)
                    }) { Text(if (scanning) "STOP" else "SCAN") }
                }
            )
        }) { pad ->
            Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("⚡ PRO EXTREME SCANNER", style = MaterialTheme.typography.headlineSmall)
                        Text(message)
                        Spacer(Modifier.height(10.dp))
                        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Text("${stats.files} files • ${stats.folders} folders • ${formatBytes(stats.bytes)}")
                        Text("${stats.filesPerSecond} files/sec • ${stats.inaccessible} inaccessible • ${stats.errors} errors")
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it; category = null },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Search all indexed files") }
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.values().take(5).forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = if (category == c) null else c; query = "" },
                            label = { Text(c.name.take(4)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(results) { f ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(f.name, style = MaterialTheme.typography.titleMedium)
                                Text("${f.category} • ${formatBytes(f.size)}")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    var v = bytes.toDouble()
    val u = arrayOf("KB", "MB", "GB", "TB")
    var i = 0
    while (v >= 1024 && i < u.lastIndex) { v /= 1024; i++ }
    return String.format(Locale.US, "%.1f %s", v, u[i])
}
