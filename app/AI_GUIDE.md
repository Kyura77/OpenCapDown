# Guia para IA — Módulo `app`

## Arquitetura

- **UI**: SPA em HTML/CSS/JS puro, carregada em WebView.
- **Kotlin**: `OpenCapDownBridge.kt` expõe o core via `@JavascriptInterface`.
- **JS**: O objeto `OpenCapDown` tem todos os métodos do core.

## Regras
- UI fica em `assets/ui/` — nunca mexa no core daqui.
- Toda chamada ao core vai pelo `OpenCapDown` bridge.
- Implementações de tela ficam em `assets/ui/js/app.js`.
- Estilos em `assets/ui/css/style.css`.

## Fluxo
1. `MainActivity.kt` carrega WebView com `file:///android_asset/ui/index.html`
2. JS chama `OpenCapDown.search(query)` etc.
3. Bridge faz `runBlocking { core.method() }` e retorna JSON.
4. JS renderiza o resultado.
