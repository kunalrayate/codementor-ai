@echo off
rem Starts the backend and the frontend from THIS folder, so both are always the same version.
start "LearnAI backend" cmd /k "%~dp0run-backend.bat"
start "LearnAI frontend" cmd /k "%~dp0run-frontend.bat"
echo Both windows are starting. Open http://localhost:4200 when the frontend says "Local: http://localhost:4200".
