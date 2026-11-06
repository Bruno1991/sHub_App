# Relatório de Governança e Atualização de Dependências — sHub

**Data:** 2026-11-06

---

## 1. Modernizações Realizadas

1. **Implantação do Gradle Version Catalog (`gradle/libs.versions.toml`):**
   - Anteriormente, as versões estavam duplicadas como strings literais em `build.gradle.kts`, `app/build.gradle.kts`, `core/gis/build.gradle.kts` e `core/geo/build.gradle.kts`.
   - Todas as bibliotecas e plugins foram consolidados em `gradle/libs.versions.toml` utilizando `alias(libs.plugins.*)` e `implementation(libs.*)`.

2. **Ativação do Processador KSP para Room em `:app`:**
   - O módulo `:app` definia `NormativeDatabase` (`@Database`) e `NormativeDao` (`@Dao` com funções `suspend`), mas possuía apenas `androidx.room:room-runtime:2.6.1` sem `room-ktx` e sem `ksp(libs.androidx.room.compiler)`.
   - Foram adicionados `alias(libs.plugins.ksp)`, `implementation(libs.androidx.room.ktx)` e `ksp(libs.androidx.room.compiler)` em `app/build.gradle.kts`, garantindo a geração em tempo de compilação de `NormativeDatabase_Impl` e `NormativeDao_Impl`.

3. **Alinhamento de Toolchain Kotlin JVM 17:**
   - Adicionado `kotlin { jvmToolchain(17) }` explicitamente em `core/gis/build.gradle.kts` para consistência total com `:app` e `:core:geo`.

---

## 2. Matriz de Estabilidade de Versões

| Componente | Versão Adotada | Justificativa Técnica |
|---|---|---|
| AGP | `8.7.3` | Suporte completo ao Android 15 (`compileSdk = 35`) e compatível com Gradle `8.9`. |
| Kotlin + KSP | `2.0.21` / `2.0.21-1.0.27` | Compilador K2 estável com geração rápida de código Room e Compose. |
| CameraX | `1.4.1` | Suporte nativo e estável à API `OverlayEffect` para estampagem simultânea em Preview, Foto e Vídeo. |
| Room | `2.6.1` | Suporte completo a KSP, Coroutines (`room-ktx`) e tabelas virtuais `@Fts4` (`NormativeFtsEntity`). |
| osmdroid | `6.1.18` | Estabilidade comprovada com `IArchiveFile` customizado (`CompositeMbtilesArchive` / `ControlledFallbackArchive`). |

