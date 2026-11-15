# AutoCommands

AutoCommands 是一个 Minecraft 1.20.1 Forge 模组：接收外部 TCP 信号或游戏内按键，按配置执行指令集。

它不关心指令来自哪个模组，也不依赖其他模组。你配置什么指令，它就执行什么指令。

## 特性

- **TCP 信号触发**：外部程序通过 TCP 发送 JSON 信号包，按 `signals[].id` 匹配绑定。
- **游戏内按键触发**：给绑定配置按键，按下后走同一套执行管线。
- **指令排队**：所有触发进入全局 FIFO 队列，由服务端 tick 驱动执行。
- **延迟控制**：每条指令可设置固定延迟和随机延迟，单位为游戏刻。
- **`&&` 指令链**：一条配置里用 `&&` 串联多条指令。
- **变量替换**：支持 `%变量名%`，数据包 `params` 优先，其次全局变量，最后 `%player_name%`。
- **游戏内 UI**：管理绑定、按键、指令、延迟和全局变量。
- **回执**：TCP 信号包会收到 `queued` / `error` 回执，支持按 `sid` 对账。

## 环境要求

| 项目 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.16 或更高（1.20.1 的 47.x） |
| Java | 17 |

## 安装

### 从发布包安装

1. 安装 Minecraft 1.20.1 和对应版本的 Forge。
2. 将 `auto_commands-0.1.0.jar` 放入游戏的 `mods/` 目录。
3. 启动游戏。单机模式只需客户端安装；专用服务器需要服务端也安装。

### 从源码构建

需要 JDK 17。

```bash
./gradlew build
```

Windows：

```bash
gradlew.bat build
```

构建产物：

```text
build/libs/auto_commands-0.1.0.jar
```

## 快速开始

1. 启动游戏，按 `H` 或执行 `/autocommand ui` 打开配置界面。
2. 点击「新建绑定」，填写绑定 ID、指令、延迟；需要按键触发时点击「绑定按键」。
3. 保存配置。
4. 外部程序连接 `127.0.0.1:8888` 发送 JSON 信号包；或直接按绑定的按键。
5. 观察聊天栏和游戏日志确认执行结果。

## 配置

### 配置文件位置

- 客户端/单机：`.minecraft/config/auto_command/config.json`
- 专用服务器：`config/auto_command/config.json`

文件不存在时，首次启动会自动创建默认配置。

### 最小示例

```json
{
  "bindings": {
    "demo": {
      "triggerMode": "sequential",
      "commands": [
        {
          "rawCommand": "/say 收到信号：%message%",
          "delay": 0,
          "randomDelay": 0
        }
      ],
      "key": "key.keyboard.g"
    }
  },
  "variables": {
    "server_name": "AutoCommands 服务器"
  },
  "server": {
    "port": 8888
  }
}
```

### 字段说明

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `bindings` | object | `{}` | 绑定表，键是绑定 ID |
| `bindings.<id>.commands` | array | `[]` | 指令列表 |
| `commands[].rawCommand` | string | `""` | 指令文本，支持 `&&` 和 `%变量%` |
| `commands[].delay` | int | `0` | 固定延迟，单位游戏刻 |
| `commands[].randomDelay` | int | `0` | 随机延迟上限，实际延迟为 `delay + random(0, randomDelay)` |
| `bindings.<id>.triggerMode` | string | `"sequential"` | `sequential` 顺序执行全部指令；`random` 随机执行一条 |
| `bindings.<id>.key` | string/null | `null` | 按键映射，如 `key.keyboard.g`；`null` 表示只能由 TCP 信号触发 |
| `variables` | object | `{}` | 全局变量，供 `%变量名%` 替换 |
| `server.port` | int | `8888` | TCP 监听端口 |
| `server.timeout` | int | `5000` | 预留字段，当前未参与连接处理 |
| `server.maxConnections` | int | `1` | 预留字段，当前为单连接设计 |

### 变量替换

