const fs = require('fs');

if (!fs.existsSync('cap_pages_api.json')) {
  console.log("Arquivo cap_pages_api.json nao encontrado!");
  process.exit(1);
}

try {
  const json = JSON.parse(fs.readFileSync('cap_pages_api.json', 'utf-8'));
  const paginas = json.cap_paginas || json.pages || [];
  console.log('Total de paginas na API:', paginas.length);
  
  console.log('\n--- Primeiras 5 paginas da API ---');
  paginas.slice(0, 5).forEach((p, i) => {
    console.log(`[Index ${i}] src: ${p.src || p.imageUrl}`);
  });

  console.log('\n--- Ultimas 5 paginas da API ---');
  paginas.slice(-5).forEach((p, i) => {
    console.log(`[Index ${paginas.length - 5 + i}] src: ${p.src || p.imageUrl}`);
  });
} catch (e) {
  console.error("Erro ao analisar JSON:", e);
}
