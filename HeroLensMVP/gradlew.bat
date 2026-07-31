@echo off
setlocal
set APP_HOME=%~dp0
set JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set URL=https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar
set EXPECTED=2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046

if not exist "%JAR%" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; New-Item -ItemType Directory -Force -Path (Split-Path '%JAR%') | Out-Null; Invoke-WebRequest -UseBasicParsing '%URL%' -OutFile '%JAR%'; $h=(Get-FileHash '%JAR%' -Algorithm SHA256).Hash.ToLower(); if($h -ne '%EXPECTED%'){Remove-Item '%JAR%' -Force; throw 'Gradle wrapper JAR checksum mismatch.'}"
  if errorlevel 1 exit /b 1
)

java -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
