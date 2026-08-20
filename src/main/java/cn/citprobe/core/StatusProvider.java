package cn.citprobe.core;

import java.util.List;

public interface StatusProvider {
    List<ServerStatus> getAllServers();
    ServerStatus getServer(String name);
    ServerStatus getPlayerServer(String playerName);
    Integer getPlayerPing(String playerName);
    boolean hasTpsData();
}
