package com.ejemplo.registroguardias;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UpdateManager {
    private static final String LATEST_RELEASE_URL =
        "https://api.github.com/repos/GonxaMS/registro-de-acceso/releases/latest";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private UpdateManager() {}

    interface CheckCallback {
        void onComplete(UpdateInfo info);
        void onError(Exception error);
    }

    interface DownloadCallback {
        void onProgress(int percent);
        void onComplete(File apkFile);
        void onError(Exception error);
    }

    static final class UpdateInfo {
        final String currentVersion;
        final String latestVersion;
        final String apkUrl;

        UpdateInfo(String currentVersion, String latestVersion, String apkUrl) {
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.apkUrl = apkUrl;
        }

        boolean isUpdateAvailable() {
            return compareVersions(latestVersion, currentVersion) > 0;
        }
    }

    static void check(Context context, CheckCallback callback) {
        Context applicationContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                UpdateInfo info = readLatestRelease(applicationContext);
                MAIN_HANDLER.post(() -> callback.onComplete(info));
            } catch (Exception error) {
                MAIN_HANDLER.post(() -> callback.onError(error));
            }
        });
    }

    static void download(Context context, UpdateInfo info, DownloadCallback callback) {
        Context applicationContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                File apkFile = downloadApk(applicationContext, info, callback);
                MAIN_HANDLER.post(() -> callback.onComplete(apkFile));
            } catch (Exception error) {
                MAIN_HANDLER.post(() -> callback.onError(error));
            }
        });
    }

    private static UpdateInfo readLatestRelease(Context context) throws Exception {
        HttpURLConnection connection = openConnection(LATEST_RELEASE_URL);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("GitHub respondió con código " + responseCode);
            }
            JSONObject release = new JSONObject(readText(connection.getInputStream()));
            String tag = release.optString("tag_name", "").trim();
            String latestVersion = normalizeVersion(tag);
            if (latestVersion.isEmpty()) throw new IOException("La versión no está disponible");

            JSONArray assets = release.optJSONArray("assets");
            String apkUrl = "";
            if (assets != null) {
                for (int index = 0; index < assets.length(); index++) {
                    JSONObject asset = assets.optJSONObject(index);
                    if (asset == null) continue;
                    String name = asset.optString("name", "");
                    String url = asset.optString("browser_download_url", "");
                    if (name.toLowerCase().endsWith(".apk") && !url.isEmpty()) {
                        apkUrl = url;
                        break;
                    }
                }
            }
            if (apkUrl.isEmpty()) throw new IOException("No hay un APK publicado");
            return new UpdateInfo(BuildConfig.VERSION_NAME, latestVersion, apkUrl);
        } finally {
            connection.disconnect();
        }
    }

    private static File downloadApk(Context context, UpdateInfo info, DownloadCallback callback)
        throws Exception {
        File directory = new File(context.getCacheDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("No se pudo preparar la descarga");
        }
        String safeVersion = info.latestVersion.replaceAll("[^A-Za-z0-9._-]", "_");
        File destination = new File(directory, "Registro-de-Acceso-v" + safeVersion + ".apk");
        File temporary = new File(directory, destination.getName() + ".download");
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("No se pudo reiniciar la descarga");
        }

        HttpURLConnection connection = openConnection(info.apkUrl);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("La descarga no está disponible");
            }
            long totalBytes = connection.getContentLengthLong();
            long downloadedBytes = 0L;
            int lastProgress = -1;
            byte[] buffer = new byte[8192];
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    downloadedBytes += read;
                    if (totalBytes > 0) {
                        int progress = (int) Math.min(99L, downloadedBytes * 100L / totalBytes);
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            int reportedProgress = progress;
                            MAIN_HANDLER.post(() -> callback.onProgress(reportedProgress));
                        }
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
        if (destination.exists() && !destination.delete()) {
            throw new IOException("No se pudo reemplazar la actualización");
        }
        if (!temporary.renameTo(destination)) {
            throw new IOException("No se pudo guardar la actualización");
        }
        MAIN_HANDLER.post(() -> callback.onProgress(100));
        return destination;
    }

    private static HttpURLConnection openConnection(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Registro-de-Acceso-Android");
        return connection;
    }

    private static String readText(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString("UTF-8");
        }
    }

    private static String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        while (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = normalizeVersion(left).split("\\.");
        String[] rightParts = normalizeVersion(right).split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            int leftValue = index < leftParts.length ? numberPart(leftParts[index]) : 0;
            int rightValue = index < rightParts.length ? numberPart(rightParts[index]) : 0;
            if (leftValue != rightValue) return Integer.compare(leftValue, rightValue);
        }
        return 0;
    }

    private static int numberPart(String value) {
        String digits = value.replaceAll("[^0-9].*", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
