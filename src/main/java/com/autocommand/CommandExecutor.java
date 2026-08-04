package com.autocommand;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 指令执行 + 变量替换。
 * 支持：
 * - {@code &&} 指令链：MC 原生不支持串联指令，这里把一段 rawCommand 按 {@code &&} 拆成多条依次执行
 * - {@code %xxx%} 变量替换：数据包 params > 全局 variables > 空串容错
 * - {@code %player_name%}：手动触发者
 */
public final class CommandExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger("autocommand.CommandExecutor");

    private static final Pattern VARIABLE = Pattern.compile("%([^%]+)%");

    private CommandExecutor() {
    }

    /**
     * 拆分指令链：{@code "/say a && /say b"} → ["/say a", "/say b"]。
     * 过滤空白段。
     */
    public static String[] splitChain(String rawCommand) {
        return rawCommand.split("\\s*&&\\s*");
    }

    /**
     * 变量替换。缺失字段替换为空串而不是报错（协议容错约定）。
     */
    public static String resolve(String rawCommand, Map<String, String> params, Map<String, String> variables, String playerName) {
        if (!rawCommand.contains("%")) {
            return rawCommand;
        }
        Matcher matcher = VARIABLE.matcher(rawCommand);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = null;
            if (params != null) {
                value = params.get(name);
            }
            if (value == null && variables != null) {
                value = variables.get(name);
            }
            if (value == null && "player_name".equals(name)) {
                value = playerName;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 以 OP4 服务器控制台权限执行单条指令。
     * 必须在服务器线程调用（scheduler tick 保证）。
     *
     * @return true = 执行成功；false = 指令不存在/参数错误/执行异常
     */
    public static boolean execute(MinecraftServer server, String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        try {
            CommandSourceStack source = server.createCommandSourceStack()
                    .withPermission(4)
                    .withSuppressedOutput();
            int result = server.getCommands().performPrefixedCommand(source, command);
            return result > 0;
        } catch (Exception e) {
            LOGGER.error("指令执行失败: /{}", command, e);
            return false;
        }
    }
}
