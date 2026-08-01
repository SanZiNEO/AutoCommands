package com.autocommand;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * AutoCommand 模组入口。
 *
 * 信号 → 指令映射器，双模式触发：
 * - 数据包模式（主）：TCP 接收 外部信号源 信号包，signals[].id 匹配绑定
 * - 手动模式：按键 → C2S 包 → 同一执行管线
 * 外加全局排队调度与 && 指令链。
 */
@Mod(AutoCommand.MOD_ID)
public final class AutoCommand {
    public static final String MOD_ID = "auto_command";
    public static final Logger LOGGER = LoggerFactory.getLogger("autocommand");

    @SuppressWarnings("removal")
    public AutoCommand() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);

        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.addListener(this::onServerStarted);
        forgeBus.addListener(this::onServerStopped);
        forgeBus.addListener(this::onServerTick);
        forgeBus.addListener(this::onRegisterCommands);
    }

    /** 双端通用初始化：注册网络通道、加载配置（客户端按键需要读配置） */
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkChannel.register();
            Config.get().load();
        });
        LOGGER.info("AutoCommand 初始化完成");
    }

    private void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Config.get().load();
        NetworkServer.get().start(Config.get().server().port);
    }

    private void onServerStopped(ServerStoppedEvent event) {
        NetworkServer.get().stop();
        ExecutionScheduler.get().clear();
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ExecutionScheduler.get().tick(event.getServer());
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("autocommand")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            Config.get().reload();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("AutoCommand 配置已重载（" + Config.get().snapshot().size() + " 个绑定）"), false);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            NetworkServer ns = NetworkServer.get();
                            Component status = Component.literal(
                                    "AutoCommand 状态: TCP " + (ns.isRunning() ? "§a运行中§r" : "§c已停止§r")
                                            + (ns.isRunning() ? " 端口 " + ns.getPort() + " 连接 " + ns.connectedCount() : "")
                                            + " | 队列 " + ExecutionScheduler.get().queueSize()
                                            + " | 绑定 " + Config.get().snapshot().size());
                            ctx.getSource().sendSuccess(() -> status, false);
                            return 1;
                        }))
                .then(Commands.literal("queue")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("AutoCommand 指令队列: " + ExecutionScheduler.get().queueSize() + " 条待执行"),
                                    false);
                            return 1;
                        }))
                .then(Commands.literal("trigger")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    boolean hit = ExecutionScheduler.get().trigger(
                                            ctx.getSource().getServer(), id, Map.of(),
                                            ctx.getSource().getTextName());
                                    if (hit) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("已触发绑定: " + id), false);
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal("未找到绑定: " + id));
                                    }
                                    return hit ? 1 : 0;
                                }))));
    }
}
