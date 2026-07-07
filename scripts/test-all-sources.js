const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const cheerio = require('cheerio');

// Simula a ponte do parser HTML (Jsoup)
global.HtmlParser = {
  query: function(html, css) {
    try {
      const $ = cheerio.load(html);
      const elements = $(css);
      const arr = [];
      elements.each((index, el) => {
        const $el = $(el);
        const attrs = {};
        const elAttrs = el.attribs || {};
        for (const key in elAttrs) {
          attrs[key] = elAttrs[key];
        }
        arr.push({
          tag: el.name || el.tagName || '',
          text: $el.text().trim(),
          html: $el.html(),
          attrs: attrs
        });
      });
      return JSON.stringify(arr);
    } catch (e) {
      return "[]";
    }
  }
};

// Simula a ponte HTTP síncrona
global.SourceEnv = {
  fetch: function(url, headers) {
    let headerFlags = '';
    if (headers) {
      for (const [key, value] of Object.entries(headers)) {
        headerFlags += ` -H "${key}: ${value.replace(/"/g, '\\"')}"`;
      }
    }
    const cmd = `curl.exe -k -s -L ${headerFlags} "${url}"`;
    try {
      const stdout = execSync(cmd, { maxBuffer: 10 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] });
      return stdout.toString('utf-8');
    } catch (e) {
      return "";
    }
  },
  queryAll: function(html, css) {
    try {
      return JSON.parse(global.HtmlParser.query(html, css));
    } catch (err) {
      return [];
    }
  }
};

function loadScraper(sourceId) {
  const filePath = path.join(__dirname, '..', 'sources', 'src', 'main', 'assets', 'sources', `${sourceId}.js`);
  if (!fs.existsSync(filePath)) {
    throw new Error(`Scraper not found: ${filePath}`);
  }
  let code = fs.readFileSync(filePath, 'utf-8');
  code = code.replace(/export\s+default\s*\{/, 'module.exports = {');
  const tempPath = path.join(__dirname, `temp_all_${sourceId}.js`);
  fs.writeFileSync(tempPath, code, 'utf-8');
  try {
    const moduleInstance = require(tempPath);
    fs.unlinkSync(tempPath);
    return moduleInstance;
  } catch (err) {
    if (fs.existsSync(tempPath)) fs.unlinkSync(tempPath);
    throw err;
  }
}

const sourcesDir = path.join(__dirname, '..', 'sources', 'src', 'main', 'assets', 'sources');
const files = fs.readdirSync(sourcesDir)
  .filter(f => f.endsWith('.js') && !f.startsWith('_'));

const summary = [];

console.log(`Iniciando testes de integração em ${files.length} fontes...`);

for (const file of files) {
  const sourceId = file.replace('.js', '');
  console.log(`\n-----------------------------------------`);
  console.log(`Testando: ${sourceId}`);
  console.log(`-----------------------------------------`);
  
  try {
    const scraper = loadScraper(sourceId);
    console.log(`Fonte: ${scraper.name} (${scraper.lang}) -> ${scraper.baseUrl}`);
    
    // Tenta testar com "Solo Leveling" ou "Martial Peak"
    const searchQuery = (scraper.lang === 'pt-BR') ? 'Pico Marcial' : 'Solo Leveling';
    const searchResults = scraper.search(searchQuery);
    
    if (searchResults.length === 0) {
      console.log(`[-] Busca por "${searchQuery}" retornou 0 resultados.`);
      summary.push({ id: sourceId, name: scraper.name, status: 'Sem Resultados', detail: 'Busca vazia' });
      continue;
    }
    
    const firstResult = searchResults[0];
    console.log(`[+] Mangá encontrado: "${firstResult.title}" (URL: ${firstResult.url})`);
    
    const details = scraper.getMangaDetail(firstResult.url);
    console.log(`[+] Capítulos encontrados: ${details.chapters.length}`);
    
    if (details.chapters.length === 0) {
      console.log(`[-] Detalhes retornaram 0 capítulos.`);
      summary.push({ id: sourceId, name: scraper.name, status: 'Sem Capítulos', detail: '0 capítulos listados' });
      continue;
    }
    
    // Testa o mais recente e o mais antigo
    const targetChapter = details.chapters[details.chapters.length - 1]; // Livre/Mais antigo
    const pages = scraper.getChapterPages(targetChapter.url);
    console.log(`[+] Páginas no capítulo gratuito "${targetChapter.title}": ${pages.length}`);
    
    if (pages.length > 0) {
      console.log(`[+] Sucesso completo!`);
      summary.push({ id: sourceId, name: scraper.name, status: 'OK', detail: `${pages.length} págs carregadas` });
    } else {
      console.log(`[-] 0 páginas retornadas.`);
      summary.push({ id: sourceId, name: scraper.name, status: 'Sem Páginas', detail: 'Retornou 0 páginas' });
    }
  } catch (err) {
    console.error(`[!] Falha crítica no scraper ${sourceId}:`, err.message);
    summary.push({ id: sourceId, name: sourceId, status: 'ERRO', detail: err.message });
  }
}

console.log(`\n=========================================`);
console.log(`           RESUMO DOS SCRAPERS           `);
console.log(`=========================================`);
console.table(summary);
