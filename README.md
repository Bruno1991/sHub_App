# sHub — Plataforma Modular de Ferramentas de Engenharia de Campo

O **sHub** é uma plataforma Android nativa (*Kotlin + Jetpack Compose*), modular e **100% offline-first**, desenvolvida para operações de engenharia, georreferenciamento e inspeção de redes de distribuição e subtransmissão elétrica em Rondônia e na região Amazônica.

---

## Módulos Integrados (Ecossistema Unificado)

| Módulo | Descrição | Tecnologias Principais |
|---|---|---|
| **sFoto** | Câmera técnica de campo para fotos (disparo único e contínuo) e vídeos com estampagem a 60 FPS de coordenadas UTM/GEO, altitude, precisão, azimute magnético, data/hora, endereço reverso e logotipo institucional SVG, gravação EXIF e banco SQLite local. Integrada bidirecionalmente ao **sGIS** (carimbo automático de ativos/clientes e exibição de fotos georreferenciadas no mapa). | Jetpack CameraX `1.4.1` (`OverlayEffect`), `ExifInterface`, `AndroidSVG`, SQLite |
| **sGIS** | Sistema de Informações Geográficas (GIS) móvel 100% offline nível QField Pro: leitura vetorial **GeoPackage (`.gpkg`)** indexada por **R-Tree** com cache offscreen 120 FPS, importação e busca de **Base de Clientes (`.json`)** por UC/CDC, Medidor ou Nome, **Conversor UTM ⇄ GEO/DMS integrado**, basemap satélite híbrido **MBTiles (`.mbtiles`)** multinível + sincronizador por área `.gpkg`, **Inspeção Offline de Anomalias** (exportação KML/CSV) e **Navegação Rural "Fora de Estrada"** com linha guia dinâmica e gravação de rastro (*tracklog*). | `:core:gis`, `:core:geo`, `osmdroid`, SQLite R-Tree & B-Tree, Room, Python Spatial Pipeline (`tools/`) |

---

## Estrutura do Projeto

```text
s_Hub_App/
├── app/                        # Aplicação principal Android (:app)
│   └── src/main/kotlin/com/saile/shub/
│       ├── core/location/      # Utilitários compartilhados unificados (CoreLocationUtils, CoreNetworkUtils)
│       ├── ui/                 # Shell sHub, HomeScreen (LruCache SVG), HubToolRegistry e Estado Compartilhado
│       ├── photo/              # Módulo sFoto (CameraX OverlayEffect, EXIF, Galeria, Integração sGIS)
│       └── gis/                # Módulo sGIS (MapView 120 FPS, Clientes, Conversor, Inspeções, Navegação Rural)
├── core/
│   ├── geo/                    # Biblioteca Kotlin JVM pura de cálculos geodésicos WGS84/SIRGAS2000 (:core:geo)
│   └── gis/                    # Biblioteca Android de GeoPackage, R-Tree, Clientes (.json -> SQLite) e Busca (:core:gis)
├── tools/                      # Pipeline Python para planejamento de cobertura HD (UTM 20S/21S) e Google Hybrid MBTiles
├── gradle/libs.versions.toml   # Catálogo centralizado de dependências (Version Catalog)
└── docs/                       # Documentação técnica completa de arquitetura, auditoria e testes
```

---

## Rápido Início (Build & Testes)

```powershell
# Compilar APK Debug
.\gradlew.bat assembleDebug

# Executar suíte completa de testes unitários JVM/Android
.\gradlew.bat testDebugUnitTest

# Executar suíte de testes Python das ferramentas GIS
python -m unittest discover -s tools -p "test_*.py"
```

Consulte o [Índice Completo da Documentação Técnica em `docs/README.md`](./docs/README.md) para detalhes de arquitetura, ADRs, auditoria de desempenho e guias de operação.
