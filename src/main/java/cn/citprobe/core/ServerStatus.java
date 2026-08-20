package cn.citprobe.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerStatus {
    public String serverName;
    public int players;
    public int maxPlayers = -1;
    public double tps = -1.0;
    public double mspt = -1.0;
    public boolean online = true;
    public long lastUpdate = System.currentTimeMillis();
    public final List<String> playerNames = new ArrayList<>();
    public final Map<String, Integer> playerPings = new HashMap<>();

    public ServerStatus() {}

    public ServerStatus(String serverName) {
        this.serverName = serverName;
    }
}
