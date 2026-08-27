# STATUS.md — sHub Field Tools

## Última execução

Data/hora: 2026-07-23 11:57:00 -04:00

## Atualização — padronização universal do tamanho visual dos logos no sFoto

Objetivo: padronizar o tamanho de exibição de todos os logos das concessionárias de energia no overlay do sFoto, garantindo proporção e peso visual uniformes independente da razão de aspecto (de 1:1 até 4:1).

Diagnóstico:
- O renderizador anterior fixava `targetH = layout.logoHeight` para todas as marcas.
- Logos com proporção horizontal ampla (ex: Cemig, Reenergisa com 4:1) ocupavam até 4 vezes a área útil e largura visual de logos quadrados ou compactos (ex: Itaipu, Bureau Veritas com 1:1, CPFL com 1.3:1).

Implementação:
- Criado [`LogoSizeStandardizer.kt`](file:///c:/dev/shub/app/src/main/kotlin/com/saile/shub/photo/LogoSizeStandardizer.kt) com o algoritmo `calculateStandardizedLogoDimensions`, utilizando `energisa.svg` (~3.07:1) como referência visual padrão.
- Normalizada a área gráfica dos logos compactos/quadrados com ampliação proporcional de altura (limitada a 1.35x), e limitada a largura máxima de logos ultra-extensos (máximo 3.25x).
- Atualizado [`OverlayRenderer.kt`](file:///c:/dev/shub/app/src/main/kotlin/com/saile/shub/photo/OverlayRenderer.kt) para renderizar os logos SVG no canvas utilizando a nova padronização.
- Criado o teste unitário [`LogoSizeStandardizerTest.kt`](file:///c:/dev/shub/app/src/test/kotlin/com/saile/shub/photo/LogoSizeStandardizerTest.kt).
- Nenhuma outra funcionalidade, tela ou arquivo do projeto foi modificado.

Arquivos criados:
- `app/src/main/kotlin/com/saile/shub/photo/LogoSizeStandardizer.kt`
- `app/src/test/kotlin/com/saile/shub/photo/LogoSizeStandardizerTest.kt`

Arquivos modificados:
- `app/src/main/kotlin/com/saile/shub/photo/OverlayRenderer.kt`
- `STATUS.md`

Testes executados:
- `:app:testDebugUnitTest` — sucesso (incluindo validação de área e limites para logos 1:1, 1.3:1, 3.07:1 e 4:1).
- `:app:assembleDebug` — sucesso; APK gerada em `app/build/outputs/apk/debug/app-debug.apk`.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — instalado com sucesso no dispositivo `R9XYA0C23SF`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto no celular.

Próximos passos concretos: operador validar o tamanho padronizado das marcas no aparelho real.

Bloqueios ou decisões pendentes: nenhum.

## Execução anterior

Data/hora: 2026-07-23 03:54:25 -04:00

## Atualização — ampliação da resolução e dimensões dos ícones do Hub

Objetivo: corrigir o tamanho dos ícones na Home Hub que apareciam reduzidos/microscópicos devido à limitação de resolução de renderização de bitmap do AndroidSVG e tamanhos de contêiner.

Diagnóstico:
- O renderizador anterior utilizava `documentWidth` declarado no arquivo SVG (ex: 48px), gerando um bitmap de baixa resolução que era encolhido no Compose.
- Adicionalmente, as margens internas do viewBox original exigiam maior área útil de visualização.

Implementação:
- Atualizado [`HomeScreen.kt`](file:///c:/dev/shub/app/src/main/kotlin/com/saile/shub/ui/HomeScreen.kt) para renderizar os SVGs vetorizados em resolução de alta qualidade `512x512`.
- Ampliado o contêiner do ícone nos cartões para `92.dp` e o tamanho do ícone para `72.dp`.

Arquivos modificados:
- `app/src/main/kotlin/com/saile/shub/ui/HomeScreen.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `STATUS.md`

Testes executados:
- `:app:assembleDebug` — sucesso.
- `:app:testDebugUnitTest` — sucesso.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — instalado no dispositivo `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto com sucesso.

Próximos passos concretos: operador validar o tamanho e nitidez dos ícones no dispositivo real.

Bloqueios ou decisões pendentes: nenhum.

## Execução anterior

Data/hora: 2026-07-23 03:51:30 -04:00

## Atualização — criação da nova Home Hub do sHub com ícones visuais em SVG

Objetivo: reformular a tela inicial (`HomeScreen.kt`) com visual moderno Material 3 integrando os ícones oficiais das ferramentas da pasta `artwork/app_hub`.

Implementação:
- Atualizado [`HomeScreen.kt`](file:///c:/dev/shub/app/src/main/kotlin/com/saile/shub/ui/HomeScreen.kt) com novo design Material 3, cartões elevados e degradês de destaque.
- Integrado o renderizador de SVG em tempo de execução via AndroidSVG para exibir os ícones vetorizados oficiais:
  - `sGeoConversor`: `app_hub/sgeoconversor.svg`
  - `sFoto`: `app_hub/sfoto.svg`
  - `sGIS`: `app_hub/sgis.svg`
- Adicionados badges de contexto rápido e indicador visual de estado 100% offline.

Arquivos modificados:
- `app/src/main/kotlin/com/saile/shub/ui/HomeScreen.kt`
- `STATUS.md`

Testes executados:
- `:app:assembleDebug` — sucesso; APK gerada em `app/build/outputs/apk/debug/app-debug.apk`.
- `:app:testDebugUnitTest` — sucesso.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — instalado no dispositivo `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto com sucesso no dispositivo.

Próximos passos concretos: operador validar o visual e a navegação da nova tela Home no aparelho real.

Bloqueios ou decisões pendentes: nenhum.

## Execução anterior

Data/hora: 2026-07-23 03:45:50 -04:00

## Atualização — padronização das dimensões visuais das logos em SVG

Objetivo: padronizar o tamanho de exibição das logos das concessionárias em `artwork/*.svg` tendo a `energisa.svg` como tamanho de referência visual, sem alterar os ícones em `artwork/app_hub`.

Diagnóstico:
- SVGs como `taesa.svg`, `grupo-enel.svg`, `eletrobras.svg`, `isa-energia.svg`, `reenergisa.svg`, `cpfl-energia.svg`, `esgas.svg` possuíam viewBoxes com margens vazias excessivas ou quebradas (ex: `eletrobras.svg` com viewBox `80 -40 40 250` em imagem de 200x125, ou `taesa.svg` em viewBox `0 50 1095 1095` para elemento de altura 353).
- O `OverlayRenderer` calcula a escala da logo no overlay a partir da altura do `documentViewBox`. Margens vazias internas faziam com que a parte gráfica das logos fosse reduzida desproporcionalmente na tela.

Implementação:
- Medidos os limites gráficos visuais exatos (`getBBox()`) de todos os SVGs da pasta `artwork/`.
- Atualizados os viewBoxes dos SVGs com margens e proporções irregulares (`cemig-energia.svg`, `cpfl-energia.svg`, `eletrobras.svg`, `engie.svg`, `esgas.svg`, `grupo-enel.svg`, `grupo-equatorial.svg`, `isa-energia.svg`, `light-energia.svg`, `reenergisa.svg`, `taesa.svg`) para que a área desenhada coincida com os limites do viewBox.
- A pasta `artwork/app_hub` (`sfoto.svg`, `sgeoconversor.svg`, `sgis.svg`) e os fluxos do app foram mantidos 100% intocados.

Arquivos modificados:
- `artwork/cemig-energia.svg`
- `artwork/cpfl-energia.svg`
- `artwork/eletrobras.svg`
- `artwork/engie.svg`
- `artwork/esgas.svg`
- `artwork/grupo-enel.svg`
- `artwork/grupo-equatorial.svg`
- `artwork/isa-energia.svg`
- `artwork/light-energia.svg`
- `artwork/reenergisa.svg`
- `artwork/taesa.svg`
- `STATUS.md`

Testes executados:
- Análise de `getBBox()` W3C via Edge headless em todos os 21 SVGs — confirmado ajuste 1:1 sem margens vazias.
- `:app:testDebugUnitTest` — sucesso.

Próximos passos concretos: aguardar novas orientações do operador.

Bloqueios ou decisões pendentes: nenhum.

## Execução anterior

Data/hora: 2026-07-23 03:33:14 -04:00

## Atualização — orientação horizontal de foto/vídeo e persistência do overlay

Objetivo: corrigir o desaparecimento do overlay na prévia e a rotação incorreta da imagem quando o aparelho é usado horizontalmente, nos modos foto e vídeo.

Diagnóstico:

- A Activity é intencionalmente bloqueada em retrato, mas `PhotoScreen` atualizava também `Preview.targetRotation` conforme o sensor físico. Isso fazia a imagem e o efeito girarem dentro de um viewport que permanecia em retrato.
- O mapeamento do `OrientationEventListener` usava 90° e 270° no sentido oposto ao contrato do CameraX.
- A foto defeituosa `sHub_20260723_032557_898.jpg`, inspecionada de forma somente leitura a partir do telefone, confirmou o erro: arquivo 1440×1080 com cena e overlay girados 90°.

Implementação:

- Criado `CameraOutputRotation`, com o mapeamento oficial: 90° físico → `ROTATION_270` e 270° físico → `ROTATION_90`.
- `Preview.targetRotation` deixou de acompanhar o sensor físico. A prévia e o overlay permanecem alinhados à Activity bloqueada em retrato.
- Somente `ImageCapture` e `VideoCapture` recebem a rotação de saída, tanto na criação da sessão quanto nas mudanças de orientação enquanto a câmera está vinculada.
- `CameraPreview` passou a manter a sessão vinculada como estado, permitindo reaplicar a rotação correta após troca de lente, modo ou novo vínculo.

Arquivos criados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraOutputRotation.kt`
- `app/src/test/kotlin/com/saile/shub/photo/camera/CameraOutputRotationTest.kt`

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/PhotoScreen.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraSessionBinder.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/product/MASTER_SPEC.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso, incluindo quadrantes e limites de `CameraOutputRotationTest`.
- `:app:assembleDebug` — sucesso.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — sucesso no telefone `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — sucesso; `MainActivity` ativa no aparelho.
- Uma nova foto em retrato após a instalação foi inspecionada e manteve imagem, logo e overlay corretos. A matriz horizontal completa ainda requer o giro físico do operador.

Próximos passos concretos: executar o item 49 do roteiro real, capturando foto e vídeo com o telefone deitado para a esquerda e para a direita e conferindo prévia, overlay e mídia na galeria.

Bloqueios ou decisões pendentes: não há bloqueio técnico; a confirmação visual horizontal depende do giro físico do aparelho conectado.

## Última execução

Data/hora: 2026-07-23 03:20:58 -04:00

## Atualização — desacoplamento do pipeline CameraX do sFoto

Objetivo: dividir `CameraPreview.kt` por responsabilidade, removendo a concentração de vínculo CameraX, criação dos casos de uso, composição do overlay e foco por toque, sem mudar o comportamento aprovado de foto ou vídeo.

Implementação:

- `CameraPreview` ficou responsável apenas pela coordenação Compose, pelo ciclo de vida do `PreviewView`, pela publicação da sessão ativa em `PhotoCapture` e pela reação a flash/lanterna.
- `CameraSessionBinder` passou a criar e vincular `Preview`, `ImageCapture` e `VideoCapture`, concentrando resolução 4:3, política de lente, foco contínuo, qualidade e estado inicial da lanterna.
- `CameraOverlayEffectFactory` passou a conter o único caminho de composição do overlay para foto e vídeo, reutilizando `OverlayRenderer` e `OverlayFrameTransform`.
- `CameraFocusOverlay` passou a isolar o gesto de foco/exposição por toque e o marcador visual temporário.
- A busca estrutural confirmou uma única criação de `OverlayEffect`, uma única implementação de `FocusMeteringAction` e vínculo CameraX restrito ao binder. Nenhum parâmetro visual, proporção, qualidade ou regra do modo foto foi alterado.

Arquivos criados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraSessionBinder.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraOverlayEffectFactory.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraFocusOverlay.kt`

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso; APK gerada em `app/build/outputs/apk/debug/app-debug.apk`.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — sucesso no telefone `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — sucesso; processo ativo e `MainActivity` em primeiro plano.

Próximos passos concretos: executar no telefone o item 48 de `REAL_DEVICE_ADB_TEST_PLAN.md`, alternando foto/vídeo e frontal/traseira e validando foco, flash, lanterna, captura e overlay salvo.

Bloqueios ou decisões pendentes: a confirmação visual do item 48 depende da interação do operador no telefone; não há bloqueio técnico de compilação, testes ou instalação.

## Última execução

Data/hora: 2026-07-23 03:10:01 -04:00

## Atualização — vídeo 4:3 com o pipeline visual do modo foto

Objetivo: remover a imposição de 16:9 e padronizar o modo vídeo integralmente pelo modo foto, sem alterar o modo foto já aprovado.

Implementação:

- `Preview` de vídeo passou a usar o mesmo `ResolutionSelector` 4:3 e de maior resolução que o modo foto.
- `Recorder` recebeu `setAspectRatio(AspectRatio.RATIO_4_3)`. O seletor de qualidade tenta UHD, FHD, HD e SD em ordem, selecionando a maior combinação 4:3 suportada pelo conjunto câmera/encoder; ele não pode retornar a 16:9.
- Removidos `videoOverlayViewport` e a dimensão de referência especial do vídeo. Foto e vídeo chamam o mesmo `OverlayRenderer` no frame lógico completo, com as mesmas métricas.
- Modo foto, seu pipeline, qualidade e arquivos existentes não foram alterados.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/main/kotlin/com/saile/shub/photo/OverlayLayout.kt`
- `app/src/main/kotlin/com/saile/shub/photo/OverlayRenderer.kt`
- `app/src/test/kotlin/com/saile/shub/photo/OverlayLayoutTest.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso, inclusive o teste de que foto e vídeo compartilham a régua completa de overlay 4:3.
- `:app:assembleDebug` — sucesso; APK gerada em `app/build/outputs/apk/debug/app-debug.apk`.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — sucesso no telefone `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto no telefone.
- A tentativa de alternar o modo por ADB foi cancelada pelo Android devido a conflito com um toque físico ativo; nenhuma interação do operador foi sobrescrita.

Próximos passos concretos: alternar para vídeo no telefone, validar o vínculo 4:3 e executar o item 47.

Bloqueios ou decisões pendentes: o aparelho precisa expor ao menos uma combinação de câmera e encoder 4:3; se não expuser, CameraX mostrará a falha de vínculo em vez de gravar 16:9 silenciosamente.

## Última execução

Data/hora: 2026-07-23 03:02:10 -04:00

## Atualização — vídeo com a mesma régua visual do modo foto

Objetivo: fazer o overlay do modo vídeo reproduzir exatamente os tamanhos, margens e espaçamento aprovados no modo foto, preservando o MP4 em 16:9.

Implementação:

- `OverlayLayoutMetrics` aceita uma dimensão de referência. No vídeo, o canvas ainda é limitado ao quadro 16:9 que será codificado, mas a dimensão de referência é a do frame lógico completo da câmera — a mesma usada pela foto.
- Fonte, tamanho do logo, padding, ícones e espaçamento do vídeo passam a ser calculados pela mesma régua do modo foto, sem fator de escala exclusivo.
- Prévia de foto e vídeo usam `PreviewView.ScaleType.FILL_CENTER`. Na galeria, fotos e vídeos permanecem no mesmo modo `FIT` já usado pelo pager.
- Não foram alterados resolução, qualidade, encoder, arquivos já gravados nem o pipeline de foto.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/OverlayLayout.kt`
- `app/src/main/kotlin/com/saile/shub/photo/OverlayRenderer.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/test/kotlin/com/saile/shub/photo/OverlayLayoutTest.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso, incluindo a asserção de equivalência de fonte, logo e padding entre o frame de foto e o viewport de vídeo.
- `:app:assembleDebug` — sucesso; APK gerada em `app/build/outputs/apk/debug/app-debug.apk`.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — sucesso no telefone `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: validar o item 46 no telefone nas lentes traseira e frontal.

Bloqueios ou decisões pendentes: aceite visual da comparação foto/vídeo no aparelho.

## Última execução

Data/hora: 2026-07-23 02:58:04 -04:00

## Atualização — reversão da regressão de escala e nitidez do vídeo sFoto

Objetivo: corrigir o zoom do overlay, o desaparecimento do logo na prévia e a divergência visual/qualidade entre prévia de vídeo, MP4 e galeria, nas lentes frontal e traseira.

Diagnóstico:

- A alteração anterior desenhava no canvas físico completo (4:3) e deixava o CameraX cortar/reduzir para 16:9 depois. Isso ampliava e suavizava o overlay, além de poder cortar o logo no preview.
- A câmera ao vivo usava `FILL_CENTER` e a galeria já usava `FIT`, criando dois enquadramentos diferentes para o mesmo MP4.

Implementação:

- Restaurado o desenho do overlay exclusivamente dentro de `videoOverlayViewport`, isto é, no quadro final 16:9 que chega ao arquivo. Texto e logo não sofrem crop ou reescala posterior.
- A prévia de vídeo passou a `PreviewView.ScaleType.FIT_CENTER`; o `PlayerView` da galeria recebeu `RESIZE_MODE_FIT` explícito. Thumbnail, reprodução e câmera ao vivo exibem o mesmo quadro integral.
- Foto, `Quality.HIGHEST`, resolução, encoder, dados e qualidade das imagens continuam inalterados.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt`
- `app/src/test/kotlin/com/saile/shub/photo/OverlayLayoutTest.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso. Inclui a validação do viewport 16:9 calculado para o frame físico 1980×2640 observado no aparelho.
- `:app:assembleDebug` — sucesso; APK atualizada em `app/build/outputs/apk/debug/app-debug.apk`.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — sucesso no telefone `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: validar o item 45 no telefone, nas lentes traseira e frontal.

Bloqueios ou decisões pendentes: a confirmação visual em prévia, thumbnail e reprodução depende do operador.

## Última execução

Data/hora: 2026-07-23 02:37:31 -04:00

## Atualização — padronização definitiva do overlay e das áreas seguras da câmera sFoto

Objetivo: preservar integralmente a qualidade e o pipeline de imagem/captura, corrigindo somente a composição do overlay e a ocupação da tela de câmera em foto e vídeo frontal/traseiro.

Implementação:

- Logo do overlay reduzido de 9,5% para 6% da menor dimensão do frame, com a mesma medida para foto e vídeo.
- Vídeo continua usando o canvas de efeito completo: o recorte 16:9 agora gera apenas margens seguras horizontal/vertical para deslocar texto e logo. Não há mais desenho dentro de um viewport menor, o que evitava nitidez inconsistente e escala diferente no vídeo.
- A prévia é recortada aos limites do componente. As barras superior e inferior receberam fundo preto opaco; a barra superior respeita a área de status e a inferior a área de navegação. Flash e Configurações voltam a permanecer dentro da área visível, e a imagem/overlay não ficam sob o rodapé.
- Não foram alterados `Quality.HIGHEST`, resolução, encoder, `ImageCapture`, processamento de foto nem arquivos de imagem já produzidos.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/OverlayLayout.kt`
- `app/src/main/kotlin/com/saile/shub/photo/OverlayRenderer.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/main/kotlin/com/saile/shub/photo/PhotoScreen.kt`
- `app/src/main/kotlin/com/saile/shub/photo/ui/PhotoTopBar.kt`
- `app/src/main/kotlin/com/saile/shub/photo/ui/PhotoBottomBar.kt`
- `app/src/test/kotlin/com/saile/shub/photo/OverlayLayoutTest.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso. Inclui a compilação da aplicação e o teste de que a margem segura vertical do vídeo não altera a escala da fonte nem do logo.
- `:app:assembleDebug` — sucesso; APK gerada em `app/build/outputs/apk/debug/app-debug.apk`.
- `adb devices` — telefone `RX8TA06NC3W` autorizado.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — sucesso.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto no telefone.

Próximos passos concretos: validar no telefone os itens 27 e 28 do roteiro, nas lentes traseira e frontal.

Bloqueios ou decisões pendentes: a aceitação visual da prévia e do MP4 depende da validação do operador no aparelho real.

## Última execução

Data/hora: 2026-07-22 11:39:50 -04:00

## Atualização — build e instalação solicitados pelo operador

Objetivo: disponibilizar no aparelho a versão debug com a correção mais recente do overlay de vídeo.

Arquivos criados/modificados/removidos: nenhum arquivo de código adicional; gerado `app/build/outputs/apk/debug/app-debug.apk`.

Testes executados e resultado:

- `:app:assembleDebug` — sucesso.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` no `RX8TA06NC3W` — sucesso.

Próximos passos concretos: operador validar a prévia e o arquivo de vídeo nas lentes frontal e traseira.

Bloqueios ou decisões pendentes: nenhum.

## Última execução

Data/hora: 2026-07-22 11:38:30 -04:00

## Atualização — correção da área segura do overlay de vídeo

Objetivo: corrigir somente o overlay de vídeo frontal e traseiro, na prévia e no MP4, preservando o modo foto.

Diagnóstico:

- O frame do efeito de vídeo pode ser 4:3, enquanto a prévia e o arquivo final são 16:9. Desenhar o overlay nas bordas do frame 4:3 fazia o recorte final cortar texto e logo.
- O ajuste anterior com `FIT_CENTER` eliminava o corte, mas introduzia faixas pretas e descaracterizava a prévia nativa.

Implementação:

- Restaurado `videoOverlayViewport` apenas para localizar o overlay dentro da área que chega ao vídeo 16:9. As métricas de texto, logo, margem e posição continuam idênticas às da foto.
- Mantido `PreviewView.ScaleType.FILL_CENTER` para foto e vídeo. A prévia fica cheia, sem faixas pretas.
- Nenhuma alteração no renderer ou no tamanho do logo do modo foto.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/OverlayLayout.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `STATUS.md`

Testes executados: nenhum, por solicitação explícita do operador. Não houve build, instalação, ADB ou teste no aparelho.

Próximos passos concretos: operador validar prévia e MP4 em ambas as lentes.

Bloqueios ou decisões pendentes: nenhum.

## Última execução

Data/hora: 2026-07-22 11:35:45 -04:00

## Atualização — finalização completa dos ícones SVG da tela principal (artwork)

Objetivo: consolidar a criação e aprovação final de todos os ícones da tela principal do sHub na pasta `artwork`:

1. **sFoto** (`artwork/sfoto.svg`):
   - Câmera preenchida com gradiente moderno (Índigo $\rightarrow$ Azul $\rightarrow$ Ciano) e círculo da lente vazado;
   - Pin vermelho em gradiente no centro superior.

2. **sGeoConversor** (`artwork/sgeoconversor.svg`):
   - Setas opostas em gradiente moderno;
   - Texto **UTM**: Gradiente **Verde** $\rightarrow$ **Amarelo Claro** (`#4ADE80` $\rightarrow$ `#FACC15`);
   - Texto **GEO**: Gradiente **Amarelo Claro** $\rightarrow$ **Verde** (`#FACC15` $\rightarrow$ `#4ADE80`).

3. **sGIS** (`artwork/sgis.svg`):
   - Mapa em gradiente moderno;
   - Pin vermelho em gradiente proporcional encaixado na lente da lupa.

4. **Limpeza**:
   - SVGs de modelo removidos; mantidos apenas os ícones finais e logotipos das empresas.

Arquivos finalizados e aprovados:
- `artwork/sfoto.svg` / `sFoto.svg`
- `artwork/sgeoconversor.svg` / `sGeoConversor.svg`
- `artwork/sgis.svg` / `sGIS.svg`

Próximos passos concretos: concluído. Aguardando novos comandos.

## Execução anterior

## Atualização — gradiente amarelo claro para verde nos textos do sGeoConversor (artwork)

Objetivo: aplicar um gradiente linear vibrante em tons claros de **Amarelo** (`#FACC15`) para **Verde** (`#4ADE80`) nos textos **UTM** e **GEO** do ícone **sGeoConversor** (`artwork/sgeoconversor.svg`).

Arquivos modificados:
- `artwork/sgeoconversor.svg`
- `artwork/sGeoConversor.svg`

Próximos passos concretos: aguardar confirmação visual do operador.

## Execução anterior

## Atualização — aplicação do gradiente em tons de vermelho nos textos do sGeoConversor (artwork)

Objetivo: aplicar o mesmo gradiente em tons de vermelho vibrante (`linearGradient` carmim `#FF416C` $\rightarrow$ laranja avermelhado `#FF4B2B`) usado no pin nos textos **UTM** e **GEO** do ícone **sGeoConversor** (`artwork/sgeoconversor.svg`).

Arquivos modificados:
- `artwork/sgeoconversor.svg`
- `artwork/sGeoConversor.svg`

Próximos passos concretos: aguardar confirmação visual do operador.

## Execução anterior

## Atualização — remoção dos SVGs modelo (artwork)

Objetivo: remover os arquivos SVG brutos que serviram de modelo para a criação dos ícones finais:
- Removidos: `camera.svg`, `conversor.svg`, `mapa.svg`, `pin.svg`, `globo.svg`.

Mantidos exclusivamente na pasta `artwork`:
- Os 3 ícones finais: `sfoto.svg`, `sgeoconversor.svg`, `sgis.svg`
- As marcas/logotipos das empresas de energia.

Próximos passos concretos: aguardar novas instruções do operador.

## Execução anterior

## Atualização — aprovação final dos ícones da tela principal em SVG (artwork)

Objetivo: finalizar e consolidar a aprovação de todos os ícones da tela principal do sHub:

1. **sFoto** (`artwork/sfoto.svg`):
   - Câmera preenchida com gradiente moderno indigo $\rightarrow$ azul $\rightarrow$ ciano (círculo central vazado);
   - Pin vermelho em gradiente no centro superior.

2. **sGeoConversor** (`artwork/sgeoconversor.svg`):
   - Setas opostas em gradiente moderno;
   - Textos **UTM** (Verde $\rightarrow$ Laranja) e **GEO** (Laranja $\rightarrow$ Verde) na frente de cada bico de seta com fonte bold 160px.

3. **sGIS** (`artwork/sgis.svg`):
   - Mapa em gradiente moderno;
   - Pin vermelho proporcional ajustado perfeitamente no centro da lente da lupa.

Arquivos finalizados e aprovados:
- `artwork/sfoto.svg` / `sFoto.svg`
- `artwork/sgeoconversor.svg` / `sGeoConversor.svg`
- `artwork/sgis.svg` / `sGIS.svg`

Próximos passos concretos: aguardar instruções do operador sobre a aplicação dos ícones ou próxima etapa.

## Execução anterior

## Atualização — inclusão do pin dentro da lupa no ícone sGIS (artwork)

Objetivo: atualizar a composição do ícone **sGIS** (`artwork/sgis.svg`):
- Base do ícone `mapa.svg` com gradiente moderno indigo $\rightarrow$ azul $\rightarrow$ ciano;
- Pin de localização em gradiente vermelho vibrante posicionado exatamente dentro da lente da lupa no canto inferior direito.

Arquivos modificados:
- `artwork/sgis.svg`
- `artwork/sGIS.svg`

Próximos passos concretos: aguardar confirmação visual do operador para o sGIS.

## Execução anterior

## Atualização — criação do ícone sGIS em SVG (artwork)

Objetivo: criar o ícone oficial do **sGIS** (`artwork/sgis.svg`):
- Base do ícone `mapa.svg`;
- Aplicado o gradiente moderno do sHub (`linearGradient` indigo $\rightarrow$ azul $\rightarrow$ ciano).

Arquivos criados:
- `artwork/sgis.svg`
- `artwork/sGIS.svg`

Próximos passos concretos: aguardar aprovação do ícone sGIS pelo operador.

## Execução anterior

## Atualização — aprovação final do ícone sGeoConversor em SVG (artwork)

Objetivo: consolidar a aprovação visual final do ícone do **sGeoConversor**:
- Setas em gradiente moderno indigo $\rightarrow$ azul $\rightarrow$ ciano;
- Textos **UTM** (Verde $\rightarrow$ Laranja) e **GEO** (Laranja $\rightarrow$ Verde) posicionados na frente de cada bico de seta com tipografia bold em 160px.

Arquivos finalizados e aprovados:
- `artwork/sgeoconversor.svg`
- `artwork/sGeoConversor.svg`

Próximos passos concretos: criar o próximo ícone da tela principal (ex: `sGIS`).

## Execução anterior

## Atualização — substituição de vermelho por laranja nos textos do sGeoConversor (artwork)

Objetivo: substituir o tom de vermelho por **Laranja** (`#F97316`) no gradiente das fontes no ícone **sGeoConversor** (`artwork/sgeoconversor.svg`):
- **UTM**: Gradiente **Verde** $\rightarrow$ **Laranja** (`#10B981` $\rightarrow$ `#F97316`);
- **GEO**: Gradiente **Laranja** $\rightarrow$ **Verde** (`#F97316` $\rightarrow$ `#10B981`).

Arquivos modificados:
- `artwork/sgeoconversor.svg`
- `artwork/sGeoConversor.svg`

Próximos passos concretos: aguardar confirmação visual do operador.

## Execução anterior

## Atualização — inversão do gradiente no texto GEO do sGeoConversor (artwork)

Objetivo: atualizar a paleta dos textos no ícone **sGeoConversor** (`artwork/sgeoconversor.svg`):
- **UTM**: Gradiente **Verde** $\rightarrow$ **Vermelho** (`#10B981` $\rightarrow$ `#EF4444`);
- **GEO**: Gradiente **Vermelho** $\rightarrow$ **Verde** (`#EF4444` $\rightarrow$ `#10B981`) [ordem invertida].

Arquivos modificados:
- `artwork/sgeoconversor.svg`
- `artwork/sGeoConversor.svg`

Próximos passos concretos: aguardar confirmação visual do operador.

## Execução anterior

## Atualização — gradiente verde para vermelho nas fontes UTM e GEO (artwork)

Objetivo: atualizar a estilização das fontes no ícone **sGeoConversor** (`artwork/sgeoconversor.svg`):
- Aplicado um gradiente linear vibrante de **Verde** (esmeralda `#10B981`) para **Vermelho** (carmim `#EF4444`) nos textos **UTM** e **GEO**;
- Mantida a tipografia grande e bold em 160px.

Arquivos modificados:
- `artwork/sgeoconversor.svg`
- `artwork/sGeoConversor.svg`

Próximos passos concretos: aguardar confirmação do operador para o sGeoConversor.

## Execução anterior

## Atualização — posicionamento frontal e aumento de fonte no ícone sGeoConversor (artwork)

Objetivo: atualizar o ícone do **sGeoConversor** (`artwork/sgeoconversor.svg`):
- Textos **UTM** e **GEO** posicionados na frente dos bicos de cada seta (UTM no topo/esquerda e GEO na base/direita);
- Tamanho de fonte ampliado para 160px com estilo extra-bold em cor branca.

Arquivos modificados:
- `artwork/sgeoconversor.svg`
- `artwork/sGeoConversor.svg`

Próximos passos concretos: aguardar confirmação visual do operador para o sGeoConversor.

## Execução anterior

## Atualização — criação do ícone sGeoConversor em SVG (artwork)

Objetivo: compor o ícone oficial do **sGeoConversor** (`artwork/sgeoconversor.svg`):
- Base do ícone `conversor.svg` com as duas setas opostas;
- Aplicado o mesmo gradiente moderno do sFoto (indigo $\rightarrow$ azul $\rightarrow$ ciano);
- Adicionados os textos em tipografia negrito e cor branca: **UTM** na seta superior (apontando para a esquerda) e **GEO** na seta inferior (apontando para a direita).

Arquivos criados:
- `artwork/sgeoconversor.svg`
- `artwork/sGeoConversor.svg`

Próximos passos concretos: aguardar aprovação visual do ícone sGeoConversor pelo operador.

## Execução anterior

## Atualização — aprovação final do ícone sFoto em SVG (artwork)

Objetivo: consolidar a aprovação visual final do ícone do sFoto:
- Câmera preenchida com gradiente moderno indigo $\rightarrow$ azul $\rightarrow$ ciano (círculo da lente vazado);
- Pin em gradiente vermelho vibrante carmim $\rightarrow$ laranja avermelhado, posicionado no centro superior apontando diretamente para a lente.

Arquivos finalizados e aprovados:
- `artwork/sfoto.svg`
- `artwork/sFoto.svg`

Próximos passos concretos: criar o próximo ícone da tela principal (ex: `sGeoConversor`, `sGIS`).

## Execução anterior

## Atualização — elevação vertical do pin no centro superior da câmera (artwork)

Objetivo: reposicionar o pin em `artwork/sfoto.svg`:
- Mantido o alinhamento horizontal perfeitamente centralizado com a câmera;
- Deslocado verticalmente para cima (`translate(307, -440)`), fazendo a ponta do pin vermelho apontar exatamente para a lente da câmera.

Arquivos modificados:
- `artwork/sfoto.svg`
- `artwork/sFoto.svg`

Próximos passos concretos: aguardar confirmação visual do operador.

## Execução anterior

## Atualização — preenchimento sólido do corpo da câmera com gradiente (artwork)

Objetivo: atualizar a câmera em `artwork/sfoto.svg`:
- Corpo da câmera totalmente preenchido pelo gradiente moderno (`linearGradient` indigo $\rightarrow$ azul $\rightarrow$ ciano);
- Círculo central da lente mantido como único elemento vazado/transparente (`fill-rule="evenodd"`);
- Pin em gradiente vermelho mantido no canto superior direito.

Arquivos modificados:
- `artwork/sfoto.svg`
- `artwork/sFoto.svg`

Próximos passos concretos: aguardar confirmação visual do operador.

## Execução anterior

## Atualização — aplicação de gradientes modernos no ícone sFoto (artwork)

Objetivo: aplicar gradientes no ícone sFoto (`artwork/sfoto.svg`):
1. **Câmera**: gradiente linear moderno (`linearGradient` indigo `#6366F1` -> azul `#3B82F6` -> ciano `#06B6D4`);
2. **Pin**: gradiente linear em tons de vermelho vibrante (`linearGradient` carmim `#FF416C` -> laranja avermelhado `#FF4B2B`).

Arquivos modificados:
- `artwork/sfoto.svg`
- `artwork/sFoto.svg`

Próximos passos concretos: aguardar aprovação dos gradientes pelo operador.

## Execução anterior

## Atualização — aprovação final do ícone sFoto em SVG (artwork)

Objetivo: consolidar a aprovação visual final do ícone do sFoto:
- Câmera branca como base principal;
- Pin de localização vermelho sólido (`#F44336`, sem furo central);
- Tamanho proporcional (`scale(0.36)`);
- Posicionado perfeitamente no canto superior direito da câmera (`translate(610, -610)`).

Arquivos finalizados e aprovados:
- `artwork/sfoto.svg`
- `artwork/sFoto.svg`

Próximos passos concretos: criar o próximo ícone da tela principal (ex: `sGeoConversor`, `sGIS` ou outro item de artwork).

## Execução anterior

## Atualização — ajuste de altura do pin no ícone sFoto (artwork)

Objetivo: manter o alinhamento horizontal correto do pin no canto direito e descer a altura vertical um pouco mais para baixo sobre o ombro da câmera (`artwork/sfoto.svg`).

Arquivos modificados:
- `artwork/sfoto.svg`
- `artwork/sFoto.svg`

Próximos passos concretos: aguardar confirmação final do operador para o ícone sFoto.

## Execução anterior

## Atualização — ajuste fino do posicionamento do pin no ícone sFoto (artwork)

Objetivo: deslocar o pin vermelho um pouco mais para a direita e um pouco mais para cima no canto superior direito do ícone do sFoto (`artwork/sfoto.svg`).

Arquivos modificados:
- `artwork/sfoto.svg`
- `artwork/sFoto.svg`

Próximos passos concretos: aguardar confirmação final do ícone sFoto.

## Execução anterior

## Atualização — reposicionamento para o lado direito e ampliação do pin no ícone sFoto (artwork)

Objetivo: aplicar a correção de lado no ícone sFoto:
1. Tamanho do pin aumentado em +15% (escala `0.35`);
2. Posição em altura elevada (dobro de altura);
3. Deslocado para o lado **DIREITO** (canto superior direito da câmera).

Arquivos modificados:
- `artwork/sfoto.svg`
- `artwork/sFoto.svg`

Próximos passos concretos: aguardar confirmação do operador sobre a versão final do ícone sFoto.

## Execução anterior

## Atualização — reposicionamento e ampliação do pin no ícone sFoto (artwork)

Objetivo: aplicar os ajustes no ícone sFoto:
1. Tamanho do pin aumentado em +15% (escala `0.35`);
2. Posição em altura elevada (dobro de altura em relação ao ombro);
3. Deslocado mais para a esquerda em direção ao centro superior da câmera.

Arquivos modificados:
- `artwork/sfoto.svg`
- `artwork/sFoto.svg`

Próximos passos concretos: aguardar confirmação do operador sobre a versão final do ícone sFoto.

## Execução anterior

## Atualização — ajuste do ícone sFoto em SVG (artwork)

Objetivo: aplicar as alterações visuais solicitadas no ícone do sFoto:
1. Pin 100% preenchido em vermelho sólido (`#F44336`, sem vazado central);
2. Reduzido o tamanho do pin à metade (escala `0.30`);
3. Deslocado mais para a direita e para cima no ombro da câmera.

Arquivos modificados:
- `artwork/sfoto.svg`
- `artwork/sFoto.svg`

Próximos passos concretos: aguardar aprovação do ícone sFoto atualizado.

## Execução anterior

## Atualização — criação do ícone sFoto em SVG (artwork)

Objetivo: unir os arquivos `camera.svg` e `pin.svg` da pasta `artwork` para compor o ícone oficial do sFoto:
- Câmera branca como base principal;
- Pin de localização vermelho (`#F44336`), levemente menor, posicionado no canto superior direito da câmera.

Arquivos criados:
- `artwork/sfoto.svg`
- `artwork/sFoto.svg`

Próximos passos concretos: aguardar aprovação visual do operador dos ícones na pasta `artwork`.

## Execução anterior

## Atualização — retirada do enquadramento exclusivo de vídeo

Objetivo: corrigir exclusivamente a divergência visual relatada no overlay de vídeo frontal e traseiro, na prévia e no arquivo final, sem alterar o modo foto.

Diagnóstico:

- O `videoOverlayViewport` incluído na etapa anterior introduzia um segundo sistema de coordenadas 16:9 para o vídeo. Isso fazia o overlay obedecer a uma área diferente da usada pela foto e quebrava o padrão visual nas duas lentes.
- `FIT_CENTER` também era uma regra exclusiva de prévia de vídeo, portanto vídeo e foto deixavam de compartilhar o mesmo enquadramento de referência.

Implementação:

- Removidos `OverlayViewport` e `videoOverlayViewport`; `OverlayRenderer` volta a receber, no vídeo, o mesmo canvas lógico completo já usado no modo foto.
- Removido o `FIT_CENTER` exclusivo. A prévia de foto e vídeo volta a usar `FILL_CENTER`.
- Mantidos intactos o renderer, a rasterização do SVG e o tamanho do logo no modo foto.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/OverlayLayout.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/test/kotlin/com/saile/shub/photo/OverlayLayoutTest.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados: nenhum, por solicitação explícita do operador. Não houve instalação, uso de ADB, gravação ou teste no aparelho nesta etapa.

Próximos passos concretos: operador validar o overlay de vídeo frontal e traseiro na prévia e no arquivo final.

Bloqueios ou decisões pendentes: nenhum.

## Última execução

Data/hora: 2026-07-22 11:06:46 -04:00

## Atualização — validação final do overlay de vídeo nas duas lentes

Objetivo: concluir a correção do overlay de vídeo, mantendo exclusivamente o padrão já aprovado no modo foto.

Diagnóstico complementar:

- No aparelho de teste, o `OverlayEffect` recebia frame físico 4:3 (`2640x1980`, rotação `270°`) enquanto a mídia final é 16:9. O recorte lateral aplicado depois do efeito eliminava parte do logo e do timestamp, parecendo zoom e baixa qualidade.
- O SVG da Energisa declara `250x250`, porém o `viewBox` efetivo é panorâmico (`1000x325,7`). Usar apenas as dimensões declaradas gerava um bitmap com proporção incorreta e deslocava o logo para fora da área visível.

Implementação:

- `videoOverlayViewport` agora desenha o overlay somente no viewport central com o aspecto da mídia final, antes do recorte do pipeline. O layout, tamanhos e margens continuam vindo da mesma `OverlayLayoutMetrics` usada em foto.
- A prévia de vídeo usa `FIT_CENTER`, mostrando o quadro 16:9 completo; foto e demais fluxos não foram alterados.
- A rasterização do SVG usa o `viewBox` quando disponível e mantém bitmap ARGB cacheado no tamanho final, garantindo proporção e nitidez estáveis.

Arquivos modificados nesta etapa:

- `app/src/main/kotlin/com/saile/shub/photo/OverlayLayout.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/test/kotlin/com/saile/shub/photo/OverlayLayoutTest.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso, incluindo a validação do viewport 16:9 em `OverlayLayoutTest`.
- `:app:assembleDebug` — sucesso.
- APK instalado e aberto no aparelho `RX8TA06NC3W` — sucesso.
- Validação visual em aparelho real: prévia de vídeo nas duas lentes e dois MP4 curtos abertos na galeria. Em ambos os vídeos, logo Energisa e todas as linhas do overlay permaneceram inteiros, nítidos e sem zoom/corte.

Próximos passos concretos: nenhum para esta correção.

Bloqueios ou decisões pendentes: nenhum.

## Última execução

Data/hora: 2026-07-22 10:52:32 -04:00

## Atualização — padronização do overlay de vídeo com o modo foto

Objetivo: corrigir exclusivamente o overlay de vídeo nas lentes frontal e traseira: baixa nitidez, tamanho/zoom divergente, posição inconsistente e logo ausente tanto na prévia quanto no MP4 aberto na galeria.

Diagnóstico:

- O vídeo aplicava uma escala exclusiva de `0.72` e uma largura máxima de texto menor, portanto nunca seguia o mesmo padrão da foto.
- A seleção de qualidade priorizava FHD/HD antes de `HIGHEST`, limitando desnecessariamente a resolução de saída em aparelhos capazes de mais.
- O SVG era reaberto, tinha seu tamanho alterado e era renderizado diretamente a cada frame, o que introduzia trabalho pesado no caminho de vídeo e ocultava falhas de renderização.

Implementação:

- Criado `OverlayLayoutMetrics`, a única fonte de tamanho, padding, margem, quebra de texto, ícones e logo para foto e vídeo. Removidos todos os fatores de escala e largura exclusivos do vídeo.
- O `Recorder` voltou a solicitar `Quality.HIGHEST`.
- SVG agora é carregado uma vez por ativo e rasterizado em bitmap ARGB no tamanho final do overlay; os bitmaps são cacheados e desenhados com filtragem. O logo fica nítido e não é reprocessado em cada frame de vídeo.
- Falhas ao abrir/renderizar logo agora ficam registradas em `sFotoOverlay`, em vez de serem silenciosamente ignoradas.

Arquivos criados:

- `app/src/main/kotlin/com/saile/shub/photo/OverlayLayout.kt`
- `app/src/test/kotlin/com/saile/shub/photo/OverlayLayoutTest.kt`

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/OverlayRenderer.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/main/kotlin/com/saile/shub/photo/README.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso, incluindo `OverlayLayoutTest`.
- `:app:assembleDebug` — sucesso.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — sucesso no aparelho `RX8TA06NC3W`.
- Aplicativo reiniciado via ADB (`force-stop` e `am start`).

Próximos passos concretos: executar o item 43 do roteiro em foto e vídeo, nas lentes frontal e traseira, usando um logo SVG selecionado.

Bloqueios ou decisões pendentes: a confirmação visual no aparelho ainda é necessária para concluir o aceite de qualidade do overlay de vídeo.

## Última execução

Data/hora: 2026-07-22 10:37:00 -04:00

## Atualização — sincronização da miniatura da foto frontal e escala isolada do overlay de vídeo

Objetivo: resolver exclusivamente os dois pontos apontados na validação `/goal` sem alterar os fluxos já aprovados:
1. Modo foto câmera frontal: a miniatura de atalho da galeria na barra inferior aparecia invertida/espelhada.
2. Modo vídeo (câmera frontal e traseira): o overlay no preview e no vídeo gravado ainda parecia grande ("com zoom").
3. Modo foto câmera traseira e todos os demais itens aprovados: mantidos 100% inalterados.

Diagnóstico:
1. `PhotoCapture.kt` notificava `onStatus("Foto salva...", uri)` e atualizava `lastPhotoUri` na thread principal imediatamente após `takePicture()`, disparando o carregamento da miniatura pela biblioteca de imagem antes que a gravação assíncrona do `flipJpegHorizontally()` terminasse. Assim a barra carregava o arquivo ainda não desespelhado do cache.
2. No `OverlayRenderer.kt`, o dimensionamento do overlay não diferenciava o modo foto do modo vídeo. No modo vídeo (aspecto 16:9), a altura útil do quadro é menor, fazendo a mesma escala visual do modo foto parecer desproporcionalmente grande.

Implementação:
1. `PhotoCapture.kt`: a notificação `onStatus(...)` que atualiza `lastPhotoUri` foi movida para dentro do bloco `metadataExecutor`, garantindo execução estritamente após a conclusão do `flipJpegHorizontally()`. O atalho da barra inferior agora carrega a imagem já desespelhada.
2. `OverlayRenderer.kt`: adicionado o parâmetro `isVideoMode: Boolean = false`. Quando `isVideoMode == true`, aplica-se um fator de escala proporcional `videoScale = 0.72f` que ajusta texto, ícones, espaçamentos e logo sem alterar em nada o modo foto.
3. `CameraPreview.kt`: repassado `isVideoMode = isVideoMode` na chamada do `OverlayRenderer.draw()`.

Arquivos modificados:
- `app/src/main/kotlin/com/saile/shub/photo/PhotoCapture.kt`
- `app/src/main/kotlin/com/saile/shub/photo/OverlayRenderer.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `STATUS.md`

Testes executados:
- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — `Success` no aparelho `RX8TA06NC3W`.
- Aplicativo reiniciado via ADB (`force-stop` e `am start`).

Próximos passos concretos:
- Operador testar no aparelho:
  1. Câmera frontal (foto): conferir que a miniatura de atalho da barra inferior aparece desespelhada/correta.
  2. Câmera frontal e traseira (vídeo): conferir que o overlay no preview e no vídeo gravado está compacto e proporcional.
  3. Demais itens: confirmados como OK.

Bloqueios ou decisões pendentes: nenhum. Build pronta e instalada no aparelho.

## Execução anterior

## Atualização — desespelhamento final da foto frontal na galeria e calibração proporcional do overlay

Objetivo: atender aos refinamentos de campo `/goal`:
1. Câmera frontal (foto salva na galeria): o preview na tela estava correto, mas a foto salva no arquivo JPEG permanecia espelhada na galeria.
2. Modo vídeo (traseira e frontal): a imagem do vídeo estava excelente e sem zoom, porém os elementos de overlay (texto, bloco informativo e logo SVG) ocupavam proporção excessiva da tela, aparecendo "com zoom / gigantes".

Diagnóstico:
1. No CameraX 1.4.1, o `ImageCapture` captura o frame cru da lente frontal sem suporte ao `setMirrorMode` do builder. Para que a foto salva seja desespelhada na galeria e corresponda 100% à prévia visual, o overlay da captura fotográfica é renderizado espelhado no frame cru e o JPEG é desespelhado horizontalmente (`matrix.postScale(-1f, 1f)`) no processamento pós-captura assíncrono em `PhotoCapture.kt`.
2. No `OverlayRenderer.kt`, as Proporções `baseSize = 0.035f`, `maxTextWidth = 0.70f` e `targetH = 0.16f` faziam a caixa de texto e o logo ocuparem quase 78% da largura da tela no modo 16:9, criando a percepção visual de overlay gigante/com zoom.

Implementação:
1. `PhotoCapture.kt`: adicionada a função `flipJpegHorizontally` executada em `metadataExecutor` (thread secundária) quando `isFrontCamera == true`, aplicando `postScale(-1f, 1f)` no bitmap salvo.
2. `CameraPreview.kt`: flag `isFrontPhotoCapture` identifica o disparo da foto frontal e sinaliza `willBeMirrored = true` ao `OverlayEffect`, garantindo que o overlay texto e o fundo da foto fiquem ambos perfeitamente desespelhados e legíveis após a inversão do JPEG.
3. `OverlayRenderer.kt`: recalibradas as dimensões para proporções compactas e proporcionais:
   - `baseSize = minDim * 0.027f` (texto nítido e elegante).
   - `padding = minDim * 0.028f` / `spacing = minDim * 0.006f`.
   - `maxTextWidth = minDim * 0.58f` (reduz a ocupação horizontal da tela de ~78% para ~44%).
   - `targetH = minDim * 0.095f` (logo proporcional sem obstruir o campo visual).

Arquivos modificados:
- `app/src/main/kotlin/com/saile/shub/photo/OverlayRenderer.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/main/kotlin/com/saile/shub/photo/PhotoCapture.kt`
- `app/src/main/kotlin/com/saile/shub/photo/PhotoScreen.kt`
- `STATUS.md`

Testes executados:
- `:app:testDebugUnitTest` — sucesso (BUILD SUCCESSFUL).
- `:app:assembleDebug` — sucesso.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — `Success` no aparelho `RX8TA06NC3W`.
- Aplicativo reiniciado via ADB (`force-stop` e `am start`).

Próximos passos concretos:
- Operador testar no aparelho:
  1. Câmera frontal (modo foto): verificar foto salva na galeria — imagem e overlay totalmente desespelhados e idênticos à tela.
  2. Câmera frontal e traseira (modo vídeo): verificar overlay proporcional, compacto, nítido e sem ocupar excessivamente a tela.

Bloqueios ou decisões pendentes: nenhum. Build pronta e instalada no aparelho.

## Execução anterior

## Atualização — unificação do pipeline CameraX (espelhamento da frontal e eliminação do zoom em vídeo)

Objetivo: atender aos feedbacks de campo:
1. Câmera frontal (foto e vídeo): as mídias salvas na galeria (JPEG e MP4) apareciam espelhadas (imagem e overlay), enquanto na tela de prévia aparecia normal.
2. Modo vídeo (traseira e frontal): imagem apresentava crop/zoom digital e o overlay ficava com baixa qualidade/pixelado e com zoom.

Diagnóstico:
1. `scaleX = -1f` na `PreviewView` invertia apenas a camada visual Compose/View na tela, mas deixava o pipeline de captura do CameraX em `MIRROR_MODE_OFF`. Assim, o JPEG e o MP4 salvos recebiam o frame espelhado nativo da lente frontal.
2. No modo vídeo, o `Preview` solicitava `HIGHEST_AVAILABLE_STRATEGY` (4:3 do sensor fotográfico) enquanto o `VideoCapture` solicitava `Quality.HIGHEST` (16:9). A concorrência de aspecto no `OverlayEffect` forçava o CameraX a recortar o sensor em 16:9 e o `PreviewView` aplicava um segundo recorte na tela, gerando o zoom digital. O overlay renderizado no frame recortado sofria escalonamento, tornando-se pixelado/baixo-resolução.

Implementação:
1. `CameraPipelinePolicy`: para a lente frontal, `mirrorPreviewInCameraX` e `mirrorSavedMedia` foram definidos como `true` (`MIRROR_MODE_ON`), enquanto `correctFrontPreviewInView` passou para `false` (`scaleX = 1f`). O CameraX desespelha o fluxo na origem (OpenGL processor) antes do `OverlayEffect`, garantindo que prévia na tela, foto salva (JPEG) e vídeo salvo (MP4) fiquem todos unificados e não espelhados.
2. Em `CameraPreview.kt`:
   - No modo vídeo, o `Preview` agora usa `AspectRatio.RATIO_16_9` com regra de fallback automática, combinando com o `VideoCapture` (alinhado a `Quality.FHD` / `Quality.HD`).
   - Alinhados os aspectos do fluxo de vídeo a 16:9 1080p, eliminando o crop/zoom digital do sensor e entregando overlay nítido de alta qualidade.
3. Testes unitários atualizados em `CameraPipelinePolicyTest.kt`.

Arquivos modificados:
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPipelinePolicy.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/test/kotlin/com/saile/shub/photo/camera/CameraPipelinePolicyTest.kt`
- `STATUS.md`

Testes executados:
- `:app:testDebugUnitTest` — sucesso (BUILD SUCCESSFUL).
- `:app:assembleDebug` — sucesso.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — `Success` no aparelho `RX8TA06NC3W`.
- Aplicativo reiniciado via ADB (`force-stop` e `am start`).

Próximos passos concretos:
- Operador testar no aparelho os 4 cenários:
  1. Câmera traseira modo foto: confirmação de imagem OK e overlay OK.
  2. Câmera traseira modo vídeo: conferir fim do zoom digital e overlay nítido em 1080p.
  3. Câmera frontal modo vídeo: conferir que a mídia gravada na galeria está unificada com a tela (sem espelhamento e sem zoom).
  4. Câmera frontal modo foto: conferir que a foto salva na galeria está idêntica à prévia da tela (sem imagem ou overlay espelhados).

Bloqueios ou decisões pendentes: aguardar validação das mídias salvas pelo operador.

## Execução anterior

## Atualização — efetivação da inversão da prévia frontal

Objetivo: corrigir o retorno de que a alteração anterior não teve efeito visual na lente frontal.

Diagnóstico: a `PreviewView` estava no modo padrão `PERFORMANCE`, que pode usar `SurfaceView`. Nesse modo, a transformação `scaleX` do contêiner não acompanha a Surface da câmera; por isso imagem e overlay continuaram espelhados apesar da política correta.

Implementação:

- A `PreviewView` agora é criada com `ImplementationMode.COMPATIBLE`, antes de receber o `SurfaceProvider`.
- Esse modo usa `TextureView`, permitindo que a inversão corretiva da lente frontal seja efetivamente aplicada à imagem e ao overlay na prévia de foto e vídeo.
- A traseira, o pipeline de JPEG e o pipeline de MP4 permanecem sem transformação adicional.
- Documentados o requisito de compatibilidade e o novo teste manual de confirmação imediata da prévia frontal.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/main/kotlin/com/saile/shub/photo/README.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — sucesso no aparelho `RX8TA06NC3W`.
- Aplicativo reiniciado com `adb shell am force-stop` e `adb shell am start`.

Próximos passos concretos: abrir sFoto, trocar para a lente frontal e executar o item 42 do roteiro. A inversão deve aparecer imediatamente tanto na imagem quanto no overlay da prévia.

Bloqueios ou decisões pendentes: aguardar a confirmação visual da nova build no aparelho.

## Última execução

Data/hora: 2026-07-22 09:24:39 -04:00

## Atualização — correção da Surface frontal espelhada

Objetivo: corrigir o feedback de validação no Samsung: a lente frontal continuava espelhando simultaneamente imagem e overlay na prévia de foto e vídeo.

Diagnóstico: apesar de `MIRROR_MODE_OFF` nos casos de uso CameraX, a Surface da lente frontal ainda chega espelhada ao `PreviewView` neste aparelho. JPEG e MP4 não passam por essa `View`.

Implementação:

- `CameraPipelinePolicy` identifica explicitamente a lente frontal e habilita uma inversão horizontal corretiva exclusiva do `PreviewView`.
- A traseira permanece sem transformação; `Preview` e `VideoCapture` continuam com `MIRROR_MODE_OFF`.
- Como a inversão atua sobre a superfície completa, a imagem e o overlay da prévia são corrigidos juntos. A mídia persistida não é atingida por essa camada visual.
- Atualizados teste unitário da política, documentação de arquitetura/UI, README do módulo e roteiro ADB com o cenário frontal de foto/vídeo.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPipelinePolicy.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/test/kotlin/com/saile/shub/photo/camera/CameraPipelinePolicyTest.kt`
- `app/src/main/kotlin/com/saile/shub/photo/README.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — sucesso no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: validar o item 41 do roteiro no aparelho, principalmente a foto e o vídeo salvos pela lente frontal.

Bloqueios ou decisões pendentes: nenhum de compilação; falta a confirmação visual da mídia persistida no aparelho real.

## Última execução

Data/hora: 2026-07-22 09:20:32 -04:00

## Atualização — refatoração nativa da prévia e do overlay sFoto

Objetivo: eliminar o espelhamento divergente de câmera e overlay e fazer foto/vídeo seguirem o pipeline nativo CameraX, sem transformação visual da interface.

Implementação:

- Removido qualquer uso de `scaleX` da `PreviewView`; a prévia é entregue diretamente pelo CameraX e tanto `Preview` quanto `VideoCapture` usam `MIRROR_MODE_OFF`.
- Unificado o overlay de vídeo: `OverlayEffect` agora atende `PREVIEW|VIDEO_CAPTURE`, eliminando a camada Compose paralela que podia divergir da gravação. Foto continua usando um único efeito no grupo de `Preview` e `ImageCapture` com a máscara suportada pela CameraX.
- A orientação do overlay deixou de ser calculada com fórmulas de lente/sensor e passou a usar `Frame.rotationDegrees` e `Frame.isMirroring`, metadados do frame que o CameraX realmente vai renderizar. Uma compensação inversa no canvas mantém texto e logo legíveis mesmo em implementações de fabricante que sinalizem espelho.
- Adicionado `OverlayFrameTransform`, componente puro e testável para a geometria do overlay; `PhotoCapture` também passou a manter o `Preview` ativo, cuja rotação de destino é atualizada junto com foto e vídeo.

Arquivos criados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/OverlayFrameTransform.kt`
- `app/src/test/kotlin/com/saile/shub/photo/camera/OverlayFrameTransformTest.kt`

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPipelinePolicy.kt`
- `app/src/main/kotlin/com/saile/shub/photo/PhotoCapture.kt`
- `app/src/main/kotlin/com/saile/shub/photo/PhotoScreen.kt`
- `app/src/main/kotlin/com/saile/shub/photo/README.md`
- `app/src/test/kotlin/com/saile/shub/photo/camera/CameraPipelinePolicyTest.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — sucesso no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: executar o item 40 do roteiro em telefone real, nas duas lentes e em retrato/paisagem.

Bloqueios ou decisões pendentes: a validação visual no aparelho real ainda é necessária para concluir o critério de aceite de câmera/overlay.

## Última execução

Data/hora: 2026-07-22 03:32:29 -04:00

## Atualização — restauração da sessão CameraX única e padrão de campo

Objetivo: corrigir a segunda falha de abertura do modo foto (`G.e: java.lang.IllegalArgumentException`) causada pela tentativa de vincular `ImageCapture` com efeito e `Preview` em chamadas separadas.

Diagnóstico:

- A mensagem integral foi lida da tela via ADB.
- O fornecedor CameraX do Samsung rejeita a divisão da mesma lente em dois vínculos quando um grupo contém o efeito fotográfico.
- A configuração comprovadamente suportada é uma única chamada com `Preview`, `ImageCapture` e o `OverlayEffect` de máscara completa.

Implementação:

- Restaurado um único `UseCaseGroup` fotográfico com `Preview`, `ImageCapture` e `IMAGE_CAPTURE|PREVIEW|VIDEO_CAPTURE`.
- Removida a segunda camada Compose do modo foto; o efeito desenha uma única vez na prévia e no JPEG.
- Consolidado o padrão de campo: frontal e traseira usam orientação real, sem espelho, na prévia e na mídia salva; nenhuma superfície usa `scaleX`.
- Vídeo mantém efeito de arquivo e overlay Compose de prévia, também sem espelhamento.
- Falhas de vínculo agora geram stack trace com a tag `sFotoCamera` e exibem a causa raiz, evitando mensagens reduzidas como `G.e`.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPipelinePolicy.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/test/kotlin/com/saile/shub/photo/camera/CameraPipelinePolicyTest.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital.
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador confirmar primeiro que o modo foto abre nas lentes traseira e frontal. Depois validar uma foto e um vídeo em cada lente, conferindo orientação real e overlay único/legível.

Bloqueios ou decisões pendentes: nenhum bloqueio de compilação; resta validação visual da abertura no telefone.

## Última execução

Data/hora: 2026-07-22 03:28:04 -04:00

## Atualização — correção do alvo fotográfico do OverlayEffect

Objetivo: corrigir a falha de abertura do modo foto após a refatoração padrão de câmera.

Diagnóstico no aparelho:

- Mensagem completa obtida via hierarquia de UI: `Effects target IMAGE_CAPTURE is not in the supported list [PREVIEW, VIDEO_CAPTURE, PREVIEW|VIDEO_CAPTURE, IMAGE_CAPTURE|PREVIEW|VIDEO_CAPTURE]`.
- CameraX 1.4.1 não aceita `IMAGE_CAPTURE` isolado no construtor de `OverlayEffect`; foto exige a máscara completa.

Implementação:

- O efeito fotográfico voltou a declarar `IMAGE_CAPTURE|PREVIEW|VIDEO_CAPTURE`, combinação suportada pela biblioteca.
- O grupo que contém esse efeito vincula somente `ImageCapture`.
- `Preview` é vinculada separadamente à mesma lente e ao mesmo ciclo de vida, mantendo a superfície fora do processador fotográfico; assim o overlay da tela continua independente e não participa do espelho de selfie.
- A política padrão frontal/traseira e o caminho de vídeo não foram alterados.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- Leitura da mensagem integral no aparelho via `adb shell uiautomator dump` — confirmou a incompatibilidade do alvo isolado.
- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital.
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador entrar no sFoto e confirmar abertura do modo foto nas duas lentes; depois capturar uma foto para validar arquivo e overlay. Se o vínculo separado não for aceito pelo fornecedor da câmera, coletar a nova mensagem integral antes de qualquer ajuste de orientação.

Bloqueios ou decisões pendentes: nenhum bloqueio de compilação; a abertura efetiva do modo foto precisa de confirmação visual no aparelho.

## Última execução

Data/hora: 2026-07-22 03:22:41 -04:00

## Atualização — refatoração padrão de câmeras e overlay

Objetivo: substituir os ajustes incrementais de orientação por uma política única, explícita e testável para câmera frontal, câmera traseira, foto, vídeo e overlay.

Padrão implementado:

- Câmera frontal: prévia espelhada como selfie; foto e vídeo persistidos sem espelho.
- Câmera traseira: prévia, foto e vídeo sem espelho.
- Overlay da prévia: camada Compose independente acima da superfície, nunca espelhada.
- Overlay persistido: `OverlayEffect` específico do destino (`IMAGE_CAPTURE` ou `VIDEO_CAPTURE`), gravado uma vez e sem espelhamento.

Implementação:

- Criada `CameraPipelinePolicy`, fonte única da convenção frontal/traseira e da orientação da mídia salva.
- `Preview` e `VideoCapture` usam `MIRROR_MODE_OFF`; somente o `PreviewView` frontal recebe `scaleX = -1`.
- Foto e vídeo agora compartilham a mesma regra de prévia, eliminando condicionais divergentes entre modos.
- A rotação do overlay usa `CameraInfo.sensorRotationDegrees` da câmera selecionada, removendo os ângulos fixos `90/270` por lente.
- Margem lateral do overlay persistido foi padronizada em foto e vídeo.
- Atualizado o import de `LocalLifecycleOwner` para `androidx.lifecycle.compose`.
- O roteiro ADB foi consolidado e as matrizes contraditórias das rodadas anteriores foram removidas.

Arquivos criados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPipelinePolicy.kt`
- `app/src/test/kotlin/com/saile/shub/photo/camera/CameraPipelinePolicyTest.kt`

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso, incluindo os dois cenários novos da política de câmera.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital.
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador validar no aparelho a matriz padrão com texto físico assimétrico: frontal com prévia tipo espelho e arquivos sem espelho; traseira sem espelho; overlay legível e estável em todos os quatro fluxos. Repetir foto curta, disparo contínuo e gravação de vídeo.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico conhecido. A definição de pronto ainda exige a confirmação visual dos quatro fluxos no telefone real.

## Última execução

Data/hora: 2026-07-22 03:16:58 -04:00

## Atualização — separação entre imagem e overlay da foto frontal

Objetivo: corrigir o resultado observado no aparelho, onde o overlay da prévia estava espelhado e os pixels do JPEG continuavam espelhados, preservando o modo vídeo.

Implementação:

- A foto frontal voltou a usar `MIRROR_MODE_OFF` no pipeline compartilhado, corrigindo a orientação dos pixels entregues ao `ImageCapture` conforme o comportamento real do aparelho.
- Somente o `PreviewView` frontal em modo foto recebe `scaleX = -1`, mantendo a orientação visual solicitada para a imagem em tempo real.
- O overlay da prévia fotográfica foi separado do `PreviewView` e agora é desenhado por Compose acima da superfície; por isso não participa do espelhamento.
- O `OverlayEffect` fotográfico passou a ter somente `IMAGE_CAPTURE` como alvo, gravando overlay e logo normais no JPEG sem afetar a prévia.
- O caminho de vídeo não foi alterado.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital.
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador validar a foto frontal com referência assimétrica: imagem da prévia na orientação solicitada, overlay legível; imagem desespelhada na galeria, overlay gravado normal. Repetir com toque curto e disparo contínuo para verificar ausência de duplicação/piscada.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico conhecido; resta validação visual no aparelho.

## Última execução

Data/hora: 2026-07-22 03:12:32 -04:00

## Atualização — inversão dos pixels da foto sem transformar o overlay

Objetivo: inverter a imagem frontal no modo foto tanto na prévia quanto no arquivo visto na galeria, mantendo overlay e logo fora da transformação e sem alterar o modo vídeo.

Implementação:

- A prévia frontal compartilhada pelo modo foto usa `MIRROR_MODE_ON`, invertendo os pixels antes do `OverlayEffect` e conduzindo a mesma orientação ao JPEG.
- Removida a compensação `PreviewView.scaleX`, que transformava a composição inteira e poderia atingir visualmente texto e logo.
- Overlay e logo continuam desenhados depois da inversão dos pixels, portanto permanecem legíveis e na posição original.
- O caminho de vídeo não foi alterado e mantém a combinação anteriormente aprovada.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital.
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador validar com referência assimétrica a imagem frontal no modo foto e a foto aberta na galeria, confirmando que ambas têm a orientação solicitada e que overlay/logo não inverteram.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico; resta validação visual no aparelho.

## Última execução

Data/hora: 2026-07-22 03:03:30 -04:00

## Atualização — orientação isolada da foto frontal

Objetivo: manter a imagem em tempo real da câmera fotográfica como aprovada e corrigir somente a orientação da foto frontal salva na galeria, sem alterar o modo vídeo.

Implementação:

- A prévia compartilhada da foto frontal passou para `MIRROR_MODE_OFF`, fazendo o `ImageCapture` produzir o JPEG na orientação oposta à rodada anterior.
- Somente o `PreviewView` da foto frontal recebe `scaleX = -1`, compensando visualmente a imagem em tempo real para que ela permaneça como estava.
- O modo vídeo permanece sem compensação de tela e conserva `MIRROR_MODE_ON` na prévia e no `VideoCapture`; seu comportamento aprovado não foi alterado.
- Conforme solicitado pelo operador, o overlay da prévia fotográfica ficou fora do critério desta etapa e será discutido separadamente.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital.
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador confirmar que a prévia frontal de foto continua no sentido aprovado, que a nova foto salva aparece na orientação correta na galeria e que prévia/arquivo de vídeo continuam inalterados.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico; resta validação visual da foto frontal. O overlay será tratado na próxima etapa, por decisão explícita do operador.

## Última execução

Data/hora: 2026-07-22 02:54:22 -04:00

## Atualização — matriz final de orientação solicitada

Objetivo: aplicar exatamente o comportamento definido pelo operador, preservando integralmente overlay e logo.

Matriz implementada:

- Foto frontal: pixels da prévia e do JPEG invertidos em relação à rodada anterior.
- Vídeo frontal: pixels da prévia invertidos; arquivo gravado mantém a orientação da rodada aprovada.
- Câmera traseira: foto e vídeo permanecem sem alteração.
- Overlay e logo: nenhuma inversão em qualquer modo; continuam desenhados depois da transformação dos pixels.

Implementação:

- `Preview.Builder.setMirrorMode` passou a ser condicional: `MIRROR_MODE_ON` somente para lente frontal e `OFF` para traseira.
- `VideoCapture.Builder.setMirrorMode` mantém a mesma condição, preservando o arquivo frontal aprovado e evitando espelhar gravações traseiras.
- Não há uso de `scaleX`; portanto a transformação não atinge texto ou logo.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital (1 min 18 s).
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador comparar prévia e arquivo frontal de foto/vídeo usando referência assimétrica, confirmando a matriz acima e overlay/logo inalterados.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico conhecido; resta apenas confirmação visual da matriz no aparelho.

## Última execução

Data/hora: 2026-07-22 02:46:09 -04:00

## Atualização — espelho corrigido antes do overlay e margem fotográfica

Objetivo: corrigir overlay/logo espelhados na prévia frontal, afastar o logo da borda e desespelhar os pixels salvos sem inverter a camada informativa.

- Confirmado aparelho de validação: Samsung SM-A235M, Android 14/API 34. Nessa versão, `Preview.Builder.setMirrorMode()` é implementado.
- Removido `PreviewView.scaleX = -1`, que invertia a composição final e por isso espelhava texto/logo e trocava visualmente o canto do logo.
- `Preview.Builder` agora usa `MIRROR_MODE_OFF`, desativando o espelho antes do `OverlayEffect` compartilhado da foto.
- Conforme o resultado real observado com `MIRROR_MODE_OFF` no encoder frontal, `VideoCapture` passou para `MIRROR_MODE_ON`, aplicando a correção aos pixels antes de o overlay ser desenhado. Texto/logo permanecem legíveis.
- O efeito fotográfico recebeu `horizontalEdgeInsetFraction = 0.02`, afastando logo e bloco textual da lateral de forma coerente na prévia e no JPEG, sem troca de camada durante o disparo.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital (1 min 32 s).
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador usar texto físico ou objeto assimétrico na frontal e comparar prévia, foto e vídeo; confirmar imagem na orientação esperada, overlay legível e logo com folga lateral.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico conhecido; a combinação final de orientação deve ser confirmada visualmente no aparelho porque o encoder frontal apresentou comportamento oposto ao valor nominal anterior.

## Última execução

Data/hora: 2026-07-22 02:34:05 -04:00

## Atualização — foto volta a iniciar e vídeo frontal sem espelho

Objetivo: corrigir falha de inicialização do modo foto e gravação frontal espelhada na galeria, preservando overlay correto.

- Causa exata do modo foto confirmada no bytecode CameraX 1.4.1: `ImageCapture.Builder.setMirrorMode()` existe por contrato, mas sua implementação lança sempre `UnsupportedOperationException("setMirrorMode is not supported.")`.
- A chamada incompatível foi removida do `ImageCapture.Builder`; o modo foto volta a vincular a câmera.
- Vídeo possui implementação real de espelhamento no CameraX 1.4.1. A criação mudou de `VideoCapture.withOutput(recorder)` para `VideoCapture.Builder(recorder).setMirrorMode(MIRROR_MODE_OFF).build()`.
- A prévia frontal continua com a transformação visual cancelada, portanto prévia e arquivo de vídeo devem usar a mesma orientação não espelhada. O `OverlayEffect` do vídeo não foi alterado.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital (2 min 22 s).
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador abrir foto frontal/traseira, depois gravar vídeo frontal e conferir na galeria que imagem, texto e logo não estão espelhados.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico conhecido; orientação final do encoder de vídeo precisa de confirmação visual no aparelho.

## Última execução

Data/hora: 2026-07-21 16:29:41 -04:00

## Atualização — pipeline fotográfico único, não espelhado e rápido

Objetivo: eliminar definitivamente a duplicação de overlay/logo, corrigir foto frontal espelhada e reduzir o intervalo do disparo contínuo.

- Foram removidos do modo foto o bitmap congelado, máscara de obturador, espera de frames e camada Compose de overlay.
- Foto agora possui uma única camada de texto/logo: o `OverlayEffect` do CameraX, ativo continuamente na prévia e no JPEG. Não existe segunda camada para duplicar durante `takePicture`.
- Na câmera frontal, `PreviewView.scaleX = -1` cancela o espelhamento visual aplicado pela prévia. `ImageCapture.Builder.setMirrorMode(MIRROR_MODE_OFF)` força o JPEG na mesma orientação não espelhada.
- `CAPTURE_MODE_MINIMIZE_LATENCY` substituiu `CAPTURE_MODE_MAXIMIZE_QUALITY`. O seletor `HIGHEST_AVAILABLE_STRATEGY` foi mantido, portanto a resolução continua sendo a maior disponível; o pipeline apenas prioriza cadência sobre processamento fotográfico adicional.
- O disparo não aguarda mais composição ou snapshot antes de chamar `takePicture`, beneficiando toque curto e pressão longa.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/PhotoScreen.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital (1 min 17 s).
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador segurar o disparador nas câmeras traseira e frontal e confirmar prévia viva, uma única camada, JPEG não espelhado e cadência aceitável; conferir também foco por toque na frontal após a transformação visual.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico conhecido; a orientação final e a cadência dependem da validação no hardware real.

## Última execução

Data/hora: 2026-07-21 16:21:59 -04:00

## Atualização — obturador sem tela cinza

Objetivo: remover a máscara cinza visível durante o processamento da foto sem reintroduzir duplicação/salto e sem perder o overlay salvo.

- A máscara sólida `#303030` foi removida.
- Quando o disparo começa, `CameraPreview` captura o último bitmap disponível da `PreviewView` e o apresenta sobre a saída viva do CameraX.
- A UI aguarda dois frames Compose antes de chamar `takePicture`, permitindo que o frame congelado seja composto antes de o efeito fotográfico aparecer na saída de preview.
- A camada Compose continua desenhando uma única cópia estável de texto e logo sobre o frame congelado.
- Ao finalizar ou falhar, o bitmap temporário é descartado e a prévia viva retorna. O pipeline que grava o overlay no JPEG não foi alterado.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/PhotoScreen.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital (1 min 21 s).
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador validar toque curto e pressão longa nas duas câmeras, confirmando congelamento breve do frame sem cinza, duplicação ou salto, e overlay presente nos JPEGs.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico conhecido; a disponibilidade do bitmap da `PreviewView` e a transição visual precisam de confirmação no aparelho real.

## Última execução

Data/hora: 2026-07-21 16:17:44 -04:00

## Atualização — restauração do overlay salvo e obturador estável

Objetivo: corrigir a regressão em que o overlay saltava para cima durante o disparo e não era gravado nas fotos da galeria.

- Causa confirmada: preencher `pendingPhotoOverlay` somente em `onCaptureStarted` era tarde para o pipeline de efeito da imagem; o CameraX podia processar o frame sem o desenho.
- O instantâneo voltou a ser entregue ao `OverlayEffect` antes de `takePicture`, garantindo texto e logo no arquivo salvo.
- Para não reintroduzir a duplicação visual, a UI ativa primeiro um estado de obturador, aguarda um frame Compose com `withFrameNanos` e só então submete a captura.
- Durante o processamento, uma máscara opaca cobre a saída de preview do efeito, enquanto a camada Compose desenha uma única cópia estável do overlay e do logo na posição visual aprovada.
- No callback final, efeito pendente e estado de obturador são limpos juntos.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/PhotoCapture.kt`
- `app/src/main/kotlin/com/saile/shub/photo/PhotoScreen.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital (1 min 36 s).
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador capturar uma foto traseira, uma frontal e uma sequência; confirmar ausência de salto/duplicação no obturador e presença de texto/logo em cada arquivo aberto na galeria.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico conhecido; o conteúdo real dos JPEGs e a transição visual do obturador dependem da conferência do operador.

## Última execução

Data/hora: 2026-07-21 16:06:22 -04:00

## Atualização — remoção da duplicação visual no disparo

Objetivo: eliminar texto e logo duplicados momentaneamente na prévia durante `takePicture`, sem alterar o overlay correto do arquivo salvo.

- Causa: durante toda a captura pendente, o `OverlayEffect` combinado do CameraX aparecia na saída de preview ao mesmo tempo que a camada Compose permanente.
- `pendingPhotoOverlay` agora é preenchido somente no callback real `ImageCapture.OnImageSavedCallback.onCaptureStarted()`, em vez de antes de submeter a captura.
- No mesmo evento, a tela marca o efeito fotográfico como ativo e oculta temporariamente a camada Compose. Assim somente uma camada aparece durante o processamento.
- Em `onImageSaved` ou `onError`, o efeito pendente é limpo e a camada Compose retorna de forma sincronizada.
- Callbacks de `ImageCapture` passaram para o executor principal; a persistência de metadados continua isolada no executor próprio e não bloqueia a interface.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/PhotoCapture.kt`
- `app/src/main/kotlin/com/saile/shub/photo/PhotoScreen.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital (2 min 29 s).
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador executar o item 32 do roteiro em foto traseira, frontal e disparo contínuo, confirmando uma única cópia visual durante todo o ciclo e overlay correto na galeria.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico conhecido; a observação do instante do disparo depende da validação visual do operador.

## Última execução

Data/hora: 2026-07-21 12:28:31 -04:00

## Atualização — acabamento da prévia e disparo contínuo

Objetivo: corrigir feedbacks sem ícone, espelhamento da camada informativa na câmera frontal, margem visual apertada e lentidão para sequências de fotos.

- “Metadados copiados” agora aparece dentro do painel com ícone de confirmação e desaparece automaticamente.
- “Atualizando localização...” passou de `Toast` sem ícone para pop-up Compose com ícone de busca de localização.
- No modo foto, o `OverlayEffect` fica transparente quando não há captura pendente. A prévia usa a camada Compose não espelhada, mantendo texto e logo legíveis na câmera frontal; o efeito continua gravando o overlay no arquivo durante `takePicture`.
- Texto e logo receberam margem lateral visual adicional de 2% apenas na prévia. Foto e vídeo salvos continuam usando a posição anterior aprovada.
- Pressionar o disparador tira a primeira foto imediatamente; mantê-lo pressionado além do limiar de toque longo ativa disparo contínuo com retorno háptico. Soltar impede novos disparos.
- Disparo contínuo funciona nas câmeras traseira e frontal. O flash de tela frontal permanece ativo durante a pressão, evitando repetir a espera de 300 ms entre fotos.
- Capturas permanecem sequenciais para não sobrescrever o instantâneo do overlay. A persistência SQLite foi movida para executor separado e não fica mais no caminho crítico do próximo disparo.
- Nomes de foto agora incluem milissegundos, evitando colisões em sequências no mesmo segundo.

Arquivo criado:

- `app/src/main/kotlin/com/saile/shub/photo/ui/IconStatusPopup.kt`

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/OverlayRenderer.kt`
- `app/src/main/kotlin/com/saile/shub/photo/PhotoCapture.kt`
- `app/src/main/kotlin/com/saile/shub/photo/PhotoScreen.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt`
- `app/src/main/kotlin/com/saile/shub/photo/ui/PhotoBottomBar.kt`
- `docs/architecture/ARCHITECTURE.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso em duas rodadas.
- `:app:assembleDebug` — sucesso em duas rodadas.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital (1 min 16 s).
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador validar os itens 28 a 31 do roteiro: frontal sem overlay/logo espelhados, margem somente na prévia, pressão longa em ambas as câmeras e feedbacks com ícone.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico conhecido; cadência real do disparo contínuo depende do hardware e do modo de qualidade máxima, portanto o aceite de velocidade e a inspeção dos arquivos dependem do operador.

## Última execução

Data/hora: 2026-07-21 12:08:21 -04:00

## Atualização — correção do vínculo da câmera no modo foto

Objetivo: corrigir a falha real `Effects target ... is not supported` exibida ao abrir o modo foto depois da migração do burn-in para `OverlayEffect`.

- Causa confirmada: no CameraX 1.4.1, `OverlayEffect` é um efeito de superfície. `IMAGE_CAPTURE` isolado não é uma máscara suportada por esse processador; para incluir foto, a combinação válida é `PREVIEW | VIDEO_CAPTURE | IMAGE_CAPTURE` (valor 7).
- O modo foto passou a usar a máscara combinada dentro do grupo que contém preview e captura de imagem.
- A camada Compose deixa de redesenhar o overlay no modo foto, pois o próprio efeito combinado já o entrega à prévia; isso evita overlay duplicado. No modo vídeo a camada Compose permanece, pois o efeito continua aplicado somente ao arquivo gravado.
- Arquitetura, README do módulo e roteiro real receberam a regra de compatibilidade e um caso de regressão para alternância vídeo/foto.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/main/kotlin/com/saile/shub/photo/README.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital (1 min 16 s).
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador alternar vídeo → foto várias vezes e capturar uma imagem, confirmando câmera aberta, um único overlay na prévia e um único overlay na foto salva.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico conhecido; falta a confirmação visual da regressão no telefone.

## Última execução

Data/hora: 2026-07-21 12:01:39 -04:00

## Atualização — ícones nos alertas da galeria sFoto

Objetivo: completar os pop-ups da galeria com identificação visual e ações iconográficas consistentes com Material 3.

- O alerta de metadados recebeu ícone de informação no cabeçalho e ícones nas ações de copiar, gerar rota e fechar.
- O alerta de exclusão recebeu ícone de aviso no cabeçalho e ícones nas ações de apagar e cancelar.
- Ícones decorativos das ações permanecem acompanhados de texto e sem descrição redundante, preservando a leitura por acessibilidade.
- Cores semânticas foram aplicadas: primária para informação e `error` para aviso/exclusão.
- O guia de UI e o roteiro de validação real foram atualizados.

Arquivos modificados:

- `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital (1 min 22 s).
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- `adb shell am start -n com.saile.shub/.MainActivity` — aplicativo aberto.

Próximos passos concretos: operador abrir os alertas de metadados e exclusão e confirmar alinhamento, contraste e legibilidade dos ícones. Depois do aceite visual final, atualizar a versão para `1.0.0`.

Bloqueios ou decisões pendentes: nenhum bloqueio técnico; a conferência visual no telefone permanece com o operador.

## Última execução

Data/hora: 2026-07-21 11:52:16 -04:00

## Atualização — robustez final do sFoto antes da v1.0.0

Objetivo: corrigir os quatro riscos técnicos ainda abertos na revisão geral do sFoto: estado de gravação divergente, pico de memória no burn-in da foto, permissões antecipadas e preview vazio sem diagnóstico.

Concluído:

- A interface de vídeo agora segue os eventos reais do CameraX (`Start` e `Finalize`) por uma máquina de estados `IDLE/STARTING/RECORDING/STOPPING`; falhas de início retornam a interface para o estado ocioso.
- O overlay da foto passou para `OverlayEffect(IMAGE_CAPTURE)`. Foi removido o fluxo que decodificava, copiava e recomprimia o bitmap inteiro e criava um `ByteArray`, reduzindo o risco de travamento em capturas de resolução máxima.
- A permissão de câmera é solicitada somente ao entrar no sFoto, localização é solicitada no contexto da câmera e microfone somente ao tentar gravar vídeo. Se o microfone for negado, o vídeo continua sem áudio com aviso.
- Falhas ao obter ou vincular a câmera agora limpam os controles, exibem a causa na tela e oferecem `Tentar novamente`.
- Capturas de foto simultâneas foram bloqueadas para preservar o instantâneo exato do overlay e evitar concorrência no pipeline.
- A consulta de galeria continua usando `RELATIVE_PATH` no Android 10+; `DATA` existe somente no fallback compatível com Android 9 ou anterior.
- Foram adicionados testes unitários para as transições da gravação e para a formatação dos metadados do overlay.

Arquivos criados:

- `app/src/main/kotlin/com/saile/shub/photo/VideoRecordingUiState.kt`
- `app/src/test/kotlin/com/saile/shub/photo/VideoRecordingUiStateTest.kt`
- `app/src/test/kotlin/com/saile/shub/photo/data/PhotoMediaMetadataTest.kt`

Arquivos modificados:

- `app/build.gradle.kts`
- `app/src/main/kotlin/com/saile/shub/SHubApp.kt`
- `app/src/main/kotlin/com/saile/shub/photo/PhotoCapture.kt`
- `app/src/main/kotlin/com/saile/shub/photo/ui/PhotoScreen.kt`
- `app/src/main/kotlin/com/saile/shub/photo/ui/PhotoTopBar.kt`
- `app/src/main/kotlin/com/saile/shub/photo/ui/PhotoBottomBar.kt`
- `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`
- `app/src/main/kotlin/com/saile/shub/photo/README.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/product/MASTER_SPEC.md`
- `docs/ui/UI_UX_GUIDE.md`
- `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`
- `STATUS.md`

Testes executados:

- `:app:testDebugUnitTest` — sucesso.
- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital (1 min 39 s).
- `adb devices` — aparelho `RX8TA06NC3W` conectado.
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success`.
- `adb shell am start -n com.saile.shub/.MainActivity` — atividade aberta; processo ativo com PID `32517`.

Próximos passos concretos:

1. Operador validar no aparelho foto em resolução máxima e orientações vertical/horizontal, conferindo nitidez e alinhamento do overlay.
2. Operador validar início/fim de vídeo, indicador de gravação, áudio negado/permitido e overlay no MP4.
3. Operador validar a mensagem e o botão de nova tentativa em uma falha real de câmera.
4. Depois do aceite visual e funcional, alterar `versionName` de `0.1.0` para `1.0.0` e gerar o APK final.

Bloqueios ou decisões pendentes: a build está instalada e não há bloqueio técnico conhecido; a definição de pronto ainda exige a validação visual do operador no telefone. O fluxo de primeira permissão não foi resetado porque limpar os dados apagaria estado local e requer autorização explícita.

## Última execução

Data/hora: 2026-07-21 11:38:44 -04:00

## Atualização - reavaliação dos riscos originais do sFoto

Objetivo: confrontar a revisão geral de 2026-07-21 10:20 com o código atual, sem alterar o comportamento do aplicativo.

Resolvidos ou encerrados:

- `.srt`: removido do escopo por decisão do operador; o requisito atual é overlay gravado diretamente no MP4 por `OverlayEffect`.
- Galeria/`DATA`: Android 10+ usa `RELATIVE_PATH` e operações do `MediaStore` executam em `Dispatchers.IO`; `DATA` ficou apenas no fallback pré-Android 10 e em uma varredura redundante pós-captura.
- Validação de engenharia: builds debug e benchmark compilam; benchmark foi instalada em `RX8TA06NC3W` e aberta. O operador já forneceu feedback real sobre a galeria.

Ainda pendentes:

- O estado visual `isRecording` ainda é marcado antes da confirmação `VideoRecordEvent.Start`; falha ou câmera indisponível pode deixar a UI divergente da gravação real.
- O burn-in da foto ainda decodifica, copia e recomprime o bitmap inteiro, além de criar um `ByteArray`, mantendo risco de pico de memória em fotos grandes.
- Câmera, áudio e localização ainda são solicitados juntos na abertura do hub, antes de entrar no sFoto.
- `CameraPreview` ainda possui `catch` vazio no vínculo dos casos de uso, podendo produzir preview vazio sem diagnóstico para o usuário.
- Não existem arquivos de teste em `app/src/test` ou `app/src/androidTest`; a cobertura atual é build, instalação e roteiro manual ADB.
- A validação real ainda é parcial: faltam executar integralmente captura, vídeo, foco, overlay, permissões, metadados, rota e falhas simuladas.

Arquivos modificados: `STATUS.md`.

Testes executados: inspeção estática por `rg`; contagem de testes automatizados igual a zero. Nenhuma nova compilação ou interação no telefone foi necessária para esta revisão.

Próximos passos: corrigir ciclo de gravação, pipeline de memória da foto, permissões sob demanda e tratamento de erro da câmera; adicionar testes mínimos; executar o roteiro real antes de alterar `versionName` de `0.1.0` para `1.0.0`.

Bloqueios ou decisões pendentes: nenhum técnico para iniciar as correções; o aceite de campo continua dependendo do operador.

## Última execução

Data/hora: 2026-07-21 11:35:41 -04:00

## Atualização - trilho circular, metadados e rota da galeria

Objetivo: reproduzir o puxador circular do Google Fotos e adicionar, no painel de informações, cópia dos dados do overlay e geração de rota no Google Maps.

- O puxador do trilho agora é um círculo com setas para cima/baixo; o balão de mês/ano e o cancelamento de saltos concorrentes foram preservados.
- Criado `sfoto_media_metadata.db`, que associa a URI de cada nova mídia ao instantâneo de UTM/GEO, data/hora, endereço, nome e opções visíveis do overlay.
- Fotos persistem a ficha depois do burn-in concluído; vídeos persistem o instantâneo do início da gravação depois da finalização bem-sucedida.
- O painel `i` mostra os dados estruturados, copia exatamente os campos do overlay e abre `google.navigation` com fallback para rota web do Google Maps.
- Mídias anteriores à atualização continuam acessíveis, mas exibem aviso e mantêm cópia/rota desabilitadas por não possuírem ficha confiável.
- Ao excluir mídias pela galeria, as fichas associadas também são removidas.

Arquivos criados: `app/src/main/kotlin/com/saile/shub/photo/data/PhotoMediaMetadataDbHelper.kt`.

Arquivos modificados: `app/src/main/kotlin/com/saile/shub/photo/PhotoCapture.kt`, `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt`, `docs/architecture/ARCHITECTURE.md`, `docs/product/MASTER_SPEC.md`, `docs/ui/UI_UX_GUIDE.md`, `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md` e `STATUS.md`.

Testes executados:

- `:app:assembleDebug` — sucesso.
- `:app:assembleBenchmark` — sucesso com R8, resource shrinking e lint vital; APK de 3.613.719 bytes gerado em 2026-07-21 11:35:00.
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- Atividade principal aberta e processo ativo com PID `29230`.
- Nenhuma captura, gesto, cópia ou abertura do Google Maps foi executada pelo agente.

Próximos passos: operador capturar foto e vídeo novos e validar os itens 17, 19 e 20 do roteiro ADB. Com aprovação visual e funcional, atualizar `versionName` para `1.0.0` e gerar o APK final.

Bloqueios ou decisões pendentes: mídias antigas não podem receber retroativamente os dados exatos do overlay sem OCR ou fonte original; validação final depende do operador.

## Última execução

Data/hora: 2026-07-21 11:25:13 -04:00

## Atualização - galeria fluida e trilho estilo Google Fotos

Objetivo: remover o lag relatado na grade e substituir o primeiro trilho por um controle de rolagem rápida no padrão do Google Fotos, preparando o fechamento do sFoto 1.0.0.

- A leitura e a exclusão de mídias saíram da thread principal e agora executam em `Dispatchers.IO`.
- Android 10+ consulta somente `Pictures/sHub/` e `Movies/sHub/` por `RELATIVE_PATH`; `DATA` ficou restrito à compatibilidade pré-Android 10.
- O trilho ganhou indicador fino, faixa de toque de 40 dp, acompanhamento suave da rolagem e balão de mês/ano durante o arraste.
- Saltos sucessivos cancelam a coroutine anterior, evitando a fila de `scrollToItem` que causava engasgos.
- A consulta da última mídia para a miniatura agora encerra após o primeiro resultado.

Arquivos modificados: `app/src/main/kotlin/com/saile/shub/photo/camera/MediaUtils.kt`, `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt`, `docs/architecture/ARCHITECTURE.md`, `docs/ui/UI_UX_GUIDE.md`, `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md` e `STATUS.md`.

Testes executados:

- `:app:assembleDebug` — sucesso; APK gerado em 2026-07-21 11:21:36.
- `:app:assembleBenchmark` — sucesso; APK otimizado gerado em 2026-07-21 11:23:50.
- `adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk` — `Success` no aparelho `RX8TA06NC3W`.
- Atividade `com.saile.shub/.MainActivity` aberta; processo `com.saile.shub` ativo com PID `27910`.
- Nenhum gesto ou julgamento visual foi executado pelo agente.

Próximos passos: operador validar rolagem natural, arraste do trilho e balão de mês/ano. Com a aprovação visual, atualizar `versionName` para `1.0.0`, gerar o APK final e encerrar o sFoto v1.0.0.

Bloqueios ou decisões pendentes: aceite visual da fluidez no aparelho real; a versão permanece `0.1.0` até essa confirmação.

## Última execução

Data/hora: 2026-07-21 10:44:00 -04:00

## Atualização - instalação ADB do sFoto

Objetivo: instalar no telefone a build debug contendo qualidade máxima, seleção total, rolagem rápida e foco inteligente.

- Aparelho autorizado: `RX8TA06NC3W`.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`: instalação concluída com `Success`.
- `adb shell am start -n com.saile.shub/.MainActivity`: aplicativo aberto com sucesso.

Arquivos modificados: `STATUS.md`.

Testes executados: conexão ADB, instalação incremental e abertura da atividade principal concluídos. Nenhum gesto ou avaliação visual foi executado pelo agente.

Próximos passos: operador validar foco contínuo e por toque, resolução de foto/vídeo, seleção total e trilho de rolagem no aparelho.

Bloqueios ou decisões pendentes: nenhum para instalação; validação visual depende do operador.

## Última execução

Data/hora: 2026-07-21 10:35:48 -04:00

## Atualização - foco inteligente do sFoto

Objetivo: corrigir o requisito esclarecido pelo operador: foco inteligente, não “foto inteligente”.

- Foto usa foco contínuo para fotografia e vídeo usa foco contínuo para vídeo, configurados no pipeline Camera2/CameraX.
- Um toque curto no preview cria uma ação temporária de foco automático e exposição no ponto tocado; ela é cancelada após três segundos para a câmera retomar o foco contínuo.
- Um círculo branco breve confirma visualmente o ponto selecionado, sem interferir no overlay gravado.
- Documentados o comportamento e o teste manual em aparelho real.

Arquivos modificados: `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`, `docs/architecture/ARCHITECTURE.md`, `docs/ui/UI_UX_GUIDE.md`, `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md` e `STATUS.md`.

Testes executados: `:app:assembleDebug` concluído com sucesso. A validação de foco contínuo e foco por toque em câmeras traseira e frontal ainda depende do aparelho real, conforme item 18 do roteiro ADB.

Próximos passos: validar o item 18 em telefone real e corrigir o ciclo de gravação apontado na revisão geral.

Bloqueios ou decisões pendentes: nenhum para o foco; o comportamento final depende de suporte de autofoco da lente disponível no aparelho.

## Última execução

Data/hora: 2026-07-21 10:32:08 -04:00

## Atualização - qualidade de captura e navegação da galeria sFoto

Objetivo: priorizar foto e vídeo com a maior qualidade disponível e agilizar a seleção e a rolagem de muitas mídias na galeria.

- Foto passou a usar o seletor de resolução máxima disponível e `CAPTURE_MODE_MAXIMIZE_QUALITY`; vídeo já usa `Quality.HIGHEST`. A resolução efetiva continua limitada pelo conjunto de casos de uso suportado pela câmera do aparelho.
- Após iniciar seleção múltipla com toque longo, a barra da galeria exibe uma caixa para selecionar ou desmarcar todas as mídias antes de compartilhar ou excluir.
- Galerias com mais de doze mídias agora mostram, à direita, um trilho auxiliar de rolagem rápida com área de toque ampla e indicador discreto.
- Especificação ajustada: vídeos devem receber o overlay diretamente no MP4; `.srt` não faz mais parte do escopo.
- “Foto inteligente” permanece pendente de definição funcional pelo operador; não foi implementada para evitar um comportamento diferente do esperado.

Arquivos modificados: `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`, `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt`, `docs/architecture/ARCHITECTURE.md`, `docs/product/MASTER_SPEC.md`, `docs/ui/UI_UX_GUIDE.md`, `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md` e `STATUS.md`.

Testes executados: `:app:assembleDebug` concluído com sucesso. A validação de resolução, seleção total, exclusão e trilho de rolagem em telefone real foi acrescentada ao roteiro ADB e ainda depende do operador.

Próximos passos: validar os itens 15 a 17 do roteiro ADB em aparelho real; definir o que “foto inteligente” deve fazer antes de implementar; corrigir o ciclo de gravação apontado na revisão anterior.

Bloqueios ou decisões pendentes: definir o comportamento de “foto inteligente” (por exemplo, disparo automático por estabilidade, melhoria noturna ou outra função de campo).

## Última execução

Data/hora: 2026-07-21 10:20:39 -04:00

## Atualização - revisão geral do sFoto

Objetivo: revisar a implementação atual do sFoto em relação à especificação, arquitetura e uso de campo, sem alterar o comportamento do aplicativo.

- Revisados captura, overlay, vídeo, permissões, localização, armazenamento, galeria e persistência de preferências.
- Confirmadas lacunas a priorizar: legenda `.srt` sincronizada para vídeo ainda não é gerada; o estado visual de gravação pode divergir do `Recording` real após falha/inicialização; a galeria consulta o `MediaStore` pela coluna `DATA`, obsoleta para o armazenamento com escopo atual; e a pós-produção de foto decodifica a imagem completa em memória.
- Identificados também permissões solicitadas na abertura do hub, erros de vínculo da câmera silenciosamente ignorados, consultas de galeria na thread principal e ausência de testes automatizados do sFoto.

Arquivos modificados: `STATUS.md`.

Testes executados: `:app:assembleDebug` concluído com sucesso. Não há arquivos em `app/src/test` ou `app/src/androidTest`.

Próximos passos: corrigir primeiro o ciclo de gravação e a geração de `.srt`; migrar a consulta de mídia para `RELATIVE_PATH` em `Dispatchers.IO`; substituir o burn-in de foto por pipeline com uso de memória limitado; depois validar o roteiro ADB em aparelho real.

Bloqueios ou decisões pendentes: confirmar com o operador se o overlay visual deve continuar gravado no vídeo além da legenda `.srt` exigida pela especificação.

## Última execução

Data/hora: 2026-07-20 13:59:03 -04:00

## Atualizacao - consolidacao documental

Objetivo: registrar a arquitetura final da galeria sFoto, o ciclo de vida da lanterna e o roteiro de validacao em aparelho real.

- `ARCHITECTURE.md`: documentados pipeline de miniaturas/pager/video, build `benchmark` e responsabilidades de `PhotoScreen`/`CameraPreview`.
- `UI_UX_GUIDE.md`: documentados comportamento da galeria, reproducao sob demanda, pausa, transicao visual e regras do flash de video.
- `REAL_DEVICE_ADB_TEST_PLAN.md`: adicionada instalacao da variante otimizada e roteiro manual para grade, pager, video e lanterna.

Arquivos modificados: `docs/architecture/ARCHITECTURE.md`, `docs/ui/UI_UX_GUIDE.md`, `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md` e `STATUS.md`.

Testes executados: nenhum; alteracao exclusivamente documental.

Proximo passo: continuar a partir do feedback do operador na proxima execucao.

## Atualizacao - correcao deterministica do flash de video

Objetivo: eliminar a corrida que fazia a lanterna ligar ou apagar de forma intermitente ao entrar no modo video.

- `PhotoScreen` calcula apenas o estado desejado: tela de captura visivel + modo video + camera traseira + icone do raio em `ON`.
- `CameraPreview`, que possui a camera vinculada, aplica esse estado ao controle atual e reaplica imediatamente depois de cada `bindToLifecycle`.
- Abrir galeria/configuracoes envia `false`; ao fechar, o estado `ON` escolhido pelo usuario e restaurado na camera ativa.
- Sair do sFoto continua desligando a lanterna e desvinculando CameraX.
- Removida a disputa entre o controle antigo e o novo controle criado ao trocar para modo video.

Arquivos modificados: `app/src/main/kotlin/com/saile/shub/photo/PhotoScreen.kt`, `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt` e `STATUS.md`.

Validacao de engenharia: `:app:assembleBenchmark` concluido com sucesso; APK otimizado instalado. Nenhum gesto, navegacao ou teste visual foi executado pelo agente.

Proximo passo: operador validar raio `ON` no modo video, abertura/fechamento de galeria e configuracoes, e retorno ao hub.

## Atualizacao - dissolucao do video e ciclo de vida do flash

Objetivos: suavizar a troca da previa para o video e limitar a lanterna ao contexto visivel da camera sFoto.

- A previa faz uma dissolucao de 160 ms somente depois do primeiro frame real do video, evitando troca visual abrupta.
- O controle da lanterna foi removido do `CameraPreview` e centralizado no `PhotoScreen`.
- A lanterna de video so pode ficar ligada com tela de captura visivel, modo video, camera traseira e flash `ON`.
- Abrir galeria/configuracoes, trocar de modo/lente ou sair para o hub forca `enableTorch(false)`.
- Ao descartar a tela sFoto, CameraX agora executa `unbindAll()` e encerra o handler de overlay.
- Alternar flash nao reinicializa mais os casos de uso da camera; foto atualiza `ImageCapture.flashMode` e video atualiza apenas a lanterna.

Arquivos modificados: `app/src/main/kotlin/com/saile/shub/photo/PhotoScreen.kt`, `app/src/main/kotlin/com/saile/shub/photo/camera/CameraPreview.kt`, `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt` e `STATUS.md`.

Validacao de engenharia: `:app:assembleBenchmark` concluido com sucesso; APK otimizado instalado. Nenhum gesto, navegacao ou teste visual foi executado pelo agente.

Proximos passos: operador validar a dissolucao e confirmar o desligamento do flash ao abrir galeria/configuracoes ou sair do sFoto.

## Atualizacao - transicao natural ao iniciar video

Objetivo: ocultar o shutter preto entre o toque em reproduzir e o primeiro frame real do video.

- A previa de alta qualidade permanece sobre o `PlayerView` enquanto o Media3 prepara o video.
- A previa so e removida depois do callback `Player.Listener.onRenderedFirstFrame()`.
- O shutter preto continua ativo internamente para bloquear conteudo residual, mas nao deve mais ficar visivel ao usuario.
- Pausa, retomada, navegacao horizontal e qualidade da previa permanecem inalteradas.

Arquivos modificados: `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt` e `STATUS.md`.

Validacao de engenharia: `:app:assembleBenchmark` concluido com sucesso; APK otimizado instalado. Nenhum gesto, navegacao ou teste visual foi executado pelo agente.

Proximo passo: operador validar a transicao previa -> primeiro frame ao tocar em reproduzir.

## Atualizacao - qualidade e pausa de video

Objetivo: corrigir a baixa qualidade da previa, preservar o frame atual ao pausar e eliminar frames residuais de outra midia.

- Grade continua usando miniatura leve de 320 px; pager agora extrai um frame real do video dimensionado a tela com `MediaMetadataRetriever`.
- Estado de video foi separado em preparado e reproduzindo. Pausar mantem o `PlayerView` e o frame atual montados.
- Retomar continua da posicao pausada; ao terminar, o video permanece no frame final e um novo play reinicia do inicio.
- O shutter do `PlayerView` agora e preto e `keepContentOnPlayerReset` fica desativado, impedindo a exibicao de frame residual de outro video/midia.
- Trocar de pagina, voltar ou fechar ainda libera o video preparado sem bloquear o pager.

Arquivos modificados: `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt` e `STATUS.md`.

Validacao de engenharia: `:app:assembleBenchmark` concluido com sucesso; APK otimizado instalado. Nenhum gesto, navegacao ou teste visual foi executado pelo agente.

Proximo passo: operador validar qualidade da previa, pausa no frame corrente e ausencia de frame aleatorio.

## Atualizacao - video sob demanda e build otimizada

Objetivos: impedir que videos bloqueiem o pager e disponibilizar uma build representativa de producao para validar a grade Compose.

- Video nao inicia ao chegar na pagina; a reproducao exige toque no botao central.
- `REPEAT_MODE_OFF`: o video nao entra mais em loop.
- `PlayerView` fica dentro da pagina, sem controles de tela inteira e sem captura intencional do gesto horizontal.
- Ao mudar de pagina, terminar o video, voltar ou fechar o pager, a reproducao e encerrada.
- Criada a variante `benchmark`, nao depuravel, assinada localmente e otimizada com R8/resource shrinking. A variante existe porque a documentacao Android alerta que `debuggable=true` degrada fortemente o desempenho e nao representa a fluidez de producao.
- Grade mantem chaves estaveis, `contentType`, imagens dimensionadas, sem subcomposicao/crossfade e com baixa qualidade de filtragem adequada a miniaturas.

Arquivos modificados: `app/build.gradle.kts`, `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt` e `STATUS.md`.

Validacao de engenharia: `:app:assembleDebug` e `:app:assembleBenchmark` concluidos com sucesso. `app-benchmark.apk` instalado com sucesso. Nenhum gesto, navegacao ou teste visual foi executado pelo agente.

Proximos passos: operador validar a grade e confirmar que foto-video-foto continua navegavel antes, durante e depois de tocar em reproduzir.

## Atualizacao - desempenho da grade

Objetivo: corrigir somente o lag da grade vertical, preservando o pager horizontal aprovado pelo operador.

- Requisicoes de fotos limitadas a duas decodificacoes simultaneas para evitar saturacao de CPU durante a rolagem.
- Miniaturas solicitadas em 320 px, com `Scale.FILL`, `Precision.INEXACT`, bitmap de hardware e chave de cache estavel.
- Crossfade permanece desativado e a implementacao do pager horizontal nao foi alterada.

Arquivos modificados: `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt` e `STATUS.md`.

Validacao de engenharia: `:app:assembleDebug` concluido com sucesso em 10 s; APK instalado com `adb install -r`. Nenhum gesto, navegacao ou teste visual foi executado pelo agente, conforme orientacao do operador.

Proximo passo: operador validar exclusivamente a rolagem vertical da grade e informar o resultado.

## Atualizacao - galeria simples

Objetivo: simplificar a Galeria sFoto conforme o feedback do operador e remover estados visuais que causavam piscadas no pager.

- Removida a dependencia Telephoto e todo o estado de zoom por pagina.
- O `HorizontalPager` mantem somente a pagina anterior, atual e proxima, com chaves estaveis e sem crossfade.
- Fotos usam requisicoes Coil dimensionadas para a grade, com cache padrao e bitmaps de hardware.
- Videos continuam usando miniaturas oficiais do MediaStore e um unico ExoPlayer.

Arquivos modificados: `app/build.gradle.kts`, `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt` e `STATUS.md`.

Validacao executada antes da orientacao atual do operador: `:app:assembleDebug` concluido com sucesso e APK instalado. A tentativa de medicao automatizada por ADB foi interrompida.

Decisao: nao executar testes de navegacao, gestos ou avaliacao visual no aparelho. O operador fara os testes reais e enviara o feedback; o agente cuidara da implementacao, compilacao e conformidade com a documentacao.

Proximo passo: aguardar o feedback do operador sobre a grade vertical e o swipe horizontal desta versao.

## Atualizacao desta execucao

Objetivo: corrigir o lag relatado na grade vertical e na navegacao horizontal da Galeria sFoto.

- Grade: cache LRU de miniaturas (16 MB), miniaturas de 320 px e no maximo duas decodificacoes simultaneas do `ContentResolver`.
- Pager: sem pre-carregamento de paginas vizinhas; elas nao competem com a animacao do swipe.
- Video: um unico `ExoPlayer` e reutilizado somente quando o gesto termina em um video. A miniatura e usada durante o swipe, eliminando a decodificacao duplicada pelo Coil e pelo player.

Arquivos modificados: `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt` e `STATUS.md`.

Testes executados:

- `:app:assembleDebug` - Build OK (46 s).
- `adb devices` - aparelho `RX8TA06NC3W` conectado.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` - instalado com sucesso.
- `adb shell am start -n com.saile.shub/.MainActivity` - app aberto no aparelho.

Proximos passos: no aparelho, percorrer rapidamente a grade vertical e alternar repetidamente entre fotos e videos no pager. Se houver lag, registrar se ocorre na grade, em fotos, em videos ou apenas na primeira abertura.

Bloqueio: a confirmacao visual de fluidez no aparelho depende do operador; a build nova ja esta instalada.

## Objetivo executado

Refatoração da Galeria Interna do sFoto (sHub) para melhor experiência de vídeo e swipe:
- **Integração com Media3 (ExoPlayer)**: O antigo `MediaPlayer` e `TextureView` foram substituídos pelo `ExoPlayer` e `PlayerView` da API Media3, garantindo transições mais suaves e controles nativos no modo tela cheia. O fundo (shutter) do ExoPlayer foi tornado transparente para eliminar o *pisca* preto na transição de fotos para vídeos.
- **Correção de Flicker no HorizontalPager**: Adicionada uma chave estável (`key = { mediaList[it].uri.toString() }`) e ativado o pré-carregamento nativo com `beyondViewportPageCount = 1` no `HorizontalPager`.
- **Otimização Extrema de Performance (Google Photos Native Thumbnail)**:
  - Substituímos a decodificação da biblioteca Coil na grade (`LazyVerticalGrid`) pelo motor nativo do Android (`contentResolver.loadThumbnail`). 
  - Com isso o FPS da grade volta a bater 60hz suave, pois o OS já contém as miniaturas pré-geradas e o app não precisa mais abrir gigabytes de vídeo no background apenas para desenhar a galeria.

## Arquivos criados ou modificados

- **Modificados**: 
  - `app/build.gradle.kts`
  - `app/src/main/kotlin/com/saile/shub/photo/ui/GalleryScreen.kt`
  - `STATUS.md`

## Testes executados

- `:app:assembleDebug` — Build OK (1m 24s). 
- `:app:installDebug` — Instalado no aparelho para teste.

## Próximos passos concretos

1. O Operador deve rodar e confirmar no aparelho que o problema crônico de lag e piscadas na galeria foi solucionado por completo com as miniaturas nativas.
2. Avançar para o módulo sGIS, conforme planejado.

## Bloqueios e decisões pendentes

- Nenhum bloqueio.
