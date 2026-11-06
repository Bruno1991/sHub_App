# Relatório de Otimização de Desempenho e Recursos — sHub

**Data:** 2026-11-06

---

## 1. Otimizações Implementadas e Resultados

### 1.1 Eliminação de Recomposições de Alta Frequência no `sFoto` (`PERF-01`)
- **Solução:** Substituídos os estados Compose `currentTime` e `currentAzimuthDegrees` por `LiveOverlaySensorState` (`@Volatile var azimuthDegrees: Float?`), lido diretamente por `currentOverlay(capturedAt: Date = Date())` quando o `OverlayEffect` da câmera solicita os metadados do frame.
- **Resultado:** Recomposições da árvore Compose de `PhotoScreen` causadas por sensores e relógio caíram de **~15/segundo para 0/segundo**, mantendo a atualização fluida do azimute e horário no canvas do `OverlayEffect`.

### 1.2 Remoção de Bloqueios de I/O na Main Thread (`PERF-02`, `PERF-03`)
- **Solução em `PhotoViewModel.kt`:** Inicialização imediata do `MutableStateFlow` com `PhotoUiState()` e carregamento assíncrono de `dbHelper.getPreferences()` em `viewModelScope.launch(Dispatchers.IO)`.
- **Solução em `PhotoScreen.kt`:** A atualização de `lastPhotoUri` no fechamento da `GalleryScreen` agora executa em `coroutineScope.launch(Dispatchers.IO)` com retorno na `Dispatchers.Main`.
- **Resultado:** Zero queries SQLite ou `ContentResolver` na Main Thread.

### 1.3 Prevenção de Vazamento de Recursos (`PERF-04`, `PERF-05`)
- **Solução em `PhotoCapture.kt`:** Instância única preguiçosa (`lazy`) de `MediaActionSound` reutilizada em todos os disparos (inclusive no modo contínuo *burst*) e liberada deterministicamente com `shutterSound?.release()` em `PhotoCapture.close()`.
- **Solução em `PhotoViewModel.kt`:** Adicionado `override fun onCleared()` fechando `dbHelper.close()`.
- **Solução em `PhotoScreen.kt`:** Adicionado `DisposableEffect(imageLoader) { onDispose { imageLoader.shutdown() } }`.

### 1.4 Cache em Memória de Ícones Vetoriais na `HomeScreen` (`PERF-06`)
- **Solução:** Adicionado `private val svgBitmapCache = LruCache<String, ImageBitmap>(16)` em `HomeScreen.kt`.
- **Resultado:** Os ícones SVG (`512x512`) das ferramentas são rasterizados apenas uma única vez por sessão do aplicativo; retornos subsequentes à tela inicial têm custo de renderização SVG zero (`O(1)` lookup).

### 1.5 Otimização Espacial e Cartográfica no `sGis` (`PERF-07`)
- **Solução:**
  - `CompositeMbtilesArchive` + `ControlledFallbackArchive` resolvem tiles em cascata (`HD -> Regional -> Overview`) com síntese matemática limitada a `maxDeltaZ = 2`.
  - `GeoPackageReader` consulta exclusivamente a tabela de índice espacial `rtree_<tabela>_<geom>` para o *Bounding Box* visível com *debounce* de movimentação e limite adaptativo por nível de zoom (`maxFeatures`).

