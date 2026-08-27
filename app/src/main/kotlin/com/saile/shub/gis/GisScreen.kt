package com.saile.shub.gis

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saile.shub.gis.data.entities.GisFeatureEntity
import com.saile.shub.gis.usecase.ImportProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisScreen(
    onBack: () -> Unit,
    viewModel: GisViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            var displayName = "imported.gpkg"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex)
                    }
                }
            }
            viewModel.importGpkg(it, displayName)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("sGIS") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text("Voltar ao sHub")
            }
            
            Button(
                onClick = { launcher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
            Text("Importar GPKG")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                viewModel.search(it)
            },
            label = { Text("Buscar elemento (ex: PTCA0088)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when (val currState = state) {
            is GisState.Importing -> {
                when (val p = currState.progress) {
                    is ImportProgress.CopyingFile -> Text("Copiando arquivo...")
                    is ImportProgress.Indexing -> Text("Indexando... ${p.featuresImported} elementos")
                    is ImportProgress.Completed -> Text("Concluído! ${p.totalFeatures} elementos importados.")
                }
            }
            is GisState.Error -> {
                Text("Erro: ${currState.message}", color = MaterialTheme.colorScheme.error)
            }
            is GisState.SearchResults -> {
                LazyColumn {
                    items(currState.results) { feature ->
                        FeatureItem(feature)
                    }
                }
            }
            GisState.Idle -> {
                Text("Nenhum resultado ou aguardando importação.")
            }
        }
        }
    }
}

@Composable
fun FeatureItem(feature: GisFeatureEntity) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Código: ${feature.codeRaw ?: "N/A"}", style = MaterialTheme.typography.titleMedium)
            Text(text = "Camada: ${feature.sourceLayer}", style = MaterialTheme.typography.bodyMedium)
            feature.centroidLat?.let { lat ->
                feature.centroidLon?.let { lon ->
                    Text(text = "Centro: $lat, $lon", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
