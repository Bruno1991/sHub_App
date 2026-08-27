# Estratégia SQLite + GPKG

## Objetivo

Carregar arquivos `.gpkg` com centenas de milhares de elementos e permitir busca rápida por identificadores, metadados e localização.

## Regras

- Nunca importar tudo para memória.
- Usar transações em lote.
- Criar índice normalizado de busca.
- Separar metadados brutos de campos indexáveis.
- Manter referência ao arquivo original.

## Modelo inicial

```sql
CREATE TABLE imported_packages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  display_name TEXT NOT NULL,
  file_uri TEXT NOT NULL,
  imported_at TEXT NOT NULL,
  feature_count INTEGER DEFAULT 0,
  status TEXT NOT NULL
);

CREATE TABLE gis_features (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  package_id INTEGER NOT NULL,
  source_layer TEXT NOT NULL,
  feature_source_id TEXT,
  code_raw TEXT,
  code_norm TEXT,
  geometry_type TEXT,
  centroid_lat REAL,
  centroid_lon REAL,
  min_lat REAL,
  min_lon REAL,
  max_lat REAL,
  max_lon REAL,
  metadata_json TEXT NOT NULL,
  FOREIGN KEY(package_id) REFERENCES imported_packages(id)
);

CREATE INDEX idx_gis_features_code_norm ON gis_features(code_norm);
CREATE INDEX idx_gis_features_layer ON gis_features(source_layer);
CREATE INDEX idx_gis_features_bbox ON gis_features(min_lat, min_lon, max_lat, max_lon);
```

## Normalização de busca

Entradas como `PTCA0088`, `ptca0088`, `ptca-0088` e `ptca 0088` devem convergir para chave normalizada.

Regra inicial:

```text
lowercase → remover acentos → remover espaços/pontuação irrelevante → manter letras e números
```

## Busca

A busca deve usar prioridade:

1. match exato `code_norm`;
2. prefixo `code_norm LIKE 'termo%'`;
3. metadados importantes;
4. busca textual parcial paginada.

## Cópia de metadados

Ao clicar em elemento, a opção “copiar metadados” deve gerar texto legível:

```text
Elemento: PTCA0088
Camada: postes
Coordenadas: -11.438000, -61.447000
UTM: Zona 20L X: ... Y: ...
Metadados:
- campo_1: valor
- campo_2: valor
```
