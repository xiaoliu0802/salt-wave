package com.saltwave.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** 圆角封面控件：占位底色 + 居中裁切绘制。 */
public class Cover extends View {

    private Bitmap bmp;
    private float radiusDp = 14f;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final RectF dst = new RectF();

    public Cover(Context c) {
        super(c);
        bgPaint.setColor(Color.rgb(228, 232, 240));
        setWillNotDraw(false);
    }

    public void setBitmap(Bitmap b) {
        bmp = b;
        invalidate();
    }

    public void setRadiusDp(float r) {
        radiusDp = r;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float r = radiusDp * getResources().getDisplayMetrics().density;
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clip);
        canvas.drawColor(bgPaint.getColor());
        if (bmp != null && !bmp.isRecycled()) {
            float bw = bmp.getWidth();
            float bh = bmp.getHeight();
            float scale = Math.max(w / bw, h / bh);
            float dw = bw * scale;
            float dh = bh * scale;
            dst.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f);
            canvas.drawBitmap(bmp, null, dst, paint);
        }
        canvas.restore();
    }
}
