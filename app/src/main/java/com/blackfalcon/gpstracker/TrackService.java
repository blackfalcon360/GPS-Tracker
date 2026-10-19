package com.blackfalcon.gpstracker;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.File;

/**
 * Keeps recording while the screen is off or the app is closed. It runs as a "foreground service", which is why Android
 * shows a small ongoing notification during a recording.
 */
public class TrackService extends Service implements LocationListener {
    public static final String ACT_START = "start", ACT_PAUSE = "pause", ACT_RESUME = "resume", ACT_STOP = "stop";
    private static final String CHANNEL = "tracking";
    private static final int NOTE_ID = 11;
    private static final long[] INTERVALS = {1000, 3000, 10000};
    private static final float[] ACCURACY = {20f, 40f, 100f};

    private LocationManager lm;
    private PowerManager.WakeLock wl;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable notifier = new Runnable() {
        @Override public void run() {
            if (Recorder.I.recording) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTE_ID, buildNote());
                handler.postDelayed(this, 5000);
            }
        }
    };

    public static void send(Context c, String action, String name) {
        Intent i = new Intent(c, TrackService.class);
        i.setAction(action);
        if (name != null) i.putExtra("name", name);
        if (ACT_START.equals(action) && Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        Recorder.I.init(new File(getFilesDir(), "tracks"));
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        SharedPreferences prefs = getSharedPreferences("gpstracker", MODE_PRIVATE);

        if (ACT_START.equals(a)) {
            startFg();                            // Android requires this right after startForegroundService()
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
            Recorder.I.maxAcc = ACCURACY[Math.max(0, Math.min(2, prefs.getInt("acc", 1)))];
            if (!Recorder.I.recording) {
                String nm = intent.getStringExtra("name");
                Recorder.I.begin(nm == null ? "" : nm, System.currentTimeMillis());
            }
            update();
            requestUpdates(prefs);
            acquireWakeLock();
            handler.removeCallbacks(notifier);
            handler.postDelayed(notifier, 5000);
        } else if (ACT_PAUSE.equals(a)) {
            Recorder.I.pause(System.currentTimeMillis());
            removeUpdates();
            update();
        } else if (ACT_RESUME.equals(a)) {
            Recorder.I.resume(System.currentTimeMillis());
            requestUpdates(prefs);
            update();
        } else if (ACT_STOP.equals(a)) {
            removeUpdates();
            handler.removeCallbacks(notifier);
            Recorder.I.finish(getString(R.string.app_name));
            releaseWakeLock();
            stopForeground(true);
            stopSelf();
        } else if (!Recorder.I.recording) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        removeUpdates();
        releaseWakeLock();
        handler.removeCallbacks(notifier);
        super.onDestroy();
    }

    // ------------------------------------------------------------- location
    private void requestUpdates(SharedPreferences prefs) {
        long interval = INTERVALS[Math.max(0, Math.min(2, prefs.getInt("interval", 0)))];
        try {
            lm.removeUpdates(this);
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval, 0f, this, Looper.getMainLooper());
            }
        } catch (SecurityException | IllegalArgumentException e) {
            /* the screen shows "GPS is off" */
        }
    }

    private void removeUpdates() {
        try { if (lm != null) lm.removeUpdates(this); } catch (Exception e) { }
    }

    @TargetApi(34)
    private static double mslAltitude(Location l) {
        return l.hasMslAltitude() ? l.getMslAltitudeMeters() : l.getAltitude();
    }

    @Override
    public void onLocationChanged(Location l) {
        double alt = Double.NaN;
        if (l.hasAltitude()) alt = Build.VERSION.SDK_INT >= 34 ? mslAltitude(l) : l.getAltitude();
        int sats = l.getExtras() != null ? l.getExtras().getInt("satellites", -1) : -1;
        Recorder.I.onFix(l.getTime() > 0 ? l.getTime() : System.currentTimeMillis(), l.getLatitude(), l.getLongitude(), alt,
                l.hasAccuracy() ? l.getAccuracy() : 99f, l.hasSpeed() ? l.getSpeed() : -1f, sats);
    }

    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    // ------------------------------------------------------------- notification + wake lock
    private void startFg() {
        if (Build.VERSION.SDK_INT >= 26) createChannel();
        Notification n = buildNote();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(NOTE_ID, n);
    }

    @TargetApi(26)
    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Track recording", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification buildNote() {
        Fmt f = new Fmt();
        SharedPreferences prefs = getSharedPreferences("gpstracker", MODE_PRIVATE);
        f.lang = prefs.getInt("lang", Lang.UR);
        f.imperial = prefs.getBoolean("imperial", false);
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        String title = Recorder.I.paused ? f.t("notif_paused") : f.t("notif_title");
        String text = f.dist(Recorder.I.dist()) + "  ·  " + f.dur(Recorder.I.elapsed(System.currentTimeMillis()));
        b.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi);
        return b.build();
    }

    private void update() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTE_ID, buildNote());
    }

    private void acquireWakeLock() {
        if (wl != null && wl.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GpsTracker:recording");
        wl.setReferenceCounted(false);
        wl.acquire();
    }

    private void releaseWakeLock() {
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception e) { }
        wl = null;
    }
}
