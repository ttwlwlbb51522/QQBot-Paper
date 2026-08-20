package cn.citprobe.paper;

import org.bukkit.plugin.java.JavaPlugin;

public class Config {
    public boolean enabled = true;
    public String wsUrl = "ws://127.0.0.1:18080";
    public int reconnectDelaySeconds = 5;
    public String commandPrefix = "/";
    public String language = "zh_cn";
    public String serverName = "我的服务器";
    public String secretFile = "forwarding.secret";
    public String proxyMode = "auto";
    public int bridgeIntervalSeconds = 2;

    public void load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        var c = plugin.getConfig();
        enabled = c.getBoolean("enabled", enabled);
        wsUrl = c.getString("wsUrl", wsUrl);
        reconnectDelaySeconds = c.getInt("reconnectDelaySeconds", reconnectDelaySeconds);
        commandPrefix = c.getString("commandPrefix", commandPrefix);
        language = c.getString("language", language);
        serverName = c.getString("serverName", serverName);
        secretFile = c.getString("secretFile", secretFile);
        proxyMode = c.getString("proxyMode", proxyMode);
        bridgeIntervalSeconds = c.getInt("bridgeIntervalSeconds", bridgeIntervalSeconds);
    }
}
