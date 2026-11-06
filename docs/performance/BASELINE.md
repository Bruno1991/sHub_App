# Linha de Base de Desempenho (Baseline) — sHub

**Data:** 2026-11-06

---

## 1. Gargalos Identificados na Auditoria Inicial (Pré-Refatoração)

Durante a inspeção estática e de fluxo de execução na **FASE 0**, foram comprovados os seguintes gargalos de desempenho e vazamentos de recursos:

| ID | Módulo / Arquivo | Problema Comprovado | Impacto Medido / Estimado |
|---|---|---|---|
| **PERF-01** | `sFoto` / `PhotoScreen.kt` | `currentTime` (atualizado a 1 Hz) e `currentAzimuthDegrees` (atualizado pelo `SensorManager` até 15 Hz) eram declarados como `mutableStateOf` na raiz de `PhotoScreen` (684 linhas), embora fossem lidos apenas por `currentOverlay()` na thread do `OverlayEffect`. | **Até 15 recomposições completas por segundo** da árvore Compose da tela de câmera, causando aquecimento e consumo desnecessário de CPU/bateria. |
| **PERF-02** | `sFoto` / `PhotoViewModel.kt` | `PhotoPreferencesDbHelper(application).getPreferences()` era executado de forma síncrona no construtor (`init`) na Main Thread. | Bloqueio de I/O SQLite na Main Thread durante a abertura do módulo `sFoto` (risco de *jank* no primeiro frame). |
| **PERF-03** | `sFoto` / `PhotoScreen.kt` | Ao fechar a `GalleryScreen`, `MediaUtils.getLastTakenMediaUri(context)` executava duas queries no `ContentResolver` (`MediaStore.Images` e `MediaStore.Video`) de forma síncrona na Main Thread. | Engasgo visual (*frame drop*) ao retornar da galeria para a câmera. |
| **PERF-04** | `sFoto` / `PhotoCapture.kt` | Uma nova instância nativa de `MediaActionSound()` era alocada a cada disparo fotográfico sem invocação de `.release()`. | Vazamento de recursos nativos de áudio (`AudioTrack`), crítico durante o modo de disparo contínuo (*burst*). |
| **PERF-05** | `sFoto` / `PhotoScreen.kt` | `ImageLoader.Builder(context)` do Coil era instanciado em `remember` sem chamada a `imageLoader.shutdown()` no `DisposableEffect`. | Retenção de pool de threads e cache de memória após sair do `sFoto`. |
| **PERF-06** | `ui` / `HomeScreen.kt` | `HubAssetSvgIcon` fazia parse de XML SVG e rasterizava bitmaps `512x512` (`ARGB_8888`, 1 MB cada) na Main Thread toda vez que `HomeScreen` entrava em composição. | Latência repetida de ~15–30 ms ao voltar de qualquer ferramenta para o `HomeScreen`. |
| **PERF-07** | `sGis` / `GisScreen.kt` + `osmdroid` | Ausência de composição multinível de MBTiles e fallback controlado gerava telas quadriculadas cinzas ao dar zoom além do nível máximo do MBTiles local. | Falha visual severa em operação 100% offline. |

