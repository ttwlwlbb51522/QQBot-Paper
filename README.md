# QQBot Paper / Velocity 版开发文档

## 1. 目标与对齐

在 Paper 与 Velocity 平台实现与 Forge 版完全一致的功能：

- QQ 指令：`/list` `/tps` `/ping [游戏ID]` `/bind <游戏ID>` `/unbind` `/me` `/help`
- 游戏内指令：`/bind accept <验证码>` `/unbind`
- 验证码绑定（8 位，5 分钟过期，游戏内确认）
- 绑定玩家上线私聊提醒
- 服务器名称附加、双语、配置外置、密钥鉴权

**一致性原则**：WebSocket 协议、验证码规则、语言文件 key、绑定文件格式、配置项名称必须与 Forge 版保持一致，确保三种实现可对接同一个中转站。

## 2. 中转站协议约定（三端通用）

### 2.1 连接与鉴权

- 连接地址：`ws://<host>:18080`
- 鉴权头：`X-Forwarding-Secret: <16位密钥>`
- 密钥文件：`forwarding.secret`（只读，内容 trim 后为 16 位）

### 2.2 消息推送（中转站 → 插件）

```json
{
  "type": "group_message",
  "data": {
    "group_nickname": "...",
    "openid": "...",
    "is_group_owner": true,
    "is_group_admin": false,
    "is_bot_admin": false,
    "message": "你好",
    "group_openid": "...",
    "msg_id": "...",
    "msg_seq": null,
    "timestamp": 1750000000000
  }
}
```

私聊为 `"type": "c2c_message"`，`group_openid` 为 `null`。

### 2.3 发送指令（插件 → 中转站）

```json
{"type": "send_group_message", "group_openid": "...", "content": "..."}
{"type": "send_c2c_message", "user_openid": "...", "content": "..."}
```

响应：`{"type":"ack"}` 或 `{"type":"error","message":"..."}`。

## 3. 平台无关的通用层

建议抽成共享包（或直接复制 Forge 版对应类，替换 logger 与配置来源）：

| 组件                    | 职责            | 说明                                                                            |
|-----------------------|---------------|-------------------------------------------------------------------------------|
| `IncomingMessage`     | 消息 DTO        | Gson + `LOWER_CASE_WITH_UNDERSCORES`，三端完全一致                                   |
| `BotWebSocketClient`  | WebSocket 客户端 | JDK `java.net.http.WebSocket`，支持 `header("X-Forwarding-Secret", secret)`、断线重连 |
| `CommandHandler`      | QQ 指令解析       | 仅依赖平台接口（在线人数/TPS/ping/绑定），其余逻辑通用                                              |
| `VerificationManager` | 验证码           | 8 位（去除 0/O/1/I/L），5 分钟过期，按玩家名校验                                               |
| `BindingManager`      | 绑定持久化         | 读写 `qqbot-bindings.json`（openid → 游戏ID）                                       |
| `Lang`                | 语言            | 读 `lang/zh_cn.json`、`lang/en_us.json`，en_us 兜底                                |
| `TpsTracker`          | TPS 计算        | 仅 Paper 需要；Velocity 走 bridge                                                  |

## 4. Paper 版

### 4.1 依赖与版本

```gradle
// build.gradle
plugins { id 'java' }

repositories { maven { url 'https://repo.papermc.io/repository/maven-public/' } }

dependencies {
    compileOnly 'io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT'
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

Java 17，主类继承 `org.bukkit.plugin.java.JavaPlugin`。

### 4.2 平台 API 映射

| 功能    | Paper API                                                     |
|-------|---------------------------------------------------------------|
| 在线人数  | `Bukkit.getOnlinePlayers().size()` / `Bukkit.getMaxPlayers()` |
| 玩家列表  | `Bukkit.getOnlinePlayers()` 遍历 `getName()`                    |
| TPS   | `Bukkit.getTPS()[0]`（1 分钟平均，Paper 扩展）                         |
| MSPT  | `Bukkit.getAverageTickTime()`（Paper 扩展）                       |
| 玩家延迟  | `Player#getPing()`（1.17+ Bukkit API）                          |
| 上线事件  | `PlayerJoinEvent`                                             |
| 下线事件  | `PlayerQuitEvent`                                             |
| 按名取玩家 | `Bukkit.getPlayerExact(name)`（在线精确名）                          |

