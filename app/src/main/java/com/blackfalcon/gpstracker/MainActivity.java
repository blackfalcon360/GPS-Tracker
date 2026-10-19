package com.blackfalcon.gpstracker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity {

    private static final int GREEN = 0xFF2ECC71, GRAY = 0xFF9E9E9E, CARD = 0xFF0C0C1E, EDGE = 0xFF3A3A60,
            AMBER = 0xFFFFB300, BLUE = 0xFF4FC3F7, RED = 0xFFE57373;
    private static final int REQ_PERM = 1, REQ_EXPORT = 2;

    private SharedPreferences prefs;
    private LocationManager lm;
    private TrackStore store;
    private final Fmt fmt = new Fmt();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private int tab = 0, intervalIdx, accIdx;
    private boolean keepOn, running = false, uiPosted = false, detailOpen = false;
    private long lastChartMs = 0;
    private int lastChartVer = -1;

    // views
    private final TextView[] tabs = new TextView[3];
    private final View[] pages = new View[4];
    private TextView gpsTv, stateTv, clockTv, btnMain, btnStop, noteTv;
    private final TextView[] stat = new TextView[6];
    private EditText nameEt;
    private PathView recPath;
    private ElevView recElev;
    private LinearLayout tracksBox, detailBox;
    private TrackStore.Meta detailMeta, pendingExport;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String t(String k) { return fmt.t(k); }

    // ------------------------------------------------------------ callbacks
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateRecordUi();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable uiRefresh = new Runnable() {
        @Override public void run() {
            uiPosted = false;
            updateRecordUi();
            if (!Recorder.I.recording && tab == 1 && !detailOpen) refreshTracks();
        }
    };

    private final Recorder.Listener recListener = new Recorder.Listener() {
        @Override public void onChange() {
            if (!uiPosted) { uiPosted = true; handler.post(uiRefresh); }
        }
    };

    private final LocationListener previewListener = new LocationListener() {
        @Override public void onLocationChanged(Location l) {
            if (Recorder.I.recording) return;
            double alt = l.hasAltitude() ? (Build.VERSION.SDK_INT >= 34 ? msl(l) : l.getAltitude()) : Double.NaN;
            int sats = l.getExtras() != null ? l.getExtras().getInt("satellites", -1) : -1;
            Recorder.I.onFix(l.getTime() > 0 ? l.getTime() : System.currentTimeMillis(), l.getLatitude(), l.getLongitude(), alt,
                    l.hasAccuracy() ? l.getAccuracy() : 99f, l.hasSpeed() ? l.getSpeed() : -1f, sats);
        }
        @Override public void onProviderEnabled(String p) { }
        @Override public void onProviderDisabled(String p) { }
        @Override public void onStatusChanged(String p, int s, Bundle e) { }
    };

    @android.annotation.TargetApi(34)
    private static double msl(Location l) { return l.hasMslAltitude() ? l.getMslAltitudeMeters() : l.getAltitude(); }

    // ------------------------------------------------------------- lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("gpstracker", MODE_PRIVATE);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Recorder.I.init(new File(getFilesDir(), "tracks"));
        store = Recorder.I.store();

        fmt.lang = prefs.getInt("lang", Lang.UR);
        fmt.imperial = prefs.getBoolean("imperial", false);
        intervalIdx = prefs.getInt("interval", 0);
        accIdx = prefs.getInt("acc", 1);
        keepOn = prefs.getBoolean("keepOn", true);
        tab = prefs.getInt("tab", 0);

        if (!Recorder.I.recording && store.hasCurrent()) {
            String id = store.finalizeCurrent(t("recovered"));
            if (id != null) Toast.makeText(this, t("recovered_toast"), Toast.LENGTH_LONG).show();
        }
        buildUi();
        if (!hasPerm()) askPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        running = true;
        Recorder.I.setListener(recListener);
        handler.post(ticker);
        syncPreview();
        refreshTracks();
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        Recorder.I.setListener(null);
        handler.removeCallbacks(ticker);
        try { lm.removeUpdates(previewListener); } catch (Exception e) { }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (detailOpen) { closeDetail(); return; }
        super.onBackPressed();
    }

    private boolean hasPerm() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void askPermission() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS}, REQ_PERM);
        else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        syncPreview();
        updateRecordUi();
    }

    private boolean gpsEnabled() {
        try { return lm.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception e) { return false; }
    }

    private void syncPreview() {
        try { lm.removeUpdates(previewListener); } catch (Exception e) { }
        if (!running || Recorder.I.recording || !hasPerm()) return;
        try {
            if (gpsEnabled()) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0f, previewListener, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException e) { }
    }

    // ------------------------------------------------------------- helpers
    private GradientDrawable box(int fill, int edge, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), edge);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView tv(String text, int size, int color) {
        TextView x = new TextView(this);
        x.setText(text);
        x.setTextSize(size);
        x.setTextColor(color);
        return x;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float weight) { return new LinearLayout.LayoutParams(w, h, weight); }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(CARD, EDGE, 14));
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        l.bottomMargin = dp(8);
        c.setLayoutParams(l);
        return c;
    }

    private TextView button(String text, int edge) {
        TextView b = tv(text, 16, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(box(0xFF1C1C3A, edge, 12));
        b.setPadding(dp(10), dp(14), dp(10), dp(14));
        return b;
    }

    private LinearLayout.LayoutParams weight(int l, int r) {
        LinearLayout.LayoutParams p = lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(l, 0, r, 0);
        return p;
    }

    private EditText editBox(String hint, String text) {
        EditText e = new EditText(new ContextThemeWrapper(this, android.R.style.Theme_Material_Light));
        e.setSingleLine(true);
        e.setHint(hint);
        e.setText(text);
        e.setTextColor(Color.BLACK);
        e.setHintTextColor(0xFF777777);
        e.setTextSize(16);
        e.setBackground(box(Color.WHITE, 0xFFBBBBBB, 10));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        return e;
    }

    private TextView chip(String text, boolean selected, View.OnClickListener l) {
        TextView c = tv(text, 14, selected ? Color.WHITE : GRAY);
        c.setGravity(Gravity.CENTER);
        c.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        c.setBackground(box(selected ? 0xFF1C1C3A : CARD, selected ? GREEN : EDGE, 10));
        c.setPadding(dp(6), dp(10), dp(6), dp(10));
        c.setOnClickListener(l);
        return c;
    }

    private LinearLayout statCell(String titleKey, int index) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(CARD, EDGE, 12));
        c.setPadding(dp(12), dp(8), dp(12), dp(8));
        c.addView(tv(t(titleKey), 12, GRAY));
        TextView v = tv("--", 20, Color.WHITE);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(v);
        stat[index] = v;
        return c;
    }

    private LinearLayout pair(View a, View b) {
        LinearLayout r = new LinearLayout(this);
        r.setBaselineAligned(false);
        LinearLayout.LayoutParams la = lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f), lb = lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        la.rightMargin = dp(4);
        lb.leftMargin = dp(4);
        r.addView(a, la);
        r.addView(b, lb);
        LinearLayout.LayoutParams rp = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        rp.bottomMargin = dp(8);
        r.setLayoutParams(rp);
        return r;
    }

    // ------------------------------------------------------------- UI
    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setLayoutDirection(fmt.lang == Lang.EN ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(8), dp(6), dp(8), dp(4));

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setPadding(0, 0, 0, dp(8));
        String[] keys = {"tab_rec", "tab_tracks", "tab_set"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView x = tv(t(keys[i]), 14, Color.WHITE);
            x.setGravity(Gravity.CENTER);
            x.setPadding(0, dp(9), 0, dp(9));
            x.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    detailOpen = false;
                    tab = idx;
                    prefs.edit().putInt("tab", tab).apply();
                    showTab();
                    if (tab == 1) refreshTracks();
                }
            });
            tabRow.addView(x, weight(dp(2), dp(2)));
            tabs[i] = x;
        }
        col.addView(tabRow);

        FrameLayout frame = new FrameLayout(this);
        pages[0] = buildRecord();
        pages[1] = buildTracksPage();
        pages[2] = buildSettings();
        ScrollView ds = new ScrollView(this);
        detailBox = new LinearLayout(this);
        detailBox.setOrientation(LinearLayout.VERTICAL);
        detailBox.setPadding(dp(2), dp(2), dp(2), dp(48));
        ds.addView(detailBox);
        pages[3] = ds;
        for (View v : pages) frame.addView(v);
        col.addView(frame, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView credit = tv("By: Black Falcon 🦅", 12, GRAY);
        credit.setPadding(dp(8), dp(3), dp(8), dp(3));
        credit.setBackground(box(0xCC000000, EDGE, 8));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
        cl.setMargins(0, 0, dp(8), dp(6));
        root.addView(credit, cl);

        setContentView(root);
        showTab();
        updateRecordUi();
    }

    private void showTab() {
        for (int i = 0; i < 3; i++) {
            pages[i].setVisibility(!detailOpen && i == tab ? View.VISIBLE : View.GONE);
            tabs[i].setBackground(box(i == tab ? 0xFF1C1C3A : CARD, i == tab ? BLUE : EDGE, 10));
            tabs[i].setTypeface(i == tab ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        pages[3].setVisibility(detailOpen ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------- record page
    private View buildRecord() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(2), dp(2), dp(2), dp(48));
        sv.addView(box);

        LinearLayout status = card();
        gpsTv = tv("", 14, GRAY);
        stateTv = tv("", 14, GRAY);
        stateTv.setTypeface(Typeface.DEFAULT_BOLD);
        status.addView(gpsTv);
        status.addView(stateTv);
        box.addView(status);

        clockTv = tv("0:00:00", 52, Color.WHITE);
        clockTv.setGravity(Gravity.CENTER);
        clockTv.setTypeface(Typeface.DEFAULT_BOLD);
        clockTv.setPadding(0, dp(4), 0, dp(8));
        box.addView(clockTv);

        nameEt = editBox(t("name_hint"), "");
        LinearLayout.LayoutParams nl = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        nl.bottomMargin = dp(8);
        box.addView(nameEt, nl);

        LinearLayout btns = new LinearLayout(this);
        btnMain = button(t("start"), GREEN);
        btnStop = button(t("stop"), RED);
        btnMain.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onMainButton(); } });
        btnStop.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onStopClicked(); } });
        btns.addView(btnMain, weight(dp(2), dp(2)));
        btns.addView(btnStop, weight(dp(2), dp(2)));
        LinearLayout.LayoutParams bl = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0);
        bl.bottomMargin = dp(10);
        box.addView(btns, bl);

        box.addView(pair(statCell("distance", 0), statCell("speed", 1)));
        box.addView(pair(statCell("avg_speed", 2), statCell("elevation", 3)));
        box.addView(pair(statCell("ascent", 4), statCell("descent", 5)));

        LinearLayout rc = card();
        TextView rt = tv(t("route"), 13, GRAY);
        rt.setTypeface(Typeface.DEFAULT_BOLD);
        rc.addView(rt);
        recPath = new PathView(this);
        rc.addView(recPath, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(170), 0));
        box.addView(rc);

        LinearLayout ec = card();
        TextView et = tv(t("elev_hist"), 13, GRAY);
        et.setTypeface(Typeface.DEFAULT_BOLD);
        ec.addView(et);
        recElev = new ElevView(this);
        recElev.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        ec.addView(recElev, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(160), 0));
        box.addView(ec);

        noteTv = tv(t("bg_note"), 12, GRAY);
        noteTv.setPadding(dp(4), dp(2), dp(4), 0);
        box.addView(noteTv);
        return sv;
    }

    private void onMainButton() {
        Recorder r = Recorder.I;
        if (!r.recording) {
            if (!hasPerm()) { askPermission(); return; }
            String nm = nameEt.getText().toString().trim();
            TrackService.send(this, TrackService.ACT_START, nm.length() > 0 ? nm : fmt.autoName(System.currentTimeMillis()));
            nameEt.setText("");
            lastChartVer = -1;
            try { lm.removeUpdates(previewListener); } catch (Exception e) { }
        } else if (r.paused) {
            TrackService.send(this, TrackService.ACT_RESUME, null);
        } else {
            TrackService.send(this, TrackService.ACT_PAUSE, null);
        }
        handler.postDelayed(new Runnable() { @Override public void run() { updateRecordUi(); } }, 300);
    }

    private void onStopClicked() {
        if (!Recorder.I.recording) return;
        String id = Recorder.I.finish(getString(R.string.app_name));
        TrackService.send(this, TrackService.ACT_STOP, null);
        Toast.makeText(this, id != null ? t("saved_toast") : t("short_toast"), Toast.LENGTH_SHORT).show();
        recPath.set(new ArrayList<Pt>(), "");
        recElev.set(new double[0], new double[0], fmt.imperial, fmt.distUnit(), fmt.altUnit(), "");
        syncPreview();
        refreshTracks();
        updateRecordUi();
    }

    private void updateRecordUi() {
        if (gpsTv == null) return;
        Recorder r = Recorder.I;
        long now = System.currentTimeMillis();
        boolean rec = r.recording, paused = r.paused;

        String g;
        int gc;
        if (!hasPerm()) { g = t("need_perm"); gc = RED; }
        else if (!gpsEnabled()) { g = t("gps_off"); gc = RED; }
        else if (r.lastFixMs == 0 || now - r.lastFixMs > 20000) { g = t("gps_wait"); gc = AMBER; }
        else {
            g = t("gps") + " ±" + Math.round(r.lastAcc) + " " + t("m") + (r.sats > 0 ? "  ·  " + r.sats + " " + t("sats") : "");
            gc = r.lastAcc <= 10 ? GREEN : (r.lastAcc <= 25 ? AMBER : RED);
        }
        gpsTv.setText("● " + g);
        gpsTv.setTextColor(gc);
        stateTv.setText(rec ? (paused ? "❚❚ " + t("paused") : "● " + t("recording")) : t("ready"));
        stateTv.setTextColor(rec ? (paused ? AMBER : RED) : GRAY);

        clockTv.setText(fmt.dur(rec ? r.elapsed(now) : 0));
        double alt = rec && !Double.isNaN(r.smoothAlt()) ? r.smoothAlt() : r.lastAlt;
        boolean fresh = r.lastFixMs > 0 && now - r.lastFixMs < 10000;
        stat[0].setText(fmt.dist(rec ? r.dist() : 0));
        stat[1].setText(fresh && !paused ? fmt.speed(Math.max(0, r.lastSpeed)) : "--");
        stat[2].setText(rec && r.movingMs() > 0 ? fmt.speed(r.dist() / (r.movingMs() / 1000.0)) : "--");
        stat[3].setText(fmt.alt(alt));
        stat[4].setText(fmt.alt(rec ? r.ascent() : 0));
        stat[5].setText(fmt.alt(rec ? r.descent() : 0));

        btnMain.setText(!rec ? t("start") : (paused ? t("resume") : t("pause")));
        btnMain.setBackground(box(0xFF1C1C3A, !rec ? GREEN : AMBER, 12));
        btnStop.setVisibility(rec ? View.VISIBLE : View.GONE);
        nameEt.setVisibility(rec ? View.GONE : View.VISIBLE);

        if (keepOn && rec && running) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (rec && tab == 0 && !detailOpen && (r.version() != lastChartVer) && now - lastChartMs > 2000) {
            lastChartMs = now;
            lastChartVer = r.version();
            List<Pt> pts = r.points();
            recPath.set(pts, "");
            Series s = Series.build(pts, 400);
            recElev.set(s.dist, s.alt, fmt.imperial, fmt.distUnit(), fmt.altUnit(), "");
        }
        if (!rec) syncPreviewIfNeeded();
    }

    private boolean previewOn = false;

    private void syncPreviewIfNeeded() {
        if (!previewOn && running && hasPerm() && gpsEnabled()) { previewOn = true; syncPreview(); }
        if (Recorder.I.recording) previewOn = false;
    }

    // ------------------------------------------------------------- tracks page
    private View buildTracksPage() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        tracksBox = new LinearLayout(this);
        tracksBox.setOrientation(LinearLayout.VERTICAL);
        tracksBox.setPadding(dp(2), dp(2), dp(2), dp(48));
        sv.addView(tracksBox);
        return sv;
    }

    private void refreshTracks() {
        if (tracksBox == null) return;
        tracksBox.removeAllViews();
        List<TrackStore.Meta> list = store.list();
        if (list.isEmpty()) {
            LinearLayout c = card();
            c.addView(tv(t("no_tracks"), 15, Color.WHITE));
            tracksBox.addView(c);
            return;
        }
        double td = 0, ta = 0;
        for (TrackStore.Meta m : list) { td += m.dist; ta += Double.isNaN(m.asc) ? 0 : m.asc; }
        LinearLayout sum = card();
        sum.addView(tv(t("total") + ":  " + list.size() + " " + t("tracks_n") + "  ·  " + fmt.dist(td) + "  ·  ↑ " + fmt.alt(ta), 14, BLUE));
        tracksBox.addView(sum);
        for (final TrackStore.Meta m : list) {
            LinearLayout c = card();
            TextView n = tv(m.name, 17, Color.WHITE);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(n);
            c.addView(tv(fmt.date(m.start), 12, GRAY));
            c.addView(tv(fmt.dist(m.dist) + "  ·  " + fmt.dur(m.dur) + "  ·  ↑ " + fmt.alt(m.asc) + "  ↓ " + fmt.alt(m.desc), 14, Color.WHITE));
            c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openDetail(m); } });
            tracksBox.addView(c);
        }
    }

    // ------------------------------------------------------------- track detail
    private void openDetail(final TrackStore.Meta m) {
        detailMeta = m;
        detailOpen = true;
        showTab();
        detailBox.removeAllViews();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = tv("‹  " + t("back"), 16, BLUE);
        back.setPadding(dp(4), dp(8), dp(14), dp(8));
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closeDetail(); } });
        top.addView(back);
        detailBox.addView(top);
        TextView name = tv(m.name, 20, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        detailBox.addView(name);
        TextView date = tv(fmt.date(m.start), 12, GRAY);
        date.setPadding(0, 0, 0, dp(8));
        detailBox.addView(date);

        LinearLayout rc = card();
        TextView rt = tv(t("route"), 13, GRAY);
        rt.setTypeface(Typeface.DEFAULT_BOLD);
        rc.addView(rt);
        final PathView path = new PathView(this);
        path.set(new ArrayList<Pt>(), t("loading"));
        rc.addView(path, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(200), 0));
        detailBox.addView(rc);

        LinearLayout ec = card();
        TextView et = tv(t("elev_hist"), 13, GRAY);
        et.setTypeface(Typeface.DEFAULT_BOLD);
        ec.addView(et);
        final ElevView elev = new ElevView(this);
        elev.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        elev.set(new double[0], new double[0], fmt.imperial, fmt.distUnit(), fmt.altUnit(), t("loading"));
        ec.addView(elev, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(190), 0));
        TextView ec2 = tv(t("min_alt") + " " + fmt.alt(m.minAlt) + "    ·    " + t("max_alt") + " " + fmt.alt(m.maxAlt), 13, Color.WHITE);
        ec2.setPadding(0, dp(4), 0, 0);
        ec.addView(ec2);
        detailBox.addView(ec);

        double avg = m.moving > 0 ? m.dist / (m.moving / 1000.0) : Double.NaN;
        detailBox.addView(pair(plainStat(t("distance"), fmt.dist(m.dist)), plainStat(t("duration"), fmt.dur(m.dur))));
        detailBox.addView(pair(plainStat(t("moving"), fmt.dur(m.moving)), plainStat(t("avg_speed"), fmt.speed(avg))));
        detailBox.addView(pair(plainStat(t("ascent"), fmt.alt(m.asc)), plainStat(t("descent"), fmt.alt(m.desc))));
        detailBox.addView(pair(plainStat(t("max_speed"), fmt.speed(m.maxSpd)), plainStat(t("points"), String.valueOf(m.n))));

        LinearLayout actions = new LinearLayout(this);
        TextView ren = button(t("rename"), BLUE), exp = button(t("export"), GREEN), del = button(t("delete"), RED);
        ren.setTextSize(14);
        exp.setTextSize(14);
        del.setTextSize(14);
        ren.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { renameDialog(m); } });
        exp.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startExport(m); } });
        del.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { deleteDialog(m); } });
        actions.addView(ren, weight(dp(2), dp(2)));
        actions.addView(exp, weight(dp(2), dp(2)));
        actions.addView(del, weight(dp(2), dp(2)));
        detailBox.addView(actions);

        io.execute(new Runnable() {
            @Override public void run() {
                final List<Pt> pts = store.load(m.id);
                final Series s = Series.build(pts, 500);
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (!detailOpen || detailMeta != m) return;
                        path.set(pts, "");
                        elev.set(s.dist, s.alt, fmt.imperial, fmt.distUnit(), fmt.altUnit(), "--");
                    }
                });
            }
        });
    }

    private LinearLayout plainStat(String title, String value) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(CARD, EDGE, 12));
        c.setPadding(dp(12), dp(8), dp(12), dp(8));
        c.addView(tv(title, 12, GRAY));
        TextView v = tv(value, 18, Color.WHITE);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(v);
        return c;
    }

    private void closeDetail() {
        detailOpen = false;
        detailMeta = null;
        showTab();
        refreshTracks();
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(new ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog_Alert));
    }

    private void renameDialog(final TrackStore.Meta m) {
        final EditText e = editBox(t("name_hint"), m.name);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(e);
        dialog().setTitle(t("rename")).setView(wrap)
                .setPositiveButton(t("save"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String nm = e.getText().toString().trim();
                        if (nm.length() == 0) return;
                        store.rename(m.id, nm);
                        for (TrackStore.Meta x : store.list()) if (x.id.equals(m.id)) { openDetail(x); break; }
                    }
                }).setNegativeButton(t("cancel"), null).show();
    }

    private void deleteDialog(final TrackStore.Meta m) {
        dialog().setTitle(t("delete_q")).setMessage(t("delete_msg"))
                .setPositiveButton(t("delete"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        store.delete(m.id);
                        closeDetail();
                    }
                }).setNegativeButton(t("cancel"), null).show();
    }

    private void startExport(TrackStore.Meta m) {
        pendingExport = m;
        String safe = m.name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (safe.length() == 0) safe = "track";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/gpx+xml");
        i.putExtra(Intent.EXTRA_TITLE, safe + ".gpx");
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            Toast.makeText(this, t("export_fail"), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, final Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_EXPORT || res != RESULT_OK || data == null || data.getData() == null || pendingExport == null) return;
        final Uri uri = data.getData();
        final TrackStore.Meta m = pendingExport;
        io.execute(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        TrackStore.writeGpx(m.name, store.load(m.id), os);
                        os.close();
                        ok = true;
                    }
                } catch (Exception e) {
                    ok = false;
                }
                final boolean fok = ok;
                handler.post(new Runnable() {
                    @Override public void run() { Toast.makeText(MainActivity.this, t(fok ? "exported" : "export_fail"), Toast.LENGTH_SHORT).show(); }
                });
            }
        });
    }

    // ------------------------------------------------------------- settings page
    private TextView title(String s) {
        TextView x = tv(s, 14, AMBER);
        x.setTypeface(Typeface.DEFAULT_BOLD);
        x.setPadding(dp(2), dp(10), 0, dp(6));
        return x;
    }

    private void putAndRebuild(String key, int value) {
        prefs.edit().putInt(key, value).apply();
        recreate();
    }

    private View buildSettings() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(2), dp(2), dp(2), dp(48));
        sv.addView(box);

        box.addView(title(t("lang")));
        LinearLayout lr = new LinearLayout(this);
        String[] ln = {"English", "اردو", "العربية"};
        for (int i = 0; i < 3; i++) {
            final int li = i;
            lr.addView(chip(ln[i], fmt.lang == i, new View.OnClickListener() { @Override public void onClick(View v) { putAndRebuild("lang", li); } }), weight(dp(2), dp(2)));
        }
        box.addView(lr);

        box.addView(title(t("units")));
        LinearLayout ur = new LinearLayout(this);
        ur.addView(chip(t("metric"), !fmt.imperial, new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putBoolean("imperial", false).apply();
                recreate();
            }
        }), weight(dp(2), dp(2)));
        ur.addView(chip(t("imperial"), fmt.imperial, new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putBoolean("imperial", true).apply();
                recreate();
            }
        }), weight(dp(2), dp(2)));
        box.addView(ur);

        box.addView(title(t("interval")));
        LinearLayout ir = new LinearLayout(this);
        final String[] ins = {"1s", "3s", "10s"};
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            ir.addView(chip(ins[i], intervalIdx == i, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    intervalIdx = ii;
                    prefs.edit().putInt("interval", ii).apply();
                    rebuildSettingsValues();
                }
            }), weight(dp(2), dp(2)));
        }
        box.addView(ir);

        box.addView(title(t("acc")));
        LinearLayout ar = new LinearLayout(this);
        final String[] acs = {t("acc_high"), t("acc_bal"), t("acc_low")};
        for (int i = 0; i < 3; i++) {
            final int ai = i;
            ar.addView(chip(acs[i], accIdx == i, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    accIdx = ai;
                    prefs.edit().putInt("acc", ai).apply();
                    rebuildSettingsValues();
                }
            }), weight(dp(2), dp(2)));
        }
        box.addView(ar);

        box.addView(title(t("screen")));
        LinearLayout sr = new LinearLayout(this);
        sr.addView(chip(t("screen_off"), !keepOn, new View.OnClickListener() {
            @Override public void onClick(View v) {
                keepOn = false;
                prefs.edit().putBoolean("keepOn", false).apply();
                recreate();
            }
        }), weight(dp(2), dp(2)));
        sr.addView(chip(t("screen_on"), keepOn, new View.OnClickListener() {
            @Override public void onClick(View v) {
                keepOn = true;
                prefs.edit().putBoolean("keepOn", true).apply();
                recreate();
            }
        }), weight(dp(2), dp(2)));
        box.addView(sr);

        return sv;
    }

    private void rebuildSettingsValues() {
        showTab();
    }
}
