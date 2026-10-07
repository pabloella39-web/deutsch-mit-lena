package com.lenadeutsch.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
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

/**
 * Deutsch mit Lena: a WebView shell around assets/index.html.
 * The page talks to Android through the "Android" bridge below:
 *  - load/save: progress stored in SharedPreferences (only on this phone)
 *  - request: HTTPS POST to the Google Gemini API (only generativelanguage.googleapis.com is allowed)
 *  - speak/stopSpeaking/ttsStatus: German text-to-speech
 *  - listen: German speech recognition through the system voice dialog
 */
public class MainActivity extends Activity {

    private static final int REQ_SPEECH = 4242;
    private static final String API_HOST_PREFIX = "https://generativelanguage.googleapis.com/";

    private WebView web;
    private TextToSpeech tts;
    private volatile String ttsStatus = "loading";
    private SharedPreferences prefs;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("lena", MODE_PRIVATE);

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme())) return false;
                openExternal(uri);
                return true;
            }
        });

        initTts();

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }
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

    private void doRequest(String url, String headersJson, String body, String callbackId) {
        int code;
        String text;
        HttpURLConnection conn = null;
        try {
            if (url == null || !url.startsWith(API_HOST_PREFIX)) {
                throw new IllegalArgumentException("URL not allowed");
            }
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);
            JSONObject headers = new JSONObject(headersJson == null ? "{}" : headersJson);
            Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                conn.setRequestProperty(k, headers.getString(k));
            }
            byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
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
        public void request(String url, String headersJson, String body, String callbackId) {
            pool.execute(() -> doRequest(url, headersJson, body, callbackId));
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
