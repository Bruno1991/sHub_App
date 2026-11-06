# Documentação Técnica do Módulo — sGis

**Data:** 2026-11-06
**Módulos:** `:core:gis`, `:app` (`com.saile.shub.gis`) e `tools/` (Pipeline Python Espacial)

---

## 1. Responsabilidade

O **sGis** é o Sistema de Informações Geográficas (GIS) móvel 100% offline do **sHub**, projetado para inspeção de redes de distribuição e subtransmissão elétrica em Rondônia e na Amazônia. Combina renderização vetorial de alta velocidade sobre **GeoPackage (`.gpkg`)** com camadas raster de satélite em múltiplos níveis via **MBTiles (`.mbtiles`)**.

---

## 2. Componentes Principais

### 2.1 Motor Android (`:core:gis` & `com.saile.shub.gis`)

| Arquivo | Responsabilidade |
|---|---|
| `GpkgReader.kt` | Leitura imparcial de qualquer arquivo `.gpkg` importado, inspeção dinâmica de SRS (`gpkg_contents`), decodificação WKB (`POINT`, `LINESTRING`, `POLYGON`, `MULTILINESTRING`) e filtragem espacial instantânea via índice SQLite R-Tree (`rtree_<tabela>_<geom>`). |
| `GisBasemapHelper.kt` | Configuração enxuta de camadas de mapa base restrita exclusivamente a `MBTiles Offline (.mbtiles)` (`OFFLINE_MBTILES`) e `Mapa de Ruas (OSM)` (`STREETS`). |
| `GisMbtilesHelper.kt` | `RobustMbtilesArchive`: leitor SQLite `.mbtiles` imparcial com detecção do esquema (`tms` vs `xyz`) via tabela `metadata`, evitando falsos positivos de hemisfério oposto em *cache misses*. |
| `CompositeMbtilesArchive.kt` | Composição hierárquica de múltiplos arquivos `.mbtiles` priorizando o arquivo selecionado pelo operador. |
| `ControlledFallbackArchive.kt` | Síntese controlada de tiles por *overzoom* até `maxDownscaleLevels = 8` níveis acima do nível nativo, recortando matematicamente o sub-quadrante exato (`cropAndScaleQuadrant`) com filtragem bilinear e cache `LruCache` de tiles ancestrais. |
| `GisNetworkMode.kt` | Política explícita de rede (`OFFLINE_ONLY` para `.mbtiles`, `HYBRID_CACHE` para `Mapa de Ruas (OSM)`). |
| `PackageManifest.kt` & `OfflinePackageManager.kt` | Manifesto JSON puro (`manifest.json`), verificação de integridade SHA-256 e instalação atômica via diretório `.staging`. |
| `GisLocationTracker.kt` | Rastreamento GNSS (priorizando GPS sobre Rede) + bússola com filtro passa-baixa e *deadband* de `2.0°`, desacoplado via `CoreLocationUtils`. |
| `GisElectricalSymbology.kt` | Simbologia técnica para Postes, Transformadores, Chaves Seccionadoras/Fusíveis, Subestações e Vãos MT/BT. |

### 2.2 Ferramentas Python de Planejamento e Empacotamento (`tools/`)

| Script | Responsabilidade |
|---|---|
| `tools/spatial_coverage_planner.py` | Inspeciona o GeoPackage da rede elétrica, reprojeta cada ativo para a zona UTM métrica correta (**UTM 20S `EPSG:31980`** para `lon < -60°` ou **UTM 21S `EPSG:31981`** para `lon >= -60°`), aplica buffers configuráveis (Postes 100 m, Trafos 150 m, Chaves 150 m, Vãos MT 200 m, Vãos BT 150 m, Subestações 400 m) e exporta a cobertura consolidada. |
| `tools/slippy_map_math.py` | Matemática Slippy Map Web Mercator (`EPSG:3857`) e enumeração otimizada de tiles via índice espacial `STRtree` (`geometry_to_tiles`). |
| `tools/mbtiles_pipeline.py` | Geração, validação e auditoria de arquivos `.mbtiles` (`PRAGMA integrity_check`) e criação de pacotes offline com manifesto SHA-256. |