> 注意：`Bukkit.getTPS()` / `getAverageTickTime()` 是 Paper 特有 API，Spigot 没有，务必使用 paper-api 并声明仅支持 Paper。

### 4.3 游戏内命令

在 `plugin.yml` 注册：

```yaml
commands:
  bind:
    description: "QQ绑定"
    usage: "/bind accept <验证码>"
  unbind:
    description: "QQ解绑"
    usage: "/unbind"
```

`onCommand` 中解析：

```java
if (cmd.equalsIgnoreCase("bind") && args.length >= 2 && args[0].equalsIgnoreCase("accept")) {
    String code = args[1];
    return handleBindAccept(player, code);
}
```

### 4.4 线程模型（重要）

- WebSocket 回调运行在 HttpClient 线程，**不能**直接调 Bukkit API。
- 通过 `Bukkit.getScheduler().runTask(plugin, () -> {...})` 切回主线程读取在线人数、TPS、玩家。
- 验证码、绑定、语言、配置为纯内存/文件操作，可任意线程；文件写建议异步或加锁。

```java
Bukkit.getScheduler().runTask(this, () -> {
    String result = commandHandler.dispatch(...);
    client.sendC2c(openid, result);
});
```

### 4.5 项目结构

```
paper-qqbot/
├── build.gradle
└── src/main/
    ├── java/cn/citprobe/
    │   ├── QQBotPlugin.java        # JavaPlugin 主类
    │   ├── core/                   # 通用层(复用 Forge 版)
    │   │   ├── IncomingMessage.java
    │   │   ├── BotWebSocketClient.java
    │   │   ├── CommandHandler.java
    │   │   ├── VerificationManager.java
    │   │   ├── BindingManager.java
    │   │   └── Lang.java
    │   └── paper/
    │       ├── Config.java         # YAML 配置
    │       ├── ServerInfo.java     # 在线人数/TPS/ping 适配
    │       └── BindCommand.java    # /bind accept、/unbind
    └── resources/
        ├── plugin.yml
        └── lang/
            ├── zh_cn.json
            └── en_us.json
```

## 5. Velocity 版

### 5.1 依赖与版本

```gradle
// build.gradle
repositories { maven { url 'https://repo.papermc.io/repository/maven-public/' } }

dependencies {
    compileOnly 'com.velocitypowered:velocity-api:3.2.0-SNAPSHOT'
    annotationProcessor 'com.velocitypowered:velocity-api:3.2.0-SNAPSHOT'
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

Java 17，主类使用 `@Plugin` 注解，构造函数注入 `ProxyServer` 与 `Logger`：

```java
@Plugin(id = "qqbot", name = "QQBot", version = "1.0.0",
        authors = {"citprobe"})
public class QQBotPlugin {
    private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public QQBotPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }
}
```

### 5.2 平台 API 映射

| 功能    | Velocity API                                                |
|-------|-------------------------------------------------------------|
| 在线人数  | `proxy.getPlayerCount()`（代理总在线）                             |
| 分服在线  | 遍历 `proxy.getAllServers()` 取 `getPlayersConnected().size()` |
| 玩家延迟  | `com.velocitypowered.api.proxy.Player#getPing()`（到代理的延迟）    |
| 上线事件  | `ConnectEvent`（或 `LoginEvent`）                              |
| 下线事件  | `DisconnectEvent`                                           |
| 按名取玩家 | `proxy.getPlayer(name)`（Optional）                           |
| TPS   | **无原生 API**，需 bridge（见 5.3）                                 |

### 5.3 TPS 与延迟的特殊处理

Velocity 运行在代理端，拿不到后端真实 TPS。建议两档实现：

- **只装 Velocity**：`/tps` 返回「未接入后端，无法获取 TPS」；`/ping` 返回玩家到代理的延迟。
- **Velocity + 后端 Paper bridge（推荐）**：后端 Paper 插件（qqbot-bridge）通过 Plugin Messaging 上报 TPS/MSPT，Velocity 聚合展示。

