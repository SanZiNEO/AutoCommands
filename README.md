# AutoCommand Mod

AutoCommand 是一个 Minecraft 1.20.1 Forge 模组，它接收外部信号 → 查配置映射 → 执行 MC 指令。
**不依赖任何其他模组**，指令配什么就执行什么。

## 核心定位

**通用信号→指令映射器。** 信号来源可以是：
- 外部程序（如外部插件）通过 TCP 发送
- 游戏内按键绑定

你配什么指令它就执行什么指令，不关心指令是哪个模组的。

## 与实时互动系统的关系

```
外部信号源 (外部插件) → TCP → AutoCommand (模组) → 指令 → 外部模组 / 任何模组
```

- 外部信号源 只管发信号，不知道 AutoCommand 存在
- AutoCommand 只管信号→指令映射，不知道 外部模组 存在
- 外部模组 只管阵营功能，不知道上层是谁在调用
- 三个组件互相独立，没写相互依赖代码

## 项目架构

```
AutoCommand Minecraft模组
├── 核心功能层
│   ├── 指令绑定系统 (CommandBinding)
│   ├── 外部通信管理 (CommunicationManager)
│   ├── 按键绑定系统 (KeyBindingManager)
│   └── 执行调度器 (ExecutionScheduler)
├── 数据管理层
│   ├── 配置管理器 (ConfigManager)
│   ├── 配置文件解析 (Config)
│   └── 数据序列化 (KeyMappingSerializer)
├── 游戏集成层
│   ├── 游戏内UI系统
│   ├── 事件监听 (AutoCommand)
│   └── 客户端集成 (Auto_command)
└── 外部接口层
    ├── TCP服务器 (内置8888端口)
    ├── JSON数据解析
    └── 变量替换系统
```

## 功能特性

### 1. 指令绑定系统
- 将指令集绑定到特定名称
- 支持多条指令
- 每条指令可设置固定延迟
- 每条指令可设置随机延迟
- 延迟执行严格按照上一条指令的结束时间计算

### 2. 变量替换
- 支持指令中的变量替换
- 变量可通过配置文件定义
- 支持从外部通信数据包中获取变量值

### 3. 外部通信
- 内置 TCP 服务器，监听指定端口（默认8888）
- 支持接收外部脚本发送的数据
- 数据格式支持 JSON
- 支持解析数据包中的变量和绑定名称

### 4. 按键绑定系统
- 游戏内按键绑定系统
- 支持键盘和鼠标按键
- 绑定名称到游戏命令
- 处理按键事件

### 5. 游戏内UI系统
- 提供直观的图形界面
- 可视化管理指令绑定
- 配置编辑和保存功能
- 实时预览绑定信息

## 与外部事件系统的集成

AutoCommand 模组与外部事件系统配合使用，实现观众与游戏的实时互动。

### 数据流程

```
外部事件系统 → AutoCommand模组
    ↓
实时数据采集 → 指令匹配 → TCP通信 → 指令执行
```

### 支持的互动类型

1. **弹幕互动**：观众发送特定弹幕触发游戏指令
2. **礼物互动**：观众赠送礼物触发游戏指令
3. **点赞互动**：观众点赞数量达到一定条件触发
4. **关注互动**：观众关注主播触发
5. **进场互动**：观众进入房间触发
6. **粉丝团互动**：观众加入粉丝团触发
7. **其他互动**：支持自定义互动类型

## 安装方法

1. 确保您的 Minecraft 版本为 1.20.1
2. 安装 Forge 47.4.16 或更高版本
3. 将 AutoCommand 模组放入 mods 文件夹
4. 启动游戏

## 配置文件

配置文件位于 `.minecraft/config/auto_command/config.json`。如果文件不存在，首次启动时会自动创建。

### 配置格式

```json
{
  "bindings": {
    "binding_name": {
      "triggerMode": "sequential",
      "commands": [
        {
          "rawCommand": "/say Hello, %player_name%!",
          "delay": 0,
          "randomDelay": 0
        },
        {
          "rawCommand": "/give %player_name% diamond 1",
          "delay": 20,
          "randomDelay": 10
        }
      ],
      "key": "key.keyboard.f"
    }
  },
  "variables": {
    "player_name": "Steve"
  }
}
```

### 配置说明

- `bindings`: 包含所有绑定配置的对象
  - `binding_name`: 绑定的名称，可通过该名称触发指令集
    - `triggerMode`: 触发模式
      - `"sequential"`（默认）：顺序执行——触发一次，全部指令按顺序执行
      - `"random"`：随机——每次触发随机执行一条
    - `commands`: 指令数组
      - `rawCommand`: 原始指令字符串，支持变量替换，`&&` 可串联多条
      - `delay`: 固定延迟（游戏刻）
      - `randomDelay`: 随机延迟范围（游戏刻）
    - `key`: 按键绑定，可为 null（表示无按键绑定）
- `variables`: 全局变量定义

## 外部通信

### 通信协议（与 外部信号源 对齐，2026-08-01 定稿）

