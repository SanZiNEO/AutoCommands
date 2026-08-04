package com.autocommand;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 执行调度器：全局 FIFO 队列 + 服务端 tick 驱动。
 *
 * - Socket 线程 / 按键线程只调用 {@link #trigger} 入队，绝不直接执行 MC 指令
 * - 服务端 tick 时检查队首是否到期，到期则主线程执行
 * - 延迟语义：每条指令的计划时间 = 上一条指令计划时间 + 该条延迟，
 *   即"严格按上一条指令的结束时间计算"（README 约定）
 * - 指令链（&& 分隔）在入队时拆分，链内子指令间隔 1 tick 保证顺序
 *
 * 触发模式（Config.Binding.triggerMode）：
 * - "sequential" — 顺序执行：触发一次，全部指令按顺序执行（默认）
 * - "random"     — 随机：每次触发随机执行一条
 */
public final class ExecutionScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger("autocommand.Scheduler");
    private static final int MAX_PER_TICK = 128;

    private static final ExecutionScheduler INSTANCE = new ExecutionScheduler();

    private final ConcurrentLinkedQueue<Task> queue = new ConcurrentLinkedQueue<>();
    private final Random random = new Random();

    public static ExecutionScheduler get() {
        return INSTANCE;
    }

    private ExecutionScheduler() {
    }

    /**
     * 触发一个绑定：查配置 → 按触发模式选择指令 → 指令链拆分 → 入队。
     * 线程安全，可从 TCP 线程 / 按键线程 / 服务端指令调用。
     *
     * @param receipt 回执来源（TCP 包携带，用于执行失败回执）；null = 无需回执
     * @return 是否命中绑定
     */
    public boolean trigger(MinecraftServer server, String bindingId, Map<String, String> params, String playerName,
                           Packet.ReceiptInfo receipt) {
        Config.Binding binding = Config.get().getBinding(bindingId);
        if (binding == null) {
            LOGGER.debug("信号 {} 未匹配到绑定", bindingId);
            return false;
        }
        if (binding.commands.isEmpty()) {
            return true;
        }

        List<Config.CommandEntry> selected = selectCommands(binding);
        int cursor = server.getTickCount();
        for (Config.CommandEntry entry : selected) {
            int delay = entry.delay + (entry.randomDelay > 0 ? random.nextInt(entry.randomDelay + 1) : 0);
            cursor += delay;

            String[] chain = CommandExecutor.splitChain(entry.rawCommand);
            for (String part : chain) {
                if (part.isBlank()) {
                    continue;
                }
                String resolved = CommandExecutor.resolve(part.trim(), params, Config.get().variablesSnapshot(), playerName);
                queue.add(new Task(cursor, resolved, bindingId, receipt));
                cursor++; // 链内相邻指令间隔 1 tick，保证顺序
            }
        }
        LOGGER.debug("绑定 {} 已入队 {} 条指令（模式 {}）", bindingId, queue.size(), binding.triggerMode);
        return true;
    }

    /** 按触发模式挑选本次要执行的指令条目 */
    private List<Config.CommandEntry> selectCommands(Config.Binding binding) {
        List<Config.CommandEntry> commands = binding.commands;
        if (commands.size() <= 1 || !"random".equals(binding.triggerMode)) {
            return commands;
        }
        return List.of(commands.get(random.nextInt(commands.size())));
    }

    /** 服务端每 tick 调用：执行所有已到期的队首任务 */
    public void tick(MinecraftServer server) {
        int now = server.getTickCount();
        int executed = 0;
        Task task;
        while (executed < MAX_PER_TICK && (task = queue.peek()) != null && task.dueTick <= now) {
            queue.poll();
            executed++;
            boolean ok = CommandExecutor.execute(server, task.command);
            if (!ok && task.receipt != null) {
                // 指令执行失败：补发 error 回执（仅失败条目）
                NetworkServer.get().sendErrorReceipt(task.receipt, "command failed: " + task.command);
            }
        }
    }

    /** 队列中待执行指令数（UI/status 显示） */
    public int queueSize() {
        return queue.size();
    }

    /** 清空队列（reload 时可选） */
    public void clear() {
        queue.clear();
    }

    private record Task(int dueTick, String command, String bindingId, Packet.ReceiptInfo receipt) {
    }
}
