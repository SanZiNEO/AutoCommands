# AutoCommand Mod

AutoCommand 是一个 Minecraft 1.20.1 Forge 模组，专为与外部脚本联动设计。它允许您绑定指令集到特定的名称，并通过网络通信或按键触发这些指令集，支持延迟和随机延迟执行。该模组特别适合用于外部平台实时-Minecraft互动系统，实现观众与游戏的实时互动。

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
      "commandSet": {
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
        ]
      },
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
    - `commandSet`: 指令集配置
      - `commands`: 指令数组
        - `rawCommand`: 原始指令字符串，支持变量替换
        - `delay`: 固定延迟（游戏刻）
        - `randomDelay`: 随机延迟范围（游戏刻）
    - `key`: 按键绑定，可为 null（表示无按键绑定）
- `variables`: 全局变量定义

## 外部通信

### 通信协议

模组监听 TCP 端口 8888，接受 JSON 格式的数据包。

### 数据包格式

```json
{
  "name": "binding_name",
  "variables": {
    "player_name": "Notch",
    "item": "emerald"
  }
}
```

### 字段说明

- `name`: 要触发的绑定名称（必填）
- `variables`: 变量替换值（可选）

### 发送数据包示例（Python）

```python
import socket
import json

# 连接到 AutoCommand 服务器
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.connect(('localhost', 8888))

# 发送数据包
data = {
    "name": "example_binding",
    "variables": {
        "player_name": "Notch"
    }
}
s.sendall(json.dumps(data).encode('utf-8'))

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
s.sendall(json.dumps({
    "name": "my_binding",
    "variables": {
        "player_name": "YourName",
        "item": "diamond"
    }
}).encode('utf-8'))
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

**当前阶段：规划阶段**

项目目前处于规划阶段，已完成架构设计和功能需求分析。开发团队正在准备开始编码工作，重点是实现以下核心功能：

### 待实现的核心功能

1. **通信管理模块**：实现稳定的TCP服务器
2. **执行调度模块**：管理指令集的调度和执行
3. **配置管理模块**：加载和保存配置文件
4. **按键绑定模块**：游戏内按键绑定系统
5. **游戏内UI系统**：提供直观的图形界面

### 开发时间表

- **阶段一（架构完善）**：预计1-2周
- **阶段二（功能增强）**：预计2-3周
- **阶段三（集成测试）**：预计1-2周

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