# Estratégia Offline-First — sHub / sGis

## 1. Regra Central

O aplicativo deve ser 100% autossuficiente e funcional sem conectividade de rede. Internet é melhoria acessória em base de escritório, jamais dependência em campo.

---

## 2. Subsistema GIS Móvel (sGis)

### 2.1 Dados Vetoriais (GeoPackage)
- **Fonte Principal:** Arquivo `.gpkg` armazenado no armazenamento interno do app.
- **Consultas Espaciais:** Executadas localmente via SQLite / R-Tree em thread de background (I/O fora da UI thread).
- **Heterogeneidade de CRS:** Suporte a dados projetados (SIRGAS 2000 / UTM 20S - EPSG:31980 e UTM 21S - EPSG:31981) e geodésicos (WGS84 - EPSG:4326 e SIRGAS 2000 - EPSG:4674).

### 2.2 Camada Raster de Satélite (MBTiles 1.3)
- **Padrão:** SQLite no formato MBTiles 1.3 com tabela `tiles` em coordenadas TMS estritas (`tms_y = (1 shl zoom) - 1 - y`) e índice espacial único `(zoom_level, tile_column, tile_row)`.
- **Modo de Operação (`GisNetworkMode`):**
  - `OFFLINE_ONLY`: Bloqueia downloaders e requisições HTTP, servindo apenas o catálogo de pacotes MBTiles locais.
  - `ONLINE_ALLOWED`: Permite provedores externos apenas sob autorização explícita do operador.
- **Cascata Multipacotes Determinística (`CompositeMbtilesArchive`):**
  Precedência estrita:
  1. *Corredores e Ativos HD* (`network_corridors_hd.mbtiles` - prioridade 1);
  2. *Polo Municipal HD* (`cacoal_hd.mbtiles` - prioridade 2);
  3. *Cobertura Regional* (`rondonia_regional.mbtiles` - prioridade 3);
  4. *Base Mundial* (`world_base.mbtiles` - prioridade 4).
- **Fallback de Zoom Controlado (`ControlledFallbackArchive`):**
  - Bloqueia ampliação indiscriminada de zooms baixos (ex: z10 nunca alimenta z18).
  - Permite fallback de até 2 níveis (z17 -> z18 em escala 2x; z16 -> z18 em escala 4x).
  - Ausência de ladrilhos dentro do limite resulta em fundo neutro suave sem artefatos ou quadriculados.

### 2.3 Gestão e Validação de Pacotes (`OfflinePackageManager`)
- Instalação atômica com arquivo temporário e backup.
- Verificação de hash criptográfico SHA-256 contra manifesto.
- Validação estrutural via `PRAGMA quick_check` e amostragem de decodificação de imagem.

---

## 3. sGeoConversor e Ferramentas Auxiliares

- Conversões métricas UTM $\leftrightarrow$ GEO são 100% locais no dispositivo.
- Foto, medições na tela, legendas de vídeo (.srt) e coordenadas GPS operam sem rede.
