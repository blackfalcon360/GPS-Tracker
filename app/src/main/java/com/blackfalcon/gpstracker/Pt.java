package com.blackfalcon.gpstracker;

/** One recorded GPS point. */
public final class Pt {
    public final long t;          // epoch ms (UTC)
    public final double lat, lon;
    public final double alt;      // metres above sea level, NaN if the fix had none
    public final float acc;       // horizontal accuracy, metres
    public final float spd;       // m/s reported by the receiver
    public final int seg;         // segment number (a pause starts a new one)

    public Pt(long t, double lat, double lon, double alt, float acc, float spd, int seg) {
        this.t = t; this.lat = lat; this.lon = lon; this.alt = alt; this.acc = acc; this.spd = spd; this.seg = seg;
    }
}
