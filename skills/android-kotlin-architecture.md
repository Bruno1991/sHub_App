# Skill — Android Kotlin Architecture

## Objetivo

Construir o sHub com Kotlin, Material 3, Compose, arquitetura modular, testes e manutenção fácil.

## Regras

- Separar UI, domínio e dados.
- Usar ViewModel + StateFlow.
- Use cases devem ser puros quando possível.
- Repositories encapsulam fontes de dados.
- Não colocar SQL, CameraX ou Location direto em Composables.
- Feature flags simples podem proteger funcionalidades incompletas.

## Checklist do agente

1. Ler `AGENTS.md` e `STATUS.md`.
2. Criar ou atualizar módulo sem quebrar os demais.
3. Escrever teste antes ou junto da feature.
4. Rodar testes.
5. Atualizar `STATUS.md`.
