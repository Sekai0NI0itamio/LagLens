package com.laglens.ui;

import com.laglens.LagLensMod;
import com.laglens.capture.CaptureSession;
import com.laglens.config.LagLensConfig;
import com.laglens.session.LagLensSession;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * LagLens dashboard: tabbed diagnostics screen with keyboard + mouse support.
 *
 * <p>Layout: header (title + live status), left tab column with the eleven
 * sections, scrollable content area, footer with hints and actions. All
 * content rendering lives in {@link PageRenderer}; this class owns navigation,
 * input, and clickable rows. Adapts to window size every frame from
 * {@link #width}/{@link #height}; press Esc or the Close button to return to
 * the game immediately.
 */
public final class LagLensScreen extends Screen {
    public static final String[] PAGES = {"overview", "bottlenecks", "terrain", "entities",
            "rendering", "network", "memory", "server", "mods", "capture", "settings"};
    private static final String[] PAGE_TITLES = {"Overview", "Bottlenecks", "Terrain & Chunks",
            "Entities", "Rendering", "Network", "Memory & GC", "Server", "Mods", "Capture & Reports",
            "Settings"};

    private final LagLensSession session;
    private int selectedPage;
    private int scroll;
    private int expandedFinding = -1;
    private final List<ClickTarget> clickTargets = new ArrayList<>();

    /** Clickable row rebuilt every frame: tab selection or finding expansion. */
    public record ClickTarget(int x1, int y1, int x2, int y2, int tab, int finding) {
    }

    public LagLensScreen(LagLensSession session) {
        super(Text.literal("LagLens diagnostics"));
        this.session = session;
        this.selectedPage = indexOfPage(session.config().defaultPage);
    }

    private static int indexOfPage(String page) {
        for (int i = 0; i < PAGES.length; i++) {
            if (PAGES[i].equals(page)) {
                return i;
            }
        }
        return 0;
    }

    public LagLensSession session() {
        return session;
    }

    public int scroll() {
        return scroll;
    }

    public int expandedFinding() {
        return expandedFinding;
    }

    public void addClickTarget(int x1, int y1, int x2, int y2, int tab, int finding) {
        clickTargets.add(new ClickTarget(x1, y1, x2, y2, tab, finding));
    }

    @Override
    protected void init() {
        clearChildren();
        clickTargets.clear();
        int footerY = height - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"),
                b -> close()).dimensions(width - 246, footerY, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(
                Text.literal(session.capture().state() == CaptureSession.State.RECORDING
                        ? "Stop capture" : "Capture"),
                b -> toggleCapture()).dimensions(width - 168, footerY, 80, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Export"),
                b -> exportNow()).dimensions(width - 80, footerY, 70, 20).build());
    }

    private void toggleCapture() {
        CaptureSession capture = session.capture();
        if (capture.state() == CaptureSession.State.RECORDING) {
            capture.stop(session.findings());
        } else {
            capture.start(CaptureSession.Mode.LIGHT, System.currentTimeMillis(),
                    session.config().autoCaptureSec * 1000L);
        }
        init();
    }

    private void exportNow() {
        close();
        if (client != null && client.player != null) {
            client.player.networkHandler.sendChatCommand("laglens export");
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        clickTargets.clear();

        // Header.
        context.fill(0, 0, width, 22, 0xFF14161A);
        context.drawTextWithShadow(textRenderer, Text.literal("LagLens diagnostics"), 10, 7,
                0xFFE8E8E8);
        String status = session.capture().state() == CaptureSession.State.RECORDING
                ? "REC " + session.capture().snapshotCount() + " snaps"
                : session.lastSnapshot().frameSampleCount + " frame samples";
        context.drawTextWithShadow(textRenderer, Text.literal(status), width - 10
                - textRenderer.getWidth(status), 7,
                session.capture().state() == CaptureSession.State.RECORDING ? 0xFFE06C5C
                        : 0xFF9AA0A6);

        // Tabs.
        int tabX = 8;
        int tabW = 104;
        int tabY = 28;
        for (int i = 0; i < PAGES.length; i++) {
            int y0 = tabY + i * 20;
            boolean sel = i == selectedPage;
            if (sel) {
                context.fill(tabX, y0, tabX + tabW, y0 + 18, 0xFF23272E);
            }
            boolean hover = mouseX >= tabX && mouseX < tabX + tabW && mouseY >= y0
                    && mouseY < y0 + 18;
            if (hover && !sel) {
                context.fill(tabX, y0, tabX + tabW, y0 + 18, 0xFF1B1E23);
            }
            context.drawTextWithShadow(textRenderer, Text.literal(PAGE_TITLES[i]), tabX + 8, y0 + 5,
                    sel ? 0xFF7BD88F : 0xFFC9CDD3);
            addClickTarget(tabX, y0, tabX + tabW, y0 + 18, i, -1);
        }

        // Content panel.
        int cx = tabX + tabW + 8;
        int cy = 28;
        int cw = width - cx - 8;
        int ch = height - cy - 34;
        if (cw > 40 && ch > 40) {
            context.fill(cx, cy, cx + cw, cy + ch, 0xFF101216);
            context.drawBorder(cx, cy, cw, ch, 0xFF2A2E35);
            PageRenderer.renderPage(this, context, PAGES[selectedPage], cx + 8, cy + 6, cw - 16,
                    ch - 12);
        }

        // Footer hints.
        String hints = "Up/Down tabs  PgUp/PgDn scroll  C capture  E export  Esc close";
        context.drawTextWithShadow(textRenderer, Text.literal(hints), 10, height - 18, 0xFF9AA0A6);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = clickTargets.size() - 1; i >= 0; i--) {
                ClickTarget t = clickTargets.get(i);
                if (mouseX >= t.x1() && mouseX < t.x2() && mouseY >= t.y1()
                        && mouseY < t.y2()) {
                    if (t.tab() >= 0) {
                        selectedPage = t.tab();
                        scroll = 0;
                        expandedFinding = -1;
                        return true;
                    }
                    if (t.finding() >= 0) {
                        expandedFinding = expandedFinding == t.finding() ? -1 : t.finding();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP) {
            selectedPage = (selectedPage + PAGES.length - 1) % PAGES.length;
            scroll = 0;
            expandedFinding = -1;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            selectedPage = (selectedPage + 1) % PAGES.length;
            scroll = 0;
            expandedFinding = -1;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scroll = Math.max(0, scroll - 6);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll = Math.min(400, scroll + 6);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C) {
            toggleCapture();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_E) {
            exportNow();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Page title for links from findings. */
    public static String titleOf(String page) {
        for (int i = 0; i < PAGES.length; i++) {
            if (PAGES[i].equals(page)) {
                return PAGE_TITLES[i];
            }
        }
        return page;
    }

    /** Jump to a page (used by finding links). */
    public void goTo(String page) {
        selectedPage = indexOfPage(page);
        scroll = 0;
        expandedFinding = -1;
    }

    /** Snapshot of config for pages. */
    public LagLensConfig config() {
        return LagLensMod.config();
    }

    /** Text renderer for page content (Screen keeps the field protected). */
    public net.minecraft.client.font.TextRenderer textRenderer() {
        return this.textRenderer;
    }
}