- 数据包里的 `params` 优先。
- 没有 `params` 时使用 `variables`。
- `%player_name%` 在手动按键触发时替换为触发玩家；TCP 信号触发时可用 `params` 覆盖。
- 变量缺失时替换为空串，不报错。

### 指令链

一条 `rawCommand` 可以用 `&&` 串联多条指令：

```json
{
  "rawCommand": "/say 第一句 && /say 第二句"
}
```

`&&` 是简单字符串拆分，不支持转义。如果指令本身需要包含 `&&`（例如 JSON 文本），请拆成多条配置或避免使用。

## TCP 协议

### 连接

- 默认监听 `8888` 端口。
- 一行一个 JSON 包，UTF-8 编码，以 `\n` 分隔。
- 单连接设计：新连接会替换并关闭旧连接。发送端负责断线重连。
- 服务器启动后开始监听；进入世界前端口不会开放。

### 请求格式

```json
{
  "type": "event",
  "timestamp": 1785588111598,
  "signals": [
    {
      "sid": 1,
      "id": "demo",
      "params": {
        "message": "Hello"
      }
    }
  ]
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `type` | 否 | 包分类，模组端仅作参考，不区分优先级 |
| `timestamp` | 否 | 毫秒时间戳，回执会原样回填为 `packet_timestamp` |
| `signals` | 是 | 触发器数组；为空时整包忽略 |
| `signals[].sid` | 否 | 包内序号，建议提供；回执会原样回填用于对账 |
| `signals[].id` | 是 | 要匹配的绑定 ID |
| `signals[].params` | 否 | 变量替换值，键值均为字符串 |

### 回执格式

收到信号包后，模组会在同一连接写回一行 JSON：

```json
{
  "type": "receipt",
  "timestamp": 1785588112000,
  "packet_timestamp": 1785588111598,
  "status": "queued",
  "receipts": [
    { "sid": 1, "status": "queued" }
  ],
  "queue_size": 0,
  "received_at": 1785588111600
}
```

| 字段 | 说明 |
|---|---|
| `type` | 固定为 `receipt` |
| `timestamp` | 模组发送回执时的毫秒时间戳 |
| `packet_timestamp` | 原样回填请求包的 `timestamp` |
| `status` | `queued`（全部入队）或 `error`（存在失败条目） |
| `receipts` | 逐条状态，与 `signals[]` 对应 |
| `receipts[].sid` | 原样回填请求的 `sid` |
| `receipts[].status` | `queued` 或 `error` |
| `receipts[].error` | 失败原因，如 `unmatched id: xxx`、`command failed: ...` |
| `queue_size` | 发送回执时的队列积压量 |
| `received_at` | 模组收到请求包的毫秒时间戳 |

成功执行的指令不会补发终态回执；只有未匹配绑定或指令执行失败时才返回 `error`。

### Python 示例

```python
import json
import socket

packet = {
    "type": "event",
    "timestamp": 1785588111598,
    "signals": [
        {
            "sid": 1,
            "id": "demo",
            "params": {"message": "Hello"},
        }
    ],
}

with socket.create_connection(("127.0.0.1", 8888), timeout=5) as sock:
    sock.sendall((json.dumps(packet, ensure_ascii=False) + "\n").encode("utf-8"))
    receipt = sock.makefile("r", encoding="utf-8").readline()
    print(receipt)
