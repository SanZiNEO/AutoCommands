package com.autocommand.client;

import com.autocommand.Config;
import com.autocommand.NetworkChannel;
import com.autocommand.client.ui.MainScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端按键管理（手动模式）：
 * - 打开 UI 键（固定注册，进游戏按键设置）
 * - 动态绑定键：配置里带 key 的绑定 → 创建 KeyMapping（构造即注册），
 *   UI 保存配置后调用 {@link #rebuild()} 重建
 * - 检测方式：isDown() + wasPressed 边缘检测（参考 参考模组 1.20），200ms 防抖
 */
public final class KeyBindings {
    private static final Logger LOGGER = LoggerFactory.getLogger("autocommand.KeyBindings");

    public static final String CATEGORY = "key.categories.autocommand";
    public static final String OPEN_UI_KEY_NAME = "key.autocommand.ui";
    private static final String BIND_KEY_PREFIX = "key.autocommand.bind.";

    private static final long COOLDOWN_MS = 200L;

    /** 打开配置界面键：默认 H */
    public static final KeyMapping OPEN_UI = new KeyMapping(OPEN_UI_KEY_NAME, InputConstants.KEY_H, CATEGORY);

    /** 绑定 id → 动态 KeyMapping + 上次按下状态 */
    private static final Map<String, KeyMapping> bindingKeys = new HashMap<>();
    private static final Map<String, Boolean> pressedState = new HashMap<>();
    private static final Map<String, Long> lastTrigger = new HashMap<>();

    private static boolean uiKeyPressed;

    private KeyBindings() {
    }

    /** 根据当前配置重建动态按键（配置加载/保存后调用） */
    public static void rebuild() {
        bindingKeys.clear();
        pressedState.clear();
        for (Map.Entry<String, Config.Binding> entry : Config.get().snapshot().entrySet()) {
            String keyStr = entry.getValue().key;
            if (keyStr == null || keyStr.isBlank()) {
                continue;
            }
            try {
                InputConstants.Key key = InputConstants.getKey(keyStr);
                KeyMapping mapping = new KeyMapping(BIND_KEY_PREFIX + entry.getKey(), key.getType(), key.getValue(), CATEGORY);
                bindingKeys.put(entry.getKey(), mapping);
                pressedState.put(entry.getKey(), false);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("绑定 {} 的按键配置非法，已忽略: {}", entry.getKey(), keyStr);
            }
        }
        LOGGER.info("已重建 {} 个动态按键绑定", bindingKeys.size());
    }

    /** 客户端每 tick 轮询按键（ClientTickEvent END） */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }

        // 打开 UI 键：按下沿触发
        boolean uiDown = OPEN_UI.isDown();
        if (uiDown && !uiKeyPressed) {
            uiKeyPressed = true;
            if (mc.screen == null) {
                LOGGER.info("按键打开 AutoCommand 界面");
                mc.setScreen(new MainScreen());
            }
        } else if (!uiDown) {
            uiKeyPressed = false;
        }

        // 绑定键：按下沿触发 + 防抖
        long now = System.currentTimeMillis();
        for (Map.Entry<String, KeyMapping> entry : bindingKeys.entrySet()) {
            String id = entry.getKey();
            boolean down = entry.getValue().isDown();
            boolean was = pressedState.getOrDefault(id, false);
            if (down && !was) {
                Long prev = lastTrigger.get(id);
                if (prev != null && now - prev < COOLDOWN_MS) {
                    LOGGER.debug("绑定 {} 触发被防抖拦截", id);
                } else {
                    lastTrigger.put(id, now);
                    LOGGER.info("按键触发绑定: {}", id);
                    NetworkChannel.get().sendToServer(new NetworkChannel.TriggerBindingPacket(id));
                }
            }
            pressedState.put(id, down);
        }
    }
}
