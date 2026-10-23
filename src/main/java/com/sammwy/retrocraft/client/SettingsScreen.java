package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.server.config.ConfigKey;
import com.sammwy.retrocraft.server.net.SettingsPackets;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Path;
import java.util.*;

import static com.sammwy.retrocraft.client.SettingsWidgets.*;

/**
 * Personal and operator settings: a sidebar with one category per config group and a ROM library
 * browser. Values are edited into a draft and committed with Apply.
 */
public final class SettingsScreen extends Screen {
    private static final int SIDEBAR = 126, ROW_H = 32, SECTION_H = 18, CONTENT_TOP = 34, FOOTER = 26;
    private static final Identifier LOGO_TEX = Identifier.of("retrocraft", "textures/gui/logotype.png");
    private static final int LOGO_W = 1852, LOGO_H = 391;
    private static final String[] GLYPH_CHIP = {" X X X ", "XXXXXXX", "X XXX X", "X XXX X", "X XXX X", "XXXXXXX", " X X X "};
    private static final String[] GLYPH_WAVES = {"  X    ", " XX  X ", "X X X X", "X X X X", "X X X X", " XX  X ", "  X    "};
    private static final String[] GLYPH_CLOCK = {" XXXXX ", "X  X  X", "X  X  X", "X  XX X", "X     X", "X     X", " XXXXX "};
    private static final String[] GLYPH_CORE = {" X X X ", "X     X", "X XXX X", "X XXX X", "X XXX X", "X     X", " X X X "};
    private static final String[] GLYPH_FOLDER = {"XXX     ", "X  XXXXX", "X      X", "X      X", "X      X", "X      X", "XXXXXXX "};
    private static final String[] GLYPH_MINUS = {"       ", "       ", "       ", " XXXXX ", "       ", "       ", "       "};
    private static final String[] GLYPH_PLUS = {"       ", "   X   ", "   X   ", " XXXXX ", "   X   ", "   X   ", "       "};

    private enum State { CONNECTING, DENIED, READY }
    private final Screen parent;
    private State state;
    private String denyReason = "timeout";
    private SettingsBackend backend;
    private List<SettingsPackets.GuiOpen.Entry> schema = List.of();
    private Map<String, SettingsPackets.GuiOpen.Entry> byKey = Map.of();
    private final Map<String, String> draft = new LinkedHashMap<>();
    private static final int CLIENT = -1;
    private int category = CLIENT;
    private final Map<String, String> clientDraft = new LinkedHashMap<>();
    private float scroll;
    private boolean syncing;
    private final List<Row> rows = new ArrayList<>();
    private Text status; private int statusColor; private long statusUntil;
    private final RomBrowser browser = new RomBrowser(new BrowserHost());
    private final CorePane corePane = new CorePane(new CoreHost());
    private long requestedAt;
    private boolean scrollDrag;
    private int px, py, pw, ph;
    private int contentX, contentY, contentW, contentH;
    private SettingsWidgets.Flat apply;

    private SettingsScreen(Screen parent, SettingsBackend backend, State state) {
        super(Text.translatable("settings.retrocraft.title"));
        this.parent = parent; this.backend = backend; this.state = state;
        schema(backend != null ? backend.schema() : List.of());
    }

    static SettingsScreen remote(Screen parent, SettingsPackets.GuiOpen grant) {
        if (!grant.granted()) return denied(parent, grant.reason());
        return new SettingsScreen(parent, new SettingsBackend.Remote(grant), State.READY);
    }
    /** ModMenu's config button: local files when hosting/offline, a server request otherwise. */
    public static SettingsScreen modMenu(Screen parent) {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return local(parent);
        if (!client.player.hasPermissionLevel(2)) return denied(parent, "operator");
        if (!SettingsClient.supported()) return denied(parent, "not_installed");
        return new SettingsScreen(parent, null, State.CONNECTING);
    }
    private static SettingsScreen local(Screen parent) {
        try { return new SettingsScreen(parent, new SettingsBackend.Local(), State.READY); }
        catch (Exception ex) { return denied(parent, "local_error"); }
    }
    private static SettingsScreen denied(Screen parent, String reason) {
        var screen = new SettingsScreen(parent, null, State.DENIED);
        screen.denyReason = reason;
        return screen;
    }

