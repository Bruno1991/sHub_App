# Configuração do Ambiente de Desenvolvimento — sHub

**Data:** 2026-11-06

---

## 1. Pré-Requisitos de Software

- **Sistema Operacional:** Windows 10/11 (64-bit), Linux ou macOS.
- **JDK:** Java Development Kit **17** (`jvmToolchain(17)` configurado em todos os módulos).
- **Android SDK:**
  - `compileSdk = 35` (Android 15)
  - `targetSdk = 35`
  - `minSdk = 26` (Android 8.0+)
  - Build Tools `35.0.0`
- **Python 3.10+ (para ferramentas espaciais em `tools/`):**
  - Pacotes: `geopandas`, `shapely>=2.0`, `pyproj`, `fiona`, `pillow`.

---

## 2. Configuração Inicial

1. **Variáveis de Ambiente (`.env`):**
   - Copie `.env.example` para `C:\dev\.env` (fora do controle de versão Git) e preencha os caminhos locais do SDK/JDK e credenciais autorizadas.
2. **SDK Location (`local.properties`):**
   - Certifique-se de que `C:\dev\s_Hub_App\local.properties` aponta para o seu Android SDK:
     ```properties
     sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
     ```
3. **Ambiente Python para Ferramentas GIS (`tools/`):**
   ```powershell
   pip install geopandas shapely pyproj pillow
   ```

