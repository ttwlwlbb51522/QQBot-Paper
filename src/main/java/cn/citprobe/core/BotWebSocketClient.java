package cn.citprobe.core;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BotWebSocketClient {

    public interface Listener {
        void onOpen();
        void onText(IncomingMessage message);
        void onAck(JsonObject data);
        void onError(String error);
        void onClose();
    }

    private final String wsUrl;
    private final String secret;
    private final int reconnectDelaySeconds;
    private final BotLogger logger;
    private final Listener listener;

    private final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "qqbot-ws-reconnect");
                t.setDaemon(true);
                return t;
            });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private volatile boolean manualClose = false;
    private volatile WebSocket socket;

    public BotWebSocketClient(String wsUrl, String secret, int reconnectDelaySeconds,
                              BotLogger logger, Listener listener) {
        this.wsUrl = wsUrl;
        this.secret = secret;
        this.reconnectDelaySeconds = Math.max(1, reconnectDelaySeconds);
        this.logger = logger;
        this.listener = listener;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        manualClose = false;
        connect();
    }

    private void connect() {
        if (!running.get() || manualClose) return;
        reconnectScheduled.set(false);
        try {
            logger.info("正在连接中转站: " + wsUrl);
            WebSocket.Builder builder = httpClient.newWebSocketBuilder();
            if (secret != null && !secret.isEmpty()) {
                builder.header("X-Forwarding-Secret", secret);
            }
            builder.buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    socket = webSocket;
                    logger.info("已连接到中转站");
                    listener.onOpen();
                    webSocket.request(1);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    handleText(data.toString());
                    webSocket.request(1);
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    listener.onError(error == null ? "未知错误" : error.getMessage());
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    logger.warn("中转站连接已关闭: " + statusCode + " " + reason);
                    listener.onClose();
                    scheduleReconnect();
                    return null;
                }
            }).whenComplete((ws, err) -> {
                if (err != null) {
                    logger.error("连接中转站失败: " + err.getMessage());
                    listener.onError(err.getMessage());
                    scheduleReconnect();
                }
            });
        } catch (Exception e) {
            logger.error("连接中转站异常: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void handleText(String text) {
        try {
            JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
            String type = obj.has("type") ? obj.get("type").getAsString() : "";
            switch (type) {
                case "group_message":
                case "c2c_message":
                    listener.onText(gson.fromJson(obj, IncomingMessage.class));
                    break;
                case "ack":
                    listener.onAck(obj);
                    break;
                case "error":
                    listener.onError(obj.has("message") ? obj.get("message").getAsString() : "未知错误");
                    break;
                default:
                    // 忽略其他消息类型
            }
        } catch (Exception e) {
            logger.warn("解析中转站消息失败: " + e.getMessage());
        }
    }

    private void scheduleReconnect() {
        if (!running.get() || manualClose) return;
        if (!reconnectScheduled.compareAndSet(false, true)) return;
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            connect();
        }, reconnectDelaySeconds, TimeUnit.SECONDS);
    }

    public boolean sendGroup(String groupOpenid, String content) {
        if (groupOpenid == null) return false;
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "send_group_message");
        obj.addProperty("group_openid", groupOpenid);
        obj.addProperty("content", content);
        return send(obj);
    }

    public boolean sendC2c(String userOpenid, String content) {
        if (userOpenid == null) return false;
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "send_c2c_message");
        obj.addProperty("user_openid", userOpenid);
        obj.addProperty("content", content);
        return send(obj);
    }

    private boolean send(JsonObject payload) {
        WebSocket s = socket;
        if (s == null) {
            logger.warn("未连接中转站，无法发送消息");
            return false;
        }
        try {
            s.sendText(payload.toString(), true);
            return true;
        } catch (Exception e) {
            logger.error("发送消息失败: " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        manualClose = true;
        running.set(false);
        WebSocket s = socket;
        if (s != null) {
            try { s.sendClose(WebSocket.NORMAL_CLOSURE, "bye"); } catch (Exception ignored) {}
        }
        scheduler.shutdownNow();
    }
}
