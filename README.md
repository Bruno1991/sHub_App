# sHub — Plataforma Modular de Ferramentas de Engenharia de Campo

O **sHub** é uma plataforma Android nativa (*Kotlin + Jetpack Compose*), modular e **100% offline-first**, desenvolvida para operações de engenharia, georreferenciamento e inspeção de redes de distribuição e subtransmissão elétrica em Rondônia e na região Amazônica.

---

## Módulos Integrados

| Módulo | Descrição | Tecnologias Principais |
|---|---|---|
| **sGeoConversor** | Conversão geodésica de alta precisão WGS84 Decimal ⇄ UTM ⇄ DMS, incluindo re-projeção direta entre zonas UTM adjacentes (ex.: transição **20S ⇄ 21S** no meridiano `-60°W`). | `:core:geo` (Pure Kotlin Snyder/Redfearn Transverse Mercator) |
| **sFoto** | Câmera técnica de campo para fotos (disparo único e contínuo) e vídeos com estampagem a 60 FPS de coordenadas UTM/GEO, altitude, precisão, azimute magnético, data/hora, endereço reverso e logotipo institucional SVG, além de gravação EXIF e banco SQLite. | Jetpack CameraX `1.4.1` (`OverlayEffect`), `ExifInterface`, `AndroidSVG`, SQLite |
| **sGis** | Sistema de Informações Geográficas (GIS) móvel 100% offline inspirado no QField, com leitura vetorial **GeoPackage (`.gpkg`)** indexada por **R-Tree**, basemap satélite **MBTiles (`.mbtiles`)** multinível (`CompositeMbtilesArchive` + `ControlledFallbackArchive`), rastreamento GPS/bússola, snapping e medição. | `:core:gis`, `osmdroid`, SQLite R-Tree, Room, Python Spatial Pipeline (`tools/`) |
| **Normative Engine** | Motor embarcado de consulta normativa (PRODIST, NBR, limites de perdas de transformadores PBE e elos fusíveis) com busca textual completa (`FTS4`). | Room `@Fts4`, Kotlin Coroutines |

---

## Estrutura do Projeto

```text
s_Hub_App/
├── app/                        # Aplicação principal Android (:app)
│   └── src/main/kotlin/com/saile/shub/
│       ├── core/location/      # Utilitários compartilhados (CoreLocationUtils, CoreNetworkUtils)
│       ├── ui/                 # Shell sHub, HomeScreen (LruCache SVG) e HubToolRegistry
│       ├── converter/          # Módulo sGeoConversor (UI & ViewModel)
│       ├── photo/              # Módulo sFoto (CameraX OverlayEffect, EXIF, Galeria)
│       ├── gis/                # Módulo sGis (MapView, MBTiles Pipeline, Simbologia Elétrica)
│       └── normative/          # Motor de regras e banco normativo Room FTS4
├── core/
│   ├── geo/                    # Biblioteca Kotlin JVM pura de cálculos geodésicos (:core:geo)
│   └── gis/                    # Biblioteca Android de leitura GeoPackage, R-Tree e Sync (:core:gis)
├── tools/                      # Pipeline Python para planejamento de cobertura HD (UTM 20S/21S) e MBTiles
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

Consulte o [Índice Completo da Documentação Técnica em `docs/README.md`](./docs/README.md) para detalhes de arquitetura, ADRs, auditoria de desempenho e guias de expansão modular.

