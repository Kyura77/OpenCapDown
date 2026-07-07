const fs = require('fs');
const { execSync } = require('child_process');

global.SourceEnv = {
  fetch: function(url) {
    const cmd = `curl.exe -k -s -L "${url}"`;
    try {
      const stdout = execSync(cmd, { maxBuffer: 15 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] });
      return stdout.toString('utf-8');
    } catch (e) {
      return "";
    }
  }
};

const obraId = "3715";
const capNum = "3861";

console.log(`Testando bypass do CDN para Obra ${obraId}, Capítulo ${capNum}...`);

const pages = [];
for (let i = 1; i <= 100; i++) {
  const imageUrl = `https://cdn.verdinha.wtf/scans/1/obras/${obraId}/capitulos/${capNum}/pagina_${i}.jpg`;
  process.stdout.write(`Verificando página ${i}... `);
  
  const res = global.SourceEnv.fetch(imageUrl);
  const trimmed = res.trim();
  
  if (!res || trimmed.startsWith("<!doctype") || trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html") || trimmed.includes("statusCode\":404") || trimmed.includes("404 Not Found")) {
    console.log("404 (Fim do capítulo detectado!)");
    break;
  }
  
  console.log(`200 OK! (Tamanho: ${res.length} bytes)`);
  pages.push({ index: i - 1, imageUrl: imageUrl });
}

console.log(`\nSucesso! Total de páginas detectadas: ${pages.length}`);
if (pages.length > 0) {
  console.log("Primeira página:", pages[0].imageUrl);
}
