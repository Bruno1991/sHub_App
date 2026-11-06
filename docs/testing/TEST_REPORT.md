# Relatório de Execução de Testes — sHub

**Data:** 2026-11-06

---

## 1. Resumo das Suítes Executadas

### 1.1 Testes Unitários e de Integração JVM/Android (`./gradlew test`)

| Módulo / Suíte | Quantidade de Testes | Falhas | Status |
|---|---|---|---|
| `:core:geo` (`UtmConverterTest`) | 9 | 0 | **100% PASS** |
| `:app` (`ConverterViewModelTest`) | 4 | 0 | **100% PASS** |
| `:app` (`CoreLocationUtilsTest`) | 2 | 0 | **100% PASS** |
| `:app` (`CompositeMbtilesTest`) | 2 | 0 | **100% PASS** |
| `:app` (`ControlledFallbackTest`) | 2 | 0 | **100% PASS** |
| `:app` (`PackageManifestTest`) | 3 | 0 | **100% PASS** |
| `:app` (`OverlayLayoutTest`) | 4 | 0 | **100% PASS** |
| `:app` (`OverlayRendererOptimizationTest`) | 5 | 0 | **100% PASS** |
| `:app` (`OverlayFrameTransformTest`) | 5 | 0 | **100% PASS** |
| `:app` (`CameraOutputRotationTest`) | 5 | 0 | **100% PASS** |
| `:app` (`CameraPipelinePolicyTest`) | 4 | 0 | **100% PASS** |
| `:app` (`LogoSizeStandardizerTest`) | 4 | 0 | **100% PASS** |
| `:app` (`VideoRecordingUiStateTest`) | 6 | 0 | **100% PASS** |
| **Total JVM / Gradle (por variante)** | **55 testes unitários** | **0** | **APROVADO** |

### 1.2 Testes Automatizados Python (`tools/test_*.py`)

| Suíte Python | Escopo | Quantidade de Testes | Status |
|---|---|---|---|
| `tools/test_slippy_math.py` | Conversão WGS84/Web Mercator, clamping ±85.05112878° e `STRtree` `geometry_to_tiles` | 6 | **100% PASS** |
| `tools/test_spatial_coverage_planner.py` | Reprojeção métrica UTM 20S/21S em Rondônia e geração de cobertura GeoPackage | 4 | **100% PASS** |
| `tools/test_mbtiles_pipeline.py` | Geração MBTiles SQLite, validação de integridade e manifesto SHA-256 | 4 | **100% PASS** |
| **Total Python GIS Tools** | | **14 testes unitários** | **APROVADO** |

