package com.autocommand.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * GUI 通用绘制与布局工具。只使用原版 GuiGraphics/Font，不引入外部库。
 * 风格：原版菜单的深色横条 + 半透明磨砂面板 + 像素边框。
 */
public final class Ui {
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFFA0A0A0;
    public static final int ACCENT = 0xFFFFD700;
    public static final int SUCCESS = 0xFF55FF55;
    public static final int ERROR = 0xFFFF5555;

    public static final int ROW = 0x18FFFFFF;
    public static final int ROW_HOVER = 0x30FFFFFF;
    public static final int DIVIDER = 0x30FFFFFF;

    private Ui() {
    }

    /** 半透明磨砂面板 + 深色像素边框。 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xB0101010, 0xD8101010);
        g.fill(x, y, x + w, y + 1, 0xFF000000);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF000000);
        g.fill(x, y, x + 1, y + h, 0xFF000000);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x20FFFFFF);
    }

    /** 原版菜单风格的深色横条：顶部高光、底部阴影。 */
    public static void bar(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x90000000);
        g.fill(x, y, x + w, y + 1, 0x30FFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF000000);
    }

    public static void section(GuiGraphics g, Font font, String text, int x, int y) {
        g.drawString(font, text, x, y, ACCENT, false);
    }

    public static void label(GuiGraphics g, Font font, String text, int x, int y) {
        g.drawString(font, text, x, y, MUTED, false);
    }

    /** 按像素宽度截断，并在末尾加省略号。 */
    public static String ellipsize(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String dots = "…";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(dots))) + dots;
    }

    public static int panelWidth(int screenWidth) {
        return Math.min(screenWidth - 40, 560);
    }

    public static int panelX(int screenWidth) {
        return (screenWidth - panelWidth(screenWidth)) / 2;
    }
}
