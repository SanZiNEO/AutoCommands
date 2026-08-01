package com.autocommand.client.ui;

import com.autocommand.Config;
import com.autocommand.NetworkServer;
import com.autocommand.client.AutoCommandClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置界面：TCP 服务器参数（端口/超时/最大连接）+ 全局变量管理。
 * 背景使用原版默认；布局宽度自适应屏幕（80%）。
 */
public final class SettingsScreen extends Screen {
    private static final int VAR_TOP = 96;
    private static final int VAR_BOTTOM_OFFSET = 66;
    private static final int ROW_HEIGHT = 24;

    private final Screen parent;

    private EditBox portBox;
    private EditBox timeoutBox;
    private EditBox maxConnBox;
    private final List<EditBox> varBoxes = new ArrayList<>();
    private int scrollOffset;

    public SettingsScreen(Screen parent) {
        super(Component.literal("AutoCommand 设置"));
        this.parent = parent;
    }

    /** 面板宽度：屏幕 80% */
    private int panelW() {
        return (int) (width * 0.8);
    }

    private int panelX() {
        return (width - panelW()) / 2;
    }

    private int listBottom() {
        return height - VAR_BOTTOM_OFFSET;
    }

    @Override
    protected void init() {
        int px = panelX();
        int py = 30;
        int pW = panelW();

        Config.ServerConfig server = Config.get().server();
        portBox = new EditBox(font, px + 14, py + 36, 70, 18, Component.literal("端口"));
        portBox.setMaxLength(5);
        portBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        portBox.setValue(String.valueOf(server.port));
        timeoutBox = new EditBox(font, px + 96, py + 36, 70, 18, Component.literal("超时(ms)"));
        timeoutBox.setMaxLength(6);
        timeoutBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        timeoutBox.setValue(String.valueOf(server.timeout));
        maxConnBox = new EditBox(font, px + 178, py + 36, 70, 18, Component.literal("最大连接"));
        maxConnBox.setMaxLength(3);
        maxConnBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        maxConnBox.setValue(String.valueOf(server.maxConnections));
        addRenderableWidget(portBox);
        addRenderableWidget(timeoutBox);
        addRenderableWidget(maxConnBox);

        addRenderableWidget(Button.builder(Component.literal("重启 TCP"), b -> restartTcp())
                .bounds(px + 258, py + 34, 60, 18).build());

        varBoxes.clear();
        for (String key : Config.get().variables().keySet()) {
            varBoxes.add(newVarBox(key + "=" + Config.get().variables().get(key)));
        }
        if (varBoxes.isEmpty()) {
            varBoxes.add(newVarBox("server_name=AutoCommand 服务器"));
        }
        for (EditBox box : varBoxes) {
            addRenderableWidget(box);
        }

        addRenderableWidget(Button.builder(Component.literal("＋ 添加变量"), b -> addVar())
                .bounds(px + 14, listBottom() + 4, 100, 18).build());

        int y = height - 29;
        addRenderableWidget(Button.builder(Component.literal("✔ 保存"), b -> save())
                .bounds(width / 2 - 155, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                        Minecraft.getInstance().setScreen(parent != null ? parent : new MainScreen()))
                .bounds(width / 2 + 5, y, 150, 20).build());
    }

    private EditBox newVarBox(String value) {
        EditBox box = new EditBox(font, panelX() + 16, 0, panelW() - 64, 18, Component.literal("key=value"));
        box.setMaxLength(256);
        box.setValue(value);
        return box;
    }

    private void addVar() {
        EditBox box = newVarBox("new_var=value");
        varBoxes.add(box);
        addRenderableWidget(box);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int px = panelX();
        int py = 30;
        int pW = panelW();
        int listBottom = listBottom();
        int right = px + pW;

        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawString(font, "服务器设置", px + 14, py + 16, 0xFF5FC9FF);
        g.drawString(font, "端口", px + 14, py + 26, 0xFF8FA3C0);
        g.drawString(font, "超时(ms)", px + 96, py + 26, 0xFF8FA3C0);
        g.drawString(font, "最大连接", px + 178, py + 26, 0xFF8FA3C0);
        g.drawString(font, "● " + (NetworkServer.get().isRunning() ? "运行中" : "已停止"),
                px + 258, py + 26, NetworkServer.get().isRunning() ? 0xFF3FD97F : 0xFFFF5C5C);

        g.drawString(font, "全局变量（%变量名% 在指令中替换，数据包 params 优先）", px + 14, py + 64, 0xFF5FC9FF);

        int maxScroll = Math.max(0, varBoxes.size() * ROW_HEIGHT - (listBottom - (py + VAR_TOP)));
        scrollOffset = Math.min(scrollOffset, maxScroll);

        for (int i = 0; i < varBoxes.size(); i++) {
            EditBox box = varBoxes.get(i);
            int rowY = py + VAR_TOP + i * ROW_HEIGHT - scrollOffset;
            if (rowY < py + VAR_TOP - ROW_HEIGHT || rowY >= listBottom) {
                continue;
            }
            box.setY(rowY + 3);
            boolean hoverDel = mouseX >= right - 30 && mouseX < right - 10 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            g.drawString(font, "✕", right - 26, rowY + 7, hoverDel ? 0xFFFF5C5C : 0xFF8FA3C0);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int px = panelX();
        int py = 30;
        int right = px + panelW();
        int listBottom = listBottom();
        if (button == 0) {
            for (int i = 0; i < varBoxes.size(); i++) {
                int rowY = py + VAR_TOP + i * ROW_HEIGHT - scrollOffset;
                if (mouseX >= right - 30 && mouseX < right - 10 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                    EditBox box = varBoxes.remove(i);
                    removeWidget(box);
                    if (varBoxes.isEmpty()) {
                        addVar();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int px = panelX();
        int py = 30;
        int listBottom = listBottom();
        if (mouseY >= py + VAR_TOP && mouseY <= listBottom && mouseX >= px + 12 && mouseX <= px + panelW() - 12) {
            int maxScroll = Math.max(0, varBoxes.size() * ROW_HEIGHT - (listBottom - (py + VAR_TOP)));
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(delta * 12)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void restartTcp() {
        Config.ServerConfig server = Config.get().server();
        int port = clampInt(portBox.getValue(), 1, 65535, server.port);
        NetworkServer.get().stop();
        NetworkServer.get().start(port);
    }

    private void save() {
        Config.ServerConfig server = Config.get().server();
        int newPort = clampInt(portBox.getValue(), 1, 65535, server.port);
        int newTimeout = clampInt(timeoutBox.getValue(), 100, 60000, server.timeout);
        int newMax = clampInt(maxConnBox.getValue(), 1, 10, server.maxConnections);
        boolean portChanged = newPort != server.port;

        server.port = newPort;
        server.timeout = newTimeout;
        server.maxConnections = newMax;

        Config.get().variables().clear();
        for (EditBox box : varBoxes) {
            String line = box.getValue().trim();
            if (line.isEmpty()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            Config.get().variables().put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }

        Config.get().save();
        AutoCommandClient.onConfigChanged();
        if (portChanged && NetworkServer.get().isRunning()) {
            NetworkServer.get().stop();
            NetworkServer.get().start(newPort);
        }
        Minecraft.getInstance().setScreen(parent != null ? parent : new MainScreen());
    }

    private static int clampInt(String s, int min, int max, int fallback) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(s.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
