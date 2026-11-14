package com.autocommand.client.ui;

import com.autocommand.Config;
import com.autocommand.NetworkServer;
import com.autocommand.client.AutoCommandClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * 主界面：标题栏 + 绑定列表 + 底部操作。
 * 列表行内提供按键捕获、编辑、删除；删除走原版 ConfirmScreen 确认。
 */
public final class MainScreen extends Screen {
    private static final int HEADER_HEIGHT = 56;
    private static final int FOOTER_HEIGHT = 42;
    private static final int ROW_HEIGHT = 36;

    private BindingList list;
    private String selectedBindingId;

    public MainScreen() {
        super(Component.literal("AutoCommands"));
    }

    @Override
    protected void init() {
        super.init();
        list = new BindingList(this, minecraft, width, height);
        addRenderableWidget(list);

        int buttonWidth = 100;
        int gap = 4;
        int total = buttonWidth * 3 + gap * 2;
        int startX = (width - total) / 2;
        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("＋ 新建绑定"), b ->
                        Minecraft.getInstance().setScreen(new EditBindingScreen(this, null)))
                .bounds(startX, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("⚙ 设置"), b ->
                        Minecraft.getInstance().setScreen(new SettingsScreen(this)))
                .bounds(startX + buttonWidth + gap, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(startX + (buttonWidth + gap) * 2, y, buttonWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int px = Ui.panelX(width);
        int pw = Ui.panelWidth(width);
        int py = 16;
        Ui.panel(g, px, py, pw, height - 24);
        Ui.bar(g, px, py, pw, HEADER_HEIGHT);
        Ui.bar(g, px, height - 36, pw, 28);

        g.drawString(font, "AutoCommands", px + 14, py + 12, Ui.TEXT, false);
        g.drawString(font, "绑定管理", px + 14, py + 26, Ui.MUTED, false);

        boolean running = NetworkServer.get().isRunning();
        String status = running ? "TCP 运行中 · " + NetworkServer.get().getPort() : "TCP 已停止";
        g.drawString(font, status, px + pw - 14 - font.width(status), py + 16,
                running ? Ui.SUCCESS : Ui.ERROR, false);
        String count = Config.get().snapshot().size() + " 个绑定";
        g.drawString(font, count, px + pw - 14 - font.width(count), py + 30, Ui.MUTED, false);

        g.fill(px + 12, HEADER_HEIGHT - 1, px + pw - 12, HEADER_HEIGHT, Ui.DIVIDER);

        if (list != null && list.children().isEmpty()) {
            g.drawCenteredString(font, "还没有绑定", width / 2, height / 2 - 18, Ui.TEXT);
            g.drawCenteredString(font, "点击下方「新建绑定」开始", width / 2, height / 2 - 4, Ui.MUTED);
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (selectedBindingId != null) {
            String hint = "按下任意键/鼠标键绑定…（ESC 取消）";
            int w = font.width(hint) + 16;
            int x = (width - w) / 2;
            int y = HEADER_HEIGHT + 4;
            g.fill(x, y, x + w, y + 18, 0xD0202630);
            g.drawString(font, hint, x + 8, y + 5, 0xFFFFC857, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectedBindingId != null) {
            if (button >= 0 && button <= 2) {
                captureKey(InputConstants.Type.MOUSE.getOrCreate(button).getName());
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectedBindingId != null) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                captureKey(null);
            } else {
                InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
                if (key.getValue() != -1) {
                    captureKey(key.getName());
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void captureKey(String keyName) {
        Config.Binding binding = Config.get().getBinding(selectedBindingId);
        if (binding != null) {
            binding.key = keyName;
            Config.get().save();
            AutoCommandClient.onConfigChanged();
        }
        selectedBindingId = null;
        if (list != null) {
            list.refreshAll();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class BindingList extends ContainerObjectSelectionList<BindingList.Entry> {
        private final MainScreen parent;
        private final Font font;

        BindingList(MainScreen parent, Minecraft minecraft, int width, int height) {
            super(minecraft, width, height, HEADER_HEIGHT, height - FOOTER_HEIGHT, ROW_HEIGHT);
            this.parent = parent;
            this.font = minecraft.font;
            setRenderBackground(false);
            setRenderTopAndBottom(false);
            for (Map.Entry<String, Config.Binding> entry : Config.get().snapshot().entrySet()) {
                addEntry(new Entry(entry.getKey(), entry.getValue()));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(160, Math.min(width - 80, 520));
        }

        @Override
        protected int getScrollbarPosition() {
            return width / 2 + getRowWidth() / 2 + 6;
        }

        void refreshAll() {
            for (Entry entry : children()) {
                entry.refresh();
            }
        }

        void reload() {
            clearEntries();
            for (Map.Entry<String, Config.Binding> entry : Config.get().snapshot().entrySet()) {
                addEntry(new Entry(entry.getKey(), entry.getValue()));
            }
        }

        private void confirmDelete(String id) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    Config.get().removeBinding(id);
                    Config.get().save();
                    AutoCommandClient.onConfigChanged();
                    if (id.equals(parent.selectedBindingId)) {
                        parent.selectedBindingId = null;
                    }
                    reload();
                }
                mc.setScreen(parent);
            }, Component.literal("删除绑定"), Component.literal("确定删除绑定 \"" + id + "\"？")));
        }

        private final class Entry extends ContainerObjectSelectionList.Entry<Entry> {
            private final String id;
            private final Config.Binding binding;
            private final Button keyButton;
            private final Button editButton;
            private final Button deleteButton;

            Entry(String id, Config.Binding binding) {
                this.id = id;
                this.binding = binding;
                this.keyButton = Button.builder(Component.literal("未绑定"), b -> {
                    parent.selectedBindingId = id;
                    refreshAll();
                }).bounds(0, 0, 90, 20).build();
                this.editButton = Button.builder(Component.literal("编辑"), b ->
                                Minecraft.getInstance().setScreen(new EditBindingScreen(parent, id)))
                        .bounds(0, 0, 44, 20).build();
                this.deleteButton = Button.builder(Component.literal("删除"), b -> confirmDelete(id))
                        .bounds(0, 0, 44, 20).build();
                refresh();
            }

            void refresh() {
                keyButton.setMessage(Component.literal(id.equals(parent.selectedBindingId)
                        ? "> " + displayKey(binding.key) + " <"
                        : displayKey(binding.key)));
            }

            @Override
            public void render(GuiGraphics g, int index, int y, int x, int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                g.fill(x, y, x + entryWidth, y + entryHeight - 1, hovered ? Ui.ROW_HOVER : Ui.ROW);
                g.fill(x, y + entryHeight - 1, x + entryWidth, y + entryHeight, Ui.DIVIDER);

                int buttonsWidth = 90 + 44 + 44 + 8;
                int textWidth = Math.max(20, entryWidth - buttonsWidth - 12);
                g.drawString(font, Ui.ellipsize(font, id, textWidth), x + 6, y + 6, Ui.TEXT, false);

                StringBuilder sub = new StringBuilder();
                sub.append(binding.commands.size()).append(" 条指令");
                int totalDelay = 0;
                for (Config.CommandEntry command : binding.commands) {
                    totalDelay += command.delay + command.randomDelay;
                }
                if (totalDelay > 0) {
                    sub.append(" · 延迟 ").append(totalDelay).append(" tick");
                }
                if ("random".equals(binding.triggerMode)) {
                    sub.append(" · 随机");
                }
                g.drawString(font, Ui.ellipsize(font, sub.toString(), textWidth), x + 6, y + 20, Ui.MUTED, false);

                int right = x + entryWidth - 4;
                deleteButton.setX(right - deleteButton.getWidth());
                deleteButton.setY(y + 7);
                editButton.setX(deleteButton.getX() - editButton.getWidth() - 4);
                editButton.setY(y + 7);
                keyButton.setX(editButton.getX() - keyButton.getWidth() - 4);
                keyButton.setY(y + 7);

                keyButton.render(g, mouseX, mouseY, partialTick);
                editButton.render(g, mouseX, mouseY, partialTick);
                deleteButton.render(g, mouseX, mouseY, partialTick);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(keyButton, editButton, deleteButton);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(keyButton, editButton, deleteButton);
            }
        }
    }

    private static String displayKey(String keyStr) {
        if (keyStr == null || keyStr.isBlank()) {
            return "未绑定";
        }
        try {
            return InputConstants.getKey(keyStr).getDisplayName().getString();
        } catch (IllegalArgumentException e) {
            return keyStr;
        }
    }
}
