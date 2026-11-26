@echo off
:: 入口：调用 PowerShell 脚本，避免中文编码问题
powershell -ExecutionPolicy Bypass -File "%~dp0build-apk.ps1"
