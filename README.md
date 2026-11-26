# Salt Wave - Navidrome 音乐播放器

一个连接 Navidrome 服务器的 Web / Android 音乐播放器。

## 功能

- 浏览专辑、艺术家、歌曲
- 在线播放、进度条拖动、上一首 / 下一首
- 歌单管理（创建 / 删除 / 播放）
- 搜索歌曲 / 专辑 / 艺术家
- 内嵌 LRC 歌词同步滚动
- 响应式布局，手机 / 电脑都能用
- Android APK 打包

## 文件结构

```
.
├── index.html              # 播放器主页面（纯前端，可浏览器直接打开）
├── libs/
│   └── crypto-js.min.js    # MD5 token 认证本地库
├── server.js               # 本地 Node.js 代理（浏览器测试 CORS 时备用）
├── apk/                    # Cordova Android 打包工程
│   ├── build-apk.bat       # Windows 一键构建脚本
│   ├── www/                # 打包用的前端文件
│   └── platforms/android/  # Android 原生工程
├── make-icons.py           # 生成 Android 图标脚本
└── README.md
```

## 一、浏览器里直接用

1. 安装 Node.js 依赖：
   ```bash
   npm install --no-save cordova
   ```

2. 启动本地服务器：
   ```bash
   node server.js
   ```

3. 浏览器打开 `http://localhost:8080`，填写 Navidrome 服务器地址、用户名、密码即可。

> 注意：浏览器模式需要你的 Navidrome 服务器开启 CORS，否则请用上面的 Node.js 代理。

## 二、打包成 Android APK

### 方案 A：本机构建（推荐长期用）

#### 前置要求

1. **JDK 17**：从 [Adoptium](https://adoptium.net/zh-CN/temurin/releases/?version=17) 下载安装，并设置 `JAVA_HOME`。
2. **Android SDK**：安装 [Android Studio](https://developer.android.com/studio)，在 SDK Manager 中安装：
   - Android SDK Build-Tools 34.0.0
   - Android SDK Platform-Tools
   - Android 14.0 (API 34) SDK Platform
3. 设置环境变量 `ANDROID_HOME`（例如 `C:\Users\你的用户名\AppData\Local\Android\Sdk`），并把以下路径加入 `Path`：
   - `%ANDROID_HOME%\platform-tools`
   - `%ANDROID_HOME%\cmdline-tools\latest\bin`

#### 一键构建

进入 `apk` 目录，**双击运行**：

```
apk/build-apk.bat
```

构建成功后 APK 位于：

```
dist/SaltWave-debug.apk
```

### 方案 B：GitHub Actions 自动构建（无需本机 Android 环境）

1. 把整个项目 push 到 GitHub 仓库。
2. 打开仓库的 **Actions → Build Salt Wave APK → Run workflow**。
3. 等待约 3-5 分钟，构建完成后在 Artifacts 中下载 `SaltWave-debug-apk`。

## 三、Navidrome 服务器注意事项

- 地址可带 `http://` / `https://` 前缀，不带时会自动补全为 `http://`。
- 如果服务器是 HTTP（局域网常见），需要在 `config.xml` 中开启 `usesCleartextTraffic`（已默认开启）。
- 内嵌歌词通过 OpenSubsonic `getLyricsBySongId` 接口读取，需要 Navidrome 0.53+ 并启用 OpenSubsonic 扩展（默认开启）。

## 四、自定义

### 修改应用名称 / 包名

编辑 `apk/config.xml`：

```xml
<widget id="com.saltwave.player" version="1.0.0">
    <name>Salt Wave</name>
    ...
</widget>
```

### 重新生成图标

```bash
python make-icons.py
```

## 五、许可证

本项目仅作为个人学习和使用，与 Salt Player（椒盐音乐）官方无关。
