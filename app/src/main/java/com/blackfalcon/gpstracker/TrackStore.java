package com.blackfalcon.gpstracker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;

/** Saved tracks live as plain files: one .csv with the points and one .meta with the summary. */
public final class TrackStore {

    public static final class Meta {
        public String id = "", name = "";
        public long start, dur, moving;
        public double dist, asc, desc, minAlt = Double.NaN, maxAlt = Double.NaN, maxSpd;
        public int n;
    }

    private final File dir;
    private final File cur, curMeta;

    public TrackStore(File dir) {
        this.dir = dir;
        dir.mkdirs();
        cur = new File(dir, "current.csv");
        curMeta = new File(dir, "current.meta");
    }

    private File csv(String id) { return new File(dir, id + ".csv"); }
    private File meta(String id) { return new File(dir, id + ".meta"); }

    // ---------------------------------------------------------------- the track being recorded
    public synchronized void beginCurrent(String name, long start) {
        cur.delete();
        Properties p = new Properties();
        p.setProperty("name", name);
        p.setProperty("start", String.valueOf(start));
        writeProps(curMeta, p);
    }

    public synchronized boolean hasCurrent() { return cur.exists() && cur.length() > 0; }

    public synchronized void appendPoint(Pt p) {
        String line = String.format(Locale.US, "%d,%.7f,%.7f,%s,%.1f,%.2f,%d\n", p.t, p.lat, p.lon,
                Double.isNaN(p.alt) ? "" : String.format(Locale.US, "%.1f", p.alt), p.acc, p.spd, p.seg);
        FileOutputStream fo = null;
        try {
            fo = new FileOutputStream(cur, true);
            fo.write(line.getBytes("UTF-8"));
        } catch (IOException e) {
            /* a lost point is better than a crash */
        } finally {
            try { if (fo != null) fo.close(); } catch (IOException e) { }
        }
    }

