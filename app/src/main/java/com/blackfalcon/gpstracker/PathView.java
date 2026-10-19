package com.blackfalcon.gpstracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** The route drawn on its own (no map tiles needed): fitted to the view, start = green, latest = red. */
public class PathView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Pt> pts = new ArrayList<>();
    private String hint = "";

    public PathView(Context c) { super(c); }

    public void set(List<Pt> points, String emptyHint) {
        pts = points;
        hint = emptyHint;
        invalidate();
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), pad = dp(18);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        if (pts.size() < 2) {
            p.setColor(0xFF777777);
            p.setTextSize(dp(13));
            cv.drawText(hint, w / 2f, h / 2f, p);
            return;
        }
        double minLa = 90, maxLa = -90, minLo = 180, maxLo = -180;
        for (Pt q : pts) {
            minLa = Math.min(minLa, q.lat); maxLa = Math.max(maxLa, q.lat);
            minLo = Math.min(minLo, q.lon); maxLo = Math.max(maxLo, q.lon);
        }
        double midLa = (minLa + maxLa) / 2, k = Math.cos(Math.toRadians(midLa));
        double spanX = Math.max(1e-6, (maxLo - minLo) * k), spanY = Math.max(1e-6, maxLa - minLa);
        double scale = Math.min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY);
        double ox = w / 2.0 - (minLo + maxLo) / 2 * k * scale, oy = h / 2.0 + (minLa + maxLa) / 2 * scale;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(3));
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFF4FC3F7);
        Path path = new Path();
        int seg = pts.get(0).seg;
        boolean first = true;
        float lx = 0, ly = 0;
        for (Pt q : pts) {
            float x = (float) (q.lon * k * scale + ox), y = (float) (oy - q.lat * scale);
            if (first || q.seg != seg) { path.moveTo(x, y); seg = q.seg; first = false; } else path.lineTo(x, y);
            lx = x; ly = y;
        }
        cv.drawPath(path, p);

        p.setStyle(Paint.Style.FILL);
        Pt s = pts.get(0);
        p.setColor(0xFF2ECC71);
        cv.drawCircle((float) (s.lon * k * scale + ox), (float) (oy - s.lat * scale), dp(6), p);
        p.setColor(0xFFE53935);
        cv.drawCircle(lx, ly, dp(6), p);
        p.setColor(0xFFFFFFFF);
        cv.drawCircle(lx, ly, dp(2.5f), p);
        // north mark
        p.setColor(0xFF9E9E9E);
        p.setTextSize(dp(12));
        cv.drawText("N \u2191", w - dp(20), dp(16), p);
    }
}
