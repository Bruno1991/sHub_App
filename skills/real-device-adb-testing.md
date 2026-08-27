# Skill — Real Device ADB Testing

## Objetivo

Validar o app em telefone real conectado por USB.

## Rotina

```bash
adb devices
./gradlew test
./gradlew connectedAndroidTest
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.saile.shub 1
```

## Evidências

- Salvar prints em `screenshots/`.
- Salvar logs relevantes.
- Atualizar `STATUS.md` com resultado.

## Regra

Se a tela mudar, capturar screenshot e registrar no status.
