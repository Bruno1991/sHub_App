# AGENTS.md — sHub Field Tools

## Comando único do operador

O operador deve iniciar qualquer agente com apenas este comando:

```text
leia AGENTS.md
```

Depois disso, o agente deve ler este arquivo primeiro, carregar o `STATUS.md`, entender o estado atual e só então decidir quais documentos específicos abrir. O agente **não deve reler o projeto inteiro** a cada execução.

## Regra obrigatória de continuidade

O agente deve manter `STATUS.md` sempre atualizado. Ao iniciar, antes de editar qualquer arquivo, deve ler `STATUS.md`. Ao finalizar qualquer etapa, deve atualizar:

- data/hora da execução;
- objetivo executado;
- arquivos criados/modificados/removidos;
- testes executados e resultado;
- próximos passos concretos;
- bloqueios ou decisões pendentes.

Se `STATUS.md` estiver ausente, o agente deve criá-lo antes de trabalhar.

## Produto

sHub é um aplicativo Android local-first para trabalho de campo, escrito em Kotlin, com SQLite como banco local. Ele reúne três ferramentas:

1. `sGeoConversor`: conversão UTM ⇄ coordenadas geográficas, geração de link Google Maps, cópia de coordenadas e rota.
2. `sFoto`: câmera com overlay estilo timestamp contendo UTM, GEO, data/hora e endereço, com 5 variações de cor.
3. `sGIS`: ferramenta de mapa e busca offline para arquivos GPKG, com camada satélite quando disponível, elementos sobrepostos, busca rápida por códigos como `ptca0088` e `rlca0010`, cópia de metadados e rota via Google Maps.

## Leitura mínima por tipo de tarefa

- Arquitetura: `docs/architecture/ARCHITECTURE.md` e `docs/product/MASTER_SPEC.md`.
- UI: `docs/ui/UI_UX_GUIDE.md`.
- Banco/GPKG: `docs/data/GPKG_SQLITE_STRATEGY.md`.
- Offline e mapas: `docs/architecture/OFFLINE_FIRST_STRATEGY.md`.
- Testes em telefone real: `docs/testing/REAL_DEVICE_ADB_TEST_PLAN.md`.
- Skills do agente: pasta `skills/`.

## Regras de engenharia

- Não implementar código antes de confirmar arquitetura, escopo e testes mínimos.
- Não usar backend obrigatório. O app deve funcionar offline sempre que os dados já estiverem no dispositivo.
- Não depender de OpenStreetMap online como requisito para uso em campo.
- Não guardar chaves ou tokens no repositório.
- Não quebrar o fluxo já funcional para adicionar recurso novo.
- Todo recurso novo deve vir com teste unitário, teste instrumental ou roteiro ADB.
- Toda tela principal deve ser testável em telefone real.
- Toda alteração relevante deve ser registrada em `STATUS.md`.

## Arquitetura recomendada

Usar arquitetura modular local-first, não microsserviços de rede. Em Android, “microsserviço” deve ser interpretado como módulos isolados e serviços internos, não processos remotos obrigatórios.

Camadas:

```text
UI Compose / Material 3
↓
ViewModel / StateFlow
↓
Use Cases
↓
Domain Models
↓
Repositories
↓
Data Sources: SQLite, GPKG, CameraX, Location, Files, Map Tiles
```

## Definição de pronto

Uma tarefa só é considerada pronta quando:

- compila localmente;
- não quebra testes existentes;
- possui teste ou roteiro de validação;
- foi validada no telefone real quando envolver UI, câmera, localização, mapa ou permissões;
- `STATUS.md` foi atualizado.
