# Auditoria Inicial do Ecossistema sHub (`INITIAL_AUDIT.md`)

**Data da Auditoria:** 06/11/2026  
**Responsável:** Argon — Arquiteto de Software & Engenheiro Android/GIS Sênior  
**Escopo:** `C:\dev`, `C:\dev\s_Hub_App`, `C:\dev\s_SER_Tools`, `C:\dev\.env`

---

## 1. Visão Geral do Ecossistema e Ambiente

### 1.1 Ambiente de Engenharia (`C:\dev`)
| Componente | Especificação Encontrada | Status |
| :--- | :--- | :---: |
| **Sistema Operacional** | Windows 10/11 x64 | OK |
| **JDK** | OpenJDK 17.0.2 (`C:\dev\jdk-17.0.2`) | OK |
| **Android SDK** | `C:\dev\android-sdk` (Compile SDK 35, Build-Tools 35) | OK |
| **Gradle Wrapper** | Gradle `8.9` (`gradle-8.9-bin.zip`) | OK |
| **Android Gradle Plugin (AGP)** | `8.7.3` | OK |
| **Kotlin** | `2.0.21` (K2 Compiler + Compose Compiler Plugin `2.0.21`) | OK |
| **KSP** | `2.0.21-1.0.27` | OK |
| **Min SDK / Target SDK** | `minSdk = 26` (Android 8.0) / `targetSdk = 35` (Android 15) | OK |
| **Python (Ferramentas GIS)** | Python `3.12.10` (`shapely 2.2.0`, `pyproj 3.8.0`, `Pillow`) | OK |

### 1.2 Auditoria de Segurança de Credenciais (`C:\dev\.env`)
- O arquivo `C:\dev\.env` foi inspecionado de forma segura (apenas chaves lidas, nenhum valor sensível exposto).
- Contém 21 variáveis de ambiente referentes a URLs de repositórios Git (`sHub_App_REPO`, `s_SER_Tools_REPO`, `REPO_LIBRARY`, etc.) e tokens de integração.
- **Achado de Segurança 1:** O repositório `C:\dev\s_Hub_App` possui `.gitignore` bloqueando `.env`, `*.env` e `.env.*`, porém não possuía um arquivo `.env.example` documentando as variáveis esperadas e a regra `.env.*` bloqueava o próprio `.env.example`.
- **Achado de Segurança 2:** Nenhum token ou credencial está hardcoded no código-fonte Kotlin/Python do `s_Hub_App`.

### 1.3 Análise das Fontes de Referência (`C:\dev\s_SER_Tools\decompiled_sources`)
O diretório `s_SER_Tools` é uma ferramenta independente baseada no projeto REA e contém fontes decompiladas para estudo comparativo:
1. **`ch.opengis.qfield` (QField — Licença GPLv2+):**
   - Arquitetura baseada em Qt/QML (`extracted_qml/`: `04_measuringTool.qml`, `12_navigationHighlight.qml`, `17_geometryHighlighter.qml`, `23_searchBar.qml`) sobre bibliotecas nativas C++ (QGIS/GDAL/PROJ) e serviços Java (`QFieldPositioningService.java`).
   - **Lição Arquitetural e Legal:** Por ser licenciado sob GPLv2+, nenhum código do QField pode ser copiado para o `sHub` sem contaminar a licença. O `sGis` implementa seus próprios leitores SQLite/GeoPackage R-Tree, parser de cabeçalhos WKB (`GeometryHeaderParser`), renderizador vetorial e provedor MBTiles em Kotlin nativo sobre `osmdroid` (Apache 2.0 / LGPL), mantendo o APK leve e livre de restrições GPL.
2. **`com.jeyluta.timestampcamerafree` (Timestamp Camera Free — Proprietário):**
   - Utiliza APIs legadas Camera1/Camera2 com pipeline OpenGL ES customizado (`com.android.grafika.gles`) e contém diversos SDKs proprietários de telemetria/anúncios (`com.apm.insight`, `com.bykv.vk.openvk`).
   - **Lição Arquitetural:** O módulo `sFoto` supera a referência ao utilizar `CameraX 1.4.1` com `OverlayEffect` nativo no hardware Canvas, gravação limpa de tags EXIF (`ExifInterface`) e banco SQLite local sem qualquer SDK de rastreamento ou publicidade.

---

## 2. Estado Atual da Arquitetura do `s_Hub_App`

### 2.1 Estrutura de Módulos Encontrada
Atualmente o projeto possui 3 módulos Gradle declarados em `settings.gradle.kts`:
1. **`:core:geo`** (Kotlin JVM puro):
   - Contém `Coordinates.kt` (`GeoCoordinate`, `UtmCoordinate`, `Hemisphere`) e `UtmConverter.kt` (conversão geodésica WGS84 $\leftrightarrow$ UTM).
   - Possui 1 arquivo de teste (`UtmConverterTest.kt`).
