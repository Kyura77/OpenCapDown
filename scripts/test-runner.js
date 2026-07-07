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
      console.error("Erro no HtmlParser.query:", e);
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
    // Adiciona User-Agent apenas se explicitamente fornecido pelo scraper
    if (headers) {
      // Se já tiver User-Agent no loop, ele será adicionado
    }
    
    // Comando curl para busca síncrona (com -k para ignorar erros de SSL na rede local)
    const cmd = `curl.exe -k -s -L ${headerFlags} "${url}"`;
    console.log(`[CURL EXEC]: ${cmd}`);
    try {
      const stdout = execSync(cmd, { maxBuffer: 15 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] });
      const res = stdout.toString('utf-8');
      if (res.trim().startsWith("<!DOCTYPE") || res.trim().startsWith("<!doctype") || res.trim().startsWith("<html")) {
         // Se for HTML retornado, registra para podermos ver
         fs.writeFileSync(path.join(__dirname, 'last_response.html'), res, 'utf-8');
      }
      return res;
    } catch (e) {
      console.error(`Falha ao buscar URL: ${url}. Erro:`, e.message);
      return "";
    }
  },
  queryAll: function(html, css) {
    try {
      return JSON.parse(global.HtmlParser.query(html, css));
    } catch (err) {
      console.error("Falha ao fazer parse do resultado de queryAll:", err);
      return [];
    }
  }
};

// Carrega o módulo do scraper limpando o "export default"
function loadScraper(sourceId) {
  const filePath = path.join(__dirname, '..', 'sources', 'src', 'main', 'assets', 'sources', `${sourceId}.js`);
  if (!fs.existsSync(filePath)) {
    throw new Error(`Scraper file not found: ${filePath}`);
  }
  
  let code = fs.readFileSync(filePath, 'utf-8');
  // Se contiver JSON.parse(res), vamos capturar erros para depuração amigável
  code = code.replace(/JSON\.parse\(([^)]+)\)/g, (match, p1) => {
     return `(function(){
       try {
         return JSON.parse(${p1});
       } catch(e) {
         console.error("FALHA DE PARSE JSON. Conteúdo retornado:", ${p1}.substring(0, 300));
         throw e;
       }
     })()`;
  });
  
  // Transforma "export default {" em "module.exports = {"
  code = code.replace(/export\s+default\s*\{/, 'module.exports = {');
  
  // Salva temporariamente e carrega como módulo CommonJS
  const tempPath = path.join(__dirname, `temp_${sourceId}.js`);
  fs.writeFileSync(tempPath, code, 'utf-8');
  
  try {
    const moduleInstance = require(tempPath);
    // Limpa o arquivo temporário
    fs.unlinkSync(tempPath);
    return moduleInstance;
  } catch (err) {
    if (fs.existsSync(tempPath)) fs.unlinkSync(tempPath);
    throw err;
  }
}

// Rodar os testes das fontes informadas no terminal
const sourceId = process.argv[2] || 'mangadex';
const query = process.argv[3] || 'Solo Leveling';

console.log(`=========================================`);
console.log(`Testando Scraper: ${sourceId}`);
console.log(`=========================================`);

try {
  const scraper = loadScraper(sourceId);
  console.log(`Nome da Fonte: ${scraper.name} (ID: ${scraper.id}, Idioma: ${scraper.lang})`);
  console.log(`Base URL: ${scraper.baseUrl}`);
  
  console.log(`\n[1/3] Executando busca por "${query}"...`);
  const searchResults = scraper.search(query);
  console.log(`Resultados de busca retornado: ${searchResults.length}`);
  if (searchResults.length === 0) {
    console.log("AVISO: Busca retornou 0 resultados.");
  } else {
    console.log("Resultados encontrados:");
    searchResults.forEach((r, idx) => {
      console.log(`  [${idx + 1}] ${r.title} (URL: ${r.url})`);
    });
    const firstResult = searchResults[0];
    console.log(`\nSelecionando primeiro resultado para teste detalhado:`);
    console.log(JSON.stringify(firstResult, null, 2));
    
    console.log(`\n[2/3] Buscando detalhes do mangá (URL: ${firstResult.url})...`);
    const details = scraper.getMangaDetail(firstResult.url);
    console.log(`Título resolvido: ${details.title}`);
    console.log(`Capítulos encontrados: ${details.chapters.length}`);
    
    if (details.chapters.length === 0) {
      console.log("AVISO: 0 capítulos encontrados.");
    } else {
      let targetChapter = details.chapters[0];
      console.log(`Capítulo mais recente: "${targetChapter.title}" (URL: ${targetChapter.url}, ID: ${targetChapter.id})`);
      
      console.log(`\n[3/3] Buscando páginas do capítulo mais recente...`);
      let pages = scraper.getChapterPages(targetChapter.url);
      console.log(`Páginas retornadas: ${pages.length}`);
      
      if (pages.length === 0 && details.chapters.length > 1) {
        console.log("Aviso: 0 páginas retornadas. Pode ser um capítulo VIP. Tentando o capítulo mais antigo/gratuito...");
        targetChapter = details.chapters[details.chapters.length - 1];
        console.log(`Capítulo mais antigo: "${targetChapter.title}" (URL: ${targetChapter.url}, ID: ${targetChapter.id})`);
        pages = scraper.getChapterPages(targetChapter.url);
        console.log(`Páginas retornadas para o capítulo livre: ${pages.length}`);
      }
      
      if (pages.length > 0) {
        console.log(`Exemplo da primeira página:`);
        console.log(JSON.stringify(pages[0], null, 2));
        console.log(`\nSUCESSO: Scraper ${sourceId} está funcionando corretamente!`);
      } else {
        console.log(`\nFALHA: Scraper ${sourceId} retornou 0 páginas em todos os testes!`);
      }
    }
  }
} catch (e) {
  console.error("Erro durante a execução do scraper:", e);
}
