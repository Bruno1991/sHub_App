# Skill — CameraX Timestamp Overlay

## Objetivo

Implementar `sFoto` com câmera, overlay e salvamento de imagem.

## Regras

- Usar CameraX para preview e captura.
- Overlay visível no preview deve bater com overlay salvo na foto.
- Cores configuráveis: amarelo, verde, ciano, magenta e branco/padrão.
- Texto mínimo: UTM, GEO, data/hora e endereço quando disponível.
- Permitir captura mesmo sem endereço textual.
- Preservar EXIF útil quando possível.

## Testes

- Validar permissão de câmera.
- Validar captura com cada cor.
- Validar foto salva na galeria ou diretório configurado.
- Validar offline.
