package cn.citprobe;

import cn.citprobe.core.*;
import cn.citprobe.paper.BridgeReporter;
import cn.citprobe.paper.Config;
import cn.citprobe.paper.ProxyDetector;
import cn.citprobe.paper.ServerInfo;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class QQBotPlugin extends JavaPlugin implements Listener {

    private Config config;
    private Lang lang;
    private BindingManager bindings;
    private VerificationManager verification;
    private ServerInfo serverInfo;
    private CommandHandler commandHandler;
    private BotWebSocketClient client;
    private BridgeReporter bridgeReporter;

    private boolean velocityMode;
    private BotLogger botLogger;

    @Override
    public void onEnable() {
        botLogger = new BotLogger() {
            public void info(String m)  { getLogger().info(m); }
            public void warn(String m)  { getLogger().warning(m); }
            public void error(String m) { getLogger().severe(m); }
        };

        config = new Config();
        config.load(this);

        if (!config.enabled) {
            getLogger().info("插件已在配置中禁用");
            return;
        }

        ensureLangFiles();
        lang = new Lang(getDataFolder().toPath().resolve("lang"), config.language, botLogger);
        bindings = new BindingManager(getDataFolder().toPath(), "qqbot-bindings.json", botLogger);
        verification = new VerificationManager();
        serverInfo = new ServerInfo(config.serverName);
        commandHandler = new CommandHandler(serverInfo, verification, bindings, lang,
                config.commandPrefix, config.serverName);

        velocityMode = ProxyDetector.isBehindVelocity(this, config.proxyMode);

        if (velocityMode) {
            // 检测到 Velocity：连接与鉴权交由 Velocity 版插件负责，忽略 wsUrl
            getLogger().info("检测到 Velocity 代理：连接与鉴权交由 Velocity 版插件负责，忽略 wsUrl 配置");
            bridgeReporter = new BridgeReporter(this, config, serverInfo);
            bridgeReporter.start();
            return;
        }

        // 独立模式：自行连接中转站
        String secret = SecretManager.load(getDataFolder().toPath(), config.secretFile, botLogger);
        client = new BotWebSocketClient(config.wsUrl, secret, config.reconnectDelaySeconds, botLogger,
                new BotWebSocketClient.Listener() {
                    @Override public void onOpen() { getLogger().info("中转站连接已建立"); }
                    @Override public void onText(IncomingMessage msg) { handleIncoming(msg); }
                    @Override public void onAck(JsonObject data) { }
                    @Override public void onError(String error) { getLogger().severe("中转站错误: " + error); }
                    @Override public void onClose() { getLogger().warning("中转站连接已断开"); }
                });
        client.start();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void ensureLangFiles() {
        for (String f : new String[]{"zh_cn.json", "en_us.json"}) {
            File target = new File(getDataFolder(), "lang/" + f);
            if (!target.exists()) saveResource("lang/" + f, false);
        }
    }

    private void handleIncoming(IncomingMessage msg) {
        if (msg == null || msg.data == null) return;
        String openid = msg.data.openid;
        String text = msg.data.message;
        String groupOpenid = msg.data.groupOpenid;
        boolean isGroup = "group_message".equals(msg.type);
        // WebSocket 回调在 HttpClient 线程，切回主线程读取平台数据
        Bukkit.getScheduler().runTask(this, () -> {
            String reply = commandHandler.dispatch(openid, text);
            if (reply == null) return;
            if (isGroup) client.sendGroup(groupOpenid, reply);
            else client.sendC2c(openid, reply);
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (velocityMode) return; // Velocity 版负责提醒
        String openid = bindings.getOpenid(e.getPlayer().getName());
        if (openid != null) {
            client.sendC2c(openid, lang.get("reminder.bound_join", e.getPlayer().getName(), config.serverName));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("仅玩家可执行此命令");
            return true;
        }
        if (velocityMode) return false; // 交给 Velocity 代理命令处理

        if (command.getName().equalsIgnoreCase("bind")) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("accept")) {
                return handleBindAccept(player, args[1]);
            }
            player.sendMessage(lang.get("bind.accept.usage"));
            return true;
        }
        if (command.getName().equalsIgnoreCase("unbind")) {
            return handleUnbind(player);
        }
        return false;
    }

    private boolean handleBindAccept(Player player, String code) {
        VerificationManager.Entry e = verification.peek(code);
        if (e == null) {
            player.sendMessage(lang.get(verification.hasCode(code) ? "bind.accept.expired" : "bind.accept.notfound"));
            return true;
        }
        if (!e.gameId.equalsIgnoreCase(player.getName())) {
            player.sendMessage(lang.get("bind.accept.mismatch"));
            return true;
        }
        verification.consume(code);
        String existing = bindings.getOpenid(e.gameId);
        if (existing != null && !existing.equals(e.openid)) {
            player.sendMessage(lang.get("bind.accept.bound_by_other"));
            return true;
        }
        bindings.bind(e.openid, e.gameId);
        player.sendMessage(lang.get("bind.accept.success", e.gameId));
        client.sendC2c(e.openid, lang.get("bind.notify", e.gameId));
        return true;
    }

    private boolean handleUnbind(Player player) {
        String openid = bindings.getOpenid(player.getName());
        if (openid == null) {
            player.sendMessage(lang.get("unbind.game.not_bound"));
            return true;
        }
        bindings.unbindByGameId(player.getName());
        player.sendMessage(lang.get("unbind.game.success"));
        client.sendC2c(openid, lang.get("unbind.notify", player.getName()));
        return true;
    }

    @Override
    public void onDisable() {
        if (bridgeReporter != null) bridgeReporter.stop();
        if (client != null) client.stop();
    }
}
