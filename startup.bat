@echo off
echo =========================================
echo       Starting OddsEngine
echo =========================================

echo Starting OddsEngine Platform (Spring Boot)...
start "OddsEngine Backend" cmd /c "cd platform && .\gradlew bootRun"

echo Starting OddsEngine Frontend (Next.js)...
start "OddsEngine Frontend" cmd /c "cd frontend && npm run dev"

echo.
echo Components are starting up in separate windows!
echo - Backend API will be available at http://localhost:8080
echo - Frontend UI will be available at http://localhost:3000
echo.
pause
