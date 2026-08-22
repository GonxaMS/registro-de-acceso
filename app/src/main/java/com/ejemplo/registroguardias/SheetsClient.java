package com.ejemplo.registroguardias;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SheetsClient implements AutoCloseable {
    private static final String URL_VALUE = BuildConfig.SHEETS_URL;
    private static final String KEY_VALUE = BuildConfig.SHEETS_KEY;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    void mirrorMovement(Person person, String time, String type, String date) {
        executor.execute(() -> post(
            "clave=" + encode(KEY_VALUE)
                + "&id=" + encode(person.id)
                + "&nombre=" + encode(person.name)
                + "&hora=" + encode(time)
                + "&movimiento=" + encode(type)
                + "&fecha=" + encode(date)
        ));
    }

    void mirrorKeyMovement(KeyItem key, String type, String person, String date, String time, String user) {
        executor.execute(() -> get(
            URL_VALUE + "?clave=" + encode(KEY_VALUE)
                + "&accion=" + encode("llave_movimiento")
                + "&llaveId=" + encode(key.id)
                + "&llave=" + encode(key.name)
                + "&movimiento=" + encode(type)
                + "&persona=" + encode(person)
                + "&fecha=" + encode(date)
                + "&hora=" + encode(time)
                + "&usuario=" + encode(user)
        ));
    }

    void syncPerson(String action, Person person) {
        executor.execute(() -> get(
            URL_VALUE + "?clave=" + encode(KEY_VALUE)
                + "&accion=" + encode(action)
                + "&id=" + encode(person.id)
                + "&nombre=" + encode(person.name)
        ));
    }

    void cancelMovement(Person person, String type, String date) {
        String operation = "Ingreso".equals(type) ? "anular_ingreso" : "anular_salida";
        executor.execute(() -> post(
            "clave=" + encode(KEY_VALUE)
                + "&id=" + encode(person.id)
                + "&nombre=" + encode(person.name)
                + "&hora=" + encode("")
                + "&movimiento=" + encode(operation)
                + "&fecha=" + encode(date)
        ));
    }
    private static void post(String body) {
        HttpURLConnection connection = null;
        try {
            connection = open(URL_VALUE);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            finish(connection);
        } catch (Exception ignored) {
            // Firebase es la fuente principal; Sheets es una copia secundaria.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void get(String address) {
        HttpURLConnection connection = null;
        try {
            String current = address;
            for (int redirects = 0; redirects < 4; redirects++) {
                connection = open(current);
                connection.setRequestMethod("GET");
                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_MOVED_TEMP
                    && code != HttpURLConnection.HTTP_MOVED_PERM
                    && code != HttpURLConnection.HTTP_SEE_OTHER) {
                    drain(connection);
                    return;
                }
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                connection = null;
                if (location == null || location.trim().isEmpty()) return;
                current = location;
            }
        } catch (Exception ignored) {
            // Firebase es la fuente principal; Sheets es una copia secundaria.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static HttpURLConnection open(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        return connection;
    }

    private static void finish(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        if (code == HttpURLConnection.HTTP_MOVED_TEMP
            || code == HttpURLConnection.HTTP_MOVED_PERM
            || code == HttpURLConnection.HTTP_SEE_OTHER) {
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location != null && !location.trim().isEmpty()) get(location);
            return;
        }
        drain(connection);
    }

    private static void drain(HttpURLConnection connection) throws Exception {
        InputStream stream = connection.getResponseCode() >= 400
            ? connection.getErrorStream()
            : connection.getInputStream();
        if (stream == null) return;
        byte[] buffer = new byte[256];
        try (InputStream input = stream) {
            while (input.read(buffer) != -1) {}
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override public void close() {
        executor.shutdown();
    }
}
