# Registro de Dívida Técnica (Resolvida e Residual) — sHub

**Data:** 2026-11-06

---

## 1. Dívidas Técnicas Resolvidas nesta Missão

| ID | Categoria | Descrição Original | Resolução Implementada |
|---|---|---|---|
| **TD-01** | Arquitetura / Acoplamento | `GisLocationTracker` (`sGis`) importava `com.saile.shub.photo.location.LocationUtils` (`sFoto`). | Extraído `CoreLocationUtils` e `CoreNetworkUtils` para `com.saile.shub.core.location`. Zero acoplamento entre features. |
| **TD-02** | Build / Persistência | `NormativeDatabase` em `:app` não tinha KSP habilitado nem `room-ktx`/`room-compiler`, o que causaria falha de instanciação em runtime. | Habilitado plugin `ksp` e `ksp(libs.androidx.room.compiler)` + `implementation(libs.androidx.room.ktx)` em `app/build.gradle.kts`. |
| **TD-03** | Governança de Build | Versões de bibliotecas espalhadas em strings literais nos arquivos `build.gradle.kts`. | Criado `gradle/libs.versions.toml` e migrados todos os módulos (`:app`, `:core:gis`, `:core:geo`). |
| **TD-04** | Desempenho / UI | `PhotoScreen` recompunha até 15 vezes/segundo por causa de `currentTime` e `currentAzimuthDegrees` em `mutableStateOf`. | Isolado azimute em `LiveOverlaySensorState` (`@Volatile`) e removida corrotina de relógio de 1 Hz da árvore Compose. |
| **TD-05** | Desempenho / Main Thread | `PhotoViewModel` lia preferências SQLite na Main Thread; `PhotoScreen` consultava `MediaStore` na Main Thread ao fechar a galeria; `HomeScreen` re-renderizava SVGs a cada navegação. | Migrado carregamento SQLite e `MediaStore` para `Dispatchers.IO`; adicionado `svgBitmapCache` (`LruCache`) em `HomeScreen`. |
| **TD-06** | Vazamento de Recursos | `MediaActionSound` não liberado em `PhotoCapture`; `ImageLoader` não encerrado em `PhotoScreen`; `PhotoPreferencesDbHelper` não fechado em `PhotoViewModel`. | `MediaActionSound` reutilizado e liberado em `close()`; `imageLoader.shutdown()` em `DisposableEffect`; `dbHelper.close()` em `onCleared()`. |
| **TD-07** | Domínio Geodésico | `UtmConverter` não validava `forcedZone` e não expunha conversão direta entre zonas UTM (`20S <-> 21S`) nem formatação DMS; `OverlayRenderer` duplicava cálculo de banda UTM. | Adicionado `convertUtmZone`, validação de `forcedZone in 1..60`, `formatDms` e deduplicado cálculo de banda em `OverlayRenderer`. |

---

## 2. Dívidas Técnicas Residuais e Recomendações de Evolução Futura

1. **Extração Física de `:feature:photo`, `:feature:gis` e `:feature:converter` (Baixa Prioridade):**
   - Atualmente, `:core:geo` e `:core:gis` já são módulos Gradle independentes e os pacotes `converter`, `photo`, `gis` e `normative` dentro de `:app` estão completamente desacoplados no nível de código. Quando o número de desenvolvedores simultâneos crescer, cada pacote poderá ser movido para seu próprio módulo `:feature:*` para paralelizar ainda mais o cache de compilação Gradle.
2. **UI dedicada para o Motor Normativo (`com.saile.shub.normative`):**
   - O motor de consulta FTS4 (`NormativeDatabase`, `RuleEngine`, `CalculationEngine`) está pronto e compilado via Room KSP. Recomenda-se expô-lo como a futura ferramenta **`sEletrica`** no `HubToolRegistry`.

