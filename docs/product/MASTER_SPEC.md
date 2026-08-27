# MASTER SPEC — sHub Field Tools

## Visão

`sHub` é um ecossistema Android de ferramentas de campo para geolocalização, documentação fotográfica e consulta espacial offline. O aplicativo deve ser confiável em áreas com sinal instável, rápido com grandes volumes de dados locais e simples para uso em trabalho real de campo.

## Componentes do ecossistema

### sHub

Aplicativo principal. Funciona como hub, tela inicial e ponto de entrada para as ferramentas.

### sGeoConversor

Conversor de coordenadas UTM ⇄ geográficas. Deve permitir:

- entrada UTM com zona, hemisfério, X/Easting e Y/Northing;
- entrada geográfica em decimal e, futuramente, DMS;
- saída UTM e GEO;
- cópia separada de UTM, GEO e link;
- geração de link clicável para Google Maps;
- botão de rota para abrir o Google Maps.

Exemplo de saída esperada:

```text
[ link ] https://maps.app.goo.gl/E66rT9KyqSjHF8SR8
[ UTM ] X: 683479.47, Y: 7460703.39
[ GEO ] -22.951761169561568, -43.21047133691749
```

### sFoto

Ferramenta de câmera estilo timestamp camera. Deve capturar fotos e gravar vídeos MP4 com overlay configurável gravado diretamente na mídia, em qualidade máxima suportada pelo aparelho. O overlay deve conter:

- Em uso horizontal, a prévia permanece estável dentro da tela bloqueada em retrato; foto e vídeo salvos recebem a orientação física correta, com cena, texto, ícones e logo legíveis e na mesma orientação.

- UTM;
- GEO;
- data e hora local;
- endereço aproximado quando disponível;
- nome do usuário;
- logo do aplicativo (configurável, importação dinâmica via arquivos `.svg` do diretório `artwork` renderizados nativamente em alta resolução, com posicionamento anti-colisão em relação ao texto);
- cor da fonte configurável.

*Nota de Status: O módulo sFoto foi concluído e estabilizado com arquitetura desacoplada. A renderização de logo agora suporta SVG dinâmico (infinitamente escalável).*

Cores mínimas do overlay:

- amarelo;
- verde;
- ciano;
- magenta;
- branco ou cor padrão de alto contraste.

Ao abrir uma foto ou vídeo na galeria interna, o painel de informações deve exibir os dados do overlay daquela mídia e permitir copiar o conjunto completo e gerar rota para as coordenadas no Google Maps.

### sGIS

Ferramenta GIS local inspirada em fluxo de campo do QField. Deve carregar arquivo `.gpkg` com grande volume de elementos, indexar dados e permitir busca por códigos como:

- `ptca0088`;
- `rlca0010`.

Ao localizar um elemento, o app deve centralizar a visualização, destacar o elemento e permitir:

- copiar todos os metadados;
- copiar coordenadas;
- gerar rota para Google Maps;
- abrir link clicável quando houver rede.

## Requisitos offline

- O app deve abrir e operar sem internet.
- Dados importados devem permanecer no dispositivo.
- O mapa deve funcionar com dados locais quando não houver internet.
- Camadas satélite online podem ser opcionais, nunca requisito obrigatório.
- O app deve avisar claramente quando uma ação depende de internet, como abrir rota no Google Maps.
- Permissões devem ser solicitadas no contexto da função: câmera/localização ao entrar no sFoto e áudio somente ao iniciar vídeo.

## Tecnologia

- Linguagem: Kotlin.
- UI: Material 3 + Jetpack Compose.
- Banco: SQLite local.
- Câmera: CameraX.
- Localização: Android Location APIs.
- Arquivos espaciais: GPKG/GeoPackage.
- Testes: unitários, instrumentados e validação ADB em telefone real.

## Princípios de design

- Interface moderna, escura por padrão, alto contraste e legível sob sol.
- Botões grandes para uso em campo.
- Feedback claro para copiar, gerar rota, importar, indexar e buscar.
- Não usar emoji na UI de produção; usar ícones PNG/SVG consistentes.
