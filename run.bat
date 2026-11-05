@echo off
rem Starts the jar if you have Java 17 or newer installed
java -jar "%~dp0lt3dconverter.jar"
if errorlevel 1 pause
