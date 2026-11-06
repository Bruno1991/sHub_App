# Decisões Arquiteturais (ADRs) — sHub

**Data:** 2026-11-06

---

## ADR-001: Módulo Geodésico Puro JVM (`:core:geo`) com Expansão de Snyder
- **Contexto:** O sHub exige conversão de alta precisão entre coordenadas geográficas WGS84 e UTM (incluindo transição entre zonas UTM 20S e 21S em Rondônia) tanto na UI (`sGeoConversor`), quanto em tempo real a 60 FPS no overlay da câmera (`sFoto`) e no rastreador GPS do mapa (`sGis`).
- **Decisão:** Manter `:core:geo` como biblioteca Kotlin JVM pura sem dependências Android, implementando as equações elipsoidais de Snyder/Redfearn para WGS84, adicionando `convertUtmZone(coordinate, targetZone)` e `formatDms(lat, lon)`.
- **Consequências:** Testes unitários executam em milissegundos na JVM; erro posicional inferior a 1 mm dentro da zona UTM e zero alocação desnecessária.

---

## ADR-002: Pipeline de Estampagem `sFoto` via Jetpack CameraX `OverlayEffect`
- **Contexto:** A análise de engenharia reversa do aplicativo de referência (`com.jeyluta.timestampcamerafree` em `s_SER_Tools\decompiled_sources`) revelou o uso de APIs legadas Camera1/Camera2 acopladas a shaders OpenGL customizados (`Grafika`), com alto risco de incompatibilidade entre fabricantes e presença de SDKs de telemetria/anúncios.
- **Decisão:** Utilizar `androidx.camera:camera-effects:1.4.1` (`OverlayEffect`) compartilhado entre `Preview`, `ImageCapture` e `VideoCapture`, combinado com memoização de layout (`CachedOverlayLayout`) e `LruCache` de logos SVG em `OverlayRenderer.kt`.
- **Otimização Crítica de Recompose:** O azimute da bússola (`15 Hz`) e o relógio (`1 Hz`) são mantidos em estado volátil não-recomposicional (`LiveOverlaySensorState`) lido diretamente pela thread do `OverlayEffect`, evitando recomposições da árvore Compose de `PhotoScreen`.

---

## ADR-003: Motor GIS Nativo (`osmdroid` + SQLite R-Tree + MBTiles Multinível) vs. Qt/QField
- **Contexto:** A análise de engenharia reversa do QField (`ch.opengis.qfield` em `s_SER_Tools\decompiled_sources`) mostrou que o QField empacota o motor C++ do QGIS e Qt6/QML sob licença **GPLv2+**, exigindo >150 MB de bibliotecas nativas `.so` e impedindo integração direta em janelas Compose sem violar a licença ou incorrer em sobrecarga de memória.
- **Decisão:** Implementar o `sGis` nativamente em Kotlin + Jetpack Compose + `osmdroid`, preservando os conceitos de UX de campo do QField (camadas GeoPackage com filtro espacial R-Tree `rtree_<table_name>_<geom_column>`, simbologia categorizada por tensão/fase, snapping na mira central e medição) sem copiar código GPLv2+.

---

## ADR-004: Composição Offline de MBTiles (`CompositeMbtilesArchive` + `ControlledFallbackArchive`)
- **Contexto:** Gerar tiles de satélite em alta resolução (z15–z19) para toda a extensão estadual ou mundial é inviável em armazenamento (dezenas de terabytes). Por outro lado, o zoom indiscriminado sobre tiles de baixa resolução gera blocos borrados sem aviso ao inspetor.
- **Decisão:**
  1. **Planejamento Espacial por Corredor (`tools/spatial_coverage_planner.py`):** Gera cobertura HD apenas num buffer métrico configurável (em UTM 20S `EPSG:31980` / UTM 21S `EPSG:31981`) ao redor dos ativos reais da rede elétrica extraídos do GeoPackage.
  2. **Arquitetura Multinível (`CompositeMbtilesArchive`):** Consulta arquivos MBTiles em ordem estrita de prioridade (`HD Corredor z15–z19` → `Regional Rondônia z8–z14` → `Overview Mundial z0–z7`).
  3. **Fallback Controlado (`ControlledFallbackArchive`):** Quando um tile `z18` fora do corredor elétrico não existe localmente, recorta e escala matematicamente o sub-quadrante do tile ancestral até `maxDeltaZ = 2` níveis acima, estampando uma faixa visual `"ZOOM APROXIMADO (z14 -> z16)"` para transparência operacional.

---

## ADR-005: Centralização de Dependências via Gradle Version Catalog (`gradle/libs.versions.toml`) e Room KSP
- **Contexto:** As versões de bibliotecas estavam espalhadas em strings literais nos arquivos `build.gradle.kts`, e o módulo `:app` declarava `@Database class NormativeDatabase` sem aplicar o plugin `com.google.devtools.ksp` nem `room-compiler`.
- **Decisão:** Centralizar 100% das versões e bibliotecas em `gradle/libs.versions.toml` e habilitar KSP para Room tanto em `:core:gis` quanto em `:app`.

