package com.hatkid.mkxpz.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.hatkid.mkxpz.Games;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Finds and decodes game covers off the UI thread, with a small memory cache. */
public final class CoverLoader
{
    private static final Object NONE = new Object();

    private final ExecutorService mPool = Executors.newFixedThreadPool(2);
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private final LruCache<String, Object> mCache;
    private final int mTargetPx;

    public CoverLoader(int targetPx)
    {
        mTargetPx = targetPx;
        // a small cache: covers are small (RGB_565, sized for the cards)
        int maxKb = (int) Math.min(Runtime.getRuntime().maxMemory() / 1024 / 16, 24 * 1024);
        mCache = new LruCache<String, Object>(maxKb) {
            @Override
            protected int sizeOf(String key, Object value) {
                return value instanceof Bitmap ? ((Bitmap) value).getByteCount() / 1024 : 1;
            }
        };
    }

    /** Shows the cover of {@code gamePath} in {@code view}, or leaves the placeholder. */
    public void load(String gamePath, ImageView view, int placeholderRes)
    {
        load(gamePath, "", view, placeholderRes);
    }

    /** Same, preferring the user's own cover image when there is one. */
    public void load(String gamePath, String customCover, ImageView view, int placeholderRes)
    {
        final String key = key(gamePath, customCover);
        view.setTag(key);
        Object cached = mCache.get(key);
        if (cached instanceof Bitmap) {
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap((Bitmap) cached);
            return;
        }
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImageResource(placeholderRes);
        if (cached == NONE) return;

        mPool.execute(() -> {
            Bitmap bmp = decode(gamePath, customCover);
            mCache.put(key, bmp != null ? bmp : NONE);
            mUi.post(() -> {
                if (bmp != null && key.equals(view.getTag())) {
                    view.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    view.setImageBitmap(bmp);
                }
            });
        });
    }

    private static String key(String gamePath, String customCover)
    {
        return gamePath + "|" + (customCover == null ? "" : customCover);
    }

    /** The cached cover if already decoded (for the second screen). */
    public Bitmap cached(String gamePath, String customCover)
    {
        Object o = mCache.get(key(gamePath, customCover));
        return o instanceof Bitmap ? (Bitmap) o : null;
    }

    private Bitmap decode(String gamePath, String customCover)
    {
        try {
            File cover = customCover != null && !customCover.isEmpty() && new File(customCover).isFile()
                ? new File(customCover) : Games.findCover(new File(gamePath));
            if (cover == null) return null;

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(cover.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= mTargetPx) sample *= 2;

            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            d.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(cover.getAbsolutePath(), d);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Frees the remembered covers (the library is hidden, e.g. while playing). */
    public void trim()
    {
        mCache.evictAll();
    }

    public void shutdown()
    {
        mPool.shutdownNow();
    }
}
