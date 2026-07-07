# Guia para IA — Módulo `app`

## Regras
- Este módulo é UI. Não faça scraping aqui.
- Use `OpenCapDownCoreFactory.create(context)` para obter o core.
- Todas as telas consomem `Flow` do core para dados reativos.
- Nunca acesse Room ou OkHttp diretamente.
