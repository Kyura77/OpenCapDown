# OpenCapDown

Aplicativo Android para leitura de mangá via web scraping com backup no Telegram.

## Estrutura
- `app/` — UI layer. Outra IA cuida. Consome `core/`.
- `core/` — Engine backend: scraping, downloads, Telegram, leitor, banco.
- `core-test-fixtures/` — Fakes e stubs compartilhados entre testes.
- `sources/` — Sources JavaScript carregadas em runtime pelo `core/`.

## Como adicionar uma source
1. Crie `sources/src/main/assets/sources/{id}.js` seguindo `_template.js`.
2. Implemente `search`, `getMangaDetail`, `getChapterPages`.
3. Rode `./gradlew :core:testDebugUnitTest`.

## Contrato público
A UI NÃO acessa módulos internos. Use apenas `com.opencapdown.core.OpenCapDownCore`.
A interface é versionada semanticamente (`OpenCapDownCore.v1()`).