    private void schema(List<SettingsPackets.GuiOpen.Entry> entries) {
        var combined = new ArrayList<>(ClientPreferences.schema());
        combined.addAll(entries);
        schema = List.copyOf(combined);
        var map = new HashMap<String, SettingsPackets.GuiOpen.Entry>();
        for (var entry : schema) map.put(entry.key(), entry);
        byKey = map;
    }

    /** DoOpenSettingsGUI arrived while this screen waited for it. */
    void granted(SettingsPackets.GuiOpen packet) {
        if (!packet.granted()) { state = State.DENIED; denyReason = packet.reason(); clearAndInit(); return; }
        backend = new SettingsBackend.Remote(packet);
        schema(packet.entries());
        state = State.READY;
        clearAndInit();
    }
    void scanFinished() { browser.reload(); }
    void configSaved(SettingsPackets.ConfigSaved packet) {
        if (backend instanceof SettingsBackend.Remote remote) remote.saved(packet);
    }

    @Override protected void init() {
        pw = Math.min(620, width - 16); ph = Math.min(340, height - 16);
        px = (width - pw) / 2; py = (height - ph) / 2;
        contentX = px + SIDEBAR + 12; contentY = py + CONTENT_TOP;
        contentW = px + pw - 12 - contentX; contentH = py + ph - FOOTER - 6 - contentY;
        int footerY = py + ph - FOOTER + 4;
        apply = addDrawableChild(new Flat(px + pw - 76 - 128, footerY, 60, 16, Text.translatable("settings.retrocraft.apply"), this::applyDraft).accent(true));
        addDrawableChild(new Flat(px + pw - 76 - 64, footerY, 56, 16, Text.translatable("settings.retrocraft.reset"), this::resetDraft));
        addDrawableChild(new Flat(px + pw - 72, footerY, 60, 16, Text.translatable("settings.retrocraft.done"), this::close));
        SettingsClient.screen(this);
        if (state == State.CONNECTING) { requestedAt = System.nanoTime(); if (SettingsClient.supported()) SettingsClient.request(); }
        {
            buildRows();
            browser.init(contentX, contentY + ROW_H + 6, contentW, contentH - ROW_H - 6);
            browser.setActive(state == State.READY && category == ConfigKey.Category.LIBRARY.ordinal());
            corePane.init(contentX, contentY, contentW, contentH);
            corePane.setActive(state == State.READY && category == ConfigKey.Category.CORES.ordinal());
        }
        refreshFooter();
    }

    private void buildRows() {
        rows.clear();
        for (var entry : schema) if (entry.kind() != SettingsPackets.CORE) rows.add(row(entry));
        scroll = 0;
    }

    private Row row(SettingsPackets.GuiOpen.Entry entry) {
        var row = new Row(entry);
        switch (entry.kind()) {
            case SettingsPackets.INT -> {
                var field = SettingsWidgets.field(textRenderer, 0, 0, 44, value(entry));
                field.setMaxLength(10);
                field.setChangedListener(text -> { if (!syncing) { (entry.category() == CLIENT ? clientDraft : draft).put(entry.key(), text); refreshFooter(); } });
                row.parts.add(addSelectableChild(field));
                row.intField = field;
                row.parts.add(addSelectableChild(new Glyph(0, 0, GLYPH_MINUS, () -> step(row, -1), Text.translatable("settings.retrocraft.decrease"))));
                row.parts.add(addSelectableChild(new Glyph(0, 0, GLYPH_PLUS, () -> step(row, 1), Text.translatable("settings.retrocraft.increase"))));
            }
            case SettingsPackets.BOOL -> {
                var toggle = new Toggle(0, 0, "true".equals(value(entry)), on -> { if (!syncing) { (entry.category() == CLIENT ? clientDraft : draft).put(entry.key(), on ? "true" : "false"); refreshFooter(); } });
                row.toggle = toggle;
                row.parts.add(addSelectableChild(toggle));
            }
            case SettingsPackets.ENUM, SettingsPackets.CORE -> {
                var cycle = new Cycle(0, 0, 150, entry.options(), value(entry), v -> { if (!syncing) { (entry.category() == CLIENT ? clientDraft : draft).put(entry.key(), v); refreshFooter(); } });
                row.cycle = cycle;
                row.parts.add(addSelectableChild(cycle));
            }
            default -> { }
        }
        return row;
    }

