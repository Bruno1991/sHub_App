# Guia de Migração e Expansão Modular — sHub

**Data:** 2026-11-06

---

## 1. O Que Mudou na Rearquitetura

1. **Utilitários de Localização e Rede (`CoreLocationUtils` & `CoreNetworkUtils`):**
   - Anteriormente localizados apenas em `com.saile.shub.photo.location`, agora residem em `com.saile.shub.core.location`.
   - As classes originais em `com.saile.shub.photo.location` foram mantidas como fachadas delegadas para preservar 100% de compatibilidade retroativa com o código interno do `sFoto`.
2. **Registro Declarativo de Ferramentas (`HubToolRegistry`):**
   - `HomeScreen` não instancia mais cartões fixos hardcoded; ela consome `HubToolRegistry.tools`.
3. **Catálogo de Versões Gradle (`gradle/libs.versions.toml`):**
   - Todos os módulos (`:app`, `:core:gis`, `:core:geo`) utilizam `libs.*` e `libs.plugins.*`.
4. **Room KSP Habilitado em `:app`:**
   - `NormativeDatabase` agora possui geração automática de código via `ksp(libs.androidx.room.compiler)`.

---

## 2. Como Adicionar um Novo Módulo ao sHub (Ex.: `sEletrica` ou `sInspecao`)

1. **Criar o Pacote ou Módulo Gradle:**
   - Para lógica de domínio reutilizável, crie `:core:<nome>` e registre em `settings.gradle.kts`.
   - Para a tela Jetpack Compose, crie o pacote `com.saile.shub.<modulo>` em `:app`.
2. **Adicionar o Destino em `SHubApp.kt`:**
   - Inclua o enum correspondente em `Destination` e mapeie a rota no `when (destination)` de `SHubApp()`.
3. **Registrar no `HubToolRegistry.kt`:**
   - Adicione um `HubToolDescriptor` em `HubToolRegistry.tools` informando `id`, `destination`, `title`, `badge`, `description`, `assetPath` (ícone SVG em `artwork/app_hub/`) e `accentGradient`. O ícone será automaticamente cacheado pelo `svgBitmapCache` da `HomeScreen`.

