package com.lenerd.spotifyplus.manager.bridge;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class BridgeClient {
    private static final String TAG = "SpotifyPlus";
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 37845;

    private static final Object lock = new Object();

    private static Socket socket;
    private static BufferedReader reader;
    private static BufferedWriter writer;
    private static Thread readerThread;
    private static Thread writerThread;

    private static volatile boolean connected = false;
    private static volatile boolean connecting = false;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final BlockingQueue<String> outboundQueue = new LinkedBlockingQueue<>();

    private BridgeClient() {
    }

    public static void connect(JSONObject platform) {
        synchronized (lock) {
            if (connected || connecting) return;
            connecting = true;
        }

        new Thread(() -> {
            try {
                for (int attempt = 1; attempt <= 20; attempt++) {
                    try {
                        Socket newSocket = new Socket();
                        newSocket.connect(new InetSocketAddress(HOST, PORT), 500);

                        BufferedReader newReader = new BufferedReader(new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
                        BufferedWriter newWriter = new BufferedWriter(new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8));

                        synchronized (lock) {
                            socket = newSocket;
                            reader = newReader;
                            writer = newWriter;
                            connected = true;
                        }

                        Log.d(TAG, "Connected to bridge");
                        startReaderThread();
                        startWriterThread();

                        send("", "event", "event.ready", platform);
                        return;
                    } catch (Exception e) {
                        Log.d(TAG, "Bridge not ready yet, attempt " + attempt);
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }

                Log.e(TAG, "Failed to connect to bridge after retries");
            } finally {
                connecting = false;
            }
        }, "SpotifyPlus-BridgeConnector").start();
    }

    /// Send message to module/node
    public static void send(String id, String type, String name, JSONObject platform) {
        try {
            JSONObject packet = new JSONObject();
            if (!id.isBlank()) packet.put("id", id);
            if (!type.isBlank()) packet.put("type", type);
            if (!name.isBlank()) packet.put("name", name);

            try {
                packet.put("payload", platform);
            } catch (Exception ignored) {
                packet.put("payload", platform.toString());
            }

            outboundQueue.offer(packet.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to queue bridge message", e);
        }
    }

    private static void startReaderThread() {
        readerThread = new Thread(() -> {
            try {
                String line;
                while (true) {
                    BufferedReader currentReader;
                    synchronized (lock) {
                        currentReader = reader;
                    }

                    if (currentReader == null) break;

                    line = currentReader.readLine();
                    if (line == null) break;

                    handleHostMessage(line);
                }
            } catch (Exception e) {
                Log.e(TAG, "Bridge reader loop failed", e);
            } finally {
                disconnect();
            }
        }, "SpotifyPlus-BridgeReader");

        readerThread.start();
    }

    private static void startWriterThread() {
        writerThread = new Thread(() -> {
            try {
                while (true) {
                    String packet = outboundQueue.take();

                    BufferedWriter currentWriter;
                    synchronized (lock) {
                        currentWriter = writer;
                    }

                    if (currentWriter == null) {
                        Log.w(TAG, "Dropping outbound message, writer is null");
                        continue;
                    }

                    currentWriter.write(packet);
                    currentWriter.write("\n");
                    currentWriter.flush();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Log.e(TAG, "Bridge writer loop failed", e);
            } finally {
                disconnect();
            }
        }, "SpotifyPlus-BridgeWriter");

        writerThread.start();
    }

    private static void disconnect() {
        synchronized (lock) {
            connected = false;

            try {
                if (reader != null) reader.close();
            } catch (Exception ignored) {
            }

            try {
                if (writer != null) writer.close();
            } catch (Exception ignored) {
            }

            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) {
            }

            reader = null;
            writer = null;
            socket = null;
        }
    }

    private static void handleHostMessage(String rawJson) {
        try {
            JSONObject packet = new JSONObject(rawJson);
            String id = packet.optString("id", "");
            String type = packet.optString("type", "");
            String name = packet.optString("name", "");
            JSONObject payload = packet.has("payload") ? packet.getJSONObject("payload") : new JSONObject();

            mainHandler.post(() -> BridgeMessageBus.dispatch(id, type, name, payload));
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse host message", e);
        }
    }
}