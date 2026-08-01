package com.autocommand.client.ui;

import com.autocommand.Config;
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
 * 绑定编辑界面：绑定 ID、触发模式（顺序/随机）、指令列表（指令 / 固定延迟 / 随机延迟，支持 && 串联）。
 * 背景使用原版默认；布局宽度自适应屏幕（80%）；按键绑定在主界面行内完成。
 */
public final class EditBindingScreen extends Screen {
    private static final int LIST_TOP = 96;
    private static final int LIST_BOTTOM_OFFSET = 66;
    private static final int ROW_HEIGHT = 26;
    private static final String[] MODES = {"sequential", "random"};
    private static final String[] MODE_LABELS = {"触发: 顺序", "触发: 随机"};

    private final Screen parent;
    private final String originalId;

    /** 构造函数时 font 尚未初始化，原始数据先存字符串，init() 中再建 EditBox */
    private final List<String[]> rowData = new ArrayList<>();

    private EditBox idBox;
    private String triggerMode = "sequential";
    private Button modeButton;
    private final List<CommandRow> rows = new ArrayList<>();
    private int scrollOffset;

    /** 面板宽度：屏幕 80% */
    private int panelW() {
        return (int) (width * 0.8);
    }

    private int panelX() {
        return (width - panelW()) / 2;
    }

    private static final class CommandRow {
        final EditBox cmdBox;
        final EditBox delayBox;
        final EditBox randomBox;

        CommandRow(net.minecraft.client.gui.Font font, int listX, int panelW, int y, String cmd, String delay, String random) {
            // 行内从右往左：✕(20) 随机延迟label(32) 随机框(38) 延迟label(24) 延迟框(38) 指令框(剩余)
            int right = listX + panelW;
            cmdBox = new EditBox(font, listX + 2, y + 3, panelW - 180, 18, Component.literal("指令"));
            cmdBox.setMaxLength(512);
            cmdBox.setValue(cmd);
            delayBox = new EditBox(font, right - 150, y + 3, 38, 18, Component.literal("固定延迟"));
            delayBox.setMaxLength(6);
            delayBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
            delayBox.setValue(delay);
            randomBox = new EditBox(font, right - 74, y + 3, 38, 18, Component.literal("随机延迟"));
            randomBox.setMaxLength(6);
            randomBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
            randomBox.setValue(random);
        }

        void setY(int y) {
            cmdBox.setY(y + 3);
            delayBox.setY(y + 3);
            randomBox.setY(y + 3);
        }
    }

    public EditBindingScreen(Screen parent, String bindingId) {
        super(Component.literal(bindingId == null ? "新建绑定" : "编辑绑定"));
        this.parent = parent;
        this.originalId = bindingId;
        if (bindingId != null) {
            Config.Binding binding = Config.get().getBinding(bindingId);
            if (binding != null) {
                this.triggerMode = binding.triggerMode != null ? binding.triggerMode : "sequential";
                for (Config.CommandEntry entry : binding.commands) {
                    rowData.add(new String[]{entry.rawCommand, String.valueOf(entry.delay), String.valueOf(entry.randomDelay)});
                }
            }
        }
        if (rowData.isEmpty()) {
            rowData.add(new String[]{"/say 收到信号！", "0", "0"});
        }
    }

    private int listBottom() {
        return height - LIST_BOTTOM_OFFSET;
    }

