# Skill — GPKG SQLite Search

## Objetivo

Carregar GPKG grande, indexar elementos e buscar rápido.

## Regras

- Processar em background.
- Usar transações em lote.
- Normalizar códigos.
- Criar índices SQLite.
- Não carregar tudo na memória.
- Mostrar progresso e permitir cancelamento.

## Busca

Entrada do usuário deve ser normalizada e comparada contra `code_norm`.

Prioridade:

1. exato;
2. prefixo;
3. parcial;
4. metadados.
