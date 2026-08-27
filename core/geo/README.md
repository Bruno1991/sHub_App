# Módulo Core: Geo (`:core:geo`)

Este módulo é responsável pelos cálculos matemáticos complexos de geografia, essenciais para as operações de campo do sHub.

## Objetivo e Restrições
- **Isolamento Total**: O código dentro de `:core:geo` não deve possuir dependências pesadas do Android (como `Context`, `Views` ou `Compose`). Ele é escrito majoritariamente em Kotlin puro.
- Isso permite testabilidade imediata via testes unitários (JUnit) muito velozes, já que são funções puramente matemáticas.

## Responsabilidades
- **Conversão UTM ↔ Lat/Lon**: Lógica vital para a câmera (sFoto) e para o mapa (sGIS). Permite que os operadores insiram coordenadas em diferentes sistemas e recebam resultados precisos, lidando corretamente com Zonas UTM, Hemisférios e Fuso (Bandas).
- **Utilitários Matemáticos**: Fórmulas de Haversine para distâncias entre dois pontos (usado no SGIS para calcular distância até um elemento da base de dados), e checagens de validação.

## Estrutura
- `GeoCoordinate`: Modelo base para Latitude e Longitude.
- `UtmCoordinate`: Modelo base para coordenadas Easting, Northing, Zona e Hemisfério.
- `UtmConverter`: Objeto Singleton de cálculos densos que abstraem e fornecem APIs prontas (`geoToUtm`, `utmToGeo`) para as camadas de apresentação (`:app`).
