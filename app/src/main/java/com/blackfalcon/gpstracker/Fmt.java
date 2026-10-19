package com.blackfalcon.gpstracker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Numbers to text in the chosen language and units. */
public final class Fmt {
    public int lang = Lang.EN;
    public boolean imperial = false;

    public String t(String k) { return Lang.t(lang, k); }

    public String dist(double m) {
        if (Double.isNaN(m)) return "--";
        if (imperial) {
            double mi = m / 1609.344;
            return mi >= 0.1 ? String.format(Locale.US, "%.2f %s", mi, t("mi")) : String.format(Locale.US, "%d %s", Math.round(m * 3.28084), t("ft"));
        }
        return m >= 1000 ? String.format(Locale.US, "%.2f %s", m / 1000.0, t("km")) : String.format(Locale.US, "%d %s", Math.round(m), t("m"));
    }

    public String speed(double ms) {
        if (Double.isNaN(ms) || ms < 0) return "--";
        double v = imperial ? ms * 2.236936 : ms * 3.6;
        return String.format(Locale.US, "%.1f %s", v, t(imperial ? "mph" : "kmh"));
    }

    public String alt(double m) {
        if (Double.isNaN(m)) return "--";
        return imperial ? String.format(Locale.US, "%d %s", Math.round(m * 3.28084), t("ft")) : String.format(Locale.US, "%d %s", Math.round(m), t("m"));
    }

    public double altValue(double m) { return imperial ? m * 3.28084 : m; }
    public double distValue(double m) { return imperial ? m / 1609.344 : m / 1000.0; }
    public String distUnit() { return t(imperial ? "mi" : "km"); }
    public String altUnit() { return t(imperial ? "ft" : "m"); }

    public String dur(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    public String date(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US).format(new Date(ms));
    }

    public String autoName(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(ms));
    }
}
