# Skill — Geodesy UTM/GEO

## Objetivo

Implementar conversão confiável UTM ⇄ geográficas.

## Regras

- Validar zona UTM, hemisfério e ranges.
- Separar cálculo de formatação.
- Testar com pontos conhecidos.
- Gerar link Google Maps no formato `https://www.google.com/maps/search/?api=1&query=lat,lon` ou alternativa configurada.
- Permitir copiar UTM, GEO e link separadamente.

## Casos de teste

- GEO → UTM → GEO deve preservar precisão aceitável.
- Coordenadas inválidas devem retornar erro claro.
- Hemisfério sul deve ser tratado corretamente.
