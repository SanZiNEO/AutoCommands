package com.autocommand;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置管理：读取/保存 {@code config/auto_command/config.json}。
 * 所有编辑操作在主线程/UI 线程进行（synchronized），
 * TCP 线程只读不可变快照（volatile + 不可变 Map），无锁竞争。
 */
public final class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("autocommand.Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();


    private static final Config INSTANCE = new Config();

    private final Path file = FMLPaths.CONFIGDIR.get().resolve("auto_command/config.json");

    /** 编辑态数据（UI / 服务端指令修改） */
    private Map<String, Binding> bindings = new LinkedHashMap<>();
    private Map<String, String> variables = new LinkedHashMap<>();
    private ServerConfig server = new ServerConfig();

    /** 读快照（不可变） */
    private volatile Map<String, Binding> bindingSnapshot = Map.of();
    private volatile Map<String, String> variableSnapshot = Map.of();

    public static Config get() {
        return INSTANCE;
    }
    /** 从磁盘加载；文件不存在时写入默认配置 */
    public synchronized void load() {
        if (!Files.exists(file)) {
            bindings = defaultBindings();
            variables = defaultVariables();
            server = new ServerConfig();
            save();
            LOGGER.info("已创建默认配置文件: {}", file);
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data == null) {
                throw new JsonSyntaxException("配置文件为空");
            }
            bindings = data.bindings != null ? data.bindings : new LinkedHashMap<>();
            variables = data.variables != null ? data.variables : new LinkedHashMap<>();
            server = data.server != null ? data.server : new ServerConfig();
            refreshSnapshot();
            LOGGER.info("已加载配置文件，共 {} 个绑定", bindings.size());
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("读取配置文件失败: {}", file, e);
        }
    }

    /** 从磁盘重新加载（保留当前数据兜底） */
    public synchronized void reload() {
        load();
    }

    // ============ 写入 ============

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            ConfigData data = new ConfigData();
            data.bindings = bindings;
            data.variables = variables;
            data.server = server;
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
            refreshSnapshot();
            LOGGER.info("配置已保存: {}", file);
        } catch (IOException e) {
            LOGGER.error("保存配置文件失败: {}", file, e);
        }
    }

    private void refreshSnapshot() {
        bindingSnapshot = Map.copyOf(bindings);
        variableSnapshot = Map.copyOf(variables);
    }

    // ============ 查询（线程安全，任意线程可调） ============

    /** 按绑定 id 取绑定；不存在返回 null */
    public Binding getBinding(String id) {
        return bindingSnapshot.get(id);
    }

    public boolean hasBinding(String id) {
        return bindingSnapshot.containsKey(id);
    }

    /** 快照视图（只读） */
    public Map<String, Binding> snapshot() {
        return bindingSnapshot;
    }
    public Map<String, String> variables() {
        return variables;
    }

    /** 变量读快照（任意线程安全） */
    public Map<String, String> variablesSnapshot() {
        return variableSnapshot;
    }

    public ServerConfig server() {
        return server;
    }

    public Path file() {
        return file;
    }

    // ============ 编辑（UI / 指令使用，主线程） ============

    public synchronized void putBinding(String id, Binding binding) {
        bindings.put(id, binding);
        refreshSnapshot();
    }

    public synchronized void removeBinding(String id) {
        bindings.remove(id);
        refreshSnapshot();
    }

    // ============ 默认配置 ============

    private static Map<String, Binding> defaultBindings() {
        Map<String, Binding> map = new LinkedHashMap<>();
        Binding demo = new Binding();
        demo.key = "key.keyboard.g";
        demo.commands.add(new CommandEntry("/say 感谢 %username% 的礼物！", 0, 0));
        demo.commands.add(new CommandEntry("/summon minecraft:creeper ~ ~ ~", 10, 5));
        map.put("demo", demo);
        return map;
    }

    private static Map<String, String> defaultVariables() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("server_name", "AutoCommand 服务器");
        return map;
    }

    // ============ 数据模型 ============

    /** 与配置文件顶层结构一致 */
    public static final class ConfigData {
        public Map<String, Binding> bindings;
        public Map<String, String> variables;
        public ServerConfig server;
    }

    /** 单个绑定：指令集 + 可选按键（手动触发）+ 触发模式 */
    public static final class Binding {
        public List<CommandEntry> commands = new ArrayList<>();
        /** 按键映射字符串（如 "key.keyboard.g" / "key.mouse.1"），null 表示纯数据包绑定 */
        public String key;
        /**
         * 触发模式：
         * "sequential" — 顺序执行：触发一次，全部指令按顺序执行（默认）
         * "random"     — 随机：每次触发随机执行一条
         */
        public String triggerMode = "sequential";

        public Binding() {
        }

        public Binding(List<CommandEntry> commands, String key) {
            this.commands = new ArrayList<>(commands);
            this.key = key;
        }
    }

    /** 单条指令：支持 && 串联，delay/randomDelay 单位游戏刻 */
    public static final class CommandEntry {
        public String rawCommand = "";
        public int delay = 0;
        public int randomDelay = 0;

        public CommandEntry() {
        }

        public CommandEntry(String rawCommand, int delay, int randomDelay) {
            this.rawCommand = rawCommand;
            this.delay = delay;
            this.randomDelay = randomDelay;
        }
    }

    /** TCP 服务器配置 */
    public static final class ServerConfig {
        public int port = 8888;
        public int timeout = 5000;
        public int maxConnections = 1;
    }
}
