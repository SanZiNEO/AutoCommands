package com.autocommand.client.ui;

import com.autocommand.Config;
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

/**
 * 绑定编辑界面：绑定 ID、触发模式、指令列表（指令 / 固定延迟 / 随机延迟）。
 * 行内使用原版 EditBox + Button；滚动时只显示可视行，避免屏幕外控件接收点击。
 */
public final class EditBindingScreen extends Screen {
    private static final int PANEL_TOP = 16;
    private static final int ROW_HEIGHT = 28;
    private static final int LIST_TOP = PANEL_TOP + 104;
    private static final int FOOTER_HEIGHT = 42;
    private static final String[] MODES = {"sequential", "random"};
    private static final String[] MODE_LABELS = {"触发: 顺序", "触发: 随机"};

    private final Screen parent;
    private final String originalId;
    private final List<String[]> rowData = new ArrayList<>();

    private EditBox idBox;
    private String triggerMode = "sequential";
    private Button modeButton;
    private final List<CommandRow> rows = new ArrayList<>();
    private int scrollOffset;
    private String error = "";

    public EditBindingScreen(Screen parent, String bindingId) {
        super(Component.literal(bindingId == null ? "新建绑定" : "编辑绑定"));
        this.parent = parent;
        this.originalId = bindingId;
        if (bindingId != null) {
            Config.Binding binding = Config.get().getBinding(bindingId);
            if (binding != null) {
                this.triggerMode = binding.triggerMode != null ? binding.triggerMode : "sequential";
                for (Config.CommandEntry entry : binding.commands) {
                    rowData.add(new String[]{
                            entry.rawCommand,
                            String.valueOf(entry.delay),
                            String.valueOf(entry.randomDelay)
                    });
                }
            }
        }
        if (rowData.isEmpty()) {
            rowData.add(new String[]{"/say 收到信号！", "0", "0"});
        }
    }

    private int panelW() {
        return Ui.panelWidth(width);
    }

    private int panelX() {
        return Ui.panelX(width);
    }

    private int rowWidth() {
        return panelW() - 28;
    }

    private int listBottom() {
        return height - FOOTER_HEIGHT - 16;
    }

