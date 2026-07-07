/* ===== BRIDGE PROXY ===== */
const api = new Proxy({}, {
  get(t, method) {
    return (...args) => {
      try {
        const json = OpenCapDown[method](...args);
        const res = JSON.parse(json);
        if (!res.ok) throw new Error(res.error || 'Erro desconhecido');
        return res.data;
      } catch (e) {
        throw e;
      }
    };
  }
});
api.fetchImage = (url, headers) => OpenCapDown.fetchImage(url, JSON.stringify(headers || {}));
api.getLocalImage = (path) => OpenCapDown.getLocalImage(path);
api.addToLibrary = (manga) => JSON.parse(OpenCapDown.addToLibrary(JSON.stringify(manga))).ok;
api.removeFromLibrary = (id) => JSON.parse(OpenCapDown.removeFromLibrary(id)).ok;
api.downloadChapter = (mangaId, chapterId) => JSON.parse(OpenCapDown.downloadChapter(mangaId, chapterId)).ok;
api.cancelDownload = (id) => JSON.parse(OpenCapDown.cancelDownload(id)).ok;
api.backupChapter = (id) => JSON.parse(OpenCapDown.backupChapter(id)).ok;
api.restoreChapter = (msgId) => JSON.parse(OpenCapDown.restoreChapter(msgId)).ok;
api.markAsRead = (id) => JSON.parse(OpenCapDown.markAsRead(id)).ok;
api.updateReadingProgress = (m, c, p) => JSON.parse(OpenCapDown.updateReadingProgress(m, c, p)).ok;
api.updateTelegramConfig = (t, c) => JSON.parse(OpenCapDown.updateTelegramConfig(t, c)).ok;

/* ===== STATE ===== */
let state = {
  library: [],
  searchResults: [],
  currentManga: null,
  queue: [],
  settings: {},
  progress: null
};
let currentRoute = 'library';
let readerState = null;