bridge 通道建议：

```text
频道: qqbot:bridge
后端 → 代理(JSON):
{"server":"lobby","tps":20.0,"mspt":48.5,"players":12,"max":100}
```

Velocity 侧用 `ServerPlayPluginMessageEvent` 或 `PluginMessageEvent` 接收，按后端名聚合，`/list` 与 `/tps` 统一展示。

### 5.4 游戏内命令

用 Brigadier（Velocity 3.x 推荐）：

```java
BrigadierCommand bind = new BrigadierCommand(
    Brigadier.literalArgumentBuilder("bind")
        .then(Brigadier.literalArgumentBuilder("accept")
            .then(Brigadier.requiredArgumentBuilder("code", StringArgumentType.word())
                .executes(ctx -> handleBindAccept(ctx.getSource(), ...))
            )
        )
);
proxy.getCommandManager().register(bindMeta, bind);
```

`/unbind` 同理注册简单命令。代理命令对所有后端服务器生效。

### 5.5 线程模型

- Velocity 事件回调在 IO 线程，避免阻塞。
- 用 `proxy.getScheduler().buildTask(this, task).schedule()` 调度到合适线程。
- `Player.getPing()`、`getPlayerCount()` 在任意线程可读；写操作注意线程安全。

### 5.6 项目结构

```
velocity-qqbot/
├── build.gradle
└── src/main/
    ├── java/cn/citprobe/
    │   ├── QQBotPlugin.java
    │   ├── core/                   # 与 Paper 共享通用层
    │   │   └── ...
    │   └── velocity/
    │       ├── Config.java         # TOML/JSON 配置
    │       ├── ServerInfo.java     # 在线人数/ping/bridge 聚合
    │       └── BindCommand.java
    └── resources/
        └── lang/
            ├── zh_cn.json
            └── en_us.json
```

## 6. 配置与语言文件

### 6.1 配置项（三端统一）

| 配置项                     | 说明      | 默认值                    |
|-------------------------|---------|------------------------|
| `enabled`               | 是否启用    | `true`                 |
| `wsUrl`                 | 中转站地址   | `ws://127.0.0.1:18080` |
| `reconnectDelaySeconds` | 重连间隔（秒） | `5`                    |
| `commandPrefix`         | QQ 指令前缀 | `/`                    |
| `language`              | 语言      | `zh_cn`                |
| `serverName`            | 服务器名称   | `我的服务器`                |
| `secretFile`            | 密钥文件    | `forwarding.secret`    |

- Paper 用 `config.yml`（YAML）。
- Velocity 用 `qqbot.toml` 或 JSON（放 `@DataDirectory` 目录）。

### 6.2 语言文件

复制 Forge 版的 `zh_cn.json` / `en_us.json`，key 与占位符（`%s` `%d` `%1$s`）完全一致。

## 7. 密钥与部署

1. 先启动中转站，生成 `forwarding.secret`。
2. 复制到插件数据目录（Paper：`plugins/QQBot/`；Velocity：`plugins/qqbot/`）。
3. 启动插件，连接时携带 `X-Forwarding-Secret` 头。
4. 密钥文件缺失时生成空文件（不写内容），日志提示。

## 8. 开发检查清单

- [ ] WebSocket 能连接并携带密钥头，断线自动重连
- [ ] 群聊/私聊消息收发正常
- [ ] QQ 七条指令全部可用且文案与 Forge 版一致
- [ ] 验证码 8 位、5 分钟过期、按玩家名校验
- [ ] 游戏内 `/bind accept` `/unbind` 可用
- [ ] 上线提醒触发正确
- [ ] 绑定持久化到 `qqbot-bindings.json` 且重启保留
- [ ] 双语言文件加载、en_us 兜底
- [ ] 服务器名称附加到查询与提醒消息
- [ ] 线程安全：Bukkit/Velocity 平台 API 在正确线程调用
- [ ] （Velocity）TPS bridge 方案明确并实现