    private void step(Row row, int dir) {
        var entry = row.entry;
        int v = entry.min();
        try { v = Integer.parseInt(value(entry)); } catch (NumberFormatException ignored) { }
        v = Math.max(entry.min(), Math.min(entry.max(), v + dir));
        row.intField.setText(Integer.toString(v));
    }

    private Map<String, String> activeDraft() { return category == CLIENT ? clientDraft : draft; }
    private boolean ready() { return category == CLIENT || state == State.READY; }
    private String value(SettingsPackets.GuiOpen.Entry entry) {
        var current = byKey.getOrDefault(entry.key(), entry);
        return (entry.category() == CLIENT ? clientDraft : draft).getOrDefault(entry.key(), current.value());
    }
    private SettingsPackets.GuiOpen.Entry entry(String key) { return byKey.get(key); }

    private void applyDraft() {
        if (category == CLIENT) {
            try {
                ClientPreferences.apply(clientDraft);
                clientDraft.clear();
                schema(backend == null ? List.of() : backend.schema());
                syncRows(); refreshFooter();
                status(Text.translatable("settings.retrocraft.saved"), GOOD);
            } catch (java.io.IOException | IllegalArgumentException ex) { status(Text.literal(ex.getMessage()), BAD); }
            return;
        }
        var changes = draft.entrySet().stream().map(e -> new SettingsPackets.ConfigApply.Change(e.getKey(), e.getValue())).toList();
        if (changes.isEmpty()) return;
        backend.apply(changes, (ok, message, entries) -> {
            schema(entries);
            if (ok) { draft.clear(); status(Text.translatable("settings.retrocraft.saved"), GOOD); }
            else status(Text.literal(message == null ? "?" : message), BAD);
            syncRows(); refreshFooter();
        });
    }
    private void resetDraft() {
        if (activeDraft().isEmpty()) return;
        activeDraft().clear(); syncRows(); refreshFooter();
    }
    private void syncRows() {
        syncing = true;
        try {
            for (var row : rows) {
                if (row.entry == null) continue; // section header
                var entry = entry(row.entry.key());
                if (entry == null) continue;
                if (row.intField != null) row.intField.setText(value(entry));
                if (row.toggle != null) row.toggle.value("true".equals(value(entry)));
                if (row.cycle != null) row.cycle.value(value(entry));
            }
        } finally { syncing = false; }
    }

    private void refreshFooter() {
        apply.active = ready() && !activeDraft().isEmpty();
    }

    void status(Text text, int color) {
        status = text; statusColor = color; statusUntil = System.currentTimeMillis() + 4500;
    }

    private void selectCategory(int index) {
        if (index == category) return;
        category = index; scroll = 0;
        setFocused(null);
        for (var row : rows) { row.hide(); if (row.intField != null) row.intField.setFocused(false); }
        refreshFooter();
        browser.setActive(state == State.READY && index == ConfigKey.Category.LIBRARY.ordinal());
        corePane.setActive(state == State.READY && index == ConfigKey.Category.CORES.ordinal());
        SettingsWidgets.click(client);
    }