    @Override
    protected void init() {
        int px = panelX();
        int py = 30;
        int pW = panelW();

        idBox = new EditBox(font, px + 14, py + 20, pW - 28, 18, Component.literal("绑定 ID"));
        idBox.setMaxLength(64);
        if (originalId != null) {
            idBox.setValue(originalId);
        } else {
            idBox.setHint(Component.literal("例如 demo"));
        }
        addRenderableWidget(idBox);

        modeButton = Button.builder(Component.literal(modeLabel()), b -> {
                    int idx = List.of(MODES).indexOf(triggerMode);
                    triggerMode = MODES[(idx + 1) % MODES.length];
                    modeButton.setMessage(Component.literal(modeLabel()));
                })
                .bounds(px + 14, py + 48, 130, 18).build();
        addRenderableWidget(modeButton);

        rows.clear();
        for (String[] data : rowData) {
            CommandRow row = new CommandRow(font, px, pW, py + LIST_TOP, data[0], data[1], data[2]);
            rows.add(row);
            addRenderableWidget(row.cmdBox);
            addRenderableWidget(row.delayBox);
            addRenderableWidget(row.randomBox);
        }

        addRenderableWidget(Button.builder(Component.literal("＋ 添加指令"), b -> addRow())
                .bounds(px + 14, listBottom() + 4, 100, 18).build());

        int y = height - 29;
        addRenderableWidget(Button.builder(Component.literal("✔ 保存"), b -> save())
                .bounds(width / 2 - 155, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                        Minecraft.getInstance().setScreen(parent != null ? parent : new MainScreen()))
                .bounds(width / 2 + 5, y, 150, 20).build());
    }

    private String modeLabel() {
        int idx = List.of(MODES).indexOf(triggerMode);
        return idx < 0 ? MODE_LABELS[0] : MODE_LABELS[idx];
    }

    private void addRow() {
        CommandRow row = new CommandRow(font, panelX(), panelW(), 30 + LIST_TOP, "/say 信号触发", "0", "0");
        rows.add(row);
        addRenderableWidget(row.cmdBox);
        addRenderableWidget(row.delayBox);
        addRenderableWidget(row.randomBox);
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
        g.drawString(font, "绑定 ID", px + 14, py + 10, 0xFF8FA3C0);
        g.drawString(font, "触发模式（多条指令时生效）", px + 154, py + 51, 0xFF8FA3C0);
        g.drawString(font, "指令列表（支持 && 串联 · %变量% 替换）", px + 14, py + 78, 0xFF8FA3C0);

        int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (listBottom - (py + LIST_TOP)));
        scrollOffset = Math.min(scrollOffset, maxScroll);

        for (int i = 0; i < rows.size(); i++) {
            CommandRow row = rows.get(i);
            int rowY = py + LIST_TOP + i * ROW_HEIGHT - scrollOffset;
            if (rowY < py + LIST_TOP - ROW_HEIGHT || rowY >= listBottom) {
                continue;
            }
            row.setY(rowY);
            g.drawString(font, "延迟", right - 174, rowY + 7, 0xFF8FA3C0);
            g.drawString(font, "随机延迟", right - 112, rowY + 7, 0xFF8FA3C0);
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
            for (int i = 0; i < rows.size(); i++) {
                int rowY = py + LIST_TOP + i * ROW_HEIGHT - scrollOffset;
                if (mouseX >= right - 30 && mouseX < right - 10 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                    removeRow(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void removeRow(int index) {
        CommandRow row = rows.remove(index);
        removeWidget(row.cmdBox);
        removeWidget(row.delayBox);
        removeWidget(row.randomBox);
        if (rows.isEmpty()) {
            addRow();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int px = panelX();
        int py = 30;
        int listBottom = listBottom();
        if (mouseY >= py + LIST_TOP && mouseY <= listBottom && mouseX >= px + 12 && mouseX <= px + panelW() - 12) {
            int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (listBottom - (py + LIST_TOP)));
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(delta * 15)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void save() {
        String id = idBox.getValue().trim();
        if (id.isEmpty()) {
            return;
        }
        Config.Binding binding = new Config.Binding();
        binding.triggerMode = triggerMode;
        for (CommandRow row : rows) {
            String cmd = row.cmdBox.getValue().trim();
            if (cmd.isEmpty()) {
                continue;
            }
            binding.commands.add(new Config.CommandEntry(cmd,
                    parseInt(row.delayBox.getValue()), parseInt(row.randomBox.getValue())));
        }
        Config config = Config.get();
        if (originalId != null && !originalId.equals(id)) {
            config.removeBinding(originalId);
        }
        config.putBinding(id, binding);
        config.save();
        AutoCommandClient.onConfigChanged();
        Minecraft.getInstance().setScreen(parent != null ? parent : new MainScreen());
    }

    private static int parseInt(String s) {
        try {
            return Math.max(0, Integer.parseInt(s.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
