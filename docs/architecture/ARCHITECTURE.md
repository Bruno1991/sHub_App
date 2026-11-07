# Arquitetura do Sistema — sHub

**Data:** 2026-11-07  
**Autor:** Argon (Principal Software Architect & GIS Specialist)

---

## 1. Visão Geral

O **sHub** é uma plataforma modular Android de ferramentas de engenharia de campo (*field-first* / *100% offline-first*) projetada para operação em ambientes remotos da região Amazônica e Rondônia (inspeção de redes elétricas de Média e Baixa Tensão, subestações, atendimento comercial por UC/Medidor, georreferenciamento e registro fotográfico auditável).

O ecossistema concentra-se em dois módulos altamente integrados na Home: **`sFoto`** e **`sGIS`** (com o antigo `sGeoConversor` agora 100% integrado dentro do `sGIS`).

```mermaid
flowchart TD
    subgraph UI["Camada de Apresentação (:app - Jetpack Compose + Estado Escopado à Activity)"]
        SHubApp["SHubApp & HubToolRegistry (sFoto ⇄ sGIS)"]
        HomeScreen["HomeScreen (SVG LruCache)"]
        PhotoUI["sFoto UI (PhotoScreen/VM + CameraX 1.4)"]
        GisUI["sGIS UI (GisScreen/VM + MapView 120 FPS + Conversor UTM/GEO Integrado)"]
    end

    subgraph CoreApp["Serviços Compartilhados & Operacionais (:app)"]
        CoreLoc["CoreLocationUtils & CoreNetworkUtils"]
        GisRasters["MBTiles Raster Pipeline (Composite / ControlledFallback / Sync GPKG)"]
        GisOps["Inspeção Offline (KML/CSV) & Navegação Rural + Tracklog"]
    end

    subgraph CoreModules["Módulos de Domínio Independentes"]
        CoreGis[":core:gis (GeoPackage R-Tree, Clientes JSON->SQLite, Busca Unificada)"]
        CoreGeo[":core:geo (Pure Kotlin WGS84 / UTM Snyder Engine)"]
    end

    SHubApp --> HomeScreen
    HomeScreen --> PhotoUI
    HomeScreen --> GisUI
    GisUI <-->|"Fotografar Ativo / Camada de Fotos"| PhotoUI

    PhotoUI --> CoreLoc
    PhotoUI --> CoreGeo
    GisUI --> CoreLoc
    GisUI --> GisRasters
    GisUI --> GisOps
    GisUI --> CoreGis
    GisUI --> CoreGeo
    CoreGis --> CoreGeo
```

---

## 2. Princípios Arquiteturais

1. **Ecossistema Integrado e Preservação de Estado (`sGIS ⇄ sFoto`):**
   - `GisViewModel` e `PhotoViewModel` são escopados à Activity em `SHubApp.kt`, preservando a posição exata da câmera do mapa (`savedCenterLat`, `savedCenterLon`, `savedZoomLevel`) e o elemento selecionado ao alternar entre o `sGIS` e o `sFoto`.
   - Ao tocar em **"Fotografar sFoto"** no card de um ativo ou cliente no `sGIS`, o `PhotoViewModel` recebe automaticamente o carimbo técnico (`Código/UC/Medidor + Camada/Subtítulo`) e retorna diretamente ao `sGIS` após a captura.
   - As fotos georreferenciadas tiradas no `sFoto` (`PhotoMediaMetadataDbHelper`) são exibidas como camada interativa no mapa do `sGIS`.

2. **Renderização Vetorial a 120 FPS (Offscreen Bitmap Layer Caching):**
   - Em `GisVectorOverlay.kt`, os milhares de vãos MT/BT e símbolos elétricos do `.gpkg` são renderizados em um *Offscreen Bitmap Buffer* indexado pelo `BoundingBox`, nível de zoom e versão dos elementos, garantindo fluidez instantânea durante o arrasto do mapa.

3. **Busca Unificada (Rede `.gpkg` + Clientes `.json` + Coordenadas `GEO/DMS/UTM`):**
   - `GisClientsHelper.kt` converte arquivos `.json` de clientes (175.000+ UCs) via streaming (`JsonReader`) para um banco SQLite indexado (`gis_clients.db`), permitindo busca `< 1 ms` por **UC/CDC**, **Medidor** ou **Nome do Cliente** simultaneamente aos ativos `.gpkg` e coordenadas.
