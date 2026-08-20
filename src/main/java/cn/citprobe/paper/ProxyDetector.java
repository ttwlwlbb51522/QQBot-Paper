package cn.citprobe.paper;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class ProxyDetector {

    /** 检测是否运行在 Velocity 代理之后。 */
    public static boolean isBehindVelocity(JavaPlugin plugin, String proxyMode) {
        if ("velocity".equalsIgnoreCase(proxyMode)) return true;
        if ("standalone".equalsIgnoreCase(proxyMode)) return false;

        // auto：读取 paper-global.yml 的 proxies.velocity.enabled
        try {
            File f = new File(Bukkit.getWorldContainer(), "config/paper-global.yml");
            if (f.exists()) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                if (y.getBoolean("proxies.velocity.enabled", false)) return true;
            }
        } catch (Throwable ignored) {}

        // 兼容：spigot.yml 的 bungeecord 也算代理模式
        try {
            File f = new File(Bukkit.getWorldContainer(), "spigot.yml");
            if (f.exists()) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                if (y.getBoolean("settings.bungeecord", false)) return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }
}
