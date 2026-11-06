# Auditoria de Dependências — sHub

**Data:** 2026-11-06

---

## 1. Inventário Completo de Dependências (`gradle/libs.versions.toml`)

| Biblioteca / Plugin | Coordenada Maven / Plugin ID | Versão | Módulo(s) | Licença | Papel no Sistema |
|---|---|---|---|---|---|
| Android Gradle Plugin | `com.android.application` / `library` | `8.7.3` | `:app`, `:core:gis` | Apache-2.0 | Build system Android (SDK 35) |
| Kotlin | `org.jetbrains.kotlin.*` | `2.0.21` | Todos | Apache-2.0 | Compilador Kotlin K2 + Compose Compiler |
| KSP | `com.google.devtools.ksp` | `2.0.21-1.0.27` | `:app`, `:core:gis` | Apache-2.0 | Processamento de anotações Room |
| Jetpack Compose BOM | `androidx.compose:compose-bom` | `2024.12.01` | `:app` | Apache-2.0 | UI declarativa Material 3 |
| Activity Compose | `androidx.activity:activity-compose` | `1.10.0` | `:app` | Apache-2.0 | Integração Activity + Compose |
| Lifecycle | `androidx.lifecycle:lifecycle-*` | `2.8.7` | `:app` | Apache-2.0 | ViewModel e `collectAsStateWithLifecycle` |
| CameraX | `androidx.camera:camera-*` | `1.4.1` | `:app` | Apache-2.0 | Captura fotográfica, vídeo e `OverlayEffect` |
| Room | `androidx.room:room-*` | `2.6.1` | `:app`, `:core:gis` | Apache-2.0 | Persistência `SyncDatabase` e `NormativeDatabase` (FTS4) |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-*` | `1.9.0` | `:app`, `:core:gis` | Apache-2.0 | Concorrência estruturada e `StateFlow` |
| Core KTX | `androidx.core:core-ktx` | `1.15.0` | `:app`, `:core:gis` | Apache-2.0 | Extensões Android KTX |
| ExifInterface | `androidx.exifinterface:exifinterface` | `1.3.7` | `:app` | Apache-2.0 | Gravação de metadados EXIF GPS/Azimute em JPEGs |
| osmdroid | `org.osmdroid:osmdroid-android` | `6.1.18` | `:app` | Apache-2.0 | Motor de renderização de mapas Slippy Map / MBTiles |
| AndroidSVG | `com.caverock:androidsvg-aar` | `1.4` | `:app` | Apache-2.0 | Renderização vetorial de ícones e logos SVG |
| Coil | `io.coil-kt:coil-compose` / `coil-video` | `2.5.0` | `:app` | Apache-2.0 | Miniaturas da galeria de fotos e vídeos |
| Media3 ExoPlayer | `androidx.media3:media3-exoplayer` / `ui` | `1.2.1` | `:app` | Apache-2.0 | Reprodução local de vídeos gravados no `sFoto` |
| JUnit 4 | `junit:junit` | `4.13.2` | Todos (Test) | EPL-1.0 | Testes unitários JVM |

---

## 2. Auditoria de Licenciamento e Conformidade Legal

1. **Dependências Diretas do sHub:** 100% das dependências em tempo de execução (`androidx.*`, `org.jetbrains.*`, `org.osmdroid`, `com.caverock:androidsvg`, `io.coil-kt`) utilizam a licença permissiva **Apache License 2.0**, compatível com distribuição comercial e corporativa.
2. **Fontes de Referência em `C:\dev\s_SER_Tools\decompiled_sources`:**
   - **QField (`ch.opengis.qfield`):** Licenciado sob **GPLv2+**. Nenhum arquivo de código-fonte C++/QML/Java do QField foi copiado para o `sHub`. Toda a implementação de leitura GeoPackage, R-Tree, MBTiles e simbologia em `:core:gis` e `com.saile.shub.gis` é autoral em Kotlin.
   - **Timestamp Camera Free (`com.jeyluta.timestampcamerafree`):** Aplicativo proprietário usado exclusivamente para estudo de requisitos visuais (posicionamento de carimbo, formatos de bússola e altitude). O `sFoto` foi construído do zero sobre Jetpack CameraX `1.4.1`.
   - **Zero Telemetria / Zero Ads:** Ao contrário dos aplicativos de referência analisados, o `sHub` não inclui nenhum SDK de anúncios (`play-services-ads`), crashlytics externo ou rastreadores.

