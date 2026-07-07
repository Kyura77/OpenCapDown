# Guia para IA — Módulo `core`

## O que este módulo faz
Engine backend. Não contém UI.

## Regras de ouro
1. UI acessa apenas `OpenCapDownCore`.
2. Nunca quebre a interface `OpenCapDownCore`. Se precisar mudar, crie `v2()` e mantenha v1.
3. Implementações ficam em pacotes `internal`.
4. Toda função pública precisa de teste.
5. Sources JS ficam em `sources/src/main/assets/sources/`.

## Onde colocar código novo
- Scraping → `sources/`
- Banco → `database/`
- Downloads → `downloads/`
- Telegram → `telegram/`
- Leitor → `reader/`
