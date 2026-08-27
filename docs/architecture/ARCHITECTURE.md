# Arquitetura — sHub Android Local-First

## Resumo

A arquitetura deve ser modular, offline-first e orientada a domínio. O aplicativo não precisa de microsserviços de rede. Em Android, a separação recomendada é por módulos Gradle e serviços internos controlados por use cases.

## Módulos atuais

```text
:app                 Shell, navegação, Home Hub (HomeScreen.kt), features (sGeoConversor, sFoto, sGIS UI) e tema Compose.
                     * Nota: HomeScreen.kt implementada com visual Material 3, cartões elevados e renderização dinâmica em tempo de execução dos ícones vetorizados de artwork/app_hub (sFoto, sGeoConversor, sGIS) em alta definição (512x512).
                     * Nota sFoto: sFoto UI foi totalmente desacoplada (PhotoTopBar, PhotoBottomBar, ConfigDialog) para legibilidade. O Geocoder roda em Dispatchers.IO (síncrono) para máxima estabilidade em ambientes com falha de conexão (Offline-first genuíno). Renderização de logos corporativas em SVG de qualidade infinita diretamente no Canvas fotográfico (AndroidSVG), com viewBoxes padronizados visualmente pela referência energisa.svg.
:core:geo            Lógica de conversão UTM/GEO (Funções puras e matemáticas)
:core:gis            sGIS backend local: GPKG, Room SQLite, busca espacial, use cases e domínios
```

## Fluxo geral

```text
Usuário
↓
Tela Compose
↓
ViewModel com StateFlow
↓
UseCase
↓
Repository
↓
DataSource local: SQLite, GPKG, Location, CameraX, Files
```

## Padrões obrigatórios

- ViewModels não acessam SQLite diretamente.
- Telas não executam regra de negócio.
- Repositories não formatam textos de UI.
- Conversão geográfica deve estar em módulo puro, testável sem Android.
- Importação GPKG deve ser feita em worker/coroutine com progresso e cancelamento.
- Toda busca em grande volume deve usar índice SQLite.

## Decisão sobre “microsserviço”

Não criar microserviços HTTP locais para a primeira versão. Isso aumentaria consumo, complexidade, risco de falha e problemas de ciclo de vida no Android. A alternativa correta é “microkernel modular local”: módulos separados, contratos claros, workers internos e filas locais.

## Armazenamento

- Banco principal: `shub.db`.
- Tabelas de app, histórico, arquivos importados, índice de busca e metadados.
- Arquivos GPKG originais preservados em diretório privado do app ou importados via SAF.
- Índices textuais normalizados para busca rápida.

## Mapa

A camada satélite deve ser tratada como provider opcional. Para offline real, o app deve suportar:

1. elementos vetoriais do GPKG renderizados localmente;
2. cache local de tiles quando permitido pela licença;
3. fallback sem basemap, mostrando grade, elementos e localização atual;
4. satélite online/cache somente quando permitido e disponível.

## Permissões Android

- Localização precisa/aproximada.
- Câmera.
- Leitura de arquivos via Storage Access Framework.
- Escrita de mídia conforme versão do Android.

## Performance

- Importação GPKG em background.
- Índices por código normalizado, tipo de elemento e bounding box.
- Paginação de resultados.
- Nunca carregar centenas de milhares de features inteiras na memória.
- Renderizar apenas viewport atual.

## Pipeline de mídia do sFoto

