package com.ejemplo.registroguardias;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

final class AdminAccess {
    static final String ADMIN = "Administrativo";
    static final String NORMAL = "Normal";
    static final String BLOCKED = "Bloqueado";

    interface Callback {
        void onResult(boolean allowed, String uid);
    }

    private AdminAccess() {}

    static void checkRole(FirebaseFirestore database, Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onResult(false, BLOCKED);
            return;
        }
        String uid = user.getUid();
        database.collection("administradores").document(uid).get()
            .addOnSuccessListener(admin -> {
                if (admin.exists() && Boolean.TRUE.equals(admin.getBoolean("activo"))) {
                    callback.onResult(true, ADMIN);
                    return;
                }
                database.collection("dispositivos").document(uid).get()
                    .addOnSuccessListener(device -> callback.onResult(false,
                        NORMAL.equals(device.getString("estado")) ? NORMAL : BLOCKED))
                    .addOnFailureListener(error -> callback.onResult(false, BLOCKED));
            })
            .addOnFailureListener(error -> callback.onResult(false, BLOCKED));
    }

    static void check(FirebaseFirestore database, Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onResult(false, "");
            return;
        }
        String uid = user.getUid();
        database.collection("administradores").document(uid).get()
            .addOnSuccessListener(document -> callback.onResult(
                document.exists() && Boolean.TRUE.equals(document.getBoolean("activo")), uid))
            .addOnFailureListener(error -> callback.onResult(false, uid));
    }
}
