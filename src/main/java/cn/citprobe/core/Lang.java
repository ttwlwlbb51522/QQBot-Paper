package cn.citprobe.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Lang {

    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private final Map<String, String> messages = new HashMap<>();

    public Lang(Path langDir, String langName, BotLogger logger) {
        // 先加载 en_us 作为兜底，再覆盖为指定语言
        load(langDir.resolve("en_us.json"), logger);
        if (!"en_us".equalsIgnoreCase(langName)) {
            load(langDir.resolve(langName + ".json"), logger);
        }
    }

    private void load(Path file, BotLogger logger) {
        try {
            if (!Files.exists(file)) {
                logger.warn("语言文件不存在: " + file);
                return;
            }
            Map<String, String> map = new Gson()
                    .fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP_TYPE);
            if (map != null) messages.putAll(map);
        } catch (Exception e) {
            logger.error("语言文件加载失败: " + file + " -> " + e.getMessage());
        }
    }

    public String get(String key, Object... args) {
        String template = messages.getOrDefault(key, key);
        if (args == null || args.length == 0) return template;
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template;
        }
    }
}
