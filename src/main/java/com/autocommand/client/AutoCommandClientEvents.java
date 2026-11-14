package com.autocommand.client;

import com.autocommand.AutoCommand;
import com.autocommand.Config;
import com.mojang.brigadier.CommandDispatcher;
import com.autocommand.client.ui.MainScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端 FORGE 总线事件：ClientTick（按键轮询）、客户端指令（/autocommand ui）。
 * 用 @Mod.EventBusSubscriber(Bus.FORGE) 注册，FML 保证在客户端加载。
 */
@Mod.EventBusSubscriber(modid = AutoCommand.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AutoCommandClientEvents {

    private AutoCommandClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            KeyBindings.tick();
        }
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("autocommand")
                .then(Commands.literal("ui")
                        .executes(ctx -> {
                            Minecraft.getInstance().setScreen(new MainScreen());
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            Config.get().reload();
                            KeyBindings.rebuild();
                            ctx.getSource().sendSuccess(() -> Component.literal("AutoCommands 配置已重载"), false);
                            return 1;
                        })));
    }
}
