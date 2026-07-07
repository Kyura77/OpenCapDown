@echo off
echo =========================================
echo Executando Testes Unitarios Kotlin/Java
echo =========================================
cd ..
call .\gradlew.bat :core:test --continue
cd scripts

echo =========================================
echo Executando Testes de Integracao de Fontes (Node.js)
echo =========================================
node test-all-sources.js
pause
