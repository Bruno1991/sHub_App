# Guia de Contribuição e Padrões de Engenharia — sHub

**Data:** 2026-11-06

---

## 1. Regras Arquiteturais Obrigatórias

1. **Isolamento entre Features:**
   - Código em `com.saile.shub.converter`, `com.saile.shub.photo`, `com.saile.shub.gis` e `com.saile.shub.normative` **nunca** deve importar classes diretamente de outra feature.
   - Qualquer utilitário compartilhado deve residir em `:core:geo`, `:core:gis` ou `com.saile.shub.core.*`.
2. **Proibição de I/O na Main Thread:**
   - Operações SQLite (`SQLiteOpenHelper`, Room, GeoPackage, MBTiles), leitura de `MediaStore` e decodificação pesada de imagens devem rodar em `Dispatchers.IO` ou executores de background.
3. **Gerenciamento de Dependências:**
   - Nunca declare versões hardcoded em `build.gradle.kts`. Adicione ou atualize bibliotecas exclusivamente em `gradle/libs.versions.toml`.
4. **Proteção de Segredos:**
   - Nunca grave chaves de API, tokens GitHub ou credenciais no código-fonte ou em arquivos versionados. Utilize `C:\dev\.env` e documente apenas chaves vazias em `.env.example`.
5. **Convenção de Commits:**
   - Utilize *Conventional Commits* (`feat:`, `fix:`, `refactor:`, `perf:`, `test:`, `docs:`).

