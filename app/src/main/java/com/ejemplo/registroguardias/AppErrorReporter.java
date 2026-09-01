package com.ejemplo.registroguardias;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class AppErrorReporter {
    private AppErrorReporter() {}

    static void report(FirebaseFirestore database, String origin, Exception error) {
        String raw = error == null || error.getMessage() == null
            ? "Error sin detalle" : error.getMessage();
        String message = raw.replaceAll("\\s+", " ").trim();
        if (message.length() > 500) message = message.substring(0, 500);
        long now = System.currentTimeMillis();
        String id = String.format(Locale.US, "E%d_%06d", now,
            Integer.toUnsignedLong((origin + now).hashCode()) % 1000000);
        Map<String, Object> data = new HashMap<>();
        data.put("origen", "App Android · " + origin);
        data.put("mensaje", message);
        data.put("ocurrido", FieldValue.serverTimestamp());
        data.put("resuelto", false);
        database.collection("erroresApp").document(id).set(data);
    }
}
