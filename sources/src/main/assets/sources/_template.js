/**
 * Template para criação de uma nova source.
 *
 * Cada source exporta três funções obrigatórias:
 *   search(query)         → Array<{ id, title, coverUrl }>
 *   getMangaDetail(id)    → { id, title, coverUrl, chapters: Array<{ id, number, title }> }
 *   getChapterPages(id)   → Array<{ url, index }>
 *
 * Regras:
 * - Retornos devem ser serializáveis em JSON.
 * - Evite dependências externas; use apenas fetch() e DOM API.
 */

// eslint-disable-next-line no-unused-vars
function search(query) {
    // TODO: implementar
    return [];
}

// eslint-disable-next-line no-unused-vars
function getMangaDetail(id) {
    // TODO: implementar
    return null;
}

// eslint-disable-next-line no-unused-vars
function getChapterPages(id) {
    // TODO: implementar
    return [];
}
