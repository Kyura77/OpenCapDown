const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Carrega o .env manualmente para nao precisar de dependencias
const envPath = path.join(__dirname, '.env');
if (fs.existsSync(envPath)) {
  const envContent = fs.readFileSync(envPath, 'utf-8');
  envContent.split('\n').forEach(line => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) return;
    const parts = trimmed.split('=');
    if (parts.length >= 2) {
      const key = parts[0].trim();
      const val = parts.slice(1).join('=').trim().replace(/^["']|["']$/g, '');
      process.env[key] = val;
    }
  });
}

const user = process.env.VERDINHA_USER;
const pass = process.env.VERDINHA_PASSKEY;

if (!user || user === 'seu_usuario_ou_email') {
  console.log("[!] Por favor, preencha as credenciais no arquivo scripts/.env antes de executar.");
  process.exit(0);
}

console.log(`[+] Efetuando login para o usuario: ${user}...`);

// Faz a requisicao POST de login via curl
const loginBody = JSON.stringify({
  login: user,
  senha: pass,
  tipo_usuario: 'usuario'
});

const loginCmd = `curl.exe -k -s -X POST -H "Content-Type: application/json" -d "${loginBody.replace(/"/g, '\\"')}" "https://api.verdinha.wtf/auth/login"`;

try {
  const loginRes = execSync(loginCmd).toString('utf-8');
  const loginJson = JSON.parse(loginRes);
  const token = loginJson.access_token || loginJson.token;
  
  if (!token) {
    console.error("[!] Falha na autenticacao. Resposta do servidor:", loginRes);
    process.exit(1);
  }
  
  console.log("[+] Autenticado com sucesso! Token JWT obtido.");
  
  // Vamos buscar o capitulo mais recente de Pico Marcial (ex: 362798)
  const targetChapter = "362798";
  console.log(`[+] Buscando paginas do capitulo ${targetChapter} usando o Token VIP...`);
  
  const fetchCmd = `curl.exe -k -s -H "Authorization: Bearer ${token}" "https://api.verdinha.wtf/capitulos/${targetChapter}"`;
  const chapterRes = execSync(fetchCmd).toString('utf-8');
  const chapterJson = JSON.parse(chapterRes);
  const paginas = chapterJson.cap_paginas || chapterJson.pages || [];
  
  console.log(`\nTotal de paginas no capitulo: ${paginas.length}`);
  
  console.log('\n--- Primeiras 5 paginas da API (VIP) ---');
  paginas.slice(0, 5).forEach((p, i) => {
    console.log(`[Index ${i}] object: ${JSON.stringify(p)}`);
  });

  console.log('\n--- Ultimas 5 paginas da API (VIP) ---');
  paginas.slice(-5).forEach((p, i) => {
    console.log(`[Index ${paginas.length - 5 + i}] src: ${p.src || p.imageUrl}`);
  });
  
} catch (e) {
  console.error("[!] Ocorreu um erro:", e.message);
}
