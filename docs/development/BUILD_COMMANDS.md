# Comandos de Build, Testes e Empacotamento — sHub

**Data:** 2026-11-06

---

## 1. Compilação Android (Gradle Wrapper)

Execute a partir de `C:\dev\s_Hub_App`:

```powershell
# Compilar APK Debug (:app:assembleDebug)
.\gradlew.bat assembleDebug

# Compilar APK Release (:app:assembleRelease)
.\gradlew.bat assembleRelease

# Instalar APK Debug no dispositivo conectado via ADB
.\gradlew.bat installDebug
```

---

## 2. Qualidade de Código e Testes Automatizados

```powershell
# Executar todos os testes unitários JVM (:core:geo, :core:gis, :app)
.\gradlew.bat testDebugUnitTest

# Executar análise estática Android Lint
.\gradlew.bat lintDebug

# Executar suíte de testes Python do pipeline espacial (tools/)
python -m unittest discover -s tools -p "test_*.py"
```

---

## 3. Ferramentas Python de Planejamento GIS (`tools/`)

```powershell
# 1. Planejar cobertura HD (buffers métricos em UTM 20S/21S) a partir de um GeoPackage
python tools/spatial_coverage_planner.py `
  --gpkg caminho/para/rede_eletrica.gpkg `
  --out-gpkg build/hd_coverage.gpkg

# 2. Auditar e validar um arquivo MBTiles existente
python tools/mbtiles_pipeline.py validate --mbtiles caminho/para/basemap.mbtiles

# 3. Gerar manifesto de pacote offline com verificação SHA-256
python tools/mbtiles_pipeline.py manifest `
  --package-id "ro-corridor-2026" `
  --version 1 `
  --files caminho/para/rede_eletrica.gpkg caminho/para/basemap.mbtiles `
  --out manifest.json
```

