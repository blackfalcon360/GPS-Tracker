package com.blackfalcon.gpstracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/** Elevation profile: height against distance. Touch and drag to read any point. */
public class ElevView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double[] dist = new double[0], alt = new double[0];
    private boolean imperial = false;
    private String distUnit = "km", altUnit = "m", hint = "";
    private float cursor = -1f;

    public ElevView(Context c) { super(c); }

    public void set(double[] dist, double[] alt, boolean imperial, String distUnit, String altUnit, String emptyHint) {
        this.dist = dist; this.alt = alt; this.imperial = imperial; this.distUnit = distUnit; this.altUnit = altUnit; this.hint = emptyHint;
        invalidate();
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private double dv(double m) { return imperial ? m / 1609.344 : m / 1000.0; }
    private double av(double m) { return imperial ? m * 3.28084 : m; }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                cursor = e.getX();
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                cursor = e.getX();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cursor = -1f;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float left = dp(8), right = dp(8), top = dp(22), bottom = dp(22);
        float cw = w - left - right, ch = h - top - bottom;
        p.setTextAlign(Paint.Align.CENTER);
        int n = dist.length;
        double mn = Double.NaN, mx = Double.NaN;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(alt[i])) continue;
            mn = Double.isNaN(mn) ? alt[i] : Math.min(mn, alt[i]);
            mx = Double.isNaN(mx) ? alt[i] : Math.max(mx, alt[i]);
        }
        if (n < 2 || Double.isNaN(mn) || dist[n - 1] <= 0) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF777777);
            p.setTextSize(dp(13));
            cv.drawText(hint, w / 2f, h / 2f, p);
            return;
        }
        double span = Math.max(10.0, mx - mn);                       // never zoom into noise: at least 10 m tall
        double lo = mn - (span - (mx - mn)) / 2.0 - span * 0.06, hi = lo + span * 1.12;
        double total = dist[n - 1];

        // grid
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(1));
        p.setColor(0x33FFFFFF);
        for (int g = 0; g <= 2; g++) cv.drawLine(left, top + ch * g / 2f, w - right, top + ch * g / 2f, p);

        Path line = new Path(), fill = new Path();
        boolean started = false;
        float lastX = left;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(alt[i])) continue;
            float x = (float) (left + dist[i] / total * cw), y = (float) (top + (1 - (alt[i] - lo) / (hi - lo)) * ch);
            if (!started) { line.moveTo(x, y); fill.moveTo(x, top + ch); fill.lineTo(x, y); started = true; }
            else { line.lineTo(x, y); fill.lineTo(x, y); }
            lastX = x;
        }
        fill.lineTo(lastX, top + ch);
        fill.close();
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, top, 0, top + ch, 0x884FC3F7, 0x114FC3F7, Shader.TileMode.CLAMP));
        cv.drawPath(fill, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2.5f));
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0xFF4FC3F7);
        cv.drawPath(line, p);

        // labels: highest / lowest, distance ends
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(dp(11));
        p.setColor(0xFFB0BEC5);
        p.setTextAlign(Paint.Align.LEFT);
        cv.drawText(String.format(Locale.US, "%d %s", Math.round(av(mx)), altUnit), left + dp(2), top - dp(6), p);
        cv.drawText(String.format(Locale.US, "%d %s", Math.round(av(mn)), altUnit), left + dp(2), top + ch + dp(14), p);
        p.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(String.format(Locale.US, "%.2f %s", dv(total), distUnit), w - right - dp(2), top + ch + dp(14), p);

        // finger read-out
        if (cursor >= 0) {
            float cx = Math.max(left, Math.min(w - right, cursor));
            double d = (cx - left) / cw * total;
            int k = 0;
            while (k < n - 1 && dist[k] < d) k++;
            while (k > 0 && Double.isNaN(alt[k])) k--;
            if (!Double.isNaN(alt[k])) {
                float y = (float) (top + (1 - (alt[k] - lo) / (hi - lo)) * ch);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1.5f));
                p.setColor(0xFFFFFFFF);
                cv.drawLine(cx, top, cx, top + ch, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, y, dp(5), p);
                String t = String.format(Locale.US, "%.2f %s   %d %s", dv(dist[k]), distUnit, Math.round(av(alt[k])), altUnit);
                p.setTextSize(dp(12));
                p.setTextAlign(Paint.Align.CENTER);
                float tw = p.measureText(t) / 2f + dp(6);
                float tx = Math.max(tw, Math.min(w - tw, cx));
                p.setColor(0xCC000000);
                cv.drawRoundRect(tx - tw, dp(2), tx + tw, dp(20), dp(6), dp(6), p);
                p.setColor(0xFFFFFFFF);
                cv.drawText(t, tx, dp(15), p);
            }
        }
    }
}
