package com.lenerd.spotifyplus.manager.bridge;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class BridgeRouter {
    private static final String TAG = "SpotifyPlus";

    private static final Object lock = new Object();
    private static final BlockingQueue<String> outboundQueue = new LinkedBlockingQueue<>();

    private static Socket clientSocket;
    private static BufferedWriter clientWriter;
    private static Thread writerThread;

    private BridgeRouter() {
    }

    public static void attachClient(Socket socket, BufferedWriter writer) {
        synchronized (lock) {
            detachClientLocked();

            clientSocket = socket;
            clientWriter = writer;

            writerThread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        String packet = outboundQueue.take();

                        BufferedWriter currentWriter;
                        synchronized (lock) {
                            currentWriter = clientWriter;
                        }

                        if (currentWriter == null) {
                            Log.w(TAG, "Dropping outbound message, no Spotify client connected");
                            continue;
                        }

                        currentWriter.write(packet);
                        currentWriter.write("\n");
                        currentWriter.flush();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    Log.e(TAG, "Bridge writer loop failed", e);
                }
            }, "SpotifyPlus-BridgeRouterWriter");

            writerThread.start();
        }
    }

    public static void detachClient() {
        synchronized (lock) {
            detachClientLocked();
        }
    }

    private static void detachClientLocked() {
        if (writerThread != null) {
            writerThread.interrupt();
            writerThread = null;
        }

        try {
            if (clientWriter != null) clientWriter.close();
        } catch (IOException ignored) {
        }

        try {
            if (clientSocket != null) clientSocket.close();
        } catch (IOException ignored) {
        }

        clientWriter = null;
        clientSocket = null;
    }

    /// Sends a message to Spotify
    public static void send(String id, String type, String name, JSONObject payload) {
        try {
            JSONObject packet = new JSONObject();
            packet.put("id", id);
            packet.put("type", type);
            packet.put("name", name);
            packet.put("payload", payload);

            outboundQueue.offer(packet.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to queue message to Spotify", e);
        }
    }

    public static boolean isConnected() {
        synchronized (lock) {
            return clientWriter != null;
        }
    }
}