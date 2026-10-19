package com.blackfalcon.gpstracker;

import java.util.HashMap;
import java.util.Map;

/** All texts in English, Urdu and Arabic. */
public final class Lang {
    private Lang() { }

    public static final int EN = 0, UR = 1, AR = 2;
    private static final Map<String, String[]> T = new HashMap<>();

    private static void a(String k, String en, String ur, String ar) { T.put(k, new String[]{en, ur, ar}); }

    static {
        a("tab_rec", "Record", "ریکارڈ", "تسجيل");
        a("tab_tracks", "Tracks", "ٹریکس", "المسارات");
        a("tab_set", "Settings", "ترتیبات", "الإعدادات");
        a("name_hint", "Track name (optional)", "ٹریک کا نام (اختیاری)", "اسم المسار (اختياري)");
        a("start", "Start", "شروع کریں", "ابدأ");
        a("pause", "Pause", "وقفہ", "إيقاف مؤقت");
        a("resume", "Resume", "جاری رکھیں", "استئناف");
        a("stop", "Stop & save", "روکیں اور محفوظ کریں", "إيقاف وحفظ");
        a("distance", "Distance", "فاصلہ", "المسافة");
        a("speed", "Speed", "رفتار", "السرعة");
        a("avg_speed", "Avg speed (moving)", "اوسط رفتار (چلتے ہوئے)", "متوسط السرعة (أثناء الحركة)");
        a("max_speed", "Max speed", "زیادہ سے زیادہ رفتار", "أقصى سرعة");
        a("elevation", "Elevation", "بلندی", "الارتفاع");
        a("ascent", "Ascent", "چڑھائی", "الصعود");
        a("descent", "Descent", "اترائی", "الهبوط");
        a("min_alt", "Lowest", "سب سے نیچے", "الأدنى");
        a("max_alt", "Highest", "سب سے اوپر", "الأعلى");
        a("moving", "Moving time", "چلنے کا وقت", "وقت الحركة");
        a("duration", "Duration", "دورانیہ", "المدة");
        a("points", "Points", "پوائنٹس", "النقاط");
        a("elev_hist", "Elevation history", "بلندی کی تاریخ", "سجل الارتفاع");
        a("route", "Route", "راستہ", "المسار");
        a("gps_wait", "Waiting for GPS fix\u2026", "GPS سگنل کا انتظار ہے\u2026", "بانتظار إشارة GPS\u2026");
        a("gps_off", "GPS is off \u2014 turn on Location", "GPS بند ہے \u2014 Location آن کریں", "GPS متوقف \u2014 فعّل الموقع");
        a("gps", "GPS", "GPS", "GPS");
        a("sats", "satellites", "سیٹلائٹ", "أقمار");
        a("need_perm", "Location permission is needed to record tracks", "ٹریک ریکارڈ کرنے کے لیے مقام کی اجازت ضروری ہے", "إذن الموقع مطلوب لتسجيل المسارات");
        a("recording", "Recording", "ریکارڈنگ جاری ہے", "جارٍ التسجيل");
        a("paused", "Paused", "وقفہ", "متوقف مؤقتًا");
        a("ready", "Ready", "تیار", "جاهز");
        a("bg_note", "Recording continues with the screen off or the app in the background.", "اسکرین بند ہو یا ایپ پس منظر میں ہو، ریکارڈنگ جاری رہتی ہے۔", "يستمر التسجيل مع إطفاء الشاشة أو في الخلفية.");
        a("saved_toast", "Track saved", "ٹریک محفوظ ہو گیا", "تم حفظ المسار");
        a("short_toast", "Too short \u2014 nothing saved", "بہت چھوٹا \u2014 کچھ محفوظ نہیں ہوا", "قصير جدًا \u2014 لم يُحفظ شيء");
        a("no_tracks", "No saved tracks yet. Record one from the Record tab.", "ابھی کوئی ٹریک محفوظ نہیں۔ ریکارڈ ٹیب سے ریکارڈ کریں۔", "لا مسارات محفوظة بعد. سجّل واحدًا من تبويب التسجيل.");
        a("total", "Total", "کل", "المجموع");
        a("tracks_n", "tracks", "ٹریکس", "مسارات");
        a("back", "Back", "واپس", "رجوع");
        a("rename", "Rename", "نام بدلیں", "إعادة تسمية");
        a("export", "Export GPX", "GPX برآمد کریں", "تصدير GPX");
        a("delete", "Delete", "حذف کریں", "حذف");
        a("cancel", "Cancel", "منسوخ", "إلغاء");
        a("save", "Save", "محفوظ کریں", "حفظ");
        a("delete_q", "Delete this track?", "کیا یہ ٹریک حذف کریں؟", "حذف هذا المسار؟");
        a("delete_msg", "It will be removed from this phone for good.", "یہ اس فون سے ہمیشہ کے لیے ہٹ جائے گا۔", "سيُزال من هذا الهاتف نهائيًا.");
        a("exported", "GPX file saved", "GPX فائل محفوظ ہو گئی", "تم حفظ ملف GPX");
        a("export_fail", "Could not save the file", "فائل محفوظ نہیں ہو سکی", "تعذّر حفظ الملف");
        a("loading", "Loading\u2026", "لوڈ ہو رہا ہے\u2026", "جارٍ التحميل\u2026");
        a("recovered", "Recovered track", "بازیافت شدہ ٹریک", "مسار مُستعاد");
        a("recovered_toast", "An unfinished recording was recovered and saved", "ادھوری ریکارڈنگ بحال کر کے محفوظ کر لی گئی", "تم استرجاع تسجيل غير مكتمل وحفظه");
        a("lang", "Language", "زبان", "اللغة");
        a("units", "Units", "اکائیاں", "الوحدات");
        a("metric", "Metric (km, m)", "میٹرک (کلومیٹر، میٹر)", "متري (كم، م)");
        a("imperial", "Imperial (mi, ft)", "امپیریل (میل، فٹ)", "إمبراطوري (ميل، قدم)");
        a("interval", "Recording interval", "ریکارڈنگ کا وقفہ", "فاصل التسجيل");
        a("i1", "1 s (best)", "1 سیکنڈ (بہترین)", "1 ث (الأفضل)");
        a("i2", "3 s", "3 سیکنڈ", "3 ث");
        a("i3", "10 s (saves battery)", "10 سیکنڈ (بیٹری بچت)", "10 ث (يوفر البطارية)");
        a("accuracy", "Ignore weak GPS fixes", "کمزور GPS ریڈنگ نظر انداز کریں", "تجاهل قراءات GPS الضعيفة");
        a("a1", "worse than 20 m", "20 میٹر سے خراب", "أسوأ من 20 م");
        a("a2", "worse than 40 m", "40 میٹر سے خراب", "أسوأ من 40 م");
        a("a3", "worse than 100 m", "100 میٹر سے خراب", "أسوأ من 100 م");
        a("keep_on", "Keep screen on while recording", "ریکارڈنگ کے دوران اسکرین چالو رکھیں", "إبقاء الشاشة قيد التشغيل أثناء التسجيل");
        a("on", "On", "آن", "تشغيل");
        a("off", "Off", "آف", "إيقاف");
        a("about", "Distance is measured on a smoothed path, so standing still adds nothing. Elevation is the GPS altitude, smoothed; changes under 3 m are not counted as climb. GPS altitude can be off by several metres, especially under trees or between tall buildings.",
                "فاصلہ ہموار کیے ہوئے راستے پر ناپا جاتا ہے، اس لیے ایک جگہ کھڑے رہنے سے فاصلہ نہیں بڑھتا۔ بلندی GPS کی ہے اور ہموار کی گئی ہے؛ 3 میٹر سے کم تبدیلی چڑھائی میں شمار نہیں ہوتی۔ GPS کی بلندی میں کئی میٹر کا فرق ہو سکتا ہے، خاص طور پر درختوں کے نیچے یا اونچی عمارتوں کے درمیان۔",
                "تُقاس المسافة على مسار مُنعَّم، فالوقوف في مكانه لا يزيدها. الارتفاع هو ارتفاع GPS بعد التنعيم؛ والتغيرات الأقل من 3 أمتار لا تُحسب صعودًا. قد يختلف ارتفاع GPS بعدة أمتار، خصوصًا تحت الأشجار أو بين المباني العالية.");
        a("km", "km", "کلومیٹر", "كم");
        a("m", "m", "میٹر", "م");
        a("mi", "mi", "میل", "ميل");
        a("ft", "ft", "فٹ", "قدم");
        a("kmh", "km/h", "کلومیٹر/گھنٹہ", "كم/س");
        a("mph", "mph", "میل/گھنٹہ", "ميل/س");
        a("notif_title", "Recording track", "ٹریک ریکارڈ ہو رہا ہے", "جارٍ تسجيل المسار");
        a("notif_paused", "Paused", "وقفہ", "متوقف مؤقتًا");
    }

    public static String t(int lang, String key) {
        String[] v = T.get(key);
        return v == null ? key : v[lang];
    }
}
