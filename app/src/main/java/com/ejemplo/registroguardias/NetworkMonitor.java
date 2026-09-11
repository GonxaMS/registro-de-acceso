package com.ejemplo.registroguardias;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

final class NetworkMonitor {
    interface Listener {
        void onNetworkChanged(boolean available);
    }

    private final ConnectivityManager connectivityManager;
    private final Listener listener;
    private boolean started;
    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) {
            publish();
        }

        @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
            publish();
        }

        @Override public void onLost(Network network) {
            publish();
        }
    };

    NetworkMonitor(Context context, Listener listener) {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.listener = listener;
    }

    void start() {
        if (started) return;
        started = true;
        publish();
        connectivityManager.registerDefaultNetworkCallback(callback);
    }

    void stop() {
        if (!started) return;
        started = false;
        connectivityManager.unregisterNetworkCallback(callback);
    }

    private void publish() {
        listener.onNetworkChanged(isAvailable());
    }

    static boolean isAvailable(Context context) {
        ConnectivityManager manager = (ConnectivityManager)
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = network == null
            ? null : manager.getNetworkCapabilities(network);
        return capabilities != null
            && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private boolean isAvailable() {
        return isAvailable(connectivityManager);
    }

    private static boolean isAvailable(ConnectivityManager manager) {
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = network == null
            ? null : manager.getNetworkCapabilities(network);
        return capabilities != null
            && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
