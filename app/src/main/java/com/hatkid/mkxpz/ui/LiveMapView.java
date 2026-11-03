package com.hatkid.mkxpz.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * Draws the game's town map (Pokémon Essentials region map) with a pulsing dot
 * where the player is. Town map squares are 16x16 pixels, like in Essentials.
 */
public final class LiveMapView extends View
{
    private static final int SQUARE = 16;

    private Bitmap mMap;
    private int mSquareX = -1;
    private int mSquareY = -1;
    private String mEmptyText = "";

    private final Paint mBitmapPaint = new Paint();
    private final Paint mDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mDst = new RectF();

    public LiveMapView(Context c)
    {
        super(c);
        mBitmapPaint.setFilterBitmap(false); // keep the pixel art sharp
        mDot.setColor(Ui.GOLD);
        mRing.setColor(Ui.GOLD);
        mRing.setStyle(Paint.Style.STROKE);
        mRing.setStrokeWidth(Ui.dp(c, 2.5f));
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
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();

        if (mMap == null) {
            canvas.drawText(mEmptyText, w / 2f, h / 2f, mText);
            return;
        }

        float scale = Math.min((float) w / mMap.getWidth(), (float) h / mMap.getHeight());
        float dw = mMap.getWidth() * scale, dh = mMap.getHeight() * scale;
        float left = (w - dw) / 2f, top = (h - dh) / 2f;
        mDst.set(left, top, left + dw, top + dh);
        canvas.drawBitmap(mMap, new Rect(0, 0, mMap.getWidth(), mMap.getHeight()), mDst, mBitmapPaint);

        if (mSquareX >= 0 && mSquareY >= 0) {
            float cx = left + (mSquareX * SQUARE + SQUARE / 2f) * scale;
            float cy = top + (mSquareY * SQUARE + SQUARE / 2f) * scale;
            float base = Math.max(SQUARE * scale * 0.35f, Ui.dp(getContext(), 5));
            float phase = (SystemClock.uptimeMillis() % 1200) / 1200f;
            mRing.setAlpha((int) (255 * (1f - phase)));
            canvas.drawCircle(cx, cy, base + phase * base * 2.2f, mRing);
            canvas.drawCircle(cx, cy, base, mDot);
            postInvalidateDelayed(40); // keep the dot pulsing
        }
    }
}
