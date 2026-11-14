package com.autocommand.client.ui;

import com.autocommand.Config;
import com.autocommand.NetworkServer;
import com.autocommand.client.AutoCommandClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 设置界面：TCP 端口 + 全局变量。
 * 变量改为「变量名 / 值」两列，滚动时隐藏屏幕外控件。
 */
public final class SettingsScreen extends Screen {
    private static final int PANEL_TOP = 16;
    private static final int ROW_HEIGHT = 26;
    private static final int LIST_TOP = PANEL_TOP + 108;
    private static final int FOOTER_HEIGHT = 42;

    private final Screen parent;
    private EditBox portBox;
    private final List<VarRow> varRows = new ArrayList<>();
    private int scrollOffset;
    private String error = "";

    public SettingsScreen(Screen parent) {
        super(Component.literal("设置"));
        this.parent = parent;
    }

    private int panelW() {
        return Ui.panelWidth(width);
    }

    private int panelX() {
        return Ui.panelX(width);
    }

    private int keyWidth() {
        return Math.max(80, (panelW() - 28 - 60) / 2);
    }

    private int listBottom() {
        return height - FOOTER_HEIGHT - 16;
    }

    @Override
    protected void init() {
        super.init();

        String currentPort = portBox != null
                ? portBox.getValue()
                : String.valueOf(Config.get().server().port);
        List<String[]> currentVariables = new ArrayList<>();
        if (!varRows.isEmpty()) {
            for (VarRow row : varRows) {
                currentVariables.add(new String[]{row.keyBox.getValue(), row.valueBox.getValue()});
            }
        } else {
            for (Map.Entry<String, String> entry : Config.get().variables().entrySet()) {
                currentVariables.add(new String[]{entry.getKey(), entry.getValue()});
            }
        }
        if (currentVariables.isEmpty()) {
            currentVariables.add(new String[]{"", ""});
        }

        int px = panelX();
        int pw = panelW();
        int py = PANEL_TOP;

        portBox = new EditBox(font, px + 92, py + 28, 64, 18, Component.literal("TCP 端口"));
        portBox.setMaxLength(5);
        portBox.setFilter(value -> value.chars().allMatch(Character::isDigit));
        portBox.setValue(currentPort);
        addRenderableWidget(portBox);

        addRenderableWidget(Button.builder(Component.literal("应用端口"), b -> applyTcp())
                .bounds(px + pw - 14 - 100, py + 28, 100, 18).build());

        varRows.clear();
        for (String[] data : currentVariables) {
            addVarRow(data[0], data[1]);
        }

        addRenderableWidget(Button.builder(Component.literal("＋ 添加变量"), b -> {
            addVarRow("", "");
            scrollOffset = Integer.MAX_VALUE;
        }).bounds(px + 14, height - 54, 100, 18).build());

        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("✔ 保存"), b -> save())
                .bounds(width / 2 - 154, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(width / 2 + 4, y, 150, 20).build());
    }

    private void addVarRow(String key, String value) {
        VarRow row = new VarRow(key, value);
        varRows.add(row);
        addRenderableWidget(row.keyBox);
        addRenderableWidget(row.valueBox);
        addRenderableWidget(row.removeButton);
    }

    private void removeVarRow(VarRow row) {
        if (!varRows.remove(row)) {
            return;
        }
        removeWidget(row.keyBox);
        removeWidget(row.valueBox);
        removeWidget(row.removeButton);
        if (varRows.isEmpty()) {
            addVarRow("", "");
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (portBox != null) {
            portBox.tick();
        }
        for (VarRow row : varRows) {
            row.keyBox.tick();
            row.valueBox.tick();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int px = panelX();
        int pw = panelW();
        int py = PANEL_TOP;
        int right = px + pw - 14;
        int listTop = LIST_TOP;
        int listBottom = Math.max(listTop + ROW_HEIGHT, listBottom());

        Ui.panel(g, px, py, pw, height - 24);
        Ui.bar(g, px, py, pw, 28);
        Ui.bar(g, px, height - 36, pw, 28);
        g.drawString(font, title.getString(), px + 14, py + 12, Ui.TEXT, false);

        boolean running = NetworkServer.get().isRunning();
        String status = running ? "TCP 运行中 · " + NetworkServer.get().getPort() : "TCP 已停止";
        g.drawString(font, status, right - font.width(status), py + 12,
                running ? Ui.SUCCESS : Ui.ERROR, false);

        g.drawString(font, "TCP 端口", px + 14, py + 32, Ui.MUTED, false);
        g.fill(px + 12, py + 56, px + pw - 12, py + 57, Ui.DIVIDER);

        g.drawString(font, "全局变量", px + 14, py + 66, Ui.ACCENT, false);
        g.drawString(font, "数据包 params 优先于这里的值", px + 14, py + 80, Ui.MUTED, false);

        int kw = keyWidth();
        g.drawString(font, "变量名", px + 14, listTop - 14, Ui.MUTED, false);
        g.drawString(font, "值", px + 14 + kw + 6, listTop - 14, Ui.MUTED, false);

        int maxScroll = Math.max(0, varRows.size() * ROW_HEIGHT - (listBottom - listTop));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        for (int i = 0; i < varRows.size(); i++) {
            VarRow row = varRows.get(i);
            int rowY = listTop + i * ROW_HEIGHT - scrollOffset;
            boolean visible = rowY + ROW_HEIGHT > listTop && rowY < listBottom;
            row.setY(rowY);
            row.setVisible(visible);
        }

        if (maxScroll > 0) {
            int trackX = px + pw - 8;
            int trackHeight = listBottom - listTop;
            g.fill(trackX, listTop, trackX + 3, listBottom, 0x30FFFFFF);
            int thumbHeight = Math.max(12, trackHeight * trackHeight / (trackHeight + maxScroll));
            int thumbY = listTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, 0x80FFFFFF);
        }

        if (!error.isEmpty()) {
            g.drawString(font, error, right - font.width(error), py + 66, Ui.ERROR, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listTop = LIST_TOP;
        int listBottom = Math.max(listTop + ROW_HEIGHT, listBottom());
        if (mouseX >= panelX() && mouseX <= panelX() + panelW()
                && mouseY >= listTop && mouseY <= listBottom) {
            int maxScroll = Math.max(0, varRows.size() * ROW_HEIGHT - (listBottom - listTop));
            scrollOffset = Math.max(0, Math.min(maxScroll,
                    scrollOffset - (int) Math.round(delta * 18)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent != null ? parent : new MainScreen());
    }

    private void applyTcp() {
        int port = clampInt(portBox.getValue(), 1, 65535, Config.get().server().port);
        Config.get().server().port = port;
        Config.get().save();

        NetworkServer.get().stop();
        NetworkServer.get().start(port);
        if (!NetworkServer.get().isRunning()) {
            error = "TCP 启动失败: " + NetworkServer.get().getLastError();
        } else {
            error = "";
        }
    }

    private void save() {
        Config.ServerConfig server = Config.get().server();
        int newPort = clampInt(portBox.getValue(), 1, 65535, server.port);
        boolean portChanged = newPort != server.port;
        server.port = newPort;

        Config.get().variables().clear();
        for (VarRow row : varRows) {
            String key = row.keyBox.getValue().trim();
            if (key.isEmpty()) {
                continue;
            }
            Config.get().variables().put(key, row.valueBox.getValue().trim());
        }
        Config.get().save();
        AutoCommandClient.onConfigChanged();

        if (portChanged || !NetworkServer.get().isRunning()) {
            NetworkServer.get().stop();
            NetworkServer.get().start(newPort);
            if (!NetworkServer.get().isRunning()) {
                error = "TCP 启动失败: " + NetworkServer.get().getLastError();
                return;
            }
        }
        onClose();
    }

    private static int clampInt(String value, int min, int max, int fallback) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private final class VarRow {
        private final EditBox keyBox;
        private final EditBox valueBox;
        private final Button removeButton;

        VarRow(String key, String value) {
            int px = panelX();
            int right = px + panelW() - 14;
            int kw = keyWidth();

            keyBox = new EditBox(font, px + 14, 0, kw, 18, Component.literal("变量名"));
            keyBox.setMaxLength(64);
            keyBox.setValue(key);

            valueBox = new EditBox(font, px + 14 + kw + 6, 0, kw, 18, Component.literal("值"));
            valueBox.setMaxLength(256);
            valueBox.setValue(value);

            removeButton = Button.builder(Component.literal("✕"), b -> removeVarRow(this))
                    .bounds(right - 32, 0, 20, 18).build();
        }

        void setY(int y) {
            keyBox.setY(y + 4);
            valueBox.setY(y + 4);
            removeButton.setY(y + 4);
        }

        void setVisible(boolean visible) {
            keyBox.visible = visible;
            keyBox.active = visible;
            valueBox.visible = visible;
            valueBox.active = visible;
            removeButton.visible = visible;
            removeButton.active = visible;
        }
    }
}
