# sGeoConversor - Módulo de Conversão Geográfica (`app/converter`)

Este módulo abriga a interface de usuário (UI) e o controle de estado (ViewModel) para a ferramenta de conversão de coordenadas (UTM ↔ Lat/Lon).

## Estrutura
- **`ui/ConverterScreen.kt`**: Tela principal e componentes (Inputs de GEO, Inputs de UTM e Card de Resultado) escritos em Jetpack Compose. Responsável apenas por desenhar a tela e coletar inputs.
- **`ConverterViewModel.kt`**: Controla as regras de apresentação, validando a entrada do usuário e acionando o módulo `:core:geo` para realizar os cálculos matemáticos pesados.

## Princípios
- A lógica puramente matemática **não** vive aqui. Este módulo apenas coleta texto (Strings) da UI e formata os dados processados pelo `core:geo`.
- Arquitetura limpa: UI desacoplada em seu próprio pacote (`ui/`) para fácil manutenção.