/* ===== ROUTER ===== */
function navigate(route) {
  const clean = route.replace(/^#/, '');
  const parts = clean.split('/');
  currentRoute = parts[0];
  document.querySelectorAll('.nav-item').forEach(el => {
    el.classList.toggle('active', el.dataset.route === currentRoute);
  });
  render();
}

function navigateBack() {
  if (readerState) return closeReader();
  if (currentRoute === 'detail' || currentRoute === 'search') {
    navigate('library');
  }
}

function render() {
  const content = document.getElementById('content');
  const title = document.getElementById('screen-title');
  const backBtn = document.getElementById('btn-back');
  const searchBtn = document.getElementById('btn-search');
  const nav = document.getElementById('bottom-nav');

  content.innerHTML = '';
  content.scrollTop = 0;

  const isReader = currentRoute === 'reader';
  nav.classList.toggle('hidden', isReader);
  document.querySelector('#top-bar').classList.toggle('hidden', isReader);

  switch (currentRoute) {
    case 'library': renderLibrary(content); title.textContent = 'Biblioteca'; backBtn.classList.remove('visible'); searchBtn.style.display = 'flex'; break;
    case 'search': renderSearch(content); title.textContent = 'Buscar'; backBtn.classList.add('visible'); searchBtn.style.display = 'none'; break;
    case 'detail': renderDetail(content); title.textContent = state.currentManga?.title || 'Detalhes'; backBtn.classList.add('visible'); searchBtn.style.display = 'none'; break;
    case 'reader': renderReader(content); title.textContent = ''; backBtn.classList.remove('visible'); searchBtn.style.display = 'none'; break;
    case 'downloads': renderDownloads(content); title.textContent = 'Downloads'; backBtn.classList.remove('visible'); searchBtn.style.display = 'none'; break;
    case 'settings': renderSettings(content); title.textContent = 'Ajustes'; backBtn.classList.remove('visible'); searchBtn.style.display = 'none'; break;
  }
}

/* ===== UTILITIES ===== */
function toast(msg) {
  const c = document.getElementById('toast-container');
  const el = document.createElement('div');
  el.className = 'toast';
  el.textContent = msg;
  c.appendChild(el);
  setTimeout(() => el.remove(), 2500);
}

function showLoading(container) {
  let html = '<div class="shimmer-grid">';
  for (let i = 0; i < 6; i++) {
    html += '<div class="shimmer-card"><div class="shimmer-cover"></div><div class="shimmer-line"></div><div class="shimmer-line"></div></div>';
  }
  html += '</div>';
  container.innerHTML = html;
}

function showEmpty(container, title, msg, action) {
  container.innerHTML = `
    <div class="empty-state">
      <h2>${title}</h2>
      <p>${msg}</p>
      ${action || ''}
    </div>`;
}

function showError(container, msg) {
  container.innerHTML = `
    <div class="empty-state">
      <h2>Algo deu errado</h2>
      <p>${msg}</p>
      <button class="btn-primary" onclick="render()">Tentar novamente</button>
    </div>`;
}

function createSVG(name) {
  const icons = {
    library: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/></svg>',
    search: '<svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="M16 16l5 5"/></svg>',
    downloads: '<svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3"/></svg>',
    settings: '<svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/></svg>',
    check: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>'
  };
  return icons[name] || '';
}

function escapeHtml(s) {
  const d = document.createElement('div');
  d.textContent = s;
  return d.innerHTML;
}

/* ===== CARD COMPONENT ===== */
function mangaCard(item, onClick) {
  return `<div class="manga-grid-item" onclick="(${onClick.toString()})()">
    <img class="cover" src="${escapeHtml(item.coverUrl || '')}" loading="lazy" onerror="this.style.display='none'">
    <div class="info">
      <div class="title">${escapeHtml(item.title)}</div>
      ${item.sourceId ? `<div class="badge">${escapeHtml(item.sourceId)}</div>` : ''}
    </div>
  </div>`;
}

/* ===== SCREEN: LIBRARY ===== */
async function renderLibrary(container) {
  showLoading(container);
  try {
    state.library = api.getLibrary() || [];
    if (state.library.length === 0) {
      showEmpty(container, 'Sua biblioteca está vazia', 'Toque na lupa para adicionar mangas.', `<button class="btn-primary" onclick="navigate('search')">Buscar mangas</button>`);
      return;
    }
    let html = '<div class="manga-grid">';
    state.library.forEach(item => {
      html += mangaCard(item, () => {
        state.currentManga = item;
        navigate(`detail/${item.sourceId}/${encodeURIComponent(item.title)}`);
      });
    });
    html += '</div>';
    container.innerHTML = html;
  } catch (e) {
    showError(container, e.message);
  }
}

/* ===== SCREEN: SEARCH ===== */
let searchTimer;
function renderSearch(container) {
  container.innerHTML = `
    <div class="search-bar">
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="M16 16l5 5"/></svg>
      <input type="text" id="search-input" placeholder="Buscar mangas..." autofocus>
    </div>
    <div id="search-results"></div>`;

  const input = document.getElementById('search-input');
  input.addEventListener('input', () => {
    clearTimeout(searchTimer);
    const q = input.value.trim();
    if (!q) { document.getElementById('search-results').innerHTML = ''; return; }
    searchTimer = setTimeout(() => doSearch(q), 400);
  });
}

async function doSearch(query) {
  const el = document.getElementById('search-results');
  el.innerHTML = '<div class="shimmer-grid">' +
    Array(4).fill('<div class="shimmer-card"><div class="shimmer-cover"></div><div class="shimmer-line"></div><div class="shimmer-line"></div></div>').join('') +
    '</div>';
  try {
    state.searchResults = api.search(query) || [];
    if (state.searchResults.length === 0) {
      showEmpty(el, 'Nenhum resultado', 'Tente outro termo de busca.');
      return;
    }
    let html = '<div class="manga-grid">';
    state.searchResults.forEach(item => {
      html += mangaCard(item, () => {
        navigate(`detail/${item.sourceId}/${encodeURIComponent(item.url)}`);
      });
    });
    html += '</div>';
    el.innerHTML = html;
  } catch (e) {
    showError(el, e.message);
  }
}

/* ===== SCREEN: DETAIL ===== */
async function renderDetail(container) {
  showLoading(container);
  try {
    const parts = currentRoute.split('/');
    const sourceId = decodeURIComponent(parts[1] || '');
    const mangaUrl = decodeURIComponent(parts.slice(2).join('/') || '');

    if (!state.currentManga || state.currentManga.sourceId !== sourceId) {
      state.currentManga = null;
    }

    const detail = sourceId ? api.getMangaDetail(sourceId, mangaUrl) : null;
    if (!detail) { showError(container, 'Nao foi possivel carregar os detalhes.'); return; }

    const inLibrary = state.library.some(m => m.sourceId === sourceId && m.title === detail.title);

    let chaptersHtml = '';
    if (detail.chapters && detail.chapters.length > 0) {
      chaptersHtml = '<div class="chapter-list"><h2>Capítulos</h2>';
      detail.chapters.forEach(ch => {
        const status = ch.isRead ? 'read' : 'pending';
        const label = ch.isRead ? 'Lido' : '';
        chaptersHtml += `
          <div class="chapter-item" onclick="openChapter('${escapeHtml(ch.id)}', '${escapeHtml(sourceId)}', '${escapeHtml(detail.title)}', '${escapeHtml(ch.url)}')">
            <span class="chapter-num">#${ch.number}</span>
            <span class="chapter-title">${escapeHtml(ch.title)}</span>
            ${label ? `<span class="chapter-status ${status}">${label}</span>` : ''}
          </div>`;
      });
      chaptersHtml += '</div>';
    }

    container.innerHTML = `
      <div class="detail-header">
        <img class="detail-cover" src="${escapeHtml(detail.coverUrl || '')}" onerror="this.style.display='none'">
        <div class="detail-info">
          <h1>${escapeHtml(detail.title)}</h1>
          <div class="status-badge">${escapeHtml(detail.status || 'Desconhecido')}</div>
          <div class="detail-desc" onclick="this.classList.toggle('expanded')">${escapeHtml(detail.description || 'Sem descricao.')}</div>
          <div class="detail-actions">
            <button class="btn-primary" onclick="${inLibrary ? `removeLib('${detail.sourceId}')` : `addLib('${escapeHtml(JSON.stringify(detail).replace(/'/g, "\\'"))}')`}">${inLibrary ? 'Remover da Biblioteca' : 'Adicionar a Biblioteca'}</button>
          </div>
        </div>
      </div>
      ${chaptersHtml}`;
  } catch (e) {
    showError(container, e.message);
  }
}

window.addLib = async function(jsonStr) {
  try {
    const detail = JSON.parse(jsonStr);
    api.addToLibrary(detail);
    toast('Adicionado a biblioteca');
    state.library = api.getLibrary() || [];
    render();
  } catch (e) { toast('Erro: ' + e.message); }
};

window.removeLib = async function(sourceId) {
  try {
    const manga = state.library.find(m => m.sourceId === sourceId);
    if (manga) api.removeFromLibrary(manga.id);
    toast('Removido da biblioteca');
    state.library = api.getLibrary() || [];
    render();
  } catch (e) { toast('Erro: ' + e.message); }
};

window.openChapter = async function(chapterId, sourceId, mangaTitle, chapterUrl) {
  const manga = state.library.find(m => m.title === mangaTitle);
  const mangaId = manga ? manga.id : sourceId + '-' + mangaTitle;
  navigate(`reader/${chapterId}/${sourceId}/${mangaId}/${encodeURIComponent(chapterUrl)}`);
};

/* ===== SCREEN: READER ===== */
readerState = null;

function renderReader(container) {
  const parts = currentRoute.split('/');
  const chapterId = decodeURIComponent(parts[1] || '');
  const sourceId = decodeURIComponent(parts[2] || '');
  const mangaId = decodeURIComponent(parts[3] || '');
  const chapterUrl = decodeURIComponent(parts.slice(4).join('/') || '');
  let mode = readerState?.mode || 'vertical';

  container.innerHTML = `
    <div class="reader-container" id="reader-container">
      <div class="reader-overlay" id="reader-overlay">
        <div class="reader-top-bar">
          <button onclick="closeReader()">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="#fff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6L6 18M6 6l12 12"/></svg>
          </button>
          <span id="reader-title">Carregando...</span>
          <button style="visibility:hidden"> </button>
        </div>
        <div class="reader-bottom-bar">
          <span id="reader-page-label">Pag. 0 / 0</span>
          <input type="range" class="page-slider" id="page-slider" min="0" max="0" value="0" oninput="readerGoTo(parseInt(this.value))">
          <button class="mode-toggle" onclick="readerToggleMode()">${mode === 'vertical' ? 'Horizontal' : 'Vertical'}</button>
        </div>
      </div>
      <div class="reader-scroll" id="reader-scroll"></div>
    </div>`;

  readerState = { chapterId, sourceId, mangaId, chapterUrl, mode, pages: [], currentPage: 0, overlayTimer: null };

  document.getElementById('reader-container').addEventListener('click', (e) => {
    if (e.target.closest('.reader-top-bar') || e.target.closest('.reader-bottom-bar') || e.target.closest('.mode-toggle')) return;
    readerToggleOverlay();
  });

  loadReaderPages();
}

async function loadReaderPages() {
  const scroll = document.getElementById('reader-scroll');
  if (!scroll) return;

  try {
    let pages = [];

    const dbChapter = api.getChapter(readerState.chapterId);
    if (dbChapter && dbChapter.pages && dbChapter.pages.length > 0) {
      pages = dbChapter.pages;
      document.getElementById('reader-title').textContent = dbChapter.chapter.title || 'Capitulo';
    } else {
      const pageResults = api.getChapterPages(readerState.sourceId, readerState.chapterUrl);
      if (pageResults && pageResults.length > 0) {
        pages = pageResults.map((p, i) => ({ index: i, imageUrl: p.imageUrl, headers: p.headers }));
      }
      const detail = api.getMangaDetail(readerState.sourceId, readerState.chapterUrl);
      const chTitle = detail?.chapters?.find(c => c.url === readerState.chapterUrl)?.title || 'Capitulo';
      document.getElementById('reader-title').textContent = chTitle;
    }

    if (pages.length === 0) {
      scroll.innerHTML = '<div style="color:#fff;text-align:center;padding:40px">Nenhuma pagina encontrada.</div>';
      return;
    }

    readerState.pages = pages;
    renderReaderPages(scroll, pages);

    const slider = document.getElementById('page-slider');
    if (slider) slider.max = pages.length - 1;

    const progress = api.getReadingProgress(readerState.mangaId);
    const startPage = progress && progress.chapterId === readerState.chapterId ? progress.pageIndex : 0;
    readerGoTo(startPage);

    document.getElementById('reader-page-label').textContent = `Pag. ${startPage + 1} / ${pages.length}`;
  } catch (e) {
    scroll.innerHTML = `<div style="color:#fff;text-align:center;padding:40px">Erro: ${escapeHtml(e.message)}</div>`;
  }
}

function renderReaderPages(scroll, pages) {
  if (readerState.mode === 'vertical') {
    scroll.className = 'reader-scroll';
    let html = '';
    pages.forEach((page, i) => {
      const src = page.localPath ? '' : (page.imageUrl || '');
      html += `<img data-page="${i}" src="${escapeHtml(src)}" alt="Pagina ${i + 1}" loading="lazy" onerror="this.alt='erro'">`;
    });
    scroll.innerHTML = html;

    pages.forEach((page, i) => {
      const img = scroll.querySelector(`img[data-page="${i}"]`);
      if (!img) return;
      if (page.localPath) {
        const b64 = api.getLocalImage(page.localPath);
        if (b64) img.src = b64;
      } else if (page.headers && Object.keys(page.headers).length > 0) {
        const b64 = api.fetchImage(page.imageUrl, page.headers);
        if (b64) img.src = b64;
      }
    });

    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          const p = parseInt(entry.target.dataset.page);
          if (!isNaN(p)) { readerState.currentPage = p; updateReaderProgress(); }
        }
      });
    }, { threshold: 0.3 });
    scroll.querySelectorAll('img').forEach(img => observer.observe(img));
    readerState.observer = observer;

  } else {
    scroll.className = 'reader-horizontal';
    let html = '';
    pages.forEach((page, i) => {
      const src = page.localPath ? '' : (page.imageUrl || '');
      html += `<img data-page="${i}" src="${escapeHtml(src)}" alt="Pagina ${i + 1}" onerror="this.alt='erro'">`;
    });
    scroll.innerHTML = html;

    pages.forEach((page, i) => {
      const img = scroll.querySelector(`img[data-page="${i}"]`);
      if (!img) return;
      if (page.localPath) {
        const b64 = api.getLocalImage(page.localPath);
        if (b64) img.src = b64;
      } else if (page.headers && Object.keys(page.headers).length > 0) {
        const b64 = api.fetchImage(page.imageUrl, page.headers);
        if (b64) img.src = b64;
      }
    });

    scroll.addEventListener('scroll', () => {
      const idx = Math.round(scroll.scrollLeft / scroll.clientWidth);
      if (idx !== readerState.currentPage && idx >= 0 && idx < readerState.pages.length) {
        readerState.currentPage = idx;
        updateReaderProgress();
      }
    });
  }
}

function updateReaderProgress() {
  const label = document.getElementById('reader-page-label');
  const slider = document.getElementById('page-slider');
  const total = readerState.pages.length;
  if (label) label.textContent = `Pag. ${readerState.currentPage + 1} / ${total}`;
  if (slider) slider.value = readerState.currentPage;
  api.updateReadingProgress(readerState.mangaId, readerState.chapterId, readerState.currentPage);
}

window.readerGoTo = function(page) {
  readerState.currentPage = Math.max(0, Math.min(page, readerState.pages.length - 1));
  const scroll = document.getElementById('reader-scroll');
  if (!scroll) return;
  if (readerState.mode === 'vertical') {
    const img = scroll.querySelector(`img[data-page="${readerState.currentPage}"]`);
    if (img) img.scrollIntoView({ behavior: 'smooth', block: 'start' });
  } else {
    scroll.scrollTo({ left: readerState.currentPage * scroll.clientWidth, behavior: 'smooth' });
  }
  updateReaderProgress();
};

window.readerToggleMode = function() {
  readerState.mode = readerState.mode === 'vertical' ? 'horizontal' : 'vertical';
  const scroll = document.getElementById('reader-scroll');
  if (scroll && readerState.pages.length > 0) {
    renderReaderPages(scroll, readerState.pages);
  }
  document.querySelector('.mode-toggle').textContent = readerState.mode === 'vertical' ? 'Horizontal' : 'Vertical';
};

let overlayTimer;
window.readerToggleOverlay = function() {
  const overlay = document.getElementById('reader-overlay');
  if (!overlay) return;
  clearTimeout(overlayTimer);
  overlay.classList.toggle('hidden');
  if (!overlay.classList.contains('hidden')) {
    overlayTimer = setTimeout(() => overlay.classList.add('hidden'), 3000);
  }
};

window.closeReader = function() {
  if (readerState?.observer) readerState.observer.disconnect();
  readerState = null;
  navigate('library');
};

/* ===== SCREEN: DOWNLOADS ===== */
let downloadTab = 'active';

function renderDownloads(container) {
  container.innerHTML = `
    <div class="tab-bar">
      <div class="tab-item ${downloadTab === 'active' ? 'active' : ''}" onclick="switchDownloadsTab('active')">Ativos</div>
      <div class="tab-item ${downloadTab === 'complete' ? 'active' : ''}" onclick="switchDownloadsTab('complete')">Completos</div>
    </div>
    <div id="downloads-list"></div>`;
  renderDownloadsList();
}

window.switchDownloadsTab = function(tab) {
  downloadTab = tab;
  render();
};

function renderDownloadsList() {
  const el = document.getElementById('downloads-list');
  if (!el) return;
  try {
    state.queue = api.getDownloadQueue() || [];

    if (downloadTab === 'active') {
      const active = state.queue.filter(j => j.status === 'QUEUED' || j.status === 'DOWNLOADING');
      if (active.length === 0) {
        showEmpty(el, 'Nenhum download ativo', 'Capitulos baixados aparecerao aqui.');
        return;
      }
      let html = '';
      active.forEach(job => {
        const statusText = job.status === 'QUEUED' ? 'Na fila' : `${job.progress}%`;
        html += `
          <div class="download-item">
            <div class="info">
              <div class="title">${escapeHtml(job.chapterId)}</div>
              <div class="sub">${statusText}</div>
              ${job.status === 'DOWNLOADING' ? `<div class="progress-bar"><div class="progress-fill" style="width:${job.progress}%"></div></div>` : ''}
            </div>
            <button class="cancel-btn" onclick="cancelDownload('${job.id}')">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M18 6L6 18M6 6l12 12"/></svg>
            </button>
          </div>`;
      });
      el.innerHTML = html;
    } else {
      const complete = state.queue.filter(j => j.status === 'COMPLETE');
      if (complete.length === 0) {
        showEmpty(el, 'Nenhum download completo', 'Capítulos baixados aparecerão aqui.');
        return;
      }
      let html = '';
      complete.forEach(job => {
        html += `<div class="download-item"><div class="info"><div class="title">${escapeHtml(job.chapterId)}</div><div class="sub">Completo</div></div></div>`;
      });
      el.innerHTML = html;
    }
  } catch (e) {
    showError(el, e.message);
  }
}

window.cancelDownload = function(jobId) {
  try {
    api.cancelDownload(jobId);
    toast('Download cancelado');
    renderDownloadsList();
  } catch (e) { toast('Erro: ' + e.message); }
};

setInterval(() => {
  if (currentRoute === 'downloads' && document.getElementById('downloads-list')) {
    renderDownloadsList();
  }
}, 2000);

/* ===== SCREEN: SETTINGS ===== */
async function renderSettings(container) {
  try {
    state.settings = api.getSettings() || {};
    const appVer = JSON.parse(OpenCapDown.getAppVersion()).data;
    const repoInfo = JSON.parse(OpenCapDown.getUpdateRepo()).data;

    const hasToken = state.settings.botToken && state.settings.botToken.length > 0;
    const hasChat = state.settings.chatId && state.settings.chatId.length > 0;
    const configured = hasToken && hasChat;

    container.innerHTML = `
      <div class="settings-section">
        <h2>Telegram</h2>
        <div class="settings-field">
          <label>Bot Token</label>
          <input type="password" id="tg-token" placeholder="123456:ABC-DEF..." value="${escapeHtml(state.settings.botToken || '')}">
        </div>
        <div class="settings-field">
          <label>Chat ID</label>
          <input type="text" id="tg-chat" placeholder="-1001234567890" value="${escapeHtml(state.settings.chatId || '')}">
        </div>
        <div class="settings-status ${configured ? 'ok' : 'na'}">
          ${configured ? createSVG('check') + ' Conectado' : 'Nao configurado'}
        </div>
        <button class="btn-primary" onclick="saveSettings()">Salvar</button>
      </div>
      <div class="settings-section">
        <h2>Atualizacao</h2>
        <div class="settings-field">
          <label>GitHub Owner</label>
          <input type="text" id="gh-owner" placeholder="owner" value="${escapeHtml(repoInfo.owner)}">
        </div>
        <div class="settings-field">
          <label>GitHub Repo</label>
          <input type="text" id="gh-repo" placeholder="repo" value="${escapeHtml(repoInfo.repo)}">
        </div>
        <button class="btn-primary" onclick="saveUpdateRepo(); checkForUpdate()">Salvar repo e verificar</button>
        <div id="update-status" style="margin-top:12px;font-size:13px;color:var(--text2)"></div>
      </div>
      <div class="settings-section">
        <h2>Sobre</h2>
        <p style="font-size:14px;color:var(--text2)">Versao: ${escapeHtml(appVer.versionName)} (build ${appVer.versionCode})</p>
        <p style="font-size:14px;color:var(--text2)">Core: ${escapeHtml(state.settings.version || '?')}</p>
      </div>`;

    checkForUpdateSilent();
  } catch (e) {
    showError(container, e.message);
  }
}

window.saveSettings = function() {
  try {
    const token = document.getElementById('tg-token').value.trim();
    const chat = document.getElementById('tg-chat').value.trim();
    if (token && chat) {
      api.updateTelegramConfig(token, chat);
      toast('Configuracoes salvas');
    } else {
      toast('Preencha token e chat ID');
    }
  } catch (e) { toast('Erro: ' + e.message); }
};

window.saveUpdateRepo = function() {
  const owner = document.getElementById('gh-owner').value.trim();
  const repo = document.getElementById('gh-repo').value.trim();
  if (owner && repo) {
    JSON.parse(OpenCapDown.setUpdateRepo(owner, repo));
  }
};

function setUpdateStatus(html) {
  var el = document.getElementById('update-status');
  if (el) el.innerHTML = html;
}

async function checkForUpdate() {
  setUpdateStatus('<span style="color:var(--text2)">Verificando...</span>');
  try {
    var res = JSON.parse(OpenCapDown.checkForUpdate());
    if (!res.ok) { setUpdateStatus('<span style="color:#e74c3c">Erro: ' + escapeHtml(res.error) + '</span>'); return; }
    if (res.data.hasUpdate) {
      setUpdateStatus('');
      showUpdateDialog(res.data);
    } else {
      setUpdateStatus('<span style="color:#2ecc71">' + createSVG('check') + ' Atualizado (v' + escapeHtml(res.data.currentVersion) + ')</span>');
    }
  } catch (e) { setUpdateStatus('<span style="color:#e74c3c">Falha: ' + escapeHtml(e.message) + '</span>'); }
}

async function checkForUpdateSilent() {
  setUpdateStatus('<span style="color:var(--text2)">Verificando...</span>');
  try {
    var res = JSON.parse(OpenCapDown.checkForUpdate());
    if (!res.ok) { setUpdateStatus('<span style="color:var(--text2)">Offline</span>'); return; }
    if (res.data.hasUpdate) {
      setUpdateStatus('');
      showUpdateDialog(res.data);
    } else {
      setUpdateStatus('<span style="color:#2ecc71">' + createSVG('check') + ' Atualizado (v' + escapeHtml(res.data.currentVersion) + ')</span>');
    }
  } catch (e) { setUpdateStatus('<span style="color:var(--text2)">Offline</span>'); }
}

function showUpdateDialog(data) {
  const existing = document.getElementById('update-dialog');
  if (existing) existing.remove();

  const overlay = document.createElement('div');
  overlay.id = 'update-dialog';
  overlay.style.cssText = 'position:fixed;inset:0;z-index:100;background:rgba(0,0,0,.6);display:flex;align-items:center;justify-content:center;padding:24px';
  overlay.onclick = (e) => { if (e.target === overlay) overlay.remove(); };

  const dialog = document.createElement('div');
  dialog.style.cssText = 'background:var(--surface);border-radius:16px;padding:24px;max-width:380px;width:100%;box-shadow:0 8px 32px rgba(0,0,0,.3);animation:dialogIn .25s ease';

  const sizeMb = data.size ? (data.size / 1048576).toFixed(1) : '?';
  dialog.innerHTML = `
    <h2 style="font-size:20px;font-weight:700;margin-bottom:8px">Atualizacao disponivel</h2>
    <p style="font-size:14px;color:var(--text2);margin-bottom:16px">
      ${escapeHtml(data.currentVersion)} &rarr; <strong>${escapeHtml(data.latestVersion)}</strong>
      ${data.size ? `<br>${sizeMb} MB` : ''}
    </p>
    ${data.changelog ? `<div style="font-size:13px;color:var(--text2);background:var(--surface2);padding:12px;border-radius:8px;margin-bottom:16px;max-height:160px;overflow-y:auto;white-space:pre-wrap">${escapeHtml(data.changelog)}</div>` : ''}
    <div style="display:flex;gap:8px">
      <button class="btn-primary" style="flex:1" onclick="installUpdate('${escapeHtml(data.downloadUrl)}', this)">Atualizar</button>
      <button style="flex:1;padding:12px;border-radius:24px;font-size:15px;font-weight:600;background:var(--surface2);color:var(--fg)" onclick="this.closest('#update-dialog').remove()">Agora nao</button>
    </div>
    <div id="update-progress" style="margin-top:12px;font-size:13px;color:var(--text2);text-align:center"></div>`;

  overlay.appendChild(dialog);
  document.body.appendChild(overlay);
}

window.installUpdate = function(url, btn) {
  btn.disabled = true;
  btn.textContent = 'Baixando...';
  const progress = document.getElementById('update-progress');
  if (progress) progress.textContent = 'Fazendo download...';

  try {
    const res = JSON.parse(OpenCapDown.installUpdate(url));
    if (res.ok) {
      if (progress) progress.textContent = 'Instalando...';
    } else {
      btn.disabled = false;
      btn.textContent = 'Tentar novamente';
      if (progress) progress.textContent = 'Erro: ' + res.error;
    }
  } catch (e) {
    btn.disabled = false;
    btn.textContent = 'Tentar novamente';
    if (progress) progress.textContent = 'Erro: ' + e.message;
  }
};

/* ===== INIT ===== */
navigate('library');
