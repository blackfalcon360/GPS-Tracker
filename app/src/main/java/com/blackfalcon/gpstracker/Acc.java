package com.blackfalcon.gpstracker;

/** Adds up a track's statistics point by point (distance, time, speed, climb). */
public final class Acc {
    public double dist, ascent, descent, minAlt = Double.NaN, maxAlt = Double.NaN, maxSpeed;
    public long movingMs, durMs, firstT = -1, lastT = -1;
    public int n;
    public double curAlt = Double.NaN;      // smoothed altitude of the latest point

    private Pt prev;
    private final double[] win = new double[5];
    private int wn = 0, wi = 0;
    private double ref = Double.NaN;
    private static final double CLIMB_STEP = 3.0;     // metres of real change before it counts as climb

    public static double haversine(double la1, double lo1, double la2, double lo2) {
        double p1 = Math.toRadians(la1), p2 = Math.toRadians(la2);
        double dp = p2 - p1, dl = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * 6371000.0 * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    // position smoothing + dead-band: raw GPS jitters a few metres even when standing still, and adding up every wobble
    // would multiply the distance several times over. The smoothed path is measured only once it has moved clearly.
    private double sLat, sLon, aLat, aLon;
    private boolean haveS = false;
    private static final double TAU = 4.5;           // seconds

    public void add(Pt p) {
        n++;
        if (firstT < 0) firstT = p.t;
        lastT = p.t;
        boolean sameSeg = prev != null && prev.seg == p.seg;
        long dt = sameSeg ? p.t - prev.t : 0;
        if (dt > 0) durMs += dt;
        if (!sameSeg || !haveS) {
            sLat = p.lat; sLon = p.lon; aLat = p.lat; aLon = p.lon;
            haveS = true;
        } else {
            double tau = TAU * Math.max(1.0, p.acc / 8.0);
            double k = 1.0 - Math.exp(-Math.max(dt, 1) / 1000.0 / tau);
            sLat += k * (p.lat - sLat);
            sLon += k * (p.lon - sLon);
            double d = haversine(aLat, aLon, sLat, sLon);
            if (d >= Math.max(5.0, p.acc)) {
                if (dt <= 0 || d / (dt / 1000.0) < 80 || dt > 60000) dist += d;
                aLat = sLat; aLon = sLon;
            }
        }
        // time spent moving and top speed come from the receiver's own (Doppler) speed, which does not jitter
        if (sameSeg && dt > 0) {
            double v = p.spd >= 0 ? p.spd : haversine(prev.lat, prev.lon, p.lat, p.lon) / (dt / 1000.0);
            if (v > 0.5 && v < 80) movingMs += dt;
            if (v < 80) maxSpeed = Math.max(maxSpeed, v);
        }
        if (!Double.isNaN(p.alt)) {
            win[wi] = p.alt;
            wi = (wi + 1) % win.length;
            if (wn < win.length) wn++;
            double s = 0;
            for (int i = 0; i < wn; i++) s += win[i];
            s /= wn;
            curAlt = s;
            minAlt = Double.isNaN(minAlt) ? s : Math.min(minAlt, s);
            maxAlt = Double.isNaN(maxAlt) ? s : Math.max(maxAlt, s);
            if (Double.isNaN(ref)) ref = s;
            else if (s - ref >= CLIMB_STEP) { ascent += s - ref; ref = s; }
            else if (ref - s >= CLIMB_STEP) { descent += ref - s; ref = s; }
        }
        prev = p;
    }
}
