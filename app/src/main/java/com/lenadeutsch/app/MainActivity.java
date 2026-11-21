package com.lenadeutsch.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.content.ClipData;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import androidx.core.content.FileProvider;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deutsch mit Lena: a WebView shell around assets/index.html.
 * The page talks to Android through the "Android" bridge below:
 *  - load/save: progress stored in SharedPreferences (only on this phone)
 *  - request: HTTPS GET/POST to the Anthropic (Claude) API (only api.anthropic.com is allowed)
 *  - speak/stopSpeaking/ttsStatus: German text-to-speech
 *  - listen: German speech recognition through the system voice dialog
 */
public class MainActivity extends Activity {

    private static final int REQ_SPEECH = 4242;
    private static final int REQ_FILES = 4243;
    private static final int REQ_BOOK = 4244;
    private PdfRenderer bookRenderer;
    private ParcelFileDescriptor bookFd;
    private final Object bookLock = new Object();
    private ValueCallback<Uri[]> fileCb;
    private Uri camUri;
    private static final String API_HOST_PREFIX = "https://api.anthropic.com/";

    private WebView web;
    private TextToSpeech tts;
    private volatile String ttsStatus = "loading";
    private SharedPreferences prefs;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile String launchTarget = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("lena", MODE_PRIVATE);
        readLaunch(getIntent());
        ReminderReceiver.schedule(this);

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("image/*");
                pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                Intent chooser = Intent.createChooser(pick, "Fotos de los apuntes");
                try {
                    File dir = new File(getCacheDir(), "photos");
                    dir.mkdirs();
                    File f = new File(dir, "cam_" + System.currentTimeMillis() + ".jpg");
                    camUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                    Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    cam.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                    cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    cam.setClipData(ClipData.newRawUri("", camUri));
                    if (cam.resolveActivity(getPackageManager()) != null)
                        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                    else camUri = null;
                } catch (Exception e) { camUri = null; }
                try {
                    startActivityForResult(chooser, REQ_FILES);
                } catch (Exception e) {
                    fileCb = null; cb.onReceiveValue(null); return false;
                }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme())) return false;
                openExternal(uri);
                return true;
            }

            /* Android 6 (API 23) only calls this older overload. */
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith("file:")) return false;
                if (url != null) openExternal(Uri.parse(url));
                return true;
            }
        });
        checkWebViewVersion(s.getUserAgentString());

        initTts();

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }
    }

    /** Old tablets ship an outdated WebView; the app needs Chrome/WebView 84 or newer. */
    private void checkWebViewVersion(String ua) {
        int v = 0;
        try {
            Matcher m = Pattern.compile("Chrome/(\\d+)").matcher(ua == null ? "" : ua);
            if (m.find()) v = Integer.parseInt(m.group(1));
        } catch (Exception ignored) { }
        if (v == 0 || v >= 84) return;
        try {
            new AlertDialog.Builder(this)
                .setTitle("Actualiza el WebView")
                .setMessage("Este dispositivo tiene un WebView antiguo (versión " + v + ") y la app puede verse mal o no funcionar. "
                    + "Abre Google Play, busca «Android System WebView» (y «Chrome») y pulsa Actualizar.")
                .setPositiveButton("Abrir Play Store", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.webview")));
                    } catch (Exception e) {
                        openExternal(Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview"));
                    }
                })
                .setNegativeButton("Continuar", null)
                .show();
        } catch (Exception ignored) { }
    }

    private void readLaunch(Intent i) {
        String o = i == null ? null : i.getStringExtra("open");
        if (o != null) launchTarget = o;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readLaunch(intent);
        js("window.__launch && window.__launch()");
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) {
                ttsStatus = "error";
                return;
            }
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onDone(String utteranceId) { ttsDone(utteranceId); }
                @Override public void onError(String utteranceId) { ttsDone(utteranceId); }
                @Override public void onError(String utteranceId, int errorCode) { ttsDone(utteranceId); }
                @Override public void onStop(String utteranceId, boolean interrupted) { ttsDone(utteranceId); }
            });
            checkGerman();
        });
    }

    private void checkGerman() {
        if (tts == null) return;
        int r;
        try {
            r = tts.setLanguage(Locale.GERMANY);
        } catch (Exception e) {
            ttsStatus = "error";
            return;
        }
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            ttsStatus = "nolang";
        } else {
            ttsStatus = "ok";
        }
    }

    private void ttsDone(String id) {
        js("window.__ttsDone && window.__ttsDone(" + JSONObject.quote(id == null ? "" : id) + ")");
    }

    private void closeBook() {
        synchronized (bookLock) {
            try { if (bookRenderer != null) bookRenderer.close(); } catch (Exception e) { }
            try { if (bookFd != null) bookFd.close(); } catch (Exception e) { }
            bookRenderer = null; bookFd = null;
        }
    }

    private int openBook() {
        synchronized (bookLock) {
            if (bookRenderer != null) return bookRenderer.getPageCount();
            File f = new File(getFilesDir(), "book.pdf");
            if (!f.exists()) return 0;
            try {
                bookFd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
                bookRenderer = new PdfRenderer(bookFd);
                return bookRenderer.getPageCount();
            } catch (Exception e) {
                closeBook();
                return -1;
            }
        }
    }

    private void js(final String code) {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(code, null);
        });
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) { }
    }

    private void startSpeech(String lang) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        i.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, lang);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich jetzt auf Deutsch …");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            startActivityForResult(i, REQ_SPEECH);
        } catch (ActivityNotFoundException e) {
            js("window.__speechError && window.__speechError('unavailable')");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_BOOK) {
            final Uri u = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
            if (u == null) { js("window.__bookReady && window.__bookReady(0)"); return; }
            pool.execute(() -> {
                try {
                    File f = new File(getFilesDir(), "book.pdf");
                    try (InputStream in = getContentResolver().openInputStream(u); OutputStream out = new FileOutputStream(f)) {
                        byte[] buf = new byte[65536]; int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    closeBook();
                    int pages = openBook();
                    js("window.__bookReady && window.__bookReady(" + pages + ")");
                } catch (Exception e) {
                    js("window.__bookReady && window.__bookReady(-1)");
                }
            });
            return;
        }
        if (requestCode == REQ_FILES) {
            Uri[] out = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    out = new Uri[n];
                    for (int k = 0; k < n; k++) out[k] = data.getClipData().getItemAt(k).getUri();
                } else if (data != null && data.getData() != null) {
                    out = new Uri[]{data.getData()};
                } else if (camUri != null) {
                    out = new Uri[]{camUri};
                }
            }
            if (fileCb != null) { fileCb.onReceiveValue(out); fileCb = null; }
            camUri = null;
            return;
        }
        if (requestCode != REQ_SPEECH) return;
        if (resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && results.get(0) != null) {
                js("window.__speechResult && window.__speechResult(" + JSONObject.quote(results.get(0)) + ")");
                return;
            }
        }
        js("window.__speechError && window.__speechError('cancelled')");
    }

    private void doRequest(String method, String url, String headersJson, String body, String callbackId) {
        int code;
        String text;
        HttpURLConnection conn = null;
        try {
            if (url == null || !url.startsWith(API_HOST_PREFIX)) {
                throw new IllegalArgumentException("URL not allowed");
            }
            boolean isGet = "GET".equalsIgnoreCase(method);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(isGet ? "GET" : "POST");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(!isGet);
            JSONObject headers = new JSONObject(headersJson == null ? "{}" : headersJson);
            Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                conn.setRequestProperty(k, headers.getString(k));
            }
            if (!isGet) {
                byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }
            code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            text = is == null ? "" : readAll(is);
        } catch (Exception e) {
            code = -1;
            text = String.valueOf(e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        js("window.__httpDone && window.__httpDone(" + JSONObject.quote(callbackId) + "," + code + "," + JSONObject.quote(text) + ")");
    }

    private static String readAll(InputStream is) throws Exception {
        try (InputStream in = is; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    /** Methods callable from the page as window.Android.* */
    public class Bridge {
        @JavascriptInterface
        public int bookInfo() { return openBook(); }

        @JavascriptInterface
        public void pickBook() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/pdf");
                try { startActivityForResult(i, REQ_BOOK); }
                catch (Exception e) { js("window.__bookReady && window.__bookReady(-2)"); }
            });
        }

        @JavascriptInterface
        public void removeBook() {
            pool.execute(() -> { closeBook(); new File(getFilesDir(), "book.pdf").delete(); });
        }

        /* Renders page n (0-based) as a JPEG with the given width and sends it back to the page. */
        @JavascriptInterface
        public void bookPage(final int n, final int width, final String id) {
            pool.execute(() -> {
                String b64 = "";
                try {
                    if (openBook() > 0) {
                        synchronized (bookLock) {
                            PdfRenderer.Page pg = bookRenderer.openPage(n);
                            int w = Math.max(300, Math.min(2000, width));
                            int h = Math.round(w * (float) pg.getHeight() / pg.getWidth());
                            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                            bm.eraseColor(Color.WHITE);
                            pg.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                            pg.close();
                            ByteArrayOutputStream bo = new ByteArrayOutputStream();
                            bm.compress(Bitmap.CompressFormat.JPEG, 82, bo);
                            bm.recycle();
                            b64 = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
                        }
                    }
                } catch (Throwable e) { b64 = ""; }
                js("window.__bookPage && window.__bookPage(" + JSONObject.quote(id) + "," + JSONObject.quote(b64) + ")");
            });
        }

        @JavascriptInterface
        public String load(String key) {
            return prefs.getString(key, null);
        }

        @JavascriptInterface
        public void save(String key, String value) {
            prefs.edit().putString(key, value).apply();
        }

        @JavascriptInterface
        public void remove(String key) {
            prefs.edit().remove(key).apply();
        }

        @JavascriptInterface
        public void request(String method, String url, String headersJson, String body, String callbackId) {
            pool.execute(() -> doRequest(method, url, headersJson, body, callbackId));
        }

        /** "" if the reminder was never configured, otherwise "on|hour|minute". */
        @JavascriptInterface
        public String getReminder() {
            if (!prefs.contains("rem_on")) return "";
            return (prefs.getBoolean("rem_on", false) ? "1" : "0") + "|" + prefs.getInt("rem_h", 19) + "|" + prefs.getInt("rem_m", 0);
        }

        @JavascriptInterface
        public void setReminder(boolean on, int hour, int minute) {
            prefs.edit().putBoolean("rem_on", on).putInt("rem_h", Math.max(0, Math.min(23, hour))).putInt("rem_m", Math.max(0, Math.min(59, minute))).apply();
            ReminderReceiver.schedule(MainActivity.this);
            if (on && Build.VERSION.SDK_INT >= 33) {
                runOnUiThread(() -> {
                    try {
                        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 99);
                        }
                    } catch (Exception ignored) { }
                });
            }
        }

        /** Returns "hoy" once when the app was opened from the reminder. */
        @JavascriptInterface
        public String takeLaunch() {
            String t = launchTarget;
            launchTarget = "";
            return t;
        }

        @JavascriptInterface
        public String ttsStatus() {
            if (!"ok".equals(ttsStatus) && !"loading".equals(ttsStatus) && tts != null) {
                checkGerman();
            }
            return ttsStatus;
        }

        @JavascriptInterface
        public void speak(String text, double rate, String id) {
            if (tts == null || !"ok".equals(ttsStatus)) {
                ttsDone(id);
                return;
            }
            Bundle params = new Bundle();
            tts.setSpeechRate((float) Math.max(0.3, Math.min(2.0, rate)));
            int r = tts.speak(text == null ? "" : text, TextToSpeech.QUEUE_FLUSH, params, id);
            if (r != TextToSpeech.SUCCESS) ttsDone(id);
        }

        @JavascriptInterface
        public void stopSpeaking() {
            if (tts != null) tts.stop();
        }

        @JavascriptInterface
        public void openTtsInstall() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    startActivity(i);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                    } catch (Exception ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void listen(String lang) {
            runOnUiThread(() -> startSpeech(lang == null || lang.isEmpty() ? "de-DE" : lang));
        }

        @JavascriptInterface
        public void openUrl(String url) {
            runOnUiThread(() -> openExternal(Uri.parse(url)));
        }
    }

    @Override
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript("(window.__onBack && window.__onBack()) ? 'yes' : 'no'", value -> {
            if (value == null || !value.contains("yes")) finish();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        pool.shutdownNow();
        if (web != null) {
            web.removeJavascriptInterface("Android");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
