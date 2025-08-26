// LogManager.java
package com.mapper.imuslam;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import android.provider.MediaStore;
public class LogManager {
    private static final String TAG = "LogManager";

    private static Process logcatProcess;
    private static BufferedWriter logWriter;
    private static final int REQUEST_CODE_OPEN_LOG_DIR = 42;
    private static final String PREFS_NAME = "log_prefs";
    private static final String KEY_LOG_DIR_URI = "log_dir_uri";
    public static void requestLogFolderAccess(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        activity.startActivityForResult(intent, REQUEST_CODE_OPEN_LOG_DIR);
    }

    /**
     * Call this once in Application.onCreate() or your first Activity.
     * Uses app‑specific external storage:
     *    /Android/data/com.mapper.imuslam/files/Mapper/…
     */

    public static void initialize(Context context) {
        // 1) app‑specific external-files directory
//        File root = new File(context.getExternalFilesDir("media"), "Mapper");
//        if (!root.exists() && !root.mkdirs()) {
//            Log.e(TAG, "Failed to create log directory: " + root.getAbsolutePath());
//            Toast.makeText(context,
//                    "Cannot create log directory. Storage may be unavailable.",
//                    Toast.LENGTH_LONG).show();
//            return;
//        }

        File root;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 and above
            root = new File(Environment.getExternalStorageDirectory(), "Android/media/com.mapper.imuslam/logs");
        } else {
            // For older Android versions
            root = new File(Environment.getExternalStorageDirectory(), "Android/media/com.mapper.imuslam/logs");
        }

        if (!root.exists() && !root.mkdirs()) {
            Log.e(TAG, "Failed to create log directory: " + root.getAbsolutePath());
            Toast.makeText(context, "Cannot create log directory. Storage may be unavailable.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // 2) timestamped file
            String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                    .format(new Date());
            File logFile = new File(root, "Mapper_" + ts + ".txt.gz");
            if (!logFile.exists() && !logFile.createNewFile()) {
                Log.e(TAG, "Could not create log file: " + logFile.getAbsolutePath());
//                Toast.makeText(context,
//                        "Cannot create log file.",
//                        Toast.LENGTH_LONG).show();
                return;
            }

            // 3) start logcat
            logcatProcess = new ProcessBuilder("logcat", "-v", "time")
                    .redirectErrorStream(true)
                    .start();

            // 4) open writer
//            logWriter = new BufferedWriter(new FileWriter(logFile, true));
            FileOutputStream fos = new FileOutputStream(logFile);
            GZIPOutputStream gos = new GZIPOutputStream(fos);
            logWriter = new BufferedWriter(new OutputStreamWriter(gos));


            // 5) background dump
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(logcatProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logWriter.write(line);
                        logWriter.newLine();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error writing logcat output", e);
                }
            }, "Logcat-Dump-Thread").start();

            Log.d(TAG, "Logcat dumping started into " + logFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to launch logcat process", e);
        }
    }

    /** Call this when you want to stop logging. */
    public static void stop() {
        if (logcatProcess != null) {
            logcatProcess.destroy();
            logcatProcess = null;
        }
        if (logWriter != null) {
            try { logWriter.close(); } catch (IOException ignored) {}
            logWriter = null;
        }
        Log.d(TAG, "Logcat dumping stopped");
    }
}
