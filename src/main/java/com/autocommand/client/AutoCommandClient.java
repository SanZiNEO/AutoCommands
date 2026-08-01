package com.autocommand.client;

import com.autocommand.AutoCommand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端 MOD 总线事件：按键注册。
 * （ClientTick / 客户端指令在 AutoCommandClientEvents 的 FORGE 总线上）
 */
@Mod.EventBusSubscriber(modid = AutoCommand.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AutoCommandClient {

    private AutoCommandClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_UI);
        // 配置已在 FMLCommonSetup 加载，重建动态按键
        KeyBindings.rebuild();
    }

    /** 供 UI 在配置保存后调用 */
    public static void onConfigChanged() {
        KeyBindings.rebuild();
    }
}
