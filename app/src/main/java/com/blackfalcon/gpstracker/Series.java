package com.blackfalcon.gpstracker;

import java.util.List;

/** Distance-vs-elevation data for the charts (distance measured on the smoothed path, elevation smoothed). */
public final class Series {
    public final double[] dist;     // metres from the start
    public final double[] alt;      // metres (NaN where the track has no altitude yet)

    private Series(double[] d, double[] a) { dist = d; alt = a; }

    public static Series build(List<Pt> pts, int maxPoints) {
        int n = pts.size();
        if (n == 0) return new Series(new double[0], new double[0]);
        double[] d = new double[n], a = new double[n];
        Acc acc = new Acc();
        for (int i = 0; i < n; i++) {
            acc.add(pts.get(i));
            d[i] = acc.dist;
            a[i] = acc.curAlt;
        }
        if (n <= maxPoints) return new Series(d, a);
        double[] d2 = new double[maxPoints], a2 = new double[maxPoints];
        for (int i = 0; i < maxPoints; i++) {
            int k = (int) Math.round(i * (n - 1) / (double) (maxPoints - 1));
            d2[i] = d[k];
            a2[i] = a[k];
        }
        return new Series(d2, a2);
    }
}
