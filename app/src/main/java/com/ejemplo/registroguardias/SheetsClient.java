package com.ejemplo.registroguardias;

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
        executor.execute(() -> post(
            "clave=" + encode(KEY_VALUE)
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
            connection = (HttpURLConnection) new URL(URL_VALUE).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            connection.getResponseCode();
        } catch (Exception ignored) {
            // Firebase es la fuente principal; Sheets es una copia secundaria.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void get(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.getResponseCode();
        } catch (Exception ignored) {
            // Firebase es la fuente principal; Sheets es una copia secundaria.
        } finally {
            if (connection != null) connection.disconnect();
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
