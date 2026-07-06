# Módulo `core`

Engine backend do OpenCapDown.

## Regras
- Toda classe pública deve ser uma interface.
- Implementações ficam em pacotes `internal`.
- A única porta de entrada é `OpenCapDownCore`.
- Testes unitários usam JVM + Robolectric. Testes de integração usam `MockWebServer`.

## Pacotes
- `common` — utilitários e abstrações.
- `database` — Room entities, DAOs, migrations.
- `domain` — modelos puros (data classes).
- `sources` — runtime JS para sources.
- `downloads` — fila e cache de downloads.
- `telegram` — backup/restore via Bot API.
- `reader` — resolução de páginas e progresso.