模组监听 TCP 端口 8888，接受 JSON 信号包，**一行一个包**（`\n` 分隔）。

### 数据包格式

```json
{
  "type": "event",
  "timestamp": 1785588111598,
  "signals": [
    { "id": "demo", "params": { "username": "张三", "count": "10" } }
  ]
}
```

### 字段说明

- `type`: 包分类（`interact` 高频攒批 / `gift` 即时），模组端可按需区分优先级，也可忽略
- `timestamp`: 毫秒级时间戳（时序校验）
- `signals`: 触发器数组（实时突发时可含多条，模组端**排队执行**）
  - `id`: 要触发的绑定名称（必填，唯一匹配字段）
  - `params`: 变量替换值（可选），模组端用 `%username%` 风格替换

### 发送数据包示例（Python）

```python
import socket
import json

# 连接到 AutoCommand 服务器
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.connect(('localhost', 8888))

# 发送信号包（一行一个包）
packet = {
    "type": "event",
    "timestamp": 1785588111598,
    "signals": [
        {"id": "demo", "params": {"username": "张三", "count": "10"}}
    ]
}
s.sendall(json.dumps(packet, ensure_ascii=False).encode('utf-8') + b'\n')

# 关闭连接
s.close()
```

## 使用说明

### 1. 创建绑定

在游戏内通过UI系统创建和配置绑定：
- 打开绑定管理界面
- 点击"新建绑定"按钮
- 填写绑定名称和指令配置
- 保存配置

### 2. 触发绑定

#### 方法一：外部通信

使用 Python 或其他语言发送包含绑定名称的数据包：

```python
import socket
import json

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.connect(('localhost', 8888))
# 发送信号包（一行一个包，signals 内可含多条触发器）
packet = {
    "type": "interact",
    "timestamp": 1785588111598,
    "signals": [
        {"id": "my_binding", "params": {"player_name": "YourName", "item": "diamond"}}
    ]
}
s.sendall(json.dumps(packet, ensure_ascii=False).encode('utf-8') + b'\n')
s.close()
```

#### 方法二：按键触发

在游戏中按下绑定的按键（例如 G 键）。

## 延迟说明

- 固定延迟（`delay`）：指令执行前的固定延迟（游戏刻）
- 随机延迟（`randomDelay`）：在固定延迟基础上添加的随机延迟范围（游戏刻）

**执行时间计算：**
```
实际延迟 = 固定延迟 + 随机(0, 随机延迟)
```

## 开发环境

### 构建项目

```bash
./gradlew build  # Linux/Mac
gradlew.bat build  # Windows
```

### 开发依赖

- Minecraft 1.20.1
- Forge 47.4.16+
- Gson 2.10.1

## 项目状态

**当前阶段：已实现（v0.1.0）**

代码已全部完成并通过 `gradlew build` 构建验证，产物为 `build/libs/auto_command-0.1.0.jar`。

### 已实现的核心功能

1. **TCP 通信模块**：流式按行切包、UTF-8 直出、突发信号全量入队、断线重连清旧连接
2. **双模式触发**：
   - 数据包模式（主）：TCP 信号包 `signals[].id` 匹配绑定 → 执行
   - 手动模式：按键 → C2S 包 → 同一执行管线
3. **指令链**：`&&` 串联多条指令（MC 原生不支持，模组自实现）
4. **执行调度**：全局 FIFO 队列 + tick 驱动，延迟/随机延迟严格按上一条结束时间计算
5. **配置管理**：`config/auto_command/config.json`，自动创建默认配置，`/autocommand reload` 重载
6. **按键绑定模块**：动态 KeyMapping（200ms 防抖），配置即生效
7. **游戏内 UI**：主界面（状态/队列/绑定列表）+ 绑定编辑（按键捕获/指令/延迟）+ 设置（端口/全局变量）

### 使用入口

- 游戏内按 **H** 键或执行 `/autocommand ui` 打开配置界面
- 服务端指令：`/autocommand reload`、`/autocommand status`、`/autocommand queue`、`/autocommand trigger <id>`
- 手动模式：在绑定编辑界面点击「绑定按键」，按任意键/鼠标键即可绑定（ESC 取消）

## 风险评估与应对策略

| 风险 | 影响 | 发生概率 | 应对策略 |
|------|------|----------|----------|
| 网络通信不稳定 | 导致数据传输失败 | 中等 | 实现重连机制和数据缓存 |
| 消息解析不准确 | 指令包匹配失败 | 低 | 完善解析算法和容错处理 |
| 网络连接不稳定 | 数据传输中断 | 中等 | 实现重连机制和数据缓存 |
| 指令包触发延迟 | 互动响应慢 | 低 | 优化0.5秒周期发送逻辑 |
| 数据包格式变化 | 解析失败 | 低 | 版本兼容设计，支持协议升级 |

## 许可证

All Rights Reserved

## 联系方式

如有问题或建议，请通过以下方式联系：

- GitHub: https://github.com/SanZiNEO/AutoCommandss
- Email: 