2. **`:core:gis`** (Android Library):
   - Contém a camada de domínio e dados espaciais do sGis: `GpkgReader.kt` (949 linhas), `GpkgRtreeReader.kt`, `GpkgWriter.kt`, `GeometryHeaderParser.kt`, `CoordinateSearchParser.kt`, `ImportGpkgUseCase.kt`, `SearchFeatureUseCase.kt`, e persistência Room (`AppDatabase`, `GisDao`).
   - Possui 5 arquivos de teste unitário.
3. **`:app`** (Android Application):
   - Concentra a `MainActivity`, navegação (`SHubApp.kt`), tela inicial (`HomeScreen.kt`) e **todas** as camadas de apresentação/UI e ViewModels das features:
     - `com.saile.shub.converter` (`sGeoConversor`)
     - `com.saile.shub.photo` (`sFoto`)
     - `com.saile.shub.gis` (`sGis` UI, MapView, MBTiles, Overlays, LocationTracker)
     - `com.saile.shub.normative` (Motor normativo / banco `normative.db`)

---

## 3. Problemas Técnicos, Riscos e Dívidas Técnicas Comprovadas

### 3.1 Acoplamento Indevido Entre Módulos (Violação de Isolamento)
- **Evidência:** `app/src/main/kotlin/com/saile/shub/gis/GisLocationTracker.kt` (linha 18) importa diretamente `com.saile.shub.photo.location.LocationUtils`.
- **Impacto:** O módulo `sGis` depende diretamente de um pacote interno do módulo `sFoto`. Uma alteração em `sFoto` pode quebrar a compilação ou o funcionamento do `sGis`, violando a regra de independência entre ferramentas.

### 3.2 Operações Bloqueantes na Main Thread (UI Jank / ANR Risk)
1. **`PhotoViewModel.kt` (linha 56):**
   - `private val mutableState = MutableStateFlow(dbHelper.getPreferences())` executa abertura e leitura de banco SQLite (`sfoto_prefs.db`) de forma síncrona na Main Thread durante a instanciação do ViewModel.
2. **`PhotoScreen.kt` (linha 672):**
   - No callback `onClose` da `GalleryScreen`, executa `lastPhotoUri = MediaUtils.getLastTakenMediaUri(context)` diretamente na Main Thread (consulta ao `ContentResolver` / `MediaStore`), enquanto na inicialização (linha 103) usava `Dispatchers.IO`.
3. **`HomeScreen.kt` (linhas 264–287):**
   - `HubAssetSvgIcon` abre streams de assets, faz parse de XML/SVG via `androidsvg` e aloca Bitmaps `512x512 ARGB_8888` (1 MB cada) de forma síncrona na Main Thread toda vez que a `HomeScreen` entra em composição, sem cache em memória entre navegações.

### 3.3 Vazamentos de Recursos Nativos e Memória (Resource Leaks)
1. **Vazamento de `MediaActionSound` em `PhotoCapture.kt` (linhas 139–142):**
   - A cada foto capturada, uma nova instância de `android.media.MediaActionSound()` é criada e **nunca** liberada com `.release()`, vazando canais de áudio nativos do Android (`AudioTrack`/`SoundPool`).
2. **Conexão SQLite não fechada em `PhotoViewModel.kt`:**
   - `PhotoPreferencesDbHelper` é instanciado no ViewModel mas não possui chamada a `dbHelper.close()` em `onCleared()`.
3. **`ImageLoader` órfão em `PhotoScreen.kt` (linhas 67–71):**
   - Um `coil.ImageLoader` com `VideoFrameDecoder` é instanciado via `remember`, mas nunca sofre `imageLoader.shutdown()` quando a tela é descartada.

### 3.4 Recomposições Excessivas de Interface (Desempenho e Bateria)
- **`PhotoScreen.kt` (linhas 111, 123, 341, 416):**
  - `currentTime` (atualizado a cada 1 segundo) e `currentAzimuthDegrees` (atualizado pelo `SensorEventListener` a cada variação de $1^\circ$ da bússola, ~15 vezes por segundo) são estados Compose (`mutableStateOf`) declarados no topo de `PhotoScreen` (684 linhas).
  - Embora esses valores sejam lidos apenas pelo callback `getOverlayInfo` da câmera (fora da árvore Compose), mutá-los invalida o escopo de composição ou gera overhead desnecessário de Snapshot. O uso de referências atômicas/voláteis para dados consumidos exclusivamente pelo `OverlayEffect` elimina recomposições parasitas na UI da câmera.

### 3.5 Bug Latente no Módulo `normative` (`NormativeDatabase.kt`)
- `NormativeDatabase` utiliza `@Database` do Room dentro do módulo `:app`, porém `:app/build.gradle.kts` não aplica o plugin `com.google.devtools.ksp` nem o processador `ksp("androidx.room:room-compiler")`. Qualquer chamada a `NormativeDatabase.getInstance(context)` falharia em tempo de execução com `RuntimeException: NormativeDatabase_Impl does not exist`.

