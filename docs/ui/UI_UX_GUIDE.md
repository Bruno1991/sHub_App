# UI/UX Guide — sHub

## Identidade visual

- Tema escuro por padrão.
- Visual técnico, moderno, com alto contraste.
- Cards grandes e legíveis.
- Tipografia clara para campo.
- Ícones consistentes em 1:1.

## Tela inicial sHub (Home Hub)

Cards principais com visual Material 3, degradês semânticos e ícones vetorizados oficiais (`artwork/app_hub`):

1. **sGeoConversor** (`artwork/app_hub/sgeoconversor.svg`): Conversão rápida UTM ⇄ GEO, cópia e rotas.
2. **sFoto** (`artwork/app_hub/sfoto.svg`): Câmera com carimbo/timestamp de campo e logo corporativa.
3. **sGIS** (`artwork/app_hub/sgis.svg`): Visualização de mapas e busca offline de GeoPackages (GPKG).

Os ícones são renderizados em tempo de execução via AndroidSVG em alta resolução (512x512) dentro de contêineres destacados de 92dp. Indicador visual inferior confirma estado de operação 100% offline.

## sGeoConversor

Componentes mínimos:

- alternância de modo: UTM → GEO e GEO → UTM;
- campos de entrada;
- card de resultado;
- botões: Copiar UTM, Copiar GEO, Copiar Link, Abrir Rota;
- link clicável para Google Maps.

## sFoto

Overlay deve seguir a estrutura do arquivo de configuração enviado:

```text
UTM:
GEO:
Data e Hora: xx/xx/xxxx 00:00:00
Endereço: Rua x Cacoal - RO
cor da fonte
```

Cores de overlay:

- amarelo;
- verde;
- ciano;
- magenta;
- branco/padrão.

### Galeria interna do sFoto

- Grade vertical de três colunas, com rolagem contínua e miniaturas recortadas em proporção 1:1.
- Em listas longas, exibir à direita um trilho no padrão Google Fotos: faixa de toque larga, puxador circular com duas setas indicando cima/baixo, resposta direta ao arraste e balão flutuante de mês/ano. O trilho não pode reduzir a fluidez da grade.
- Toque longo inicia seleção múltipla; a barra superior passa a oferecer caixa de seleção de todas as mídias, além de compartilhar e excluir os itens marcados.
- Pager horizontal deve permitir navegar livremente entre fotos e vídeos nos dois sentidos.
- Vídeo deve permanecer parado ao entrar na página e só iniciar após toque no botão de reprodução.
- Vídeo não entra em loop; pausa preserva o frame corrente e retomada continua da mesma posição.
- A passagem da prévia para o primeiro frame do vídeo deve ser suave e não pode exibir shutter preto ou conteúdo residual de outra mídia.
- Metadados, compartilhamento e exclusão permanecem acessíveis no topo.
- O painel de informações mostra também UTM, GEO, data/hora, endereço e nome conforme gravados no overlay, com ações “Copiar metadados” e “Gerar rota no Google Maps”.
- Alertas da galeria usam ícones semânticos no cabeçalho e nas ações: informação para metadados, aviso/exclusão para apagar, copiar, navegação e fechamento. Texto e ícone permanecem juntos para não depender apenas da simbologia.

### Flash no modo vídeo

- O ícone do raio define a intenção do usuário enquanto a tela de captura está visível.
- Com raio `ON`, modo vídeo e câmera traseira, a lanterna permanece ligada durante a captura.
- Abrir galeria ou configurações e sair para o hub devem desligar fisicamente a lanterna sem apagar a preferência do usuário.
- Ao retornar à tela de captura, restaurar a lanterna se o raio ainda estiver em `ON`.

### Qualidade de captura

- Foto deve usar a maior resolução disponível para o conjunto de casos de uso ativo, priorizando qualidade sobre latência.
- Vídeo com overlay gravado deve usar a maior qualidade 4:3 suportada pelo dispositivo; não reduzir a resolução para acomodar o overlay nem recortar para 16:9.
- A câmera mantém foco automático contínuo. Um toque curto no preview prioriza foco e exposição naquele ponto, mostra uma marca breve de confirmação e depois retorna ao foco contínuo.
- Durante início/finalização de vídeo e processamento de foto, controles conflitantes permanecem bloqueados. O cronômetro só começa após confirmação real da gravação.
- Se a câmera não puder ser vinculada, mostrar a causa e o botão “Tentar novamente”; nunca deixar apenas um preview vazio.
- Permissões aparecem dentro do sFoto. Áudio só é solicitado ao iniciar vídeo e sua recusa permite gravação sem som.
- Foto e vídeo seguem uma única convenção de campo: frontal e traseira mostram e salvam a orientação real da cena, sem espelho. Quando o fornecedor espelha a superfície frontal, o `PreviewView` em modo compatível (`TextureView`) recebe uma inversão corretiva exclusiva da prévia; texto e logo acompanham a imagem e também ficam legíveis. O JPEG/MP4 não passam por essa correção visual e continuam sem espelho.
- Vídeo usa o mesmo frame 4:3, a mesma resolução de referência e o mesmo renderer do modo foto. Logo, fonte, ícones, margem e espaçamento devem ser indistinguíveis entre os dois modos; SVG deve permanecer nítido tanto na prévia quanto no vídeo reproduzido na galeria.
- A prévia de vídeo usa `FILL_CENTER`, como a foto. A galeria usa a mesma regra `FIT` já usada para fotos e vídeos. Não há recorte ou formato 16:9 imposto pelo aplicativo. Topo e rodapé são opacos, respeitam as áreas seguras do sistema e nunca podem ser cobertos pela imagem ou pelo overlay.
- Ao deitar o aparelho, somente os ícones de controle acompanham a orientação física. A prévia e seu overlay não giram dentro da Activity bloqueada em retrato; a orientação horizontal é aplicada ao JPEG/MP4 salvo e deve aparecer correta na galeria.
- Toque curto captura uma foto. Pressionar e segurar ativa disparo contínuo após retorno háptico; soltar interrompe novos disparos, tanto na câmera traseira quanto na frontal.
- Feedbacks transitórios de “Metadados copiados” e “Atualizando localização” exibem ícone semântico junto ao texto.

## sGIS

Componentes mínimos:

- botão importar GPKG;
- progresso de importação/indexação;
- barra de busca persistente;
- lista de resultados;
- visualização de mapa/canvas;
- painel de detalhe do elemento;
- ações: copiar metadados, copiar coordenadas, gerar rota.

## Acessibilidade de campo

- Alvos de toque grandes.
- Contraste alto.
- Texto mínimo de 14sp, preferencial 16sp+.
- Evitar depender apenas de cor para status.
- Feedback visual e háptico para copiar e salvar.