- A grade da galeria usa `LazyVerticalGrid`, chaves estáveis, `contentType` e miniaturas dimensionadas; não carregar arquivos em resolução original nas células.
- Fotos da grade usam Coil sem crossfade ou subcomposição, com concorrência de decodificação limitada.
- Foto usa `CAPTURE_MODE_MINIMIZE_LATENCY` com o seletor da maior resolução 4:3 disponível: a resolução permanece máxima, enquanto o pipeline prioriza a cadência de captura. Vídeo usa o mesmo seletor 4:3 no `Preview` e configura `Recorder.setAspectRatio(RATIO_4_3)`, com lista de qualidades da maior para a menor. Assim CameraX escolhe a maior combinação 4:3 suportada por câmera e encoder, sem retornar a 16:9. A mídia persistida recebe `OverlayEffect` sem decodificar ou recomprimir bitmaps no heap do aplicativo. CameraX 1.4.1 exige a máscara completa `IMAGE_CAPTURE|PREVIEW|VIDEO_CAPTURE` para foto; `Preview` e `ImageCapture` permanecem no mesmo `UseCaseGroup` e numa única chamada de vínculo, combinação validada pelo fornecedor da câmera. Vídeo usa o alvo `VIDEO_CAPTURE`.
- `CameraPipelinePolicy` define uma única convenção de campo: frontal e traseira mantêm a orientação real da cena tanto na prévia quanto na mídia salva. `Preview` e `VideoCapture` usam `MIRROR_MODE_OFF`. Como alguns fornecedores ainda espelham a superfície da lente frontal, somente o `PreviewView` frontal recebe uma inversão horizontal corretiva; ele é mantido no modo `COMPATIBLE` (`TextureView`), pois o `SurfaceView` do modo `PERFORMANCE` não aplica essa transformação de forma confiável. JPEG e MP4 não passam por essa `View` e permanecem sem espelho.
- Foto e vídeo usam o mesmo `OverlayEffect` para prévia e arquivo, portanto o overlay é desenhado uma única vez por frame. `OverlayLayoutMetrics` é a única régua de tamanho, margem, quebra de texto e posição para os dois modos; não existe viewport, fator de escala ou regra de overlay exclusiva de vídeo. A prévia de câmera usa `FILL_CENTER` nos dois modos; a galeria usa `FIT` para fotos e vídeos. A geometria vem de `Frame.rotationDegrees` e `Frame.isMirroring`, metadados emitidos pelo CameraX para aquele frame; o app aplica a transformação inversa somente ao canvas do overlay. Isso preserva texto e logo legíveis mesmo se um fabricante informar espelhamento no pipeline.
- O logo do timestamp tem altura de 6% da menor dimensão do frame, compartilhada por foto e vídeo. `PhotoTopBar` respeita a área de status e `PhotoBottomBar` respeita a navegação do sistema; as duas barras têm fundo opaco e o preview é recortado aos seus próprios limites, sem mídia ou overlay sob os controles.
- Logos SVG são carregados uma vez por ativo e rasterizados em cache exatamente no tamanho final do frame. Isso evita reabrir/renderizar o SVG em cada frame de vídeo e preserva nitidez e visibilidade no preview e no MP4.
- `PhotoCapture` mantém referências aos três casos de uso ativos (`Preview`, `ImageCapture` e `VideoCapture`), permitindo que a rotação de destino seja atualizada para todos sem recriar a sessão de câmera.
- `CameraPreview` é somente o coordenador Compose da câmera: mantém o `PreviewView`, acompanha o ciclo de vida e publica os casos de uso vinculados em `PhotoCapture`.
- `CameraSessionBinder` cria e vincula `Preview`, `ImageCapture` ou `VideoCapture`, aplica a política de lente, resolução 4:3, foco contínuo e estado inicial da lanterna. As diferenças nativas entre foto e vídeo ficam restritas a esse componente.
- `CameraOverlayEffectFactory` contém o único caminho de composição do overlay nos frames de foto e vídeo. `CameraFocusOverlay` isola foco/exposição por toque e seu marcador visual transitório.
- Como a Activity permanece bloqueada em retrato, o `Preview.targetRotation` acompanha somente a rotação fixa da tela e nunca o sensor físico. `CameraOutputRotation` converte o `OrientationEventListener` no sentido exigido pelo CameraX (90° físico → `ROTATION_270`; 270° físico → `ROTATION_90`) e atualiza apenas `ImageCapture` e `VideoCapture`. Assim a prévia e o overlay não giram dentro do viewport, enquanto JPEG e MP4 recebem a orientação correta.
- Pressionar e segurar o disparador inicia captura contínua após o limiar de toque longo. As fotos são submetidas sequencialmente para não sobrescrever o instantâneo do overlay; metadados são persistidos em um executor separado para não prolongar o intervalo entre disparos. Nomes incluem milissegundos para evitar colisões.
- `CameraFocusOverlay` usa a fábrica de pontos do `PreviewView` para foco e exposição por toque. A ação expira após três segundos e devolve a câmera ao foco contínuo.
- O pager mantém apenas a página anterior, atual e próxima. Fotos usam uma única camada visual.
- Vídeos não reproduzem automaticamente. O ExoPlayer só é preparado após ação explícita no botão de reprodução e usa `REPEAT_MODE_OFF`.
- A prévia de vídeo em tela cheia vem de `MediaMetadataRetriever`; ela permanece visível até `onRenderedFirstFrame()` e então faz uma dissolução curta para o vídeo.
- Ao pausar, manter o `PlayerView` montado para preservar o frame atual. Ao trocar de página ou fechar a galeria, liberar o estado preparado.
- Fluidez Compose deve ser validada com a variante `benchmark`, não depurável e otimizada com R8. A variante `debug` não representa desempenho de produção.
- Em galerias longas, a grade oferece trilho lateral de rolagem rápida com alvo de toque amplo, puxador circular com setas para cima/baixo e balão de mês/ano durante o arraste. Os saltos cancelam o pedido anterior para não acumular coroutines nem decodificações desnecessárias.
- Consulta, atualização e exclusão no `MediaStore` executam em `Dispatchers.IO`. Android 10+ filtra as pastas do sHub por `RELATIVE_PATH`; `DATA` permanece apenas como compatibilidade pré-Android 10.
- Após a primeira seleção por toque longo, há uma caixa para selecionar ou desmarcar todas as mídias antes de compartilhar ou excluir.
- Cada nova foto ou vídeo possui uma ficha SQLite local associada à URI com o instantâneo dos dados do overlay na captura. A galeria usa essa ficha para copiar os campos e abrir rota pelas coordenadas, sem OCR nem dependência da localização atual.

## Ciclo de vida da câmera e lanterna

- `PhotoScreen` calcula o estado desejado da lanterna: tela de captura visível, modo vídeo, câmera traseira e flash `ON`.
- `CameraPreview` aplica mudanças desse estado ao controle atualmente vinculado; `CameraSessionBinder` aplica o valor inicial logo após cada `bindToLifecycle`.
- Galeria, configurações, troca para câmera frontal/foto e saída do sFoto devem desligar a lanterna.
- Ao descartar o sFoto, executar `enableTorch(false)`, `unbindAll()` e encerrar recursos auxiliares da câmera.
- O estado visual da gravação segue os eventos `VideoRecordEvent.Start` e `Finalize`; iniciar e parar são estados transitórios que bloqueiam novos comandos até a confirmação da CameraX.
- Falha de vínculo limpa os casos de uso obsoletos, apresenta mensagem ao usuário e oferece nova tentativa explícita.

## Permissões do sFoto

- O hub não solicita permissões.
- Câmera é solicitada ao entrar no sFoto; localização é solicitada depois da câmera para preencher o overlay.
- Áudio é solicitado somente na primeira tentativa de gravar vídeo. Se negado, o vídeo continua sem áudio.
