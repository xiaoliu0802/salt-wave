/*
    Salt Wave - Navidrome 音乐播放器 (Android 壳)

    关键实现：
    1. WebView 允许混合内容 + 关闭 file 域的同源限制，让播放器直接请求 Navidrome API（绕过 CORS）
    2. 硬件返回键转发给网页 JS（先关面板，再回上一页，最后退出）
    3. 沉浸式状态栏，适配深色主题
*/

package com.saltwave.player;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.apache.cordova.*;

public class MainActivity extends CordovaActivity
{
    private WebView appWebView;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.getBoolean("cdvStartInBackground", false)) {
            moveTaskToBack(true);
        }

        // 深色状态栏 / 导航栏
        getWindow().setStatusBarColor(Color.parseColor("#0f0f1e"));
        getWindow().setNavigationBarColor(Color.parseColor("#0f0f1e"));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        loadUrl(launchUrl);

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
