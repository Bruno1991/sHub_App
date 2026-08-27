package com.saile.shub.photo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saile.shub.photo.OverlayColor
import com.saile.shub.photo.OverlayPosition
import com.saile.shub.photo.PhotoUiState
import com.saile.shub.photo.PhotoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSettingsDrawer(
    state: PhotoUiState,
    viewModel: PhotoViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var positionExpanded by remember { mutableStateOf(false) }
    var colorExpanded by remember { mutableStateOf(false) }
    var logoExpanded by remember { mutableStateOf(false) }
    
    val logos = remember {
        val list = runCatching {
            context.assets.list("")?.filter { it.endsWith(".svg") } ?: emptyList()
        }.getOrDefault(emptyList())
        listOf("Nenhuma") + list
    }

    BackHandler {
        onClose()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Configurações", style = MaterialTheme.typography.titleLarge)
            }
            
            ListItem(
                headlineContent = { Text("Exibir UTM") },
                trailingContent = { Switch(checked = state.showUtm, onCheckedChange = viewModel::toggleUtm) }
            )
            ListItem(
                headlineContent = { Text("Exibir GEO") },
                trailingContent = { Switch(checked = state.showGeo, onCheckedChange = viewModel::toggleGeo) }
            )
            ListItem(
                headlineContent = { Text("Exibir Data e Hora") },
                trailingContent = { Switch(checked = state.showDateTime, onCheckedChange = viewModel::toggleDateTime) }
            )
            ListItem(
                headlineContent = { Text("Exibir Endereço") },
                trailingContent = { Switch(checked = state.showAddress, onCheckedChange = viewModel::toggleAddress) }
            )
            ListItem(
                headlineContent = { Text("Adicionar Nome do Usuário") },
                trailingContent = { Switch(checked = state.showName, onCheckedChange = viewModel::toggleName) }
            )
            if (state.showName) {
                OutlinedTextField(
                    value = state.userName,
                    onValueChange = viewModel::setUserName,
                    label = { Text("Nome do Usuário") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            ListItem(
                headlineContent = { Text("Som do Obturador") },
                trailingContent = { Switch(checked = state.playShutterSound, onCheckedChange = viewModel::toggleShutterSound) }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            ListItem(
                headlineContent = { Text("Posição", fontWeight = FontWeight.Bold) },
                trailingContent = { Icon(if (positionExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) },
                modifier = Modifier.clickable { positionExpanded = !positionExpanded }
            )
            if (positionExpanded) {
                OverlayPosition.entries.forEach { pos ->
                    NavigationDrawerItem(
                        label = { Text(pos.label) },
                        selected = state.position == pos,
                        onClick = { viewModel.setPosition(pos) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            
            ListItem(
                headlineContent = { Text("Cor da fonte", fontWeight = FontWeight.Bold) },
                trailingContent = { Icon(if (colorExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) },
                modifier = Modifier.clickable { colorExpanded = !colorExpanded }
            )
            if (colorExpanded) {
                OverlayColor.entries.forEach { color ->
                    NavigationDrawerItem(
                        label = { Text(color.label) },
                        selected = state.overlayColor == color,
                        onClick = { viewModel.selectColor(color) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            if (logos.isNotEmpty()) {
                ListItem(
                    headlineContent = { Text("Selecionar Logo", fontWeight = FontWeight.Bold) },
                    trailingContent = { Icon(if (logoExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) },
                    modifier = Modifier.clickable { logoExpanded = !logoExpanded }
                )
                if (logoExpanded) {
                    logos.forEach { logoName ->
                        val isNone = logoName == "Nenhuma"
                        val isSelected = if (isNone) state.selectedLogo == null else state.selectedLogo == logoName
                        NavigationDrawerItem(
                            label = { Text(logoName) },
                            selected = isSelected,
                            onClick = { viewModel.setLogo(if (isNone) null else logoName) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
