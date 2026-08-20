# QQBot-Paper

基于 [Paper](https://papermc.io/software/paper) 的 QQ 群服互联机器人插件，通过 WebSocket 连接自建中转站，实现 QQ 群/私聊与游戏服务器的消息、指令、绑定互通。

> 🔗 这是 **Paper（后端）版**。代理端请使用 [QQBot-Velocity](https://github.com/ttwlwlbb51522/QQBot-Velocity) 插件。

## 功能特性

- QQ 指令：`/list` `/tps` `/ping` `/bind` `/unbind` `/me` `/help`
- 游戏内指令：`/bind accept <验证码>` `/unbind`
- 验证码绑定：8 位（去除 0/O/1/I/L），5 分钟过期，游戏内确认
- 绑定玩家上线私聊提醒
- 服务器名称附加、双语（zh_cn / en_us）、配置外置、密钥鉴权
- TPS / MSPT / 人数通过 bridge 上报给 Velocity（**bridge 功能已并入本插件**）
- 未识别的一级指令静默忽略，便于多项目共用一个中转站机器人

## 双端架构

|            | QQBot-Paper（本仓库）  | QQBot-Velocity |
|------------|-------------------|----------------|
| 运行位置       | 后端服务器             | 代理（Proxy）      |
| 连接中转站      | 独立部署时自行连接         | 负责连接与鉴权        |
| TPS / MSPT | 本地计算并通过 bridge 上报 | 聚合展示           |
| 游戏内命令      | 独立部署时处理           | 代理命令，对所有后端生效   |

**关键规则**：Paper 插件检测到 Velocity 后，**连接与鉴权交由 Velocity 版负责，并忽略配置文件中的 `wsUrl`**，只保留 TPS bridge 上报。

## 中转站协议

- 连接地址：`ws://<host>:18080`
- 鉴权头：`X-Forwarding-Secret: <16位密钥>`
- 密钥文件：`forwarding.secret`

### 桥接频道

频道: qqbot:bridge
后端 → 代理 (JSON):
```json
{"type":"status","server":"<serverName>","tps":20.0,"mspt":48.5,"players":12,"max":100,"playerNames":["Steve","Alex"]}
```

## 指令说明

所有状态类指令：`/xx` 默认输出全部服务器，`/xx <服务器名>` 输出对应服务器，找不到则提示。

| 指令                 | 说明    |
|--------------------|-------|
| `/list [服务器]`      | 在线人数  |
| `/tps [服务器]`       | 服务器性能 |
| `/ping [游戏ID/服务器]` | 延迟    |
| `/bind <游戏ID>`     | 发起绑定  |
| `/unbind`          | 解绑    |
| `/me`              | 查看绑定  |
| `/help`            | 帮助    |

游戏内：`/bind accept <验证码>`、`/unbind`。

## 配置（config.yml）

```yaml
enabled: true
wsUrl: "ws://127.0.0.1:18080"   # 检测到 Velocity 时此配置被忽略
reconnectDelaySeconds: 5
commandPrefix: "/"
language: "zh_cn"
serverName: "我的服务器"          # 同时作为 bridge 上报的服务器名
secretFile: "forwarding.secret"
proxyMode: "auto"                 # auto / standalone / velocity
bridgeIntervalSeconds: 2
```

## 部署

1. 先启动中转站，生成 `forwarding.secret`。
2. 复制到插件数据目录 `plugins/QQBot/`。
3. 独立部署：配置 `wsUrl` 与密钥即可。
4. 配合 Velocity：在 Paper 后端开启 Velocity 转发，或设置 `proxyMode: velocity`。
5. 启动插件，连接时携带 `X-Forwarding-Secret` 头。

## 依赖

- Paper 1.20.1+（依赖 Paper 扩展 API：`Bukkit.getTPS()` / `getAverageTickTime()`）

## [License](LICENSE)

MIT

---