const fs = require('fs');
const { execSync } = require('child_process');

console.log("Baixando bundle JavaScript principal...");
const jsUrl = "https://verdinha.wtf/assets/index-BJeZP5k_.js";
const cmd = `curl.exe -k -s "${jsUrl}"`;
let jsCode = "";
try {
  jsCode = execSync(cmd, { maxBuffer: 30 * 1024 * 1024 }).toString('utf-8');
  fs.writeFileSync('bundle.js', jsCode);
  console.log("Bundle salvo (tamanho:", jsCode.length, "bytes).");
} catch (e) {
  console.error("Falha ao baixar o bundle:", e);
  process.exit(1);
}

console.log("\nProcurando por rotas de API e strings de POST de login...");

// Procura por rotas contendo /login, /auth, /usuarios, etc.
const regexes = [
  /\/auth\/[a-zA-Z0-9_-]+/g,
  /\/usuarios\/[a-zA-Z0-9_-]+/g,
  /post\(['"`][^'"`]*login[^'"`]*['"`]/gi,
  /post\(['"`][^'"`]*auth[^'"`]*['"`]/gi
];

regexes.forEach((regex, idx) => {
  const matches = jsCode.match(regex);
  if (matches) {
    console.log(`\nResultados da Expressao #${idx + 1} (${regex}):`);
    const unique = [...new Set(matches)];
    unique.slice(0, 15).forEach(m => console.log(" - Found:", m));
  }
});

// Procura por trechos onde email e password sao enviados
const keywords = ["email", "password", "senha", "nick", "usuario", "username"];
keywords.forEach(kw => {
  const index = jsCode.indexOf(kw);
  if (index !== -1) {
    console.log(`\nTrecho contendo a palavra-chave "${kw}":`);
    console.log(jsCode.substring(Math.max(0, index - 200), Math.min(jsCode.length, index + 300)));
  }
});
