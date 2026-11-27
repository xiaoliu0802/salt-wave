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

        // 拿到 Cordova 内部的 WebView，调整设置以绕过 CORS
        appWebView = (WebView) appView.getEngine().getView();

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

        // 供网页切换状态栏明暗（首页浅色时需要深色图标）
        appWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void setTheme(final boolean light) {
                applyShellTheme(light);
            }
        }, "SaltWaveNative");

        loadUrl(launchUrl);
    }

    /** 切换系统栏明暗：light=true 时用浅色底 + 深色图标 */
    public void applyShellTheme(final boolean light)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String barBg = light ? "#f6f4f2" : "#0f0f1e";
                getWindow().setStatusBarColor(Color.parseColor(barBg));
                getWindow().setNavigationBarColor(Color.parseColor(barBg));
                int flag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        ? (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0))
                        : 0;
                View decor = getWindow().getDecorView();
                if (light) {
                    decor.setSystemUiVisibility(flag);
                } else {
                    decor.setSystemUiVisibility(0);
                }
            }
        });
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
