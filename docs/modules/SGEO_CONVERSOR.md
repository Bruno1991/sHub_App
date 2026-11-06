# Documentação Técnica do Módulo — sGeoConversor

**Data:** 2026-11-06
**Módulos Gradle:** `:core:geo` (`com.saile.shub.core.geo`) e `:app` (`com.saile.shub.converter`)

---

## 1. Responsabilidade

O **sGeoConversor** realiza transformações geodésicas de alta precisão entre coordenadas geográficas decimais (**WGS84**), Graus-Minutos-Segundos (**DMS**) e coordenadas planas **UTM** (Universal Transverse Mercator), incluindo conversão direta entre zonas UTM adjacentes (como a transição entre as zonas **20S** e **21S** no meridiano `-60°W` em Rondônia).

---

## 2. Componentes Principais

| Arquivo | Pacote | Responsabilidade |
|---|---|---|
| `Coordinates.kt` | `com.saile.shub.core.geo` | Modelos imutáveis `GeoCoordinate`, `UtmCoordinate` e enum `Hemisphere` (`NORTH`, `SOUTH`). |
| `UtmConverter.kt` | `com.saile.shub.core.geo` | Motor matemático Snyder/Redfearn para o elipsoide WGS84 (`a = 6378137.0 m`, `e² = 0.00669438`, `k₀ = 0.9996`), conversão entre zonas (`convertUtmZone`), cálculo de banda latitudinal MGRS (`getUtmBand`) e formatação DMS (`formatDms`). |
| `ConverterViewModel.kt` | `com.saile.shub.converter` | Gerenciamento reativo de estado (`ConverterUiState`), validação de entradas (suporte a ponto ou vírgula decimal) e controle de zona de destino opcional (`targetZone`). |
| `ConverterScreen.kt` | `com.saile.shub.converter` | Interface Jetpack Compose Material 3 com alternância `GEO para UTM` / `UTM para GEO`, exibição simultânea de Decimal, DMS e UTM, cópia para área de transferência e abertura de rota externa. |

---

## 3. Validações e Regras de Negócio

- **Limites Latitudinais UTM:** `-80.0°` a `+84.0°` (rejeita valores fora do domínio da projeção Transverse Mercator e `NaN`).
- **Limites Longitudinais:** `-180.0°` a `+180.0°`.
- **Zonas UTM:** `1` a `60` (validadas tanto em `utmToGeo` quanto no parâmetro `forcedZone` de `geoToUtm` e `convertUtmZone`).
- **Easting / Northing:** `0` a `1.000.000 m` (Easting) e `0` a `10.000.000 m` (Northing).