    @Override
    protected void init() {
        super.init();

        String currentId = idBox != null ? idBox.getValue() : null;
        if (!rows.isEmpty()) {
            rowData.clear();
            for (CommandRow row : rows) {
                rowData.add(new String[]{
                        row.cmdBox.getValue(),
                        row.delayBox.getValue(),
                        row.randomBox.getValue()
                });
            }
        }

        int px = panelX();
        int pw = panelW();
        int py = PANEL_TOP;

        idBox = new EditBox(font, px + 92, py + 28, pw - 92 - 14, 18, Component.literal("绑定 ID"));
        idBox.setMaxLength(64);
        if (currentId != null) {
            idBox.setValue(currentId);
        } else if (originalId != null) {
            idBox.setValue(originalId);
        } else {
            idBox.setHint(Component.literal("例如 demo_binding"));
        }
        addRenderableWidget(idBox);

        modeButton = Button.builder(Component.literal(modeLabel()), b -> {
            int index = List.of(MODES).indexOf(triggerMode);
            triggerMode = MODES[(index + 1) % MODES.length];
            modeButton.setMessage(Component.literal(modeLabel()));
        }).bounds(px + 14, py + 52, 120, 18).build();
        addRenderableWidget(modeButton);

        rows.clear();
        for (String[] data : rowData) {
            addRowWidgets(data[0], data[1], data[2]);
        }
        if (rows.isEmpty()) {
            addRowWidgets("/say 收到信号！", "0", "0");
        }

        addRenderableWidget(Button.builder(Component.literal("＋ 添加指令"), b -> {
            addRowWidgets("/say 信号触发", "0", "0");
            scrollOffset = Integer.MAX_VALUE;
        }).bounds(px + 14, height - 54, 100, 18).build());

        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("✔ 保存"), b -> save())
                .bounds(width / 2 - 154, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(width / 2 + 4, y, 150, 20).build());
    }

    private void addRowWidgets(String command, String delay, String random) {
        CommandRow row = new CommandRow(command, delay, random);
        rows.add(row);
        addRenderableWidget(row.cmdBox);
        addRenderableWidget(row.delayBox);
        addRenderableWidget(row.randomBox);
        addRenderableWidget(row.removeButton);
    }

    private void removeRow(CommandRow row) {
        if (!rows.remove(row)) {
            return;
        }
        removeWidget(row.cmdBox);
        removeWidget(row.delayBox);
        removeWidget(row.randomBox);
        removeWidget(row.removeButton);
        if (rows.isEmpty()) {
            addRowWidgets("/say 收到信号！", "0", "0");
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (idBox != null) {
            idBox.tick();
        }
        for (CommandRow row : rows) {
            row.cmdBox.tick();
            row.delayBox.tick();
            row.randomBox.tick();
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

        String context = originalId == null ? "新建绑定" : "编辑: " + originalId;
        g.drawString(font, context, right - font.width(context), py + 12, Ui.MUTED, false);

        g.drawString(font, "绑定 ID", px + 14, py + 32, Ui.MUTED, false);
        g.drawString(font, "触发模式", px + 14, py + 56, Ui.MUTED, false);
        g.drawString(font, "多条指令时生效", px + 142, py + 56, Ui.MUTED, false);

        g.drawString(font, "指令列表（支持 && 串联 · %变量% 替换）", px + 14, listTop - 28, Ui.ACCENT, false);
        g.drawString(font, "指令", px + 14, listTop - 14, Ui.MUTED, false);
        g.drawString(font, "延迟", right - 150, listTop - 14, Ui.MUTED, false);
        g.drawString(font, "随机", right - 80, listTop - 14, Ui.MUTED, false);

        int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (listBottom - listTop));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        for (int i = 0; i < rows.size(); i++) {
            CommandRow row = rows.get(i);
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
            g.drawString(font, error, right - font.width(error), py + 74, Ui.ERROR, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listTop = LIST_TOP;
        int listBottom = Math.max(listTop + ROW_HEIGHT, listBottom());
        if (mouseX >= panelX() && mouseX <= panelX() + panelW()
                && mouseY >= listTop && mouseY <= listBottom) {
            int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (listBottom - listTop));
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

    private String modeLabel() {
        int index = List.of(MODES).indexOf(triggerMode);
        return index < 0 ? MODE_LABELS[0] : MODE_LABELS[index];
    }

    private void save() {
        String id = idBox.getValue().trim();
        if (id.isEmpty()) {
            error = "绑定 ID 不能为空";
            return;
        }
        if (!id.equals(originalId) && Config.get().hasBinding(id)) {
            error = "绑定 ID 已存在";
            return;
        }

        Config.Binding binding = new Config.Binding();
        binding.triggerMode = triggerMode;
        for (CommandRow row : rows) {
            String command = row.cmdBox.getValue().trim();
            if (command.isEmpty()) {
                continue;
            }
            binding.commands.add(new Config.CommandEntry(
                    command,
                    parseInt(row.delayBox.getValue()),
                    parseInt(row.randomBox.getValue())
            ));
        }
        if (binding.commands.isEmpty()) {
            error = "至少需要一条指令";
            return;
        }

        Config.Binding previous = originalId != null ? Config.get().getBinding(originalId) : null;
        if (previous != null) {
            binding.key = previous.key;
        }
        if (originalId != null && !originalId.equals(id)) {
            Config.get().removeBinding(originalId);
        }
        Config.get().putBinding(id, binding);
        Config.get().save();
        AutoCommandClient.onConfigChanged();
        onClose();
    }

    private static int parseInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private final class CommandRow {
        private final EditBox cmdBox;
        private final EditBox delayBox;
        private final EditBox randomBox;
        private final Button removeButton;

        CommandRow(String command, String delay, String random) {
            int px = panelX();
            int right = px + panelW() - 14;
            int commandWidth = Math.max(80, rowWidth() - 200);

            cmdBox = new EditBox(font, px + 14, 0, commandWidth, 18, Component.literal("指令"));
            cmdBox.setMaxLength(512);
            cmdBox.setValue(command);

            delayBox = new EditBox(font, right - 150, 0, 40, 18, Component.literal("延迟"));
            delayBox.setMaxLength(6);
            delayBox.setFilter(value -> value.chars().allMatch(Character::isDigit));
            delayBox.setValue(delay);

            randomBox = new EditBox(font, right - 80, 0, 40, 18, Component.literal("随机"));
            randomBox.setMaxLength(6);
            randomBox.setFilter(value -> value.chars().allMatch(Character::isDigit));
            randomBox.setValue(random);

            removeButton = Button.builder(Component.literal("✕"), b -> removeRow(this))
                    .bounds(right - 32, 0, 20, 18).build();
        }

        void setY(int y) {
            cmdBox.setY(y + 5);
            delayBox.setY(y + 5);
            randomBox.setY(y + 5);
            removeButton.setY(y + 5);
        }

        void setVisible(boolean visible) {
            cmdBox.visible = visible;
            cmdBox.active = visible;
            delayBox.visible = visible;
            delayBox.active = visible;
            randomBox.visible = visible;
            randomBox.active = visible;
            removeButton.visible = visible;
            removeButton.active = visible;
        }
    }
}
