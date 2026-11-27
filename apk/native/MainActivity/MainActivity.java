/*
    Salt Wave - Navidrome 音乐播放器 (Android 壳)

    关键实现：
    1. WebView 允许混合内容 + 关闭 file 域的同源限制，让播放器直接请求 Navidrome API（绕过 CORS）
    2. 硬件返回键转发给网页 JS（先关面板，再回上一页，最后退出）
    3. 状态栏 / 导航栏颜色可由网页切换（首页浅色 + 深色图标）
*/

package com.saltwave.player;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.apache.cordova.*;

public class MainActivity extends CordovaActivity
{
    private static final String TAG = "SaltWave";
    private WebView appWebView;

    /** 启动/运行异常兜底：把堆栈直接画到屏幕上，避免静默闪退 */
    private void showCrashScreen(String tag, Throwable t)
    {
        Log.e(TAG, "crash in " + tag, t);
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Salt Wave 启动异常: ").append(tag).append("\n\n");
            sb.append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n\n");
            for (StackTraceElement el : t.getStackTrace()) {
                sb.append(el.toString()).append("\n");
            }
            Throwable cause = t.getCause();
            if (cause != null) {
                sb.append("\nCaused by: ").append(cause.toString()).append("\n");
            }
            TextView tv = new TextView(this);
            tv.setText(sb.toString());
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(11);
            tv.setPadding(24, 24, 24, 24);
            tv.setBackgroundColor(Color.parseColor("#1a0008"));
            ScrollView sv = new ScrollView(this);
            sv.addView(tv);
            setContentView(sv);
        } catch (Throwable ignored) { /* 连兜底界面都画不出来 */ }
    }

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // 全局未捕获异常也记进 Logcat，方便排查
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                Log.e(TAG, "uncaught on " + thread.getName(), ex);
                try {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showCrashScreen("uncaught/" + thread.getName(), ex); }
                    });
                } catch (Throwable ignored) {}
                try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                System.exit(2);
            }
        });

        try {
            initShell();
        } catch (Throwable t) {
            showCrashScreen("onCreate", t);
        }
    }

    private void initShell()
    {
        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.getBoolean("cdvStartInBackground", false)) {
            moveTaskToBack(true);
        }

        // 深色状态栏 / 导航栏（首页为浅色时由网页切换）
        getWindow().setStatusBarColor(Color.parseColor("#0f0f1e"));
        getWindow().setNavigationBarColor(Color.parseColor("#0f0f1e"));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Cordova 15 依赖 loadUrl() 里的惰性 init()，此时 appView 可能还没创建。
        // 先确保 WebView 已经初始化（init() 由 loadUrl 内部复用，不会重复创建）。
        if (appView == null) {
            init();
        }
        if (appView == null) {
            throw new IllegalStateException("Cordova WebView 未初始化（appView 仍为空）");
        }

        // 拿到 Cordova 内部的 WebView，调整设置以绕过 CORS
        try {
            appWebView = (WebView) appView.getEngine().getView();
        } catch (Throwable ignore) { /* 引擎取法不同时走下面的兜底 */ }
        if (appWebView == null) {
            View v = appView.getView();
            if (v instanceof WebView) appWebView = (WebView) v;
        }
        if (appWebView == null) {
            throw new IllegalStateException("取不到 Cordova WebView 实例");
        }

        WebSettings settings = appWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);   // 允许自动播放
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 允许 https 页面加载 http 资源（局域网 Navidrome 常见）
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // 关键：关闭 file:// 域的同源限制，播放器可直连任意服务器 API
            settings.setAllowUniversalAccessFromFileURLs(true);
            settings.setAllowFileAccessFromFileURLs(true);
        }

        appWebView.setBackgroundColor(Color.parseColor("#0f0f1e"));
        appWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // 让内容一直铺到状态栏下面（首页封面与状态栏融为一体）
        enableEdgeToEdge();

        // 供网页切换状态栏明暗（首页浅色时需要深色图标）
        appWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void setTheme(final boolean light) {
                applyShellTheme(light);
            }
        }, "SaltWaveNative");

        loadUrl(launchUrl);

        // 布局完成后把真实状态栏/导航栏高度同步给网页（CSS 变量 --sbh / --sbn）
        appWebView.postDelayed(new Runnable() {
            @Override public void run() { syncInsets(); }
        }, 300);
    }

    /** 全屏沉浸：WebView 延伸到状态栏/导航栏下方，由网页自己按 insets 留白 */
    private void enableEdgeToEdge()
    {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    /** 把状态栏/导航栏实际高度（dp）写进网页的 CSS 变量 */
    private void syncInsets()
    {
        if (appWebView == null) {
            return;
        }
        try {
            int top = 0;
            int bottom = 0;
            android.view.WindowInsets ins = getWindow().getDecorView().getRootWindowInsets();
            if (ins != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    top = ins.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                    bottom = ins.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
                } else {
                    top = ins.getSystemWindowInsetTop();
                    bottom = ins.getSystemWindowInsetBottom();
                }
            }
            float density = getResources().getDisplayMetrics().density;
            int topDp = Math.round(top / density);
            int bottomDp = Math.round(bottom / density);
            String js = "(function(){var r=document.documentElement;"
                    + "r.style.setProperty('--sbh','" + topDp + "px');"
                    + "r.style.setProperty('--sbn','" + bottomDp + "px');})()";
            appWebView.evaluateJavascript(js, null);
        } catch (Throwable t) {
            Log.w(TAG, "syncInsets failed", t);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (appWebView != null) {
            appWebView.postDelayed(new Runnable() {
                @Override public void run() { syncInsets(); }
            }, 150);
        }
    }

    /** 切换系统栏明暗：light=true 时用浅色底 + 深色图标 */
    public void applyShellTheme(final boolean light)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 系统栏透明（内容本身铺到状态栏下面），窗口底色跟着主题走，
                // 避免状态栏那一条露出与页面不同的底色（浅色页出现灰带）
                int bg = Color.parseColor(light ? "#f6f4f2" : "#121218");
                getWindow().setStatusBarColor(Color.TRANSPARENT);
                getWindow().setNavigationBarColor(Color.TRANSPARENT);
                getWindow().getDecorView().setBackgroundColor(bg);
                if (appWebView != null) appWebView.setBackgroundColor(bg);

                int layoutFlags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ? 0
                        : (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
                getWindow().getDecorView().setSystemUiVisibility(
                        (light ? legacyLightFlags() : 0) | layoutFlags);

                // Android 11+ 上 setSystemUiVisibility 对图标明暗已基本无效，必须走 InsetsController
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        android.view.WindowInsetsController c = getWindow().getInsetsController();
                        if (c != null) {
                            int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                     | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                            int cur = c.getSystemBarsAppearance();
                            int next = light ? (cur | mask) : (cur & ~mask);
                            c.setSystemBarsAppearance(next, mask);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "insets appearance failed", t);
                    }
                }
                syncInsets();
            }
        });
    }

    /** 低版本（API 23~29）用的旧式浅色系统栏 flag */
    private int legacyLightFlags()
    {
        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        return flags;
    }

    /**
     * 硬件返回键：优先交给网页处理（关面板 / 回上一页），网页处理不了才退出
     */
    @Override
    public void onBackPressed()
    {
        if (appWebView != null) {
            appWebView.evaluateJavascript(
                "(function(){ try { if (typeof window.SaltWaveBack === 'function') { return window.SaltWaveBack(); } return 'exit'; } catch(e) { return 'exit'; } })()",
                value -> {
                    // 只有网页明确表示“退无可退”才真正退出，避免一按返回就杀进程
                    if (value == null || !value.contains("handled")) {
                        finish();
                    }
                }
            );
        } else {
            super.onBackPressed();
        }
    }
}
