# Plano de Teste em Telefone Real via ADB

## Observação importante

Este pacote não acessa diretamente o seu telefone. O agente que rodar no seu PC poderá executar os comandos abaixo se tiver `adb` disponível e o aparelho autorizado.

## Pré-checagem

```bash
adb devices
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
```

## Instalação do APK

```bash
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Para validar fluidez de Compose, usar a variante otimizada:

```bash
./gradlew assembleBenchmark
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
```

A variante `debug` serve para diagnóstico e não deve ser comparada diretamente com aplicativos de produção como Google Fotos ou Samsung Gallery.

## Abrir app

```bash
adb shell monkey -p com.saile.shub 1
```

## Screenshots para análise de UI

```bash
adb exec-out screencap -p > screenshots/shub_home.png
adb exec-out screencap -p > screenshots/sgeoconversor.png
adb exec-out screencap -p > screenshots/sfoto_overlay.png
adb exec-out screencap -p > screenshots/sgis_search.png
```

## Logs

```bash
adb logcat | grep -i "sHub\|sFoto\|sGIS\|sGeo"
```

## Testes obrigatórios — sGeoConversor

1. Inserir GEO `-22.951761169561568, -43.21047133691749`.
2. Confirmar saída UTM aproximada.
3. Copiar GEO.
4. Copiar UTM.
5. Copiar link.
6. Clicar em rota e validar abertura no Google Maps.

## Testes obrigatórios — sFoto

1. Abrir câmera.
2. Autorizar permissão.
3. Alternar cores: amarelo, verde, ciano, magenta e branco.
4. Capturar foto.
5. Conferir se overlay aparece na imagem salva.
6. Validar comportamento sem internet.
7. Abrir a galeria e percorrer a grade vertical rapidamente nos dois sentidos.
8. Abrir uma foto e navegar repetidamente por foto → vídeo → foto no pager horizontal.
9. Confirmar que o vídeo não inicia automaticamente e que o pager continua navegável.
10. Tocar em reproduzir, pausar e confirmar que o frame corrente permanece visível; retomar da mesma posição.
11. Confirmar ausência de frame preto ou conteúdo residual na transição prévia → vídeo.
12. No modo vídeo com câmera traseira, ativar o raio e confirmar a lanterna ligada.
13. Com o raio em `ON`, abrir galeria e configurações separadamente; confirmar que a lanterna apaga e religa ao retornar.
14. Voltar ao hub e fechar o app; confirmar que a lanterna permanece desligada.
15. Capturar foto e vídeo e conferir, nas propriedades do arquivo, que cada um usa a maior resolução/qualidade que o aparelho disponibiliza para aquele modo.
16. Na galeria, fazer toque longo em uma mídia, usar a caixa para selecionar todas, desmarcar todas e excluir uma seleção parcial; confirmar que somente os itens confirmados são removidos.
17. Com uma galeria longa, confirmar o puxador circular com setas para cima/baixo; arrastar o trilho e validar salto rápido para início, meio e fim, balão correto de mês/ano e ausência de engasgos.
18. Na câmera traseira e frontal, alternar entre um objeto próximo e um distante; confirmar foco contínuo em foto e vídeo. Tocar em um ponto do preview e confirmar a marca breve, a priorização de foco/exposição e o retorno ao foco automático após alguns segundos.
19. Capturar uma nova foto e um novo vídeo com localização. Em cada mídia, abrir o painel de informações, comparar UTM/GEO/data/endereço/nome com o overlay, copiar os metadados e confirmar o conteúdo da área de transferência.
20. Em cada mídia nova, tocar em “Gerar rota no Google Maps” e confirmar que o destino usa as coordenadas daquela captura. Em uma mídia antiga sem ficha de overlay, confirmar ações desabilitadas e mensagem explicativa.
21. Limpar os dados do app, abrir o hub e confirmar que nenhuma permissão é solicitada. Entrar no sFoto: câmera deve ser solicitada primeiro e localização apenas no contexto da câmera. Entrar no modo vídeo e iniciar: áudio deve ser solicitado nesse momento.
22. Negar áudio e confirmar vídeo gravado sem som; negar câmera e confirmar tela explicativa com ação para solicitar novamente.
23. Iniciar vídeo antes de a câmera ficar pronta, iniciar/parar rapidamente e sair do sFoto durante uma gravação; confirmar que o indicador nunca permanece preso e que a lanterna desliga.
24. Capturar repetidamente fotos na maior resolução disponível e observar `adb logcat`/memória; confirmar overlay gravado, ausência de `OutOfMemoryError` e ausência de segunda recompressão JPEG pelo app.
25. Simular falha de câmera (revogar permissão ou ocupar a câmera com outro app), confirmar mensagem visível e usar “Tentar novamente” depois de liberar o recurso.
26. Abrir os alertas de metadados e exclusão. Confirmar ícones semânticos no cabeçalho e nas ações de copiar, gerar rota, fechar, apagar e cancelar, com alinhamento correto, contraste legível e texto sempre presente ao lado do ícone.
27. Com logo selecionado, comparar foto, vídeo traseiro e vídeo frontal: o logo deve permanecer menor, nítido e inteiro; texto e logo não podem ser ampliados, borrados ou cortados pelo recorte 16:9.
28. Em foto e vídeo, verificar que Flash e Configurações aparecem e respondem no topo. Confirmar que a prévia e o overlay terminam antes do rodapé preto e não ficam sob os botões inferiores, inclusive com as barras do sistema visíveis.
27. Abrir o sFoto em vídeo, alternar para foto e repetir a troca várias vezes. Confirmar que os dois modos vinculam a câmera sem mensagem `Effects target`, que a prévia de foto mostra apenas um overlay e que a captura salva o mesmo overlay uma única vez.
28. Na câmera frontal, confirmar que texto e logo da prévia são legíveis e não espelhados. Capturar e conferir que o arquivo salvo mantém texto/logo corretos e sem duplicação.
29. Comparar prévia e foto salva nas posições esquerda e direita: texto e logo devem permanecer afastados da borda, estáveis e coerentes entre visualização e arquivo.
30. Tocar brevemente no disparador e confirmar uma foto. Depois segurar por pelo menos dois segundos nas câmeras traseira e frontal, confirmar retorno háptico, múltiplas fotos com nomes distintos e interrupção de novos disparos ao soltar.
31. Abrir metadados, copiar e confirmar feedback com ícone. Forçar atualização da localização e confirmar o pop-up “Atualizando localização...” com ícone.
32. Em foto traseira e frontal, observar a prévia durante toque curto e disparo contínuo. Confirmar que texto e logo aparecem uma única vez durante todo o ciclo, sem duplicar no início nem piscar em dobro antes de desaparecer.
33. Depois dos disparos do item anterior, abrir cada foto na galeria e confirmar que texto e logo foram gravados no arquivo, mantendo posição, conteúdo e nitidez esperados.
34. Durante toque curto e disparo contínuo, confirmar que a prévia continua viva, sem congelamento, tela cinza/preta, deslocamento ou duplicação de texto e logo.
35. Usar texto físico ou outra referência assimétrica e validar a matriz padrão de campo nas duas orientações do aparelho: frontal e traseira devem mostrar e salvar foto/vídeo sem espelho.
36. Em frontal e traseira, foto e vídeo, confirmar que texto e logo permanecem legíveis, não espelhados, na mesma posição relativa e com margem lateral consistente na prévia e na galeria.
37. Alternar repetidamente frontal/traseira e foto/vídeo. Confirmar ausência de `Effects target`, preview vazio, overlay duplicado, piscada, tela cinza ou mudança de convenção entre os modos.
38. Fazer toque curto e disparo contínuo em ambas as lentes. Confirmar que a prévia permanece fluida e que cada JPEG contém exatamente um overlay normal.
39. Abrir foto nas duas lentes após instalação limpa e após alternar foto/vídeo. Confirmar que não aparece erro `Effects target` nem `IllegalArgumentException`; foto deve usar um único vínculo com `Preview`, `ImageCapture` e o efeito suportado.
40. Em ambas as lentes e em retrato/paisagem, usar uma referência assimétrica. Confirmar que a prévia não sofre inversão visual, que foto e vídeo salvos têm a mesma orientação da prévia e que o texto/logo do overlay não ficam espelhados. Alternar foto/vídeo durante o teste e confirmar que o overlay aparece exatamente uma vez.
41. Na lente frontal, conferir uma referência assimétrica na prévia, em uma foto salva e em um vídeo salvo. Os três devem ficar sem espelho, com texto/logo legíveis. Repetir no modo frontal após alternar para a traseira e voltar.
42. Na lente frontal, alternar foto/vídeo e comparar a prévia antes e depois da primeira imagem. Confirmar que a inversão corretiva é visível imediatamente — imagem e overlay devem mudar juntos e não podem voltar a espelhar durante a captura.
43. Com o mesmo logo e a mesma posição selecionados, comparar foto e vídeo nas lentes frontal e traseira. Na prévia e nos arquivos da galeria, texto, ícones e logo devem manter a mesma escala, nitidez, margem e posição relativa; o logo não pode desaparecer durante a gravação ou a reprodução.
44. No modo vídeo em retrato, comparar a prévia e o arquivo final com uma foto feita na mesma posição. Texto, ícones e logo devem usar a mesma escala e a mesma distância relativa às bordas, sem regra exclusiva de viewport para vídeo.
45. Em vídeo frontal e traseiro, comparar a prévia, o primeiro frame da galeria e a reprodução: os três devem mostrar o quadro 16:9 inteiro na mesma escala, sem zoom/crop adicional. Logo, texto e ícones devem estar visíveis, nítidos e com a mesma posição relativa em todos os três.
46. Com os mesmos dados, posição e logo, comparar foto e vídeo em cada lente. Fonte, ícones, logo, margens e espaçamento devem ser indistinguíveis; o 16:9 pode alterar somente o enquadramento da cena, nunca a régua do overlay.
47. Em ambas as lentes, iniciar uma gravação e conferir nas propriedades do MP4 que a proporção é 4:3. Comparar com uma foto feita no mesmo ponto: prévia, arquivo e galeria devem usar o mesmo enquadramento e a mesma régua de overlay; nenhum modo pode reintroduzir 16:9.
48. Após alterações internas no pipeline da câmera, alternar repetidamente foto/vídeo e frontal/traseira, testar foco por toque, flash e lanterna, capturar uma foto e um vídeo e conferir o overlay na galeria. O comportamento deve permanecer idêntico ao aprovado antes do refactor, sem falha de vínculo ou perda de controles.
49. Com a Activity em retrato, deitar o telefone 90° para a esquerda e para a direita em foto e vídeo. A prévia e o overlay devem permanecer visíveis e estáveis, sem girar dentro do viewport. Capturar em cada lado e conferir na galeria que JPEG/MP4 ficam horizontais, com cena, texto, ícones e logo na orientação correta.

## Testes automatizados do sFoto

```bash
./gradlew :app:testDebugUnitTest
```

- `VideoRecordingUiStateTest`: início, confirmação, parada, finalização, falha e comandos duplicados.
- `PhotoMediaMetadataTest`: composição dos campos visíveis e fallback de coordenadas indisponíveis.
- `CameraPipelinePolicyTest`: frontal, traseira e toda mídia persistida permanecem sem espelho.
- `OverlayFrameTransformTest`: a geometria do overlay usa a rotação e o estado de espelhamento fornecidos pelo frame CameraX.
- `OverlayLayoutTest`: foto e vídeo compartilham as mesmas métricas visuais.
- `CameraOutputRotationTest`: converte os quadrantes do sensor para a rotação CameraX no sentido correto, incluindo os limites de cada faixa.

## Testes obrigatórios — sGIS

1. Importar GPKG pequeno de fixture.
2. Validar indexação.
3. Buscar `ptca0088`.
4. Buscar `rlca0010`.
5. Abrir detalhe.
6. Copiar metadados.
7. Gerar rota.
8. Reabrir app e confirmar que índice continua disponível.

## Critérios de aceite

- App não trava durante importação.
- UI permanece responsiva.
- Busca retorna em tempo aceitável.
- Nenhuma permissão é solicitada sem necessidade.
- Funciona sem internet para operações locais.
