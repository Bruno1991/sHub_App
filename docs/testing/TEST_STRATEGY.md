# Estratégia de Testes — sHub

**Data:** 2026-11-06

---

## 1. Pirâmide de Testes do Ecossistema sHub

1. **Testes Unitários JVM Puros (`:core:geo`):**
   - Verificam a exatidão matemática das transformações WGS84 ↔ UTM (`UtmConverterTest`), conversão entre fusos (`convertUtmZone` 20S ↔ 21S), bandas latitudinais MGRS, formatação DMS e rejeição de entradas inválidas (`NaN`, zonas fora de `1..60`, latitudes fora de `-80..84`).
2. **Testes Unitários e de Integração de Componentes Android (`:app` & `:core:gis`):**
   - **sGeoConversor (`ConverterViewModelTest`):** Fluxos completos `GEO_TO_UTM`, `UTM_TO_GEO`, zona de destino forçada, vírgula decimal e tratamento de erros.
   - **sFoto (`OverlayLayoutTest`, `OverlayRendererOptimizationTest`, `CameraPipelinePolicyTest`, `CameraOutputRotationTest`, `LogoSizeStandardizerTest`, `VideoRecordingUiStateTest`):** Garantem o cálculo proporcional de fontes/logos, memoização de texto/layout, rotação EXIF e máquina de estados de gravação de vídeo.
   - **sGis (`CompositeMbtilesTest`, `ControlledFallbackTest`, `PackageManifestTest`):** Validam a prioridade de busca em cascata (`HD -> Regional -> Overview`), limites de *overzoom* (`maxDeltaZ = 2`), serialização JSON pura do manifesto, verificação SHA-256 e instalação atômica com rollback em caso de arquivo corrompido.
   - **Core & Plataforma (`CoreLocationUtilsTest`):** Mapeamento de siglas de estados brasileiros e integridade do `HubToolRegistry`.
3. **Testes Automatizados Python (`tools/test_*.py`):**
   - `test_slippy_math.py`, `test_spatial_coverage_planner.py`, `test_mbtiles_pipeline.py`: Validam a matemática Web Mercator, o cálculo de buffers métricos em UTM 20S/21S sobre GeoPackages sintéticos e a criação/validação de bancos MBTiles SQLite.

---

## 2. Comandos de Execução

```powershell
# Executar todos os testes unitários Gradle (:core:geo, :core:gis, :app)
.\gradlew.bat testDebugUnitTest

# Executar a suíte de testes Python das ferramentas GIS
python -m unittest discover -s tools -p "test_*.py"
```

