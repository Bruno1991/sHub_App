# Matriz de Regressão — sHub

**Data:** 2026-11-06

---

## Matriz de Cobertura Funcional e Regressão

| ID | Módulo | Funcionalidade / Caso de Uso | Suíte de Teste Automatizado | Status |
|---|---|---|---|---|
| **REG-01** | `sGeoConversor` | Conversão WGS84 Decimal → UTM (Hemisférios Sul e Norte) | `UtmConverterTest`, `ConverterViewModelTest` | APROVADO |
| **REG-02** | `sGeoConversor` | Conversão UTM → WGS84 Decimal e DMS | `UtmConverterTest`, `ConverterViewModelTest` | APROVADO |
| **REG-03** | `sGeoConversor` | Re-projeção entre fusos UTM (Zona 20S ↔ Zona 21S em Rondônia) | `UtmConverterTest`, `ConverterViewModelTest` | APROVADO |
| **REG-04** | `sGeoConversor` | Rejeição de coordenadas inválidas (`NaN`, lat > 84°, zona > 60) | `UtmConverterTest`, `ConverterViewModelTest` | APROVADO |
| **REG-05** | `sFoto` | Cálculo de layout e quebra de linha do carimbo técnico | `OverlayLayoutTest`, `OverlayFrameTransformTest` | APROVADO |
| **REG-06** | `sFoto` | Memoização de data, localização UTM/GEO e azimute no `OverlayRenderer` | `OverlayRendererOptimizationTest` | APROVADO |
| **REG-07** | `sFoto` | Padronização de dimensões de logotipos SVG e rotação de saída | `LogoSizeStandardizerTest`, `CameraOutputRotationTest` | APROVADO |
| **REG-08** | `sFoto` | Máquina de estados de gravação de vídeo e política de pipeline CameraX | `VideoRecordingUiStateTest`, `CameraPipelinePolicyTest` | APROVADO |
| **REG-09** | `sGis` | Resolução em cascata de múltiplos arquivos MBTiles (`CompositeMbtilesArchive`) | `CompositeMbtilesTest` | APROVADO |
| **REG-10** | `sGis` | Limite estrito de *overzoom* (`maxDeltaZ = 2`) no `ControlledFallbackArchive` | `ControlledFallbackTest` | APROVADO |
| **REG-11** | `sGis` | Manifesto JSON, validação SHA-256 e instalação atômica via `.staging` | `PackageManifestTest` | APROVADO |
| **REG-12** | `sGis (Tools)` | Planejamento de cobertura HD em UTM 20S/21S e enumeração Slippy Map `STRtree` | `test_slippy_math.py`, `test_spatial_coverage_planner.py`, `test_mbtiles_pipeline.py` | APROVADO |
| **REG-13** | `Core / UI` | Registro modular `HubToolRegistry` e utilitários `CoreLocationUtils` | `CoreLocationUtilsTest` | APROVADO |

