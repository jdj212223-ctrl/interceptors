package com.interceptors.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.interceptors.Interceptors;
import com.interceptors.compat.ObsAuth;
import com.interceptors.config.ClientConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

/**
 * Optional link to OBS Studio's WebSocket server on this computer (off unless turned on in the client config).
 * It only listens to whether OBS is streaming or recording, so the overlay's LIVE tag tells the truth, and can
 * switch OBS scenes (a Chopper Cam scene while that app is open). It never streams and never sees a stream key.
 */
public final class ObsLink {
    private static volatile boolean connected, streaming, recording;
    private static volatile WebSocket socket;
    private static long nextTry;
    private static boolean onChopper;

    private ObsLink() {}

    public static boolean connected() {
        return connected;
    }

    public static boolean streaming() {
        return streaming;
    }

    public static boolean recording() {
        return recording;
    }

    /** Every client tick: connect when turned on, retry every 10 seconds, let go when turned off. */
    static void tick() {
        boolean enabled = ClientConfig.get(ClientConfig.OBS_ENABLED);
        if (!enabled) {
            if (socket != null) close();
            return;
        }
        long now = System.currentTimeMillis();
        if (socket != null || now < nextTry) return;
        nextTry = now + 10_000;
        try {
            URI uri = URI.create("ws://" + ClientConfig.get(ClientConfig.OBS_HOST) + ":" + ClientConfig.get(ClientConfig.OBS_PORT));
            HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, new Listener())
                    .whenComplete((ws, err) -> {
                        if (err != null) socket = null;
                        else socket = ws;
                    });
        } catch (Exception e) {
            Interceptors.LOGGER.debug("OBS link: can't connect", e);
        }
    }

    private static void close() {
        WebSocket ws = socket;
        socket = null;
        connected = streaming = recording = false;
        if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    }

    private static void send(WebSocket ws, int op, JsonObject d) {
        JsonObject msg = new JsonObject();
        msg.addProperty("op", op);
        msg.add("d", d);
        ws.sendText(msg.toString(), true);
    }

    private static void request(WebSocket ws, String type, String id, JsonObject data) {
        JsonObject d = new JsonObject();
        d.addProperty("requestType", type);
        d.addProperty("requestId", id);
        if (data != null) d.add("requestData", data);
        send(ws, 6, d);
    }

    /** A StormOS app opened (null: back home or closed). Switches OBS to the Chopper Cam scene and back. */
    public static void appOpened(String appId) {
        WebSocket ws = socket;
        if (!connected || ws == null) return;
        String chopper = ClientConfig.get(ClientConfig.OBS_CHOPPER_SCENE), main = ClientConfig.get(ClientConfig.OBS_MAIN_SCENE);
        boolean nowChopper = "chopper".equals(appId);
        String scene = nowChopper && !onChopper ? chopper : !nowChopper && onChopper ? main : "";
        onChopper = nowChopper;
        if (scene.isEmpty()) return;
        JsonObject data = new JsonObject();
        data.addProperty("sceneName", scene);
        request(ws, "SetCurrentProgramScene", "scene", data);
    }

    private static final class Listener implements WebSocket.Listener {
        private final StringBuilder text = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                try {
                    handle(ws, JsonParser.parseString(text.toString()).getAsJsonObject());
                } catch (Exception e) {
                    Interceptors.LOGGER.debug("OBS link: bad message", e);
                }
                text.setLength(0);
            }
            ws.request(1);
            return null;
        }

        private void handle(WebSocket ws, JsonObject msg) {
            int op = msg.get("op").getAsInt();
            JsonObject d = msg.getAsJsonObject("d");
            switch (op) {
                case 0 -> { // Hello: log in, and ask for output (stream/record) events
                    JsonObject id = new JsonObject();
                    id.addProperty("rpcVersion", 1);
                    id.addProperty("eventSubscriptions", 1 << 6);
                    if (d.has("authentication")) {
                        JsonObject auth = d.getAsJsonObject("authentication");
                        id.addProperty("authentication", ObsAuth.answer(ClientConfig.get(ClientConfig.OBS_PASSWORD),
                                auth.get("salt").getAsString(), auth.get("challenge").getAsString()));
                    }
                    send(ws, 1, id);
                }
                case 2 -> { // Identified
                    connected = true;
                    request(ws, "GetStreamStatus", "stream", null);
                    request(ws, "GetRecordStatus", "record", null);
                }
                case 5 -> { // Event
                    String type = d.get("eventType").getAsString();
                    JsonObject data = d.getAsJsonObject("eventData");
                    if (type.equals("StreamStateChanged")) streaming = data.get("outputActive").getAsBoolean();
                    if (type.equals("RecordStateChanged")) recording = data.get("outputActive").getAsBoolean();
                }
                case 7 -> { // RequestResponse
                    JsonObject data = d.getAsJsonObject("responseData");
                    if (data == null || !data.has("outputActive")) return;
                    String id = d.get("requestId").getAsString();
                    if (id.equals("stream")) streaming = data.get("outputActive").getAsBoolean();
                    if (id.equals("record")) recording = data.get("outputActive").getAsBoolean();
                }
                default -> {}
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
            socket = null;
            connected = streaming = recording = false;
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            socket = null;
            connected = streaming = recording = false;
        }
    }
}