### 3.6 Ausência de Version Catalog e Centralização de Dependências
- As versões de bibliotecas estão espalhadas manualmente entre `build.gradle.kts`, `app/build.gradle.kts` e `core/gis/build.gradle.kts`, sem um catálogo centralizado `gradle/libs.versions.toml`.

### 3.7 Oportunidades de Evolução no `sGeoConversor` (`:core:geo`)
- `UtmConverter.geoToUtm(coordinate, forcedZone)` não valida se `forcedZone` está no intervalo `1..60`.
- Não há função dedicada para conversão direta entre zonas UTM (`convertZone`) nem suporte a entrada/saída em Graus, Minutos e Segundos (DMS) ou cópia rápida de coordenadas a partir do GPS atual do aparelho.
- `OverlayRenderer.kt` duplicava a lógica de cálculo de banda latitudinal UTM em vez de delegar para `UtmConverter.formatUtm()`.

### 3.8 Classes Monolíticas
- `GisScreen.kt` possui **1.765 linhas**, misturando diálogos de basemap, diálogos de posicionamento GNSS, folha de atributos de feições, barra de busca e controles de edição na mesma classe.
- `GpkgReader.kt` possui **949 linhas**.

---

## 4. Pontos Fortes a Preservar

1. **`:core:geo` puro e exato:** Matemática geodésica WGS84 limpa, sem dependências Android, testável na JVM.
2. **Pipeline `sFoto` com `CameraX` `OverlayEffect`:** Renderização de carimbo (timestamp, UTM, GEO, endereço, azimute e logo corporativo SVG) otimizada com `ThreadLocal<Paint>` e `LruCache` em `OverlayRenderer.kt`.
3. **Motor espacial `sGis` 100% Offline:** Leitura de GeoPackage com índice R-Tree (`GpkgRtreeReader`), parser WKB sem alocação de objetos pesados (`GeometryHeaderParser`), cascata determinística de pacotes MBTiles (`CompositeMbtilesArchive`) e fallback controlado (`ControlledFallbackArchive`).
4. **Suíte de testes existente:** 52 testes unitários em `:app`, 5 suítes em `:core:gis`, 1 suíte em `:core:geo` e 3 suítes Python para ferramentas espaciais.

---

## 5. Plano de Execução Priorizado (Fases 1 a 10)

1. **Fase 1 (Arquitetura):** Definir a arquitetura modular limpa, registro de módulos (`HubToolModule`), contratos compartilhados e documentar ADRs (`ARCHITECTURE.md`, `MODULE_DEPENDENCIES.md`, `ARCHITECTURE_DECISIONS.md`).
2. **Fase 2 (Desacoplamento):**
   - Extrair utilitários compartilhados de localização e rede para um pacote core compartilhado (`com.saile.shub.core.location`), eliminando a dependência de `sGis` sobre `sFoto`.
   - Isolar contratos de navegação e registro de ferramentas do `sHub`.
   - Extrair componentes de diálogos de `GisScreen.kt` para reduzir complexidade ciclomática.
3. **Fase 3 (Modernização de Dependências):**
   - Implementar `gradle/libs.versions.toml` (Version Catalog).
   - Adicionar KSP ao `:app` para corrigir a geração de código do Room (`NormativeDatabase`).
   - Auditar e documentar todas as dependências (`DEPENDENCY_AUDIT.md`, `DEPENDENCY_UPDATES.md`).
4. **Fase 4 (Otimização de Desempenho):**
   - Eliminar leituras de SQLite e `MediaStore` na Main Thread em `PhotoViewModel` e `PhotoScreen`.
   - Implementar `LruCache` estático para ícones SVG em `HomeScreen.kt`.
   - Corrigir o vazamento nativo de `MediaActionSound` em `PhotoCapture.kt` e o ciclo de vida do `ImageLoader` e `PhotoPreferencesDbHelper`.
   - Converter estados de alta frequência não-visuais (`currentAzimuthDegrees`, `currentTime`) em `PhotoScreen` para referências sem recomposição global da tela.
5. **Fase 5 (Segurança e Confiabilidade):**
   - Criar `.env.example`, ajustar `.gitignore`, auditar permissões e `AndroidManifest.xml`.
6. **Fase 6 & 7 (Testes Automatizados e Padronização):**
   - Expandir testes unitários e de regressão para `sGeoConversor` (incluindo conversão entre zonas UTM, DMS, casos extremos), `sFoto` e `sGis`.
7. **Fase 8, 9 & 10 (Documentação Completa, Builds Debug/Release/Lint e Validação Final):**
   - Gerar toda a árvore documental em `docs/`, executar `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease` e validar no dispositivo físico via ADB.

