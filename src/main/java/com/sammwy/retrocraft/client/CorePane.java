package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.server.net.SettingsPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.nio.file.Path;
import java.util.*;

import static com.sammwy.retrocraft.client.SettingsWidgets.*;

/** Core library: download/delete/enable emulator cores and manage their BIOS files. */
final class CorePane {
    interface Host {
        SettingsBackend backend();
        TextRenderer textRenderer();
        void status(Text text, int color);
    }

    private static final int ROW = 30, BIOS_ROW = 18;
    private static final String[] GLYPH_CORE = {" X X X ", "X     X", "X XXX X", "X XXX X", "X XXX X", "X     X", " X X X "};
    private static final String[] GLYPH_DOWN = {"       ", "   X   ", "   X   ", " X X X ", "  XXX  ", "   X   ", "       "};
    private static final String[] GLYPH_TRASH = {"       ", " XXXXX ", " X X X ", " X   X ", " X   X ", " X   X ", "       "};
    private static final String[] GLYPH_CHIP = {" X X X ", "XXXXXXX", "X XXX X", "X XXX X", "X XXX X", "XXXXXXX", " X X X "};
    private static final String[] GLYPH_FILE = {"  XXXX  ", "  X   X ", "  X   X ", "  X   X ", "  X   X ", "  XXXXX ", "        "};
    private static final String[] GLYPH_PLUS = {"       ", "   X   ", "   X   ", " XXXXX ", "   X   ", "   X   ", "       "};

    private final Host host;
    private int x, y, w, h;
    private boolean active;
    private float scroll;
    private boolean scrollDrag;
    private List<SettingsPackets.CoreList.Entry> entries = List.of();
    private final Set<String> expanded = new HashSet<>();
    private final Set<String> busy = new HashSet<>();
    private boolean loading = true;
    private String pickingCore;
    private int uploadId;
    private float uploadProgress = -1;
    private String uploadName;

    CorePane(Host host) { this.host = host; }

    void init(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        refresh();
    }
    void setActive(boolean value) { active = value; if (value) refresh(); }

    private void refresh() {
        loading = true;
        host.backend().cores((ok, message, list) -> {
            loading = false;
            if (list != null) entries = list;
            if (!ok) host.status(Text.translatable("settings.retrocraft.error", message == null ? "?" : message), BAD);
        });
    }

    private void act(int action, String coreId, String target) {
        busy.add(coreId);
        host.backend().coreAction(action, coreId, target, (ok, message, list) -> {
            busy.remove(coreId);
            if (list != null) entries = list;
            if (!ok) host.status(Text.translatable("settings.retrocraft.error", message == null ? "?" : message), BAD);
            else if ("downloading".equals(message)) host.status(Text.translatable("settings.retrocraft.cores.downloading", coreId), ACCENT);
        });
    }

    private void pickBios(SettingsPackets.CoreList.Entry entry) {
        if (pickingCore != null) return;
        pickingCore = entry.id();
        Thread.ofVirtual().name("retrocraft-bios-picker").start(() -> {
            String selected = TinyFileDialogs.tinyfd_openFileDialog("Select " + entry.display() + " BIOS", "", null, null, false);
            MinecraftClient.getInstance().execute(() -> {
                pickingCore = null;
                if (selected != null) uploadBios(entry.id(), Path.of(selected));
            });
        });
    }
    private void uploadBios(String coreId, Path file) {
        uploadName = file.getFileName().toString(); uploadProgress = 0; uploadId++;
        int id = uploadId;
        host.backend().coreUpload(file, coreId, new SettingsBackend.UploadCallback() {
            @Override public void progress(float fraction) { if (uploadId == id) uploadProgress = Math.max(uploadProgress, fraction); }
            @Override public void done(boolean ok, String message) {
                if (uploadId != id) return;
                uploadProgress = -1;
                if (ok) { host.status(Text.translatable("settings.retrocraft.cores.bios_uploaded", uploadName), GOOD); refresh(); }
                else host.status(Text.translatable("settings.retrocraft.error", message == null ? "?" : message), BAD);
            }
        });
    }


    private int rowCount() {
        int total = 0;
        for (var e : entries) { total += ROW; if (expanded.contains(e.id())) total += BIOS_ROW * (e.bios().size() + (e.needsBios() ? 1 : 0)) + 6; }
        return total;
    }
    private float clampScroll(float v) { return Math.max(0, Math.min(v, Math.max(0, rowCount() - h))); }

    boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!active || !inside(mx, my)) return false;
        scroll = clampScroll(scroll - (float) vertical * 12);
        return true;
    }
    boolean mouseDragged(double mx, double my) {
        if (scrollDrag) { dragScroll(my); return true; }
        return false;
    }
    void mouseReleased() { scrollDrag = false; }
    private boolean inside(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private boolean scrollTrackHit(double mx, double my) {
        return rowCount() > h && mx >= x + w - 7 && mx < x + w && my >= y && my < y + h;
    }
    private void dragScroll(double my) {
        int thumb = Math.max(12, h * h / Math.max(1, rowCount()));
        float max = Math.max(0, rowCount() - h);
        scroll = Math.max(0, Math.min(max, (float) ((my - y - thumb / 2.0) / Math.max(1, h - thumb) * max)));
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (!active || !inside(mx, my)) return false;
        if (button != 0) return true;
        if (scrollTrackHit(mx, my)) { scrollDrag = true; dragScroll(my); return true; }
        int ry = y - (int) scroll;
        for (var entry : entries) {
            if (my >= ry && my < ry + ROW) { clickCore(entry, mx); return true; }
            ry += ROW;
            if (expanded.contains(entry.id())) {
                int bioRows = entry.bios().size() + (entry.needsBios() ? 1 : 0);
                for (int i = 0; i < bioRows; i++, ry += BIOS_ROW)
                    if (my >= ry && my < ry + BIOS_ROW) { clickBios(entry, i, mx); return true; }
                ry += 6;
            }
        }
        return true;
    }

    private void clickCore(SettingsPackets.CoreList.Entry entry, double mx) {
        // Action slots walk back from the row's right edge: [bios ▸] [on/off] [download|trash].
        int edge = x + w - 10;
        if (entry.installed() && mx >= edge - 34 && mx < edge) { act(entry.enabled() ? SettingsPackets.CORE_DISABLE : SettingsPackets.CORE_ENABLE, entry.id(), ""); return; }
        if (mx >= edge - 50 && mx < edge - 36) {
            if (busy.contains(entry.id())) return;
            act(entry.installed() ? SettingsPackets.CORE_DELETE : SettingsPackets.CORE_DOWNLOAD, entry.id(), "");
            return;
        }
        if (entry.needsBios() && mx >= edge - 72 && mx < edge - 54) {
            if (expanded.contains(entry.id())) expanded.remove(entry.id()); else expanded.add(entry.id());
            return;
        }
        if (entry.needsBios()) { if (expanded.contains(entry.id())) expanded.remove(entry.id()); else expanded.add(entry.id()); }
    }

    private void clickBios(SettingsPackets.CoreList.Entry entry, int index, double mx) {
        if (index == entry.bios().size()) { pickBios(entry); return; } // the "+ upload" row
        var bios = entry.bios().get(index);
        int edge = x + w - 10;
        if (mx >= edge - 14 && mx < edge) act(SettingsPackets.BIOS_DELETE, entry.id(), bios.name());
    }


    void render(DrawContext context, int mx, int my, float delta) {
        if (!active) return;
        var tr = host.textRenderer();
        context.enableScissor(x, y, x + w, y + h);
        int ry = y - (int) scroll;
        for (var entry : entries) {
            if (ry + ROW > y && ry < y + h) renderCore(context, tr, entry, ry, mx, my);
            ry += ROW;
            if (expanded.contains(entry.id())) {
                int bioRows = entry.bios().size() + (entry.needsBios() ? 1 : 0);
                for (int i = 0; i < bioRows; i++, ry += BIOS_ROW)
                    if (ry + BIOS_ROW > y && ry < y + h) renderBios(context, tr, entry, i, ry, mx, my);
                ry += 6;
            }
        }
        if (loading) context.drawCenteredTextWithShadow(tr, Text.translatable("settings.retrocraft.roms.loading"), x + w / 2, y + h / 2 - 4, MUTED);
        context.disableScissor();
        int content = rowCount();
        if (content > h) {
            int trackX = x + w - 4;
            context.fill(trackX, y, trackX + 3, y + h, 0x30FFFFFF);
            int thumb = Math.max(12, h * h / content);
            int ty = y + (int) ((h - thumb) * scroll / Math.max(1, content - h));
            context.fill(trackX, ty, trackX + 3, ty + thumb, 0xB0FFFFFF);
        }
        if (uploadProgress >= 0 || pickingCore != null) {
            int by = y + h - 12;
            rounded(context, x + 4, by, w - 8, 10, 0xE014171E, 3);
            String label = pickingCore != null ? Text.translatable("settings.retrocraft.roms.picking").getString()
                    : Text.translatable("settings.retrocraft.roms.uploading", uploadName, Math.round(uploadProgress * 100)).getString();
            context.drawTextWithShadow(tr, tr.trimToWidth(label, w - 24), x + 10, by + 1, TEXT);
            if (uploadProgress >= 0) context.fill(x + 8, by + 9, x + 8 + (int) ((w - 16) * uploadProgress), by + 10, ACCENT);
        }
    }

    private void renderCore(DrawContext context, TextRenderer tr, SettingsPackets.CoreList.Entry entry, int ry, int mx, int my) {
        boolean hot = mx >= x + 2 && mx < x + w - 6 && my >= ry && my < ry + ROW;
        if (hot) rounded(context, x, ry, w - 6, ROW, 0x0FFFFFFF, 3);
        draw(context, GLYPH_CORE, x + 6, ry + 4, entry.installed() ? ACCENT : 0xFF6B7288);
        context.drawTextWithShadow(tr, entry.display(), x + 18, ry + 5, entry.enabled() ? TEXT : MUTED);
        context.drawTextWithShadow(tr, entry.id(), x + 18, ry + 16, 0xFF67707F);
        int edge = x + w - 10;
        // State chip: installed size, "downloading…" or "available".
        String state;
        int stateColor;
        if (busy.contains(entry.id()) || entry.downloading()) { state = Text.translatable("settings.retrocraft.cores.busy").getString(); stateColor = ACCENT; }
        else if (entry.installed()) { state = humanSize(entry.size()); stateColor = GOOD; }
        else { state = Text.translatable("settings.retrocraft.cores.available").getString(); stateColor = MUTED; }
        context.drawTextWithShadow(tr, state, edge - 90 - tr.getWidth(state), ry + 11, stateColor);
        // BIOS disclosure toggle.
        if (entry.needsBios()) {
            boolean open = expanded.contains(entry.id());
            draw(context, GLYPH_CHIP, edge - 68, ry + 11, open ? ACCENT : (hot ? 0xFFE4E8F0 : MUTED));
            context.drawTextWithShadow(tr, "BIOS", edge - 58, ry + 11, open ? TEXT : MUTED);
        }
        // Enable/disable pill.
        if (entry.installed()) drawToggle(context, edge - 34, ry + 9, entry.enabled(), hot);
        // Download (or remove) action.
        if (!busy.contains(entry.id()))
            draw(context, entry.installed() ? GLYPH_TRASH : GLYPH_DOWN, edge - 11, ry + 11, entry.installed() ? (hot ? BAD : MUTED) : (hot ? ACCENT_HI : ACCENT));
    }

    private void renderBios(DrawContext context, TextRenderer tr, SettingsPackets.CoreList.Entry entry, int index, int ry, int mx, int my) {
        int ix = x + 18;
        boolean hot = mx >= ix && mx < x + w - 6 && my >= ry && my < ry + BIOS_ROW;
        if (hot) rounded(context, ix - 4, ry, w - 6 - ix + 8, BIOS_ROW, 0x0FFFFFFF, 3);
        int edge = x + w - 10;
        if (index == entry.bios().size()) {
            draw(context, GLYPH_PLUS, ix, ry + 5, pickingCore != null ? MUTED : ACCENT);
            context.drawTextWithShadow(tr, Text.translatable("settings.retrocraft.cores.upload_bios"), ix + 12, ry + 5, pickingCore != null ? MUTED : TEXT);
            return;
        }
        var bios = entry.bios().get(index);
        draw(context, GLYPH_FILE, ix, ry + 5, 0xFF8B93A6);
        context.drawTextWithShadow(tr, tr.trimToWidth(bios.name(), w - 150), ix + 12, ry + 5, TEXT);
        context.drawTextWithShadow(tr, humanSize(bios.size()), edge - 18 - tr.getWidth(humanSize(bios.size())), ry + 5, MUTED);
        if (hot) draw(context, GLYPH_TRASH, edge - 11, ry + 5, BAD);
    }

    private static void drawToggle(DrawContext context, int x, int y, boolean on, boolean hot) {
        rounded(context, x, y + 1, 30, 12, on ? (hot ? ACCENT_HI : ACCENT) : (hot ? 0xFF3A3352 : 0xFF282138), 6);
        int kx = on ? x + 20 : x + 2;
        rounded(context, kx, y + 3, 8, 8, on ? ON_ACCENT : 0xFFCDC4E4, 4);
        if (!on) context.fill(kx + 3, y + 6, kx + 5, y + 8, 0xFF181226);
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KiB";
        return String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0);
    }
}
