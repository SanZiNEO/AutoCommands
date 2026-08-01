package com.autocommand.client.ui;

import com.autocommand.Config;
import com.autocommand.client.AutoCommandClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * 主界面：绑定列表（行内按键捕获 / 编辑 / 删除）+ 底部操作。
 * 写法参考原版 KeyBindsScreen + 参考模组 1.20：
 * - ContainerObjectSelectionList + Entry（Entry 内嵌 Button，children() 转发事件）
 * - Screen 级按键捕获：选中绑定后按任意键/鼠标键直接改绑
 */
public final class MainScreen extends Screen {
    private BindingList list;

    /** 捕获模式：正在改绑的绑定 id；null = 正常 */
    private String selectedBindingId;

    public MainScreen() {
        super(Component.literal("AutoCommand 绑定管理"));
    }

    @Override
    protected void init() {
        list = new BindingList(this, minecraft, width, height);
        addWidget(list);

        int y = height - 29;
        addRenderableWidget(Button.builder(Component.literal("＋ 新建绑定"), b ->
                        Minecraft.getInstance().setScreen(new EditBindingScreen(this, null)))
                .bounds(width / 2 - 155, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("⚙ 设置"), b ->
                        Minecraft.getInstance().setScreen(new SettingsScreen(this)))
                .bounds(width / 2 - 51, y, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 + 53, y, 100, 20).build());
    }

    // ============ 按键捕获（参考原版 KeyBindsScreen） ============

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
                captureKey(null); // 解绑
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
            list.refresh();
        }
    }

    // ============ 渲染 ============

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        if (list != null) {
            list.render(g, mouseX, mouseY, partialTick);
        }
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        if (selectedBindingId != null) {
            g.drawCenteredString(font, "按下任意键/鼠标键绑定…（ESC 取消）", width / 2, 20, 0xFFFFC857);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ============ 绑定列表 ============

    private static final class BindingList extends ContainerObjectSelectionList<BindingList.Entry> {
        private final MainScreen parent;
        private final net.minecraft.client.gui.Font font;

        BindingList(MainScreen parent, Minecraft minecraft, int width, int height) {
            super(minecraft, width + 45, height, 32, height - 32, 28);
            this.parent = parent;
            this.font = minecraft.font;
            for (Map.Entry<String, Config.Binding> e : Config.get().snapshot().entrySet()) {
                addEntry(new Entry(e.getKey(), e.getValue()));
            }
        }

        void refresh() {
            children().forEach(Entry::refreshEntry);
        }

        @Override
        protected int getScrollbarPosition() {
            return super.getScrollbarPosition() + 15;
        }

        @Override
        public int getRowWidth() {
            return super.getRowWidth() + 90;
        }

        private final class Entry extends ContainerObjectSelectionList.Entry<Entry> {
            private final String id;
            private final Config.Binding binding;

            private final Button changeButton;
            private final Button editButton;
            private final Button deleteButton;

            Entry(String id, Config.Binding binding) {
                this.id = id;
                this.binding = binding;
                this.changeButton = Button.builder(Component.literal("未绑定"), b -> {
                            parent.selectedBindingId = id;
                            refreshEntry();
                        })
                        .bounds(0, 0, 95, 20).build();
                this.editButton = Button.builder(Component.literal("编辑"), b ->
                                Minecraft.getInstance().setScreen(new EditBindingScreen(parent, id)))
                        .bounds(0, 0, 50, 20).build();
                this.deleteButton = Button.builder(Component.literal("删除"), b -> {
                            Config.get().removeBinding(id);
                            Config.get().save();
                            AutoCommandClient.onConfigChanged();
                            BindingList.this.removeEntry(this);
                        })
                        .bounds(0, 0, 50, 20).build();
                refreshEntry();
            }

            @Override
            public void render(GuiGraphics g, int index, int y, int x, int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                g.drawString(BindingList.this.font, id, x + 6, y + 3, 0xFFFFFFFF);
                String sub = binding.commands.size() + " 条指令";
                int totalDelay = 0;
                for (Config.CommandEntry c : binding.commands) {
                    totalDelay += c.delay + c.randomDelay;
                }
                if (totalDelay > 0) {
                    sub += " · 延迟 " + totalDelay + " tick";
                }
                String mode = binding.triggerMode;
                if ("random".equals(mode)) {
                    sub += " · 随机";
                }

                deleteButton.setX(x + entryWidth - deleteButton.getWidth());
                deleteButton.setY(y);
                editButton.setX(x + entryWidth - deleteButton.getWidth() - editButton.getWidth() - 2);
                editButton.setY(y);
                changeButton.setX(x + entryWidth - deleteButton.getWidth() - editButton.getWidth() - changeButton.getWidth() - 4);
                changeButton.setY(y);
                deleteButton.render(g, mouseX, mouseY, partialTick);
                editButton.render(g, mouseX, mouseY, partialTick);
                changeButton.render(g, mouseX, mouseY, partialTick);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(changeButton, editButton, deleteButton);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(changeButton, editButton, deleteButton);
            }

            void refreshEntry() {
                changeButton.setMessage(Component.literal(displayKey(binding.key)));
                if (id.equals(parent.selectedBindingId)) {
                    changeButton.setMessage(Component.literal("> " + displayKey(binding.key) + " <"));
                }
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