```

### 安全提示

**TCP 服务器默认监听所有网卡，且没有认证。** 任何能访问该端口的人都可以触发配置中的指令，这些指令以 OP4 权限执行。

- 只在单机或可信局域网使用。
- 不要把端口暴露到公网。
- 必要时用防火墙限制来源地址。
- 不要把绑定配置成高风险指令，除非你确认网络环境可信。

## 游戏内指令

| 指令 | 端 | 说明 |
|---|---|---|
| `/autocommand ui` | 客户端 | 打开配置界面 |
| `/autocommand reload` | 客户端/服务端 | 重新加载配置；客户端同时重建按键 |
| `/autocommand status` | 服务端 | 查看 TCP 状态、连接数、队列长度、绑定数 |
| `/autocommand queue` | 服务端 | 查看待执行指令数 |
| `/autocommand trigger <id>` | 服务端 | 手动触发指定绑定 |

服务端指令需要权限等级 2。

## 项目结构

```text
src/main/java/com/autocommand/
├── AutoCommand.java         模组入口、事件注册、服务端指令
├── Config.java              配置读写与数据模型
├── Packet.java              TCP 信号数据模型
├── Receipt.java             回执数据模型
├── NetworkServer.java       TCP 服务器、流式读取、回执写回
├── NetworkChannel.java      客户端到服务端的按键触发通道
├── ExecutionScheduler.java  全局 FIFO 队列与 tick 调度
├── CommandExecutor.java     变量替换、指令链拆分、指令执行
└── client/
    ├── AutoCommandClient.java        客户端按键注册
    ├── AutoCommandClientEvents.java  客户端 tick 与客户端指令
    ├── KeyBindings.java              动态按键、防抖、C2S 发送
    └── ui/                           游戏内配置界面
```

资源文件位于 `src/main/resources/`：`META-INF/mods.toml`、语言文件、`example_config.json`。

## 构建

```bash
./gradlew build
```

Windows：

```bash
gradlew.bat build
```

清理：

```bash
./gradlew clean
```

构建要求 JDK 17。产物在 `build/libs/auto_commands-0.1.0.jar`。

## 测试

当前仓库没有自动化测试，`./gradlew test` 会显示 `NO-SOURCE`。

手动验证建议：

1. 启动游戏，确认日志中出现 TCP 监听端口。
2. 用上面的 Python 示例发送信号，确认回执为 `queued`。
3. 在游戏内按绑定键，确认指令执行。
4. 修改配置后执行 `/autocommand reload`，确认按键和绑定更新。

## 兼容性与限制

- 仅支持 Minecraft 1.20.1 / Forge 47.x / Java 17。
- TCP 为单连接设计；新连接会替换旧连接。
- TCP 无认证；请按上面的安全提示使用。
- 手动按键触发没有外部 `params`，变量只能来自全局 `variables` 和 `%player_name%`。
- `&&` 拆分不支持转义；指令文本本身包含 `&&` 时会被拆开。
- 远程服务器场景下，客户端和服务端的配置不会自动同步。
- `server.timeout` 和 `server.maxConnections` 当前为预留字段，不参与连接处理。
- 修改配置后建议执行 `/autocommand reload`，确保按键和绑定立即生效。

## 常见问题

| 问题 | 原因 | 解决 |
|---|---|---|
| TCP 连接被拒绝 | 服务器还没启动，或端口被占用 | 进入世界后再连接；检查日志中的端口；修改 `server.port` |
| 按键没有反应 | 配置未重载，或绑定没有配置 `key` | 执行 `/autocommand reload`；检查绑定配置；确认修改的是客户端配置 |
| 变量替换为空 | 手动按键触发没有 `params` | 在 `variables` 中配置默认值，或改用 TCP 信号传 `params` |
| 信号没有匹配到绑定 | `signals[].id` 和绑定 ID 不一致 | 检查大小写和空格；执行 `/autocommand status` 查看绑定数 |
| 指令没有执行 | 指令不存在、参数错误或权限不足 | 查看游戏日志；先在聊天栏手动执行同一条指令验证 |
| 收不到回执 | 连接已断开，或发送端没有读取 | 保持连接；按行读取回执；成功执行不会补发终态回执 |
| 回执 `received_at` 与预期不同 | 失败回执是执行阶段补发的 | 以 `packet_timestamp` 和 `sid` 对账，不要只依赖 `received_at` |

## 贡献

1. Fork 本仓库，创建功能分支。
2. 修改后运行 `./gradlew build`，确保构建通过。
3. 手动验证受影响的路径（TCP 信号、按键触发、配置加载、UI）。
4. 提交 PR，说明改动内容和验证方式。
5. 较大改动建议先开 Issue 讨论。

当前没有自动化测试套件；涉及核心逻辑的改动请在 PR 中写清手动验证步骤。

## 许可证

MIT License。

完整声明见 [LICENSE](LICENSE)。
