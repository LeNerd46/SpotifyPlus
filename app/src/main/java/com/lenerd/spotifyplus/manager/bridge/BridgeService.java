package com.lenerd.spotifyplus.manager.bridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.lenerd.spotifyplus.R;

import com.lenerd.spotifyplus.manager.scripting.NodePacketSink;
import com.lenerd.spotifyplus.manager.scripting.NodePacketSinkHolder;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class BridgeService extends Service {
    private static final String TAG = "SpotifyPlus";
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 37845;

    private static final String CHANNEL_ID = "spotifyplus_bridge";
    private static final int NOTIFICATION_ID = 1001;

    private volatile boolean shuttingDown = false;

    private ServerSocket serverSocket;
    private Thread serverThread;

    private final Object clientLock = new Object();
    private Socket clientSocket;
    private BufferedReader clientReader;
    private BufferedWriter clientWriter;
    private Thread clientThread;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "BridgeService created");

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.bind(new InetSocketAddress(HOST, PORT));
                Log.d(TAG, "Socket server listening on " + HOST + ":" + PORT);

                while (!Thread.currentThread().isInterrupted()) {
                    Socket socket = serverSocket.accept();
                    Log.d(TAG, "Accepted bridge client: " + socket.getRemoteSocketAddress());
                    handleClient(socket);
                }
            } catch (IOException e) {
                if (shuttingDown) {
                    Log.d(TAG, "Socket server stopped during shutdown");
                } else {
                    Log.e(TAG, "Socket server failed", e);
                }
            }
        }, "SpotifyPlus-BridgeServer");

        serverThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void handleClient(Socket socket) {
        synchronized (clientLock) {
            closeClientLocked();

            try {
                clientSocket = socket;
                clientReader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                );
                clientWriter = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                );

                BridgeRouter.attachClient(clientSocket, clientWriter);
            } catch (IOException e) {
                Log.e(TAG, "Failed to open client streams", e);
                closeClientLocked();
                return;
            }

            clientThread = new Thread(() -> {
                try {
                    BridgeRouter.send("", "", "bridge.connected", new JSONObject().put("ok", true));
                    JSONObject packet = new JSONObject();
                    packet.put("type", "event");
                    packet.put("name", "event.connecting");
                    packet.put("payload", new JSONObject().put("ok", true));

                    NodePacketSink sink = NodePacketSinkHolder.get();
                    if (sink == null) return;

                    sink.sendToNode(packet);

                    String line;
                    while ((line = clientReader.readLine()) != null) {
                        if (line.isBlank()) continue;

                        handleSpotifyMessage(line);
                    }

                    Log.d(TAG, "Bridge client disconnected");
                } catch (IOException e) {
                    if (shuttingDown) {
                        Log.d(TAG, "Bridge client reader stopped during shutdown");
                    } else {
                        Log.e(TAG, "Bridge client connection error", e);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to create JSON object somehow?", e);
                } finally {
                    synchronized (clientLock) {
                        closeClientLocked();
                    }
                }
            }, "SpotifyPlus-BridgeClientReader");

            clientThread.start();
        }
    }

    private void handleSpotifyMessage(String rawJson) {
        try {
            JSONObject packet = new JSONObject(rawJson);
            String type = packet.optString("type", null);
            String name = packet.optString("name", null);
            JSONObject payload = packet.getJSONObject("payload");

            if (name == null || name.isEmpty()) {
                Log.w(TAG, "Bridge packet missing name");
                return;
            }

            Log.d(TAG, "Type: " + type + " | Payload: " + payload);
            NodePacketSink sink = NodePacketSinkHolder.get();
            if (sink == null) return;

            switch (name) {
                case "ping":
//                    BridgeRouter.send("pong", "{\"ok\":true}");
                    break;

                case "event.ready":
                    JSONObject readyPacket = new JSONObject();
                    readyPacket.put("type", "event");
                    readyPacket.put("name", "event.ready");
                    readyPacket.put("payload", payload);
                    sink.sendToNode(readyPacket);
                    break;

                case "response":
                    sink.sendToNode(new JSONObject(rawJson));
                    break;

                default:
                    sink.sendToNode(packet);
                    //                    Log.d(TAG, "Unhandled Spotify message type: " + type);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Invalid bridge JSON from Spotify: " + rawJson, e);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.lastfm) // replace with your real small icon
                .setContentTitle("SpotifyPlus bridge")
                .setContentText("Keeping the Spotify bridge alive")
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SpotifyPlus Bridge",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps the SpotifyPlus bridge service running in the background");
        channel.setSound(null, null);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void closeClientLocked() {
        BridgeRouter.detachClient();

        closeQuietly(clientReader);
        closeQuietly(clientWriter);
        closeQuietly(clientSocket);

        clientReader = null;
        clientWriter = null;
        clientSocket = null;

        if (clientThread != null) {
            clientThread.interrupt();
            clientThread = null;
        }
    }

    private void closeQuietly(@Nullable Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private void closeQuietly(@Nullable ServerSocket serverSocket) {
        if (serverSocket == null) return;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void onDestroy() {
        shuttingDown = true;
        Log.d(TAG, "BridgeService destroyed");

        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }

        synchronized (clientLock) {
            closeClientLocked();
        }

        closeQuietly(serverSocket);
        serverSocket = null;

        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
