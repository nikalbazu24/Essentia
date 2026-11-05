package com.hatkid.mkxpz;

import android.content.Intent;

import org.json.JSONException;
import org.json.JSONObject;

/** Per-game framing preferences. Zero ratio uses the active display. */
final class WideOptions {
    private static final String RATIO = "panorama.ratio";
    private static final String BATTLES = "panorama.battles";
    private static final String CAMERA = "panorama.camera";
    private static final String SPREAD = "panorama.spread";
    private static final float[] ASPECTS = {0, 16f / 9, 16f / 10, 18f / 9};
    static final WideOptions DEFAULT = new WideOptions(0, false, 0, 55);

    final int ratio;
    final boolean battles;
    final int camera;
    final int spread;

    WideOptions(int ratio, boolean battles, int camera, int spread) {
        this.ratio = ratio >= 0 && ratio < ASPECTS.length ? ratio : 0;
        this.battles = battles;
        this.camera = Math.max(-15, Math.min(15, camera));
        this.spread = Math.max(0, Math.min(55, spread));
    }

    static WideOptions read(JSONObject data) {
        if (data == null) return DEFAULT;
        return new WideOptions(data.optInt("ratio"), data.optBoolean("battles"),
            data.optInt("camera"), data.optInt("spread", DEFAULT.spread));
    }

    JSONObject write() throws JSONException {
        return new JSONObject().put("ratio", ratio).put("battles", battles)
            .put("camera", camera).put("spread", spread);
    }

    void applyTo(Intent intent) {
        intent.putExtra(RATIO, ratio).putExtra(BATTLES, battles)
            .putExtra(CAMERA, camera).putExtra(SPREAD, spread);
    }

    static WideOptions from(Intent intent) {
        if (intent == null) return DEFAULT;
        return new WideOptions(intent.getIntExtra(RATIO, 0), intent.getBooleanExtra(BATTLES, false),
            intent.getIntExtra(CAMERA, 0), intent.getIntExtra(SPREAD, DEFAULT.spread));
    }

    float aspect(float display) {
        return ratio == 0 ? display : ASPECTS[ratio];
    }
}
