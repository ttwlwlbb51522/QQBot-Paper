package cn.citprobe.core;

import java.util.List;
import java.util.Locale;

public class CommandHandler {

    private final StatusProvider statusProvider;
    private final VerificationManager verification;
    private final BindingManager bindings;
    private final Lang lang;
    private final String prefix;
    private final String serverName;

    public CommandHandler(StatusProvider statusProvider, VerificationManager verification,
                          BindingManager bindings, Lang lang, String prefix, String serverName) {
        this.statusProvider = statusProvider;
        this.verification = verification;
        this.bindings = bindings;
        this.lang = lang;
        this.prefix = (prefix == null || prefix.isEmpty()) ? "/" : prefix;
        this.serverName = serverName;
    }

    public String dispatch(String openid, String raw) {
        if (raw == null) return null;
        String msg = raw.trim();
        if (msg.length() < prefix.length() || !msg.startsWith(prefix)) return null;
        String body = msg.substring(prefix.length()).trim();
        if (body.isEmpty()) return null;

        String[] parts = body.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length >= 2 ? join(parts, 1) : null;

        switch (cmd) {
            case "list":   return handleList(arg);
            case "tps":    return handleTps(arg);
            case "ping":   return handlePing(arg);
            case "bind":   return handleBind(openid, arg);
            case "unbind": return handleUnbind(openid);
            case "me":     return handleMe(openid);
            case "help":   return handleHelp();
            default:       return null;
        }
    }

    private String join(String[] parts, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < parts.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    // ---------------- /list ----------------
    private String handleList(String arg) {
        if (arg == null || arg.isEmpty()) {
            List<ServerStatus> all = statusProvider.getAllServers();
            if (all.isEmpty()) return lang.get("list.empty");
            StringBuilder sb = new StringBuilder(lang.get("list.all.header")).append('\n');
            for (ServerStatus s : all) {
                if (s.maxPlayers > 0) {
                    sb.append(lang.get("list.all.line", s.serverName, s.players, s.maxPlayers));
                } else {
                    sb.append(lang.get("list.all.line.nomax", s.serverName, s.players));
                }
                sb.append('\n');
            }
            return sb.toString().trim();
        }
        ServerStatus s = statusProvider.getServer(arg);
        if (s == null) return lang.get("list.server.notfound", arg);
        StringBuilder sb = new StringBuilder(lang.get("list.server.header", s.serverName)).append('\n');
        if (s.playerNames.isEmpty()) {
            sb.append(lang.get("list.server.empty", s.serverName));
        } else {
            for (String name : s.playerNames) {
                Integer ping = s.playerPings.get(name);
                sb.append(lang.get("list.player.line", name, ping == null ? 0 : ping)).append('\n');
            }
        }
        return sb.toString().trim();
    }

    // ---------------- /tps ----------------
    private String handleTps(String arg) {
        if (arg == null || arg.isEmpty()) {
            if (!statusProvider.hasTpsData()) return lang.get("tps.no_bridge");
            List<ServerStatus> all = statusProvider.getAllServers();
            if (all.isEmpty()) return lang.get("list.empty");
            StringBuilder sb = new StringBuilder(lang.get("tps.all.header")).append('\n');
            for (ServerStatus s : all) {
                sb.append(lang.get("tps.line", s.serverName, fmtTps(s.tps), fmtMspt(s.mspt))).append('\n');
            }
            return sb.toString().trim();
        }
        ServerStatus s = statusProvider.getServer(arg);
        if (s == null) return lang.get("tps.server.notfound", arg);
        if (s.tps < 0) return lang.get("tps.server.no_data", s.serverName);
        return lang.get("tps.server.header", s.serverName) + "\n"
                + lang.get("tps.line", s.serverName, fmtTps(s.tps), fmtMspt(s.mspt));
    }

    private String fmtTps(double v) {
        return v < 0 ? "?" : String.format(Locale.ROOT, "%.1f", v);
    }

    private String fmtMspt(double v) {
        return v < 0 ? "?" : String.format(Locale.ROOT, "%.1f", v);
    }

    // ---------------- /ping ----------------
    private String handlePing(String arg) {
        if (arg == null || arg.isEmpty()) {
            List<ServerStatus> all = statusProvider.getAllServers();
            if (all.isEmpty()) return lang.get("list.empty");
            StringBuilder sb = new StringBuilder(lang.get("ping.all.header")).append('\n');
            for (ServerStatus s : all) {
                sb.append(lang.get("ping.all.line", s.serverName, avgPing(s), s.players)).append('\n');
            }
            return sb.toString().trim();
        }
        // 先按服务器名匹配，再按玩家名匹配
        ServerStatus server = statusProvider.getServer(arg);
        if (server != null) {
            StringBuilder sb = new StringBuilder(lang.get("ping.server.header", server.serverName)).append('\n');
            if (server.playerNames.isEmpty()) {
                sb.append(lang.get("list.server.empty", server.serverName));
            } else {
                for (String name : server.playerNames) {
                    Integer ping = server.playerPings.get(name);
                    sb.append(lang.get("list.player.line", name, ping == null ? 0 : ping)).append('\n');
                }
            }
            return sb.toString().trim();
        }
        Integer ping = statusProvider.getPlayerPing(arg);
        if (ping != null) {
            ServerStatus loc = statusProvider.getPlayerServer(arg);
            String srv = (loc != null && loc.serverName != null) ? loc.serverName : serverName;
            return lang.get("ping.player", arg, ping, srv);
        }
        return lang.get("ping.notfound", arg);
    }

    private int avgPing(ServerStatus s) {
        if (s.playerPings.isEmpty()) return 0;
        long sum = 0;
        for (int p : s.playerPings.values()) sum += p;
        return (int) (sum / s.playerPings.size());
    }

    // ---------------- /bind ----------------
    private String handleBind(String openid, String gameId) {
        if (gameId == null || gameId.isEmpty()) return lang.get("bind.usage");
        if (bindings.isBound(openid)) return lang.get("bind.already_bound", bindings.getGameId(openid));
        String code = verification.create(openid, gameId);
        return lang.get("bind.code_sent", gameId, code);
    }

    // ---------------- /unbind ----------------
    private String handleUnbind(String openid) {
        String gameId = bindings.getGameId(openid);
        if (gameId == null) return lang.get("unbind.not_bound");
        bindings.unbindByOpenid(openid);
        return lang.get("unbind.success", gameId);
    }

    // ---------------- /me ----------------
    private String handleMe(String openid) {
        String gameId = bindings.getGameId(openid);
        if (gameId == null) return lang.get("me.not_bound");
        return lang.get("me.result", gameId);
    }

    private String handleHelp() {
        return lang.get("help.message");
    }
}
