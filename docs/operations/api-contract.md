# Contrato Público OpenCapDownCore v1.0.0

Interface estável. Mudanças quebram versão.

## search(query)
Busca em todas as sources.
Returns `List<SearchResult>`.

## getMangaDetail(sourceId, mangaUrl)
Retorna detalhes + capítulos.

## downloadChapter(mangaId, chapterId)
Enfileira download. Observe via `observeDownloadQueue()`.

## backupChapter(chapterId)
Envia capítulo pro Telegram. Requer bot configurado.
