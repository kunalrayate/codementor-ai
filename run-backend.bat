@echo off
rem ---- Edit the lines below only if you need them (remove "rem") ----
rem set JAVA_HOME=C:\Users\Dell\AppData\Local\Programs\Eclipse Adoptium\jdk-21.0.12.101-hotspot
rem set PATH=%JAVA_HOME%\bin;%PATH%
rem set DB_PORT=3307
rem set DB_PASSWORD=root
cd /d "%~dp0backend"
mvn spring-boot:run
pause
