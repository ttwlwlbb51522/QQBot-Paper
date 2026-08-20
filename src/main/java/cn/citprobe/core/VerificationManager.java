package cn.citprobe.core;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VerificationManager {

    // 去除易混淆字符 0/O/1/I/L
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final long EXPIRE_MILLIS = 5 * 60 * 1000L;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> codes = new ConcurrentHashMap<>();

    public static final class Entry {
        public final String openid;
        public final String gameId;
        public final long expireAt;

        public Entry(String openid, String gameId, long expireAt) {
            this.openid = openid;
            this.gameId = gameId;
            this.expireAt = expireAt;
        }
    }

    public String create(String openid, String gameId) {
        cleanup();
        String code;
        do {
            code = generate();
        } while (codes.containsKey(code));
        codes.put(code, new Entry(openid, gameId, System.currentTimeMillis() + EXPIRE_MILLIS));
        return code;
    }

    /** 校验码存在且未过期，返回条目（不删除）。过期时返回 null 但保留 key，以便区分“过期”与“不存在”。 */
    public Entry peek(String code) {
        if (code == null) return null;
        Entry e = codes.get(code.toUpperCase());
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expireAt) return null;
        return e;
    }

    public Entry consume(String code) {
        if (code == null) return null;
        return codes.remove(code.toUpperCase());
    }

    public boolean hasCode(String code) {
        return code != null && codes.containsKey(code.toUpperCase());
    }

    private String generate() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        return sb.toString();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        codes.entrySet().removeIf(e -> now > e.getValue().expireAt);
    }
}
