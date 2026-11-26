# Salt Wave APK Builder (Windows PowerShell)
# 前置要求：JDK 17 + Android SDK + Node.js

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $root

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Salt Wave APK Builder" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Node
$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) {
    Write-Host "[X] 未检测到 Node.js，请先安装：https://nodejs.org/" -ForegroundColor Red
    pause
    exit 1
}

# 2. Java
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
    Write-Host "[X] 未检测到 Java JDK。请按以下步骤安装：" -ForegroundColor Red
    Write-Host "   1. 下载 JDK 17：https://adoptium.net/zh-CN/temurin/releases/?version=17"
    Write-Host "   2. 安装后设置环境变量 JAVA_HOME 指向 JDK 目录"
    Write-Host "   3. 把 %JAVA_HOME%\bin 加到 Path"
    Write-Host "   4. 重新打开命令行再运行此脚本"
    Start-Process "https://adoptium.net/zh-CN/temurin/releases/?version=17"
    Write-Host "按 Enter 退出..."
    Read-Host
    exit 1
}

# 3. Android SDK
if (-not $env:ANDROID_HOME) {
    Write-Host "[X] 未设置 ANDROID_HOME 环境变量。请按以下步骤安装 Android SDK：" -ForegroundColor Red
    Write-Host "   1. 下载 Android Studio：https://developer.android.com/studio"
    Write-Host "   2. 安装完成后打开 SDK Manager，安装以下组件："
    Write-Host "      - Android SDK Build-Tools 34.0.0"
    Write-Host "      - Android SDK Platform-Tools"
    Write-Host "      - Android 14.0 (API 34) SDK Platform"
    Write-Host "   3. 设置环境变量 ANDROID_HOME 指向 SDK 目录，例如："
    Write-Host "      C:\Users\$env:USERNAME\AppData\Local\Android\Sdk"
    Write-Host "   4. 把 %ANDROID_HOME%\platform-tools 和 %ANDROID_HOME%\cmdline-tools\latest\bin 加到 Path"
    Start-Process "https://developer.android.com/studio"
    Write-Host "按 Enter 退出..."
    Read-Host
    exit 1
}

# 4. 同步前端文件
Write-Host "[+] 同步前端文件..." -ForegroundColor Green
New-Item -ItemType Directory -Force -Path "www\libs" | Out-Null
Copy-Item "..\index.html" "www\index.html" -Force
Copy-Item "..\libs\crypto-js.min.js" "www\libs\crypto-js.min.js" -Force

# 5. 安装插件
Write-Host "[+] 安装 Cordova 插件..." -ForegroundColor Green
$cordova = "..\node_modules\.bin\cordova.cmd"
$plugins = @("cordova-plugin-whitelist@^1.3.5", "cordova-plugin-statusbar@^4.0.0", "cordova-plugin-network-information@^3.0.0")
foreach ($plugin in $plugins) {
    $name = $plugin.Split('@')[0]
    if (-not (Test-Path "plugins\$name")) {
        & $cordova plugin add $plugin
    }
}

# 6. 构建
Write-Host "[+] 开始构建 Debug APK..." -ForegroundColor Green
& $cordova build android --debug
if ($LASTEXITCODE -ne 0) {
    Write-Host "[X] 构建失败，请查看上方错误信息。" -ForegroundColor Red
    pause
    exit 1
}

# 7. 复制 APK
Write-Host "[+] 复制 APK..." -ForegroundColor Green
New-Item -ItemType Directory -Force -Path "..\dist" | Out-Null
$apks = Get-ChildItem "platforms\android\app\build\outputs\apk\debug\*.apk"
foreach ($apk in $apks) {
    $dest = "..\dist\SaltWave-debug.apk"
    Copy-Item $apk.FullName $dest -Force
    Write-Host "[OK] 已生成：$dest" -ForegroundColor Green
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " 构建完成！APK 位于：dist\SaltWave-debug.apk" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "按 Enter 退出..."
Read-Host
