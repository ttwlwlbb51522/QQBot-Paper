package cn.citprobe.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BindingManager {

    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private final Path file;
    private final BotLogger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, String> openidToGame = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();

    public BindingManager(Path dataDir, String fileName, BotLogger logger) {
        this.logger = logger;
        this.file = dataDir.resolve(fileName);
        load();
    }

    public void load() {
        try {
            if (!Files.exists(file)) return;
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, String> map = gson.fromJson(content, MAP_TYPE);
            openidToGame.clear();
            if (map != null) openidToGame.putAll(map);
        } catch (Exception e) {
            logger.error("读取绑定文件失败: " + e.getMessage());
        }
    }

    public void save() {
        synchronized (writeLock) {
            try {
                if (file.getParent() != null) Files.createDirectories(file.getParent());
                Files.writeString(file, gson.toJson(openidToGame), StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.error("写入绑定文件失败: " + e.getMessage());
            }
        }
    }

    public String getGameId(String openid) {
        return openid == null ? null : openidToGame.get(openid);
    }

    public String getOpenid(String gameId) {
        if (gameId == null) return null;
        for (Map.Entry<String, String> e : openidToGame.entrySet()) {
            if (e.getValue().equalsIgnoreCase(gameId)) return e.getKey();
        }
        return null;
    }

    public boolean isBound(String openid) {
        return openid != null && openidToGame.containsKey(openid);
    }

    public void bind(String openid, String gameId) {
        openidToGame.put(openid, gameId);
        save();
    }

    public void unbindByOpenid(String openid) {
        if (openid != null && openidToGame.remove(openid) != null) save();
    }

    public void unbindByGameId(String gameId) {
        String openid = getOpenid(gameId);
        if (openid != null) {
            openidToGame.remove(openid);
            save();
        }
    }
}
