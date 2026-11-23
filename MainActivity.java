package com.example.lifenotes;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestStoragePermissions();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new NotesBridge(), "AndroidNotes");
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        101
                );
            }
        }
    }

    private File getNotesFolder() {
        File base = Environment.getExternalStorageDirectory(); // /storage/emulated/0
        File dir = new File(base, "LifeNotes");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public class NotesBridge {

        @JavascriptInterface
        public boolean saveNote(String fileName, String content) {
            try {
                File dir = getNotesFolder();
                File file = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(file, false)) {
                    fos.write(content.getBytes(StandardCharsets.UTF_8));
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String readNote(String fileName) {
            try {
                File file = new File(getNotesFolder(), fileName);
                if (!file.exists()) return "";
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] data = new byte[(int) file.length()];
                    int read = fis.read(data);
                    return new String(data, 0, Math.max(0, read), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean deleteNote(String fileName) {
            File file = new File(getNotesFolder(), fileName);
            return file.exists() && file.delete();
        }

        @JavascriptInterface
        public String listNotes() {
            JSONArray arr = new JSONArray();
            try {
                File[] files = getNotesFolder().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".txt"));
                if (files != null) {
                    Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
                    for (File f : files) {
                        JSONObject obj = new JSONObject();
                        obj.put("fileName", f.getName());
                        obj.put("content", readNote(f.getName()));
                        obj.put("updatedAt", sdf.format(new Date(f.lastModified())));
                        arr.put(obj);
                    }
                }
            } catch (Exception ignored) {}
            return arr.toString();
        }
    }
}
