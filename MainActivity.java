package com.example.lifenotes;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
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

    private static final int REQ_STORAGE = 101;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new NotesBridge(), "AndroidNotes");
        webView.loadUrl("file:///android_asset/www/index.html");

        requestStorageAccess();
        createFolderIfReady();
    }

    @Override
    protected void onResume() {
        super.onResume();
        createFolderIfReady();
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        REQ_STORAGE
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            createFolderIfReady();
        }
    }

    private File getNotesFolder() {
        return new File(Environment.getExternalStorageDirectory(), "LifeNotes");
    }

    private void createFolderIfReady() {
        File dir = getNotesFolder();
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
    }

    public class NotesBridge {

        @JavascriptInterface
        public boolean saveNote(String fileName, String content) {
            try {
                File dir = getNotesFolder();
                if (!dir.exists() && !dir.mkdirs()) {
                    return false;
                }
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
