# Estratégia Offline-First

## Regra central

O app deve ser útil sem internet. Internet é melhoria, não dependência.

## sGeoConversor offline

Conversões UTM/GEO são 100% locais. Link Google Maps pode ser gerado offline como texto, mas abrir rota depende de app externo e conectividade.

## Modos de Operação

Foto, overlay (em imagens), legendas de vídeo (.srt) e coordenadas GPS funcionam offline. Endereço textual pode depender de geocoder online; por isso o app deve permitir salvar sem endereço ou usar último endereço resolvido.

## sGIS offline

O GPKG é a fonte principal. Busca, metadados, geometria e navegação até elemento devem funcionar sem rede.

## Mapa satélite

Camada satélite exige atenção a licença e cache. A primeira versão deve permitir:

- modo sem basemap: fundo escuro, grade, localização e elementos;
- modo tiles locais: MBTiles/tiles permitidos;
- modo satélite online opcional se houver provider permitido.

Google Maps pode ser usado para rota externa, mas não como camada embutida offline do app.

## Sincronização

Não há sincronização obrigatória na V1. Tudo é local no telefone.
