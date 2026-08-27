# sFoto - Módulo de Câmera (app/photo)

Este módulo é responsável por toda a experiência de captura de fotos e vídeos com sobreposição (overlay) georreferenciada do aplicativo sHub.
A prioridade deste módulo é estabilidade em ambientes de campo (Offline-first) e alta legibilidade da marca d'água sob diversas condições de luz e uso (landscape/portrait).

## Responsabilidades dos Pacotes

### `camera`
- **`CameraPreview.kt`**: Integra a API CameraX ao Compose com foco por toque, flash e captura de alta qualidade. Usa `PreviewView` compatível (`TextureView`) para que a inversão corretiva da lente frontal alcance a imagem e o overlay; arquivos salvos não passam por ela.
- **`CameraPipelinePolicy.kt` e `OverlayFrameTransform.kt`**: Centralizam a orientação nativa da câmera e transformam somente o canvas do overlay usando os metadados do `Frame` (`rotationDegrees` e `isMirroring`) emitidos pelo CameraX.
- **`MediaUtils.kt`**: Lida com o salvamento das mídias usando o `MediaStore`, gerando arquivos no diretório `Movies/sHub` e `Pictures/sHub`. Também é responsável pela Intent nativa que abre a Galeria (`makeMainSelectorActivity`).
- **`CameraPreview.kt` e `OverlayRenderer.kt`**: Aplicam o overlay georreferenciado diretamente no pipeline CameraX, uma única vez tanto na prévia quanto no arquivo. Foto usa a máscara combinada suportada (`PREVIEW | VIDEO_CAPTURE | IMAGE_CAPTURE`) e vídeo usa `PREVIEW | VIDEO_CAPTURE`. Uma mesma régua de layout atende foto e vídeo; logos SVG são rasterizados no tamanho final e mantidos em cache para preservar nitidez durante a gravação. Fotos não são decodificadas nem recomprimidas no heap do aplicativo.

### `location`
- **`NetworkUtils.kt`**: Monitora se o aparelho possui conexão à internet usando o `ConnectivityManager`.
- **`LocationUtils.kt`**: Conversor simples de estados (ex: "Amazonas" para "AM") para otimização do espaço na tela.

### `ui`
- Contém todos os componentes visuais em Compose que flutuam sobre o preview da câmera.
- **`PhotoTopBar.kt` e `PhotoBottomBar.kt`**: Barras de controle (engrenagem, toggle de lente, galeria, disparo) desacopladas para manter a leitura do código principal limpa.
- **`ConfigDialog.kt`**: Tela de configurações contendo a lista de logos e opções dinâmicas da marca d'água.

## Fluxo Principal (`PhotoScreen.kt`)
Atua como a "cola" (Screen/ViewModel) que une:
1. Sensores (Giroscópio / Orientação para rotacionar ícones no UI)
2. GPS (Buscando location via `LocationManager.requestSingleUpdate`)
3. Geocoder Síncrono (`Dispatchers.IO`) com fallback para omissão de endereço offline, preservando as coordenadas UTM.
4. CameraX (Repasse dos estados de gravação e URI do arquivo para galeria).
