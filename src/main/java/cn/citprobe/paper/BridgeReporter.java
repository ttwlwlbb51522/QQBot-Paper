package cn.citprobe.paper;

import cn.citprobe.core.ServerStatus;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;

public class BridgeReporter implements Listener {

    public static final String CHANNEL = "qqbot:bridge";

    private final JavaPlugin plugin;
    private final Config config;
    private final ServerInfo serverInfo;
    private int taskId = -1;

    public BridgeReporter(JavaPlugin plugin, Config config, ServerInfo serverInfo) {
        this.plugin = plugin;
        this.config = config;
        this.serverInfo = serverInfo;
    }

    public void start() {
        registerChannel();
        // 监听玩家加入：Velocity 重启后玩家重连时，重新协商 outgoing 频道
        Bukkit.getPluginManager().registerEvents(this, plugin);

        long interval = Math.max(1, config.bridgeIntervalSeconds) * 20L;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::report, 20L, interval).getTaskId();
        plugin.getLogger().info("TPS Bridge已启动，每 " + config.bridgeIntervalSeconds + " 秒上报一次");
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        try {
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        } catch (Throwable ignored) {}
    }

    private void registerChannel() {
        // 幂等：重复调用无害，会重新向后端服务器连接发送 minecraft:register
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // 关键修复：Velocity 重启后连接重建，需重新协商 outgoing 频道
        registerChannel();
    }

    public void report() {
        registerChannel();
        try {
            ServerStatus s = serverInfo.currentStatus();

            double tps = Double.isFinite(s.tps) && s.tps > 0 ? s.tps : -1.0;
            double mspt = Double.isFinite(s.mspt) && s.mspt > 0 ? s.mspt : -1.0;
            if (tps < 0 && mspt > 0) {
                tps = 1000.0 / mspt;
            }

            JsonObject obj = new JsonObject();
            obj.addProperty("type", "status");
            obj.addProperty("server", config.serverName);
            obj.addProperty("tps", tps);
            obj.addProperty("mspt", mspt);
            obj.addProperty("players", s.players);
            obj.addProperty("max", s.maxPlayers);

            JsonArray names = new JsonArray();
            for (String name : s.playerNames) names.add(name);
            obj.add("playerNames", names);

            byte[] data = obj.toString().getBytes(StandardCharsets.UTF_8);

            Player target = null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                target = p;
                break;
            }
            if (target != null) {
                target.sendPluginMessage(plugin, CHANNEL, data);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("上报状态失败: " + t.getMessage());
        }
    }
}
