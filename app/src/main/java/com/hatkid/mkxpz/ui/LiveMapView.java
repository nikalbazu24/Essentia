package com.hatkid.mkxpz.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Draws the game's town map (Pokémon Essentials region map) with a pulsing marker
 * where the player is. Town map squares are 16x16 pixels, like in Essentials.
 * Tap to zoom in on the player.
 */
public final class LiveMapView extends View
{
    private static final int SQUARE = 16;
    private static final float ZOOM = 2.6f;

    private Bitmap mMap;
    private int mSquareX = -1;
    private int mSquareY = -1;
    private String mEmptyText = "";
    private boolean mZoom = false;

    private final Paint mBitmapPaint = new Paint();
    private final Paint mDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mShade = new Paint();
    private final RectF mDst = new RectF();
    private final Rect mSrc = new Rect();

    public LiveMapView(Context c)
    {
        super(c);
        mBitmapPaint.setFilterBitmap(false); // keep the pixel art sharp
        mDot.setColor(Ui.GOLD);
        mOutline.setColor(0xFFFFFFFF);
        mRing.setColor(Ui.GOLD);
        mRing.setStyle(Paint.Style.STROKE);
        mRing.setStrokeWidth(Ui.dp(c, 3));
        mText.setColor(Ui.TEXT_DIM);
        mText.setTextSize(Ui.dp(c, 14));
        mText.setTextAlign(Paint.Align.CENTER);
    }

    public void setMap(Bitmap map)
    {
        mMap = map;
        invalidate();
    }

    public boolean hasMap() { return mMap != null; }

    public boolean isZoom() { return mZoom; }

    public void setZoom(boolean zoom)
    {
        mZoom = zoom;
        invalidate();
    }

    public void setPlayerSquare(int x, int y)
    {
        mSquareX = x;
        mSquareY = y;
        invalidate();
    }

    public void setEmptyText(String text)
    {
        mEmptyText = text;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh)
    {
        super.onSizeChanged(w, h, oldw, oldh);
        mShade.setShader(new LinearGradient(0, h * 0.6f, 0, h, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();

        if (mMap == null) {
            canvas.drawText(mEmptyText, w / 2f, h / 2f, mText);
            return;
        }

        int bw = mMap.getWidth(), bh = mMap.getHeight();
        float fit = Math.min((float) w / bw, (float) h / bh);
        boolean player = mSquareX >= 0 && mSquareY >= 0;
        float scale = mZoom && player ? fit * ZOOM : fit;
        float dw = bw * scale, dh = bh * scale;
        float left, top;
        if (mZoom && player) {
            // Center on the player, without showing past the map's edges
            float px = (mSquareX * SQUARE + SQUARE / 2f) * scale;
            float py = (mSquareY * SQUARE + SQUARE / 2f) * scale;
            left = clamp(w / 2f - px, w - dw, 0);
            top = clamp(h / 2f - py, h - dh, 0);
            if (dw < w) left = (w - dw) / 2f;
            if (dh < h) top = (h - dh) / 2f;
        } else {
            left = (w - dw) / 2f;
            top = (h - dh) / 2f;
        }
        mDst.set(left, top, left + dw, top + dh);
        mSrc.set(0, 0, bw, bh);
        canvas.drawBitmap(mMap, mSrc, mDst, mBitmapPaint);
        canvas.drawRect(0, 0, w, h, mShade);

        if (player) {
            float cx = left + (mSquareX * SQUARE + SQUARE / 2f) * scale;
            float cy = top + (mSquareY * SQUARE + SQUARE / 2f) * scale;
            float base = Math.max(SQUARE * scale * 0.32f, Ui.dp(getContext(), 6));
            float phase = (SystemClock.uptimeMillis() % 1400) / 1400f;
            mRing.setAlpha((int) (255 * (1f - phase)));
            canvas.drawCircle(cx, cy, base + phase * base * 2.4f, mRing);
            canvas.drawCircle(cx, cy, base + Ui.dp(getContext(), 2), mOutline);
            canvas.drawCircle(cx, cy, base, mDot);
            postInvalidateDelayed(40); // keep the marker pulsing
        }
    }

    private static float clamp(float v, float min, float max)
    {
        return v < min ? min : (v > max ? max : v);
    }
}
