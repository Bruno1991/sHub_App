# Módulo Core: GIS (`:core:gis`)

Este módulo abriga toda a complexidade geoespacial, leitura de bancos locais e renderização de mapas offline do sHub (Sistema de Trabalho de Campo).

## Objetivo
Atender ao pilar `sGIS` do aplicativo, que exige que o usuário consiga abrir dados de infraestrutura (GeoPackage - SQLite/GPKG) totalmente offline, sobrepostos em um mapa, permitindo cliques nos elementos e busca rápida.

## Responsabilidades
- **Manipulação de GPKG**: Leitura crua e eficiente de bancos de dados GeoPackage (SQLite com extensão espacial). O módulo precisa transformar BLOBs de geometria em coordenadas utilizáveis (Polígonos, Linhas, Pontos).
- **Map Viewer**: Controle da tela de mapas, seja via biblioteca nativa ou MapLibre/Osmdroid, garantindo que o operador visualize o próprio trajeto e a base de dados em campo.
- **Camada de Busca Espacial**: Permitir ao usuário digitar "ptca0088" na barra de busca e, usando queries otimizadas (`RTree` / Índices Espaciais), centralizar a câmera do mapa diretamente na coordenada.

## Relacionamento
- Interage constantemente com `:core:geo` para projetar coordenadas no sistema do mapa.
- Fornece sua UI (`Composable` Map Viewer) para ser importado dentro do `:app`, onde é de fato montado na `BottomNavigation` do app inteiro.