    private int categoryAt(double mx, double my) {
        if (mx >= px + 6 && mx < px + SIDEBAR - 6 && my >= py + 68 && my < py + 86) return CLIENT;
        int y = py + 108;
        for (int i = 0; i < ConfigKey.Category.values().length; i++, y += 20)
            if (mx >= px + 6 && mx < px + SIDEBAR - 6 && my >= y && my < y + 18) return i;
        return -2;
    }

    @Override public void tick() {
        if (state == State.CONNECTING && System.nanoTime() - requestedAt > 5_000_000_000L) {
            state = State.DENIED; denyReason = SettingsClient.supported() ? "timeout" : "not_installed";
            clearAndInit();
        }
        if (status != null && System.currentTimeMillis() > statusUntil) status = null;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        {
            int idx = categoryAt(mx, my);
            if (idx >= CLIENT) { selectCategory(idx); return true; }
            if (!ready()) return super.mouseClicked(mx, my, button);
            if (scrollTrackHit(mx, my)) { scrollDrag = true; dragScroll(my); return true; }
            if (category == ConfigKey.Category.LIBRARY.ordinal() && browser.mouseClicked(mx, my, button)) return true;
            if (category == ConfigKey.Category.CORES.ordinal() && corePane.mouseClicked(mx, my, button)) return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        if (scrollDrag) { dragScroll(my); return true; }
        if (ready() && category == ConfigKey.Category.LIBRARY.ordinal() && browser.mouseDragged(mx, my)) return true;
        if (ready() && category == ConfigKey.Category.CORES.ordinal() && corePane.mouseDragged(mx, my)) return true;
        return super.mouseDragged(mx, my, button, deltaX, deltaY);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        scrollDrag = false;
        browser.mouseReleased();
        corePane.mouseReleased();
        return super.mouseReleased(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (ready() && category == ConfigKey.Category.LIBRARY.ordinal() && browser.mouseScrolled(mx, my, horizontal, vertical)) return true;
        if (ready() && category == ConfigKey.Category.CORES.ordinal() && corePane.mouseScrolled(mx, my, horizontal, vertical)) return true;
        if (ready() && mx >= contentX && mx < contentX + contentW && my >= contentY && my < contentY + contentH) {
            scroll = Math.max(0, Math.min(scroll - (float) vertical * 14, maxScroll()));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private float maxScroll() {
        int total = 0;
        for (var row : rows) if (rowCategory(row) == category) total += row.height();
        return Math.max(0, total - contentH);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (ready() && category == ConfigKey.Category.LIBRARY.ordinal() && browser.keyPressed(key, scanCode, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean charTyped(char chr, int modifiers) {
        if (ready() && category == ConfigKey.Category.LIBRARY.ordinal() && browser.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override public void filesDragged(List<Path> files) {
        if (ready() && category == ConfigKey.Category.LIBRARY.ordinal()) browser.filesDropped(files);
    }

    private int rowCategory(Row row) {
        if (row.entry == null) return ConfigKey.Category.EMULATION.ordinal();
        return row.entry.category();
    }

    @Override public void render(DrawContext context, int mx, int my, float delta) {
        var tr = textRenderer;
        for (var row : rows) row.hide();
        context.fill(0, 0, width, height, 0xB0080A0E);
        // Panel
        rounded(context, px + 3, py + 5, pw, ph, 0x90000000, 6);
        rounded(context, px, py, pw, ph, BG, 6);
        context.fill(px + 4, py, px + pw - 4, py + 1, 0x30FFFFFF);
        // Sidebar
        rounded(context, px + 1, py + 1, SIDEBAR - 1, ph - 2, 0xFF0E0B16, 5);
        context.fill(px + SIDEBAR - 7, py + 4, px + SIDEBAR, py + ph - 4, 0xFF0E0B16);
        context.drawTexture(LOGO_TEX, px + (SIDEBAR - 104) / 2, py + 9, 104, 22, 0, 0, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
        String version = "v" + FabricLoader.getInstance().getModContainer("retrocraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        context.drawCenteredTextWithShadow(tr, version + (backend != null ? "  ·  " + backend.scope().getString() : ""), px + SIDEBAR / 2 + 1, py + 36, MUTED);
        context.fill(px + 8, py + 48, px + SIDEBAR - 8, py + 49, 0x18FFFFFF);
        renderCategories(context, tr, mx, my);
        // Content header
        if (ready()) {
            String categoryName = category == CLIENT ? "client" : ConfigKey.Category.values()[category].name().toLowerCase(Locale.ROOT);
            context.drawTextWithShadow(tr, Text.translatable("settings.retrocraft.category." + categoryName), contentX, py + 12, TEXT);
            context.fill(contentX, py + 26, px + pw - 12, py + 27, 0x18FFFFFF);
            context.fill(contentX, py + 26, contentX + 34, py + 27, ACCENT);
        }
        switch (category == CLIENT ? State.READY : state) {
            case CONNECTING -> {
                int dots = (int) ((System.currentTimeMillis() / 350) % 4);
                context.drawCenteredTextWithShadow(tr, Text.translatable("settings.retrocraft.connecting").append(".".repeat(dots)), px + SIDEBAR + (pw - SIDEBAR) / 2, py + ph / 2 - 4, MUTED);
            }
            case DENIED -> {
                int cx = px + SIDEBAR + (pw - SIDEBAR) / 2;
                draw(context, GLYPH_CLOCK, cx - 4, py + ph / 2 - 22, BAD);
                context.drawCenteredTextWithShadow(tr, Text.translatable("settings.retrocraft.denied." + denyReason), cx, py + ph / 2 - 6, TEXT);
                if ("operator".equals(denyReason)) context.drawCenteredTextWithShadow(tr, Text.translatable("settings.retrocraft.denied_hint"), cx, py + ph / 2 + 6, MUTED);
            }
            case READY -> {
                if (category == ConfigKey.Category.LIBRARY.ordinal()) renderLibrary(context, tr, mx, my, delta);
                else if (category == ConfigKey.Category.CORES.ordinal()) corePane.render(context, mx, my, delta);
                else renderSettings(context, tr, mx, my, delta);
            }
        }
        // Footer
        context.fill(px + SIDEBAR + 8, py + ph - FOOTER, px + pw - 8, py + ph - FOOTER + 1, 0x18FFFFFF);
        if (status != null) {
            context.fill(px + SIDEBAR + 14, py + ph - FOOTER + 9, px + SIDEBAR + 18, py + ph - FOOTER + 13, statusColor);
            context.drawTextWithShadow(tr, tr.trimToWidth(status.getString(), pw - SIDEBAR - 250), px + SIDEBAR + 24, py + ph - FOOTER + 7, statusColor == BAD ? 0xFFF0A8A2 : MUTED);
        } else if (ready() && !activeDraft().isEmpty()) {
            context.fill(px + SIDEBAR + 14, py + ph - FOOTER + 9, px + SIDEBAR + 18, py + ph - FOOTER + 13, ACCENT);
            context.drawTextWithShadow(tr, Text.translatable("settings.retrocraft.unsaved", activeDraft().size()), px + SIDEBAR + 24, py + ph - FOOTER + 7, MUTED);
        }
        super.render(context, mx, my, delta);
    }

    private void renderCategories(DrawContext context, TextRenderer tr, int mx, int my) {
        String[][] icons = {GLYPH_CHIP, GLYPH_WAVES, GLYPH_CLOCK, GLYPH_CORE, GLYPH_FOLDER};
        context.drawTextWithShadow(tr, Text.literal("Client"), px + 12, py + 56, MUTED);
        boolean clientSelected = category == CLIENT;
        rounded(context, px + 6, py + 68, SIDEBAR - 12, 18, clientSelected ? 0xFF32284A : CONTROL, 3);
        context.drawTextWithShadow(tr, Text.translatable("settings.retrocraft.category.client"), px + 24, py + 73, clientSelected ? TEXT : MUTED);
        context.drawTextWithShadow(tr, Text.literal("Server"), px + 12, py + 96, MUTED);
        int y = py + 108;
        for (var cat : ConfigKey.Category.values()) {
            boolean selected = cat.ordinal() == category;
            boolean hot = mx >= px + 6 && mx < px + SIDEBAR - 6 && my >= y && my < y + 18;
            if (selected) {
                rounded(context, px + 6, y, SIDEBAR - 12, 18, 0xFF32284A, 3);
                context.fill(px + 6, y + 4, px + 8, y + 14, ACCENT);
            } else if (hot) rounded(context, px + 6, y, SIDEBAR - 12, 18, 0x14FFFFFF, 3);
            draw(context, icons[cat.ordinal()], px + 12, y + 5, selected ? ACCENT : 0xFF6B7288);
            context.drawTextWithShadow(tr, Text.translatable("settings.retrocraft.category." + cat.name().toLowerCase(Locale.ROOT)), px + 24, y + 5, selected ? TEXT : MUTED);
            y += 20;
        }
    }

    private void renderSettings(DrawContext context, TextRenderer tr, int mx, int my, float delta) {
        int clipY = contentY, clipH = contentH;
        // Position rows first so hover math and widget coordinates agree.
        int ry = contentY - (int) scroll;
        for (var row : rows) {
            if (rowCategory(row) != category) { row.hide(); continue; }
            row.y = ry;
            boolean visible = ry >= clipY && ry + row.height() <= clipY + clipH;
            row.layout(contentX + contentW - 6, ry, visible);
            ry += row.height();
        }
        context.enableScissor(contentX, clipY, contentX + contentW, clipY + clipH);
        for (var row : rows) {
            if (rowCategory(row) != category || row.y + row.height() <= clipY || row.y >= clipY + clipH) continue;
            row.render(context, tr, mx, my, delta);
        }
        context.disableScissor();
        // Scrollbar hugging the panel's inner right edge, draggable via the thumb or track.
        float max = maxScroll();
        if (max > 0) {
            int trackX = px + pw - 10;
            context.fill(trackX, clipY, trackX + 3, clipY + clipH, 0x30FFFFFF);
            int thumb = scrollThumb();
            int ty = clipY + (int) ((clipH - thumb) * scroll / max);
            context.fill(trackX, ty, trackX + 3, ty + thumb, 0xB0FFFFFF);
        }
    }

    private int scrollThumb() { return Math.max(12, contentH * contentH / Math.max(1, (int) maxScroll() + contentH)); }
    private boolean scrollTrackHit(double mx, double my) {
        return category != ConfigKey.Category.LIBRARY.ordinal() && maxScroll() > 0
                && mx >= px + pw - 13 && mx < px + pw - 5 && my >= contentY && my < contentY + contentH;
    }
    private void dragScroll(double my) {
        float max = maxScroll();
        scroll = Math.max(0, Math.min(max, (float) ((my - contentY - scrollThumb() / 2.0) / Math.max(1, contentH - scrollThumb()) * max)));
    }

    private void renderLibrary(DrawContext context, TextRenderer tr, int mx, int my, float delta) {
        // The one scalar that lives in this category stays pinned above the browser.
        for (var row : rows) {
            if (rowCategory(row) != category) { row.hide(); continue; }
            row.y = contentY;
            row.layout(contentX + contentW - 6, contentY, true);
            row.render(context, tr, mx, my, delta);
        }
        browser.render(context, mx, my, delta);
    }

    @Override public void renderBackground(DrawContext context, int mx, int my, float delta) { }
    @Override public boolean shouldPause() { return false; }

    @Override public void removed() {
        SettingsClient.screen(null);
        if (backend != null) backend.close();
        super.removed();
    }

    @Override public void close() {
        if (parent != null && client != null) client.setScreen(parent);
        else super.close();
    }


    private final class Row {
        final SettingsPackets.GuiOpen.Entry entry; // null → section header
        final List<ClickableWidget> parts = new ArrayList<>();
        TextFieldWidget intField; Toggle toggle; Cycle cycle;
        int y;
        Row(SettingsPackets.GuiOpen.Entry entry) { this.entry = entry; }
        int height() { return entry == null ? SECTION_H : ROW_H; }
        void hide() { for (var part : parts) part.visible = false; }

        void layout(int right, int ry, boolean visible) {
            int cy = ry + (ROW_H - 16) / 2;
            if (entry == null) return;
            for (var part : parts) part.visible = visible;
            if (!visible) return;
            switch (entry.kind()) {
                case SettingsPackets.INT -> {
                    parts.get(1).setX(right - 76); parts.get(1).setY(cy + 1);   // minus
                    intField.setX(right - 60); intField.setY(cy); intField.setWidth(42);
                    parts.get(2).setX(right - 15); parts.get(2).setY(cy + 1);   // plus
                }
                case SettingsPackets.BOOL -> { toggle.setX(right - 34); toggle.setY(cy + 1); }
                case SettingsPackets.ENUM, SettingsPackets.CORE -> { cycle.setX(right - 150); cycle.setY(cy); }
                default -> { }
            }
        }

        void render(DrawContext context, TextRenderer tr, int mx, int my, float delta) {
            if (entry == null) {
                context.fill(contentX + 2, y + 12, contentX + contentW - 8, y + 13, 0x18FFFFFF);
                context.drawTextWithShadow(tr, Text.translatable("settings.retrocraft.cores"), contentX + 2, y + 4, MUTED);
                return;
            }
            boolean hot = mx >= contentX && mx < contentX + contentW && my >= y && my < y + ROW_H;
            if (hot) rounded(context, contentX, y, contentW, ROW_H, 0x0FFFFFFF, 3);
            // Cartridge icon for core mappings; otherwise a plain label.
            int labelX = contentX + 6;
            if (entry.kind() == SettingsPackets.CORE) {
                var item = Registries.ITEM.get(Identifier.tryParse(entry.key().substring(6)));
                context.drawItem(new ItemStack(item), contentX + 4, y + 8);
                context.drawTextWithShadow(tr, tr.trimToWidth(item.getName().getString(), contentW - 170), contentX + 24, y + 8, TEXT);
            } else {
                context.drawTextWithShadow(tr, tr.trimToWidth(Text.translatable("settings.retrocraft.key." + entry.key()).getString(), Math.max(0, contentW - 100)), labelX, y + 6, TEXT);
                context.drawTextWithShadow(tr, tr.trimToWidth(Text.translatable("settings.retrocraft.key." + entry.key() + ".desc").getString(), Math.max(0, contentW - 100)), labelX, y + 17, 0xFF67707F);
            }
            boolean invalid = entry.kind() == SettingsPackets.INT && !intValid();
            if (invalid) context.fill(contentX + 2, y + ROW_H - 1, contentX + contentW - 8, y + ROW_H, BAD);
            for (var part : parts) part.render(context, mx, my, delta);
        }
        private boolean intValid() {
            try {
                int v = Integer.parseInt(value(entry));
                return v >= entry.min() && v <= entry.max();
            } catch (NumberFormatException ex) { return false; }
        }
    }

    private final class BrowserHost implements RomBrowser.Host {
        @Override public SettingsBackend backend() { return backend; }
        @Override public TextRenderer textRenderer() { return textRenderer; }
        @Override public MinecraftClient client() { return client; }
        @Override public void status(Text text, int color) { SettingsScreen.this.status(text, color); }
        @Override public <T extends Element & Drawable & Selectable> T add(T widget) { return addDrawableChild(widget); }
        @Override public <T extends Element & Selectable> T addHidden(T element) { return addSelectableChild(element); }
        @Override public void removeInput(Element element) { remove(element); }
    }

    private final class CoreHost implements CorePane.Host {
        @Override public SettingsBackend backend() { return backend; }
        @Override public TextRenderer textRenderer() { return textRenderer; }
        @Override public void status(Text text, int color) { SettingsScreen.this.status(text, color); }
    }
}
