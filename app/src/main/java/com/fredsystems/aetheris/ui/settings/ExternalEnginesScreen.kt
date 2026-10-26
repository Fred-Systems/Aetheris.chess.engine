package com.fredsystems.aetheris.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fredsystems.aetheris.engine.process.ExternalEngineManager
import java.io.File

@Composable
fun ExternalEnginesScreen() {
    val context = LocalContext.current
    val manager = remember { ExternalEngineManager(context) }
    var engines by remember { mutableStateOf(manager.list()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { manager.import(it); engines = manager.list() }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("External Engines", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { launcher.launch(arrayOf("*/*")) }) { Text("Import Engine Binary") }
        Spacer(Modifier.height(8.dp))
        engines.forEach { f ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(f.name)
                TextButton(onClick = { manager.remove(f); engines = manager.list() }) { Text("Remove") }
            }
        }
    }
}