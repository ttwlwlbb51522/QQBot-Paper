package cn.citprobe.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SecretManager {

    public static String load(Path dataDir, String secretFile, BotLogger logger) {
        Path p = dataDir.resolve(secretFile);
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent() == null ? dataDir : p.getParent());
                Files.createFile(p);   // 生成空文件，不写入内容
                logger.warn("密钥文件不存在，已生成空文件: " + p.toAbsolutePath());
                return "";
            }
            String s = Files.readString(p, StandardCharsets.UTF_8).trim();
            if (s.isEmpty()) {
                logger.warn("密钥文件为空: " + p.toAbsolutePath());
            } else if (s.length() != 16) {
                logger.warn("密钥长度应为 16 位，当前为 " + s.length() + " 位");
            }
            return s;
        } catch (Exception e) {
            logger.error("读取密钥文件失败: " + e.getMessage());
            return "";
        }
    }
}
