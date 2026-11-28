package com.saltwave.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 异步封面加载：内存 LRU + 线程池 + 视图 tag 校验。 */
public class Art {

    private static Context appCtx;
    private static LruCache<String, Bitmap> mem;
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);
    private static final Handler ui = new Handler(Looper.getMainLooper());

    public static void init(Context c) {
        appCtx = c.getApplicationContext();
        if (mem != null) return;
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        mem = new LruCache<String, Bitmap>(maxKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public static void into(final Cover view, final String ref, final float radiusDp) {
        view.setRadiusDp(radiusDp);
        view.setTag(ref);
        if (ref == null || ref.length() == 0) {
            view.setBitmap(null);
            return;
        }
        Bitmap cached = mem.get(ref);
        if (cached != null) {
            view.setBitmap(cached);
            return;
        }
        view.setBitmap(null);
        pool.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bm = null;
                try {
                    bm = fetch(ref);
                } catch (Throwable ignored) {
                }
                if (bm == null) return;
                mem.put(ref, bm);
                final Bitmap fb = bm;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (ref.equals(view.getTag())) view.setBitmap(fb);
                    }
                });
            }
        });
    }

    private static Bitmap fetch(String ref) throws Exception {
        byte[] bytes;
        if (ref.startsWith("asset:")) {
            bytes = readAll(appCtx.getAssets().open("assets/" + ref.substring(6)));
        } else {
            HttpURLConnection c = (HttpURLConnection) new URL(ref).openConnection();
            c.setConnectTimeout(9000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "SaltWave/1.1");
            bytes = readAll(c.getInputStream());
            c.disconnect();
        }
        if (bytes.length == 0) return null;

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int sample = 1;
        int big = Math.max(bounds.outWidth, bounds.outHeight);
        while (big / sample > 900) sample *= 2;

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
    }

    private static byte[] readAll(InputStream is) throws Exception {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            is.close();
        }
    }
}
