package cn.citprobe.paper;

import cn.citprobe.core.ServerStatus;
import cn.citprobe.core.StatusProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerInfo implements StatusProvider {

    private final String serverName;
    private static final AtomicBoolean DEBUG_DONE = new AtomicBoolean(false);

    public ServerInfo(String serverName) {
        this.serverName = serverName;
    }

    @Override
    public List<ServerStatus> getAllServers() {
        List<ServerStatus> list = new ArrayList<>();
        list.add(currentStatus());
        return list;
    }

    @Override
    public ServerStatus getServer(String name) {
        if (name != null && name.equalsIgnoreCase(serverName)) return currentStatus();
        return null;
    }

    @Override
    public ServerStatus getPlayerServer(String playerName) {
        Player p = Bukkit.getPlayerExact(playerName);
        return (p != null && p.isOnline()) ? currentStatus() : null;
    }

    @Override
    public Integer getPlayerPing(String playerName) {
        Player p = Bukkit.getPlayerExact(playerName);
        return (p != null && p.isOnline()) ? p.getPing() : null;
    }

    @Override
    public boolean hasTpsData() {
        return true;
    }

    public ServerStatus currentStatus() {
        ServerStatus s = new ServerStatus(serverName);
        s.players = Bukkit.getOnlinePlayers().size();
        s.maxPlayers = Bukkit.getMaxPlayers();

        double[] tpsRaw = null;
        try {
            tpsRaw = Bukkit.getTPS();
            if (tpsRaw != null && tpsRaw.length > 0) s.tps = tpsRaw[0];
        } catch (Throwable t) {
            s.tps = -1.0;
        }

        try {
            s.mspt = Bukkit.getAverageTickTime();
        } catch (Throwable t) {
            s.mspt = -1.0;
        }

        // ===== 一次性诊断日志（确认后删除）=====
        if (DEBUG_DONE.compareAndSet(false, true)) {
            Bukkit.getLogger().info("[QQBot-debug] getTPS()=" + Arrays.toString(tpsRaw)
                    + " | getAverageTickTime()=" + s.mspt
                    + " | 最终 s.tps=" + s.tps);
        }
        // ====================================

        for (Player p : Bukkit.getOnlinePlayers()) {
            s.playerNames.add(p.getName());
            s.playerPings.put(p.getName(), p.getPing());
        }
        s.online = true;
        s.lastUpdate = System.currentTimeMillis();
        return s;
    }
}