    /** Turns current.csv into a saved track. Returns its id, or null if there was too little to keep. */
    public synchronized String finalizeCurrent(String fallbackName) {
        List<Pt> pts = readCsv(cur);
        Properties p = readProps(curMeta);
        if (pts.size() < 2) {
            cur.delete();
            curMeta.delete();
            return null;
        }
        long start = pts.get(0).t;
        String id = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(start));
        File target = csv(id);
        int k = 1;
        while (target.exists()) target = csv(id + "_" + (k++));
        String base = target.getName().substring(0, target.getName().length() - 4);
        Meta m = summarize(base, p.getProperty("name", ""), pts);
        if (m.name.trim().length() == 0) m.name = fallbackName;
        if (!cur.renameTo(target)) { cur.delete(); curMeta.delete(); return null; }
        saveMeta(m);
        curMeta.delete();
        return base;
    }

    public static Meta summarize(String id, String name, List<Pt> pts) {
        Acc a = new Acc();
        for (Pt p : pts) a.add(p);
        Meta m = new Meta();
        m.id = id;
        m.name = name;
        m.start = a.firstT;
        m.dur = a.durMs;
        m.moving = a.movingMs;
        m.dist = a.dist;
        m.asc = a.ascent;
        m.desc = a.descent;
        m.minAlt = a.minAlt;
        m.maxAlt = a.maxAlt;
        m.maxSpd = a.maxSpeed;
        m.n = a.n;
        return m;
    }

    // ---------------------------------------------------------------- saved tracks
    public synchronized void saveMeta(Meta m) {
        Properties p = new Properties();
        p.setProperty("name", m.name);
        p.setProperty("start", String.valueOf(m.start));
        p.setProperty("dur", String.valueOf(m.dur));
        p.setProperty("moving", String.valueOf(m.moving));
        p.setProperty("dist", String.valueOf(m.dist));
        p.setProperty("asc", String.valueOf(m.asc));
        p.setProperty("desc", String.valueOf(m.desc));
        p.setProperty("minAlt", String.valueOf(m.minAlt));
        p.setProperty("maxAlt", String.valueOf(m.maxAlt));
        p.setProperty("maxSpd", String.valueOf(m.maxSpd));
        p.setProperty("n", String.valueOf(m.n));
        writeProps(meta(m.id), p);
    }

    private static double d(Properties p, String k) {
        try { return Double.parseDouble(p.getProperty(k, "NaN")); } catch (NumberFormatException e) { return Double.NaN; }
    }

    private static long l(Properties p, String k) {
        try { return Long.parseLong(p.getProperty(k, "0")); } catch (NumberFormatException e) { return 0; }
    }

    public synchronized List<Meta> list() {
        List<Meta> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            String n = f.getName();
            if (!n.endsWith(".meta") || n.equals("current.meta")) continue;
            Properties p = readProps(f);
            Meta m = new Meta();
            m.id = n.substring(0, n.length() - 5);
            m.name = p.getProperty("name", m.id);
            m.start = l(p, "start");
            m.dur = l(p, "dur");
            m.moving = l(p, "moving");
            m.dist = d(p, "dist");
            m.asc = d(p, "asc");
            m.desc = d(p, "desc");
            m.minAlt = d(p, "minAlt");
            m.maxAlt = d(p, "maxAlt");
            m.maxSpd = d(p, "maxSpd");
            m.n = (int) l(p, "n");
            if (csv(m.id).exists()) out.add(m);
        }
        Collections.sort(out, new Comparator<Meta>() {
            @Override public int compare(Meta a, Meta b) { return Long.compare(b.start, a.start); }
        });
        return out;
    }

    public synchronized List<Pt> load(String id) { return readCsv(csv(id)); }

    public synchronized void delete(String id) {
        csv(id).delete();
        meta(id).delete();
    }

    public synchronized void rename(String id, String name) {
        Properties p = readProps(meta(id));
        p.setProperty("name", name);
        writeProps(meta(id), p);
    }

    // ---------------------------------------------------------------- files
    private static List<Pt> readCsv(File f) {
        List<Pt> pts = new ArrayList<>();
        if (!f.exists()) return pts;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.length() == 0 || line.charAt(0) == '#') continue;
                String[] s = line.split(",", -1);
                if (s.length < 7) continue;
                try {
                    pts.add(new Pt(Long.parseLong(s[0]), Double.parseDouble(s[1]), Double.parseDouble(s[2]),
                            s[3].length() == 0 ? Double.NaN : Double.parseDouble(s[3]),
                            Float.parseFloat(s[4]), Float.parseFloat(s[5]), Integer.parseInt(s[6].trim())));
                } catch (NumberFormatException e) { /* a half-written last line */ }
            }
        } catch (IOException e) {
            /* return what we have */
        } finally {
            try { if (br != null) br.close(); } catch (IOException e) { }
        }
        return pts;
    }

    private static Properties readProps(File f) {
        Properties p = new Properties();
        if (!f.exists()) return p;
        Reader r = null;
        try {
            r = new InputStreamReader(new FileInputStream(f), "UTF-8");
            p.load(r);
        } catch (IOException e) {
            /* empty */
        } finally {
            try { if (r != null) r.close(); } catch (IOException e) { }
        }
        return p;
    }

    private static void writeProps(File f, Properties p) {
        Writer w = null;
        try {
            w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            p.store(w, null);
        } catch (IOException e) {
            /* ignore */
        } finally {
            try { if (w != null) w.close(); } catch (IOException e) { }
        }
    }

    // ---------------------------------------------------------------- GPX export
    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static void writeGpx(String name, List<Pt> pts, OutputStream os) throws IOException {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        StringBuilder sb = new StringBuilder(Math.max(1024, pts.size() * 110));
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"GPS Tracker by Black Falcon\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        sb.append("<metadata><name>").append(esc(name)).append("</name>");
        if (!pts.isEmpty()) sb.append("<time>").append(iso.format(new Date(pts.get(0).t))).append("</time>");
        sb.append("</metadata>\n<trk><name>").append(esc(name)).append("</name>\n");
        int seg = Integer.MIN_VALUE;
        for (Pt p : pts) {
            if (p.seg != seg) {
                if (seg != Integer.MIN_VALUE) sb.append("</trkseg>\n");
                sb.append("<trkseg>\n");
                seg = p.seg;
            }
            sb.append(String.format(Locale.US, "<trkpt lat=\"%.7f\" lon=\"%.7f\">", p.lat, p.lon));
            if (!Double.isNaN(p.alt)) sb.append(String.format(Locale.US, "<ele>%.1f</ele>", p.alt));
            sb.append("<time>").append(iso.format(new Date(p.t))).append("</time></trkpt>\n");
        }
        if (seg != Integer.MIN_VALUE) sb.append("</trkseg>\n");
        sb.append("</trk>\n</gpx>\n");
        os.write(sb.toString().getBytes("UTF-8"));
        os.flush();
    }
}
