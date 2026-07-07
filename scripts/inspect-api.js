const { execSync } = require('child_process');

console.log("Baixando dados do capitulo 48943 da API...");
const cmd = `curl.exe -k -s "https://api.verdinha.wtf/capitulos/48943"`;
try {
  const stdout = execSync(cmd).toString('utf-8');
  const json = JSON.parse(stdout);
  const paginas = json.cap_paginas || json.pages || [];
  console.log("Total de paginas na API:", paginas.length);
  
  console.log('\n--- Primeiras 5 paginas da API ---');
  paginas.slice(0, 5).forEach((p, i) => {
    console.log(`[Index ${i}] src: ${p.src || p.imageUrl}`);
  });

  console.log('\n--- Ultimas 5 paginas da API ---');
  paginas.slice(-5).forEach((p, i) => {
    console.log(`[Index ${paginas.length - 5 + i}] src: ${p.src || p.imageUrl}`);
  });
} catch (e) {
  console.error("Falha ao processar:", e.message);
}
