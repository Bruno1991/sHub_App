# Documentação Técnica do Módulo — sGIS (QField Pro Edition)

**Data:** 2026-11-07
**Módulos:** `:core:gis`, `:core:geo`, `:app` (`com.saile.shub.gis`) e `tools/` (Pipeline Python Espacial)

---

## 1. Responsabilidade

O **sGIS** é o Sistema de Informações Geográficas (GIS) móvel 100% offline do **sHub**, projetado para inspeção de redes de distribuição e subtransmissão elétrica e atendimento comercial/técnico em Rondônia e na Amazônia. Integra:
1. Renderização vetorial a **120 FPS (Offscreen Bitmap Layer Caching)** sobre **GeoPackage (`.gpkg`)** indexado por R-Tree;
2. Importação e busca ultrarrápida de **Base de Clientes (`.json`)** indexada em SQLite (`gis_clients.db`) por **UC/CDC**, **Medidor** ou **Nome do Cliente**;
3. **Conversor Geodésico Integrado (`UTM ⇄ GEO / DMS`)** com re-projeção entre zonas (`20S ⇄ 21S`) e localização direta no mapa em Zoom 18;
4. Integração bidirecional **`sGIS ⇄ sFoto`** (botão *"Fotografar sFoto"* com carimbo automático do ativo/cliente + camada de fotos georreferenciadas no mapa);
5. **Inspeção e Apontamento de Anomalias Offline** (`GisFieldInspectionHelper.kt`) com exportação e compartilhamento em **KML** e **CSV**;
6. **Navegação Rural "Fora de Estrada"** com linha guia dinâmica (distância e azimute em tempo real) + gravação de **Rastro da Viatura (Tracklog GPS)**;
7. Camada raster híbrida **MBTiles (`.mbtiles`)** multinível + **Sincronizador de Satélite Guiado pelo `.gpkg`** direto no aplicativo (`OfflinePackageManager.syncGpkgAreaTilesToMbtiles`).

---

## 2. Componentes Principais

### 2.1 Motor Android (`:core:gis` & `com.saile.shub.gis`)

| Arquivo | Responsabilidade |
|---|---|
| `GpkgReader.kt` | Leitura imparcial de qualquer arquivo `.gpkg` importado, inspeção dinâmica de SRS (`gpkg_contents`), decodificação WKB e filtragem espacial instantânea via índice SQLite R-Tree (`rtree_<tabela>_<geom>`). |
| `GisClientsHelper.kt` | Importador streaming (`JsonReader`) de bases de clientes `.json` (175.000+ UCs com `< 5 MB` de RAM) para banco SQLite indexado (`gis_clients.db`), permitindo busca `< 1 ms` por UC/CDC, Medidor ou Nome e busca espacial de clientes próximos. |
| `CoordinateSearchParser.kt` & `SearchFeatureUseCase.kt` | Busca unificada simultânea por ativos da rede (`.gpkg`), clientes/UCs/medidores (`.json`), coordenadas Geográficas (Decimais e GMS/DMS) e coordenadas UTM (com zona explícita ou padrão `20S`). |
| `GisVectorOverlay.kt` | Renderizador vetorial com **Offscreen Bitmap Layer Cache (120 FPS)** para linhas (Vão MT/BT) e símbolos elétricos, além de marcadores interativos para **Fotos do `sFoto`**, **Anomalias de Campo**, **Rastro GPS (Tracklog)** e **Linha Guia Rural**. |
| `GisFieldInspectionHelper.kt` | Persistência local e exportação em **KML** (Google Earth/QGIS) e **CSV** (Excel) de apontamentos e anomalias de campo via `FileProvider`. |
| `GisNavigationHelper.kt` & `GisLocationTracker.kt` | Cálculo geodésico Haversine de distância e azimute magnético/verdadeiro para navegação rural fora de estrada (`RuralGuidanceInfo`), gravação de *tracklog* da viatura e lançamento de rotas externas (Google Maps / Waze). |
| `OfflinePackageManager.kt` | Manifesto JSON (`manifest.json`), verificação SHA-256 e sincronizador embarcado (`syncGpkgAreaTilesToMbtiles`) que baixa em segundo plano apenas os tiles `lyrs=y&hl=pt-BR` necessários para cobrir os ativos do `.gpkg` ativo. |
| `GisMbtilesHelper.kt`, `CompositeMbtilesArchive.kt` & `ControlledFallbackArchive.kt` | Pipeline de leitura `.mbtiles` imparcial (`tms`/`xyz`) com composição de múltiplos arquivos e *overzoom* matemático por recorte de sub-quadrante até Zoom 22. |

### 2.2 Ferramentas Python de Planejamento e Empacotamento (`tools/`)

| Script | Responsabilidade |
|---|---|
| `tools/build_google_hybrid_rondonia.py` | Construtor de alta velocidade para geração de pacotes `.mbtiles` Google Hybrid (`lyrs=y&hl=pt-BR`, Satélite + Rodovias + Nomes em Português) guiados pela cobertura dos ativos do `.gpkg` (Zooms 7–15 estadual + Zooms 16–17 sobre a rede elétrica). |
| `tools/spatial_coverage_planner.py` | Reprojeta ativos para UTM métrico (**UTM 20S `EPSG:31980`** / **UTM 21S `EPSG:31981`**) e calcula buffers espaciais. |
| `tools/mbtiles_pipeline.py` | Validação e auditoria de arquivos `.mbtiles` (`PRAGMA integrity_check`). |
