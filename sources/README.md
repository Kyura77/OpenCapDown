# Módulo `sources`

Assets JavaScript para fontes de mangá.

## Como adicionar
1. Crie `src/main/assets/sources/{id}.js`.
2. Implemente `export default { search, getMangaDetail, getChapterPages }`.
3. O `core/` carrega automaticamente pelo nome do arquivo.
