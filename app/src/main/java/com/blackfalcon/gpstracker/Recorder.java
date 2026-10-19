package com.blackfalcon.gpstracker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The recording in progress. It lives in memory (shared by the background service and the screen) and writes every
 * accepted point to disk straight away, so a crash or a killed app never loses the track.
 */
public final class Recorder {
    public static final Recorder I = new Recorder();

    public interface Listener { void onChange(); }

    private TrackStore store;
    private Listener listener;

    public volatile boolean recording = false, paused = false;
    public volatile String name = "";
    public volatile long startMs = 0;

    // settings (set by the service from the saved preferences)
    public volatile float maxAcc = 40f;
    public volatile int minDistM = 3;
    public volatile long minGapMs = 15000;

    // live receiver info
    public volatile float lastAcc = -1f;
    public volatile int sats = -1;
    public volatile long lastFixMs = 0;
    public volatile double lastSpeed = 0;
    public volatile double lastAlt = Double.NaN;

    private final List<Pt> pts = new ArrayList<>();
    private Acc acc = new Acc();
    private int seg = 0;
    private Pt lastStored;
    private long pausedAt = 0, pausedTotal = 0;
    private int version = 0;

    public synchronized void init(File dir) {
        if (store == null) store = new TrackStore(dir);
    }

    public synchronized TrackStore store() { return store; }

    public synchronized void setListener(Listener l) { listener = l; }

    private void changed() {
        Listener l = listener;
        if (l != null) l.onChange();
    }

    public synchronized void begin(String nm, long now) {
        pts.clear();
        acc = new Acc();
        seg = 0;
        lastStored = null;
        pausedAt = 0;
        pausedTotal = 0;
        version++;
        name = nm == null ? "" : nm;
        startMs = now;
        recording = true;
        paused = false;
        store.beginCurrent(name, now);
        changed();
    }

    public synchronized void pause(long now) {
        if (!recording || paused) return;
        paused = true;
        pausedAt = now;
        changed();
    }

    public synchronized void resume(long now) {
        if (!recording || !paused) return;
        paused = false;
        pausedTotal += now - pausedAt;
        seg++;
        lastStored = null;
        changed();
    }

    /** Saves the track. Returns its id, or null if it was too short to keep. */
    public synchronized String finish(String fallbackName) {
        if (!recording) return null;
        recording = false;
        paused = false;
        String id = store.finalizeCurrent(fallbackName);
        pts.clear();
        version++;
        changed();
        return id;
    }

    public synchronized long elapsed(long now) {
        if (!recording) return 0;
        return Math.max(0, now - startMs - pausedTotal - (paused ? now - pausedAt : 0));
    }

    /** a new fix from the receiver */
    public synchronized void onFix(long t, double lat, double lon, double alt, float accuracy, float speed, int satellites) {
        lastAcc = accuracy;
        sats = satellites;
        lastFixMs = t;
        lastSpeed = speed;
        if (!Double.isNaN(alt)) lastAlt = alt;
        if (recording && !paused && accuracy <= maxAcc) {
            boolean keep = true;
            if (lastStored != null) {
                double d = Acc.haversine(lastStored.lat, lastStored.lon, lat, lon);
                if (d < minDistM && t - lastStored.t < minGapMs) keep = false;
            }
            if (keep) {
                Pt p = new Pt(t, lat, lon, alt, accuracy, speed, seg);
                pts.add(p);
                acc.add(p);
                store.appendPoint(p);
                lastStored = p;
                version++;
            }
        }
        changed();
    }

    // ---- read-outs for the screen
    public synchronized int version() { return version; }
    public synchronized int count() { return pts.size(); }
    public synchronized double dist() { return acc.dist; }
    public synchronized double ascent() { return acc.ascent; }
    public synchronized double descent() { return acc.descent; }
    public synchronized double maxAlt() { return acc.maxAlt; }
    public synchronized double minAlt() { return acc.minAlt; }
    public synchronized double maxSpeed() { return acc.maxSpeed; }
    public synchronized long movingMs() { return acc.movingMs; }
    public synchronized double smoothAlt() { return acc.curAlt; }
    public synchronized List<Pt> points() { return new ArrayList<>(pts); }
}
