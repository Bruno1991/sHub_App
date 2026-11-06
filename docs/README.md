# Índice da Documentação Técnica — sHub

Bem-vindo à documentação oficial de engenharia e arquitetura do **sHub**.

---

## 1. Auditoria Técnica (`docs/audit/`)
- [Auditoria Inicial (FASE 0)](./audit/INITIAL_AUDIT.md) — Diagnóstico completo do repositório, fontes de referência (`s_SER_Tools`) e problemas comprovados.
- [Auditoria Final (Pós-Refatoração)](./audit/FINAL_AUDIT.md) — Comparativo técnico antes vs. depois.
- [Registro de Dívida Técnica](./audit/TECHNICAL_DEBT.md) — Dívidas técnicas eliminadas e roteiro de evolução.

## 2. Arquitetura (`docs/architecture/`)
- [Visão Geral da Arquitetura](./architecture/ARCHITECTURE.md) — Diagrama de camadas, princípios *offline-first* e concorrência.
- [Dependências entre Módulos](./architecture/MODULE_DEPENDENCIES.md) — Grafo de módulos Gradle (`:core:geo`, `:core:gis`, `:app`) e regras de isolamento.
- [Decisões Arquiteturais (ADRs)](./architecture/ARCHITECTURE_DECISIONS.md) — Justificativas para Snyder UTM, CameraX `OverlayEffect`, `osmdroid` + R-Tree e MBTiles multinível.
- [Fluxo de Dados](./architecture/DATA_FLOW.md) — Diagramas de sequência para `sGeoConversor`, `sFoto` e `sGis`.

## 3. Módulos Funcionais (`docs/modules/`)
- [sGeoConversor](./modules/SGEO_CONVERSOR.md) — Conversão WGS84 ↔ UTM, re-projeção entre fusos (20S/21S) e DMS.
- [sFoto](./modules/SFOTO.md) — Câmera técnica com carimbo georreferenciado em tempo real, EXIF e auditoria SQLite.
- [sGis](./modules/SGIS.md) — Motor GIS 100% offline com GeoPackage R-Tree, MBTiles multinível e ferramentas Python espaciais.

## 4. Desempenho e Dependências (`docs/performance/` & `docs/dependencies/`)
- [Linha de Base de Desempenho (Baseline)](./performance/BASELINE.md)
- [Relatório de Otimizações Implementadas](./performance/OPTIMIZATION_REPORT.md)
- [Auditoria de Dependências e Licenças](./dependencies/DEPENDENCY_AUDIT.md)
- [Governança e Atualizações (`libs.versions.toml`)](./dependencies/DEPENDENCY_UPDATES.md)

## 5. Testes, Desenvolvimento e Migração (`docs/testing/`, `docs/development/`, `docs/migration/`)
- [Estratégia de Testes](./testing/TEST_STRATEGY.md)
- [Matriz de Regressão](./testing/REGRESSION_MATRIX.md)
- [Relatório de Execução de Testes](./testing/TEST_REPORT.md)
- [Configuração do Ambiente de Desenvolvimento](./development/DEVELOPMENT_SETUP.md)
- [Comandos de Build e Ferramentas](./development/BUILD_COMMANDS.md)
- [Guia de Contribuição](./development/CONTRIBUTING.md)
- [Guia de Migração e Expansão Modular](./migration/MIGRATION_GUIDE.md)

