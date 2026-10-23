package com.sammwy.retrocraft.client;

import com.sammwy.retrocraft.cores.KnownCores;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.net.SettingsPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.nio.file.Path;
import java.util.*;

import static com.sammwy.retrocraft.client.SettingsWidgets.*;

/** BIOS manager: every known core is preinstalled, so this pane only manages firmware files. */
final class CorePane {
    interface Host {
        SettingsBackend backend();
        TextRenderer textRenderer();
        void status(Text text, int color);
    }

    private static final int ROW = 30, BIOS_ROW = 18;
    private static final String[] GLYPH_CHIP = {" X X X ", "XXXXXXX", "X XXX X", "X XXX X", "X XXX X", "XXXXXXX", " X X X "};
    private static final String[] GLYPH_TRASH = {"       ", " XXXXX ", " X X X ", " X   X ", " X   X ", " X   X ", "       "};
    private static final String[] GLYPH_FILE = {"  XXXX  ", "  X   X ", "  X   X ", "  X   X ", "  X   X ", "  XXXXX ", "        "};
    private static final String[] GLYPH_PLUS = {"       ", "   X   ", "   X   ", " XXXXX ", "   X   ", "   X   ", "       "};
    private static final List<KnownCores.Core> BIOS_CORES = KnownCores.all();
    private static final Map<String, ItemStack> CONSOLE_ICONS = consoleIcons();

    private static Map<String, ItemStack> consoleIcons() {
        Map<String, ItemStack> icons = new HashMap<>();
        for (var mapping : KnownCores.defaultMappings().entrySet()) {
            var item = ConsoleRegistry.cartridge(Identifier.of(mapping.getKey()));
            if (item != null) icons.putIfAbsent(mapping.getValue(), new ItemStack(item));
        }
        return Map.copyOf(icons);
    }

    private final Host host;
    private int x, y, w, h;
    private boolean active;
    private float scroll;
    private boolean scrollDrag;
    private final Map<String, List<SettingsPackets.RomList.Entry>> bios = new HashMap<>();
    private final Set<String> expanded = new HashSet<>();
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
        int[] remaining = {BIOS_CORES.size()};
        for (var core : BIOS_CORES) {
            if (!core.needsBios()) { bios.put(core.id(), List.of()); if (--remaining[0] == 0) loading = false; continue; }
            host.backend().biosList(core.id(), (ok, entries, truncated, error) -> {
                bios.put(core.id(), ok ? entries : List.of());
                if (--remaining[0] == 0) loading = false;
            });
        }
    }

    private void pickBios(KnownCores.Core core) {
        if (pickingCore != null) return;
        pickingCore = core.id();
        Thread.ofVirtual().name("retrocraft-bios-picker").start(() -> {
            String selected = TinyFileDialogs.tinyfd_openFileDialog("Select " + core.display() + " BIOS", "", null, null, false);
            MinecraftClient.getInstance().execute(() -> {
                pickingCore = null;
                if (selected != null) uploadBios(core.id(), Path.of(selected));
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
        for (var core : BIOS_CORES) {
            total += ROW;
            if (expanded.contains(core.id())) total += BIOS_ROW * (bios.getOrDefault(core.id(), List.of()).size() + 1) + 6;
        }
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
        for (var core : BIOS_CORES) {
            if (my >= ry && my < ry + ROW) { clickCore(core, mx); return true; }
            ry += ROW;
            if (expanded.contains(core.id())) {
                var files = bios.getOrDefault(core.id(), List.of());
                for (int i = 0; i <= files.size(); i++, ry += BIOS_ROW)
                    if (my >= ry && my < ry + BIOS_ROW) { clickBios(core, files, i, mx); return true; }
                ry += 6;
            }
        }
        return true;
    }

    private void clickCore(KnownCores.Core core, double mx) {
        int edge = x + w - 10;
        if (core.needsBios() && mx >= edge - 72 && mx < edge - 28) {
            if (expanded.contains(core.id())) expanded.remove(core.id()); else expanded.add(core.id());
        }
    }

    private void clickBios(KnownCores.Core core, List<SettingsPackets.RomList.Entry> files, int index, double mx) {
        if (index == files.size()) { pickBios(core); return; }
        int edge = x + w - 10;
        if (mx >= edge - 14 && mx < edge)
            host.backend().biosDelete(core.id(), files.get(index).name(), (ok, message) -> {
                if (ok) refresh();
                else host.status(Text.translatable("settings.retrocraft.error", message == null ? "?" : message), BAD);
            });
    }

    void render(DrawContext context, int mx, int my, float delta) {
        if (!active) return;
        var tr = host.textRenderer();
        context.enableScissor(x, y, x + w, y + h);
        int ry = y - (int) scroll;
        for (var core : BIOS_CORES) {
            if (ry + ROW > y && ry < y + h) renderCore(context, tr, core, ry, mx, my);
            ry += ROW;
            if (expanded.contains(core.id())) {
                var files = bios.getOrDefault(core.id(), List.of());
                for (int i = 0; i <= files.size(); i++, ry += BIOS_ROW)
                    if (ry + BIOS_ROW > y && ry < y + h) renderBios(context, tr, core, files, i, ry, mx, my);
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

    private void renderCore(DrawContext context, TextRenderer tr, KnownCores.Core core, int ry, int mx, int my) {
        boolean hot = mx >= x + 2 && mx < x + w - 6 && my >= ry && my < ry + ROW;
        if (hot) rounded(context, x, ry, w - 6, ROW, 0x0FFFFFFF, 3);
        var icon = CONSOLE_ICONS.getOrDefault(core.id(), ItemStack.EMPTY);
        if (!icon.isEmpty()) context.drawItem(icon, x + 5, ry + 7);
        context.drawTextWithShadow(tr, core.display(), x + 26, ry + 5, TEXT);
        int idEnd = x + 26 + tr.getWidth(core.id());
        context.drawTextWithShadow(tr, core.id(), x + 26, ry + 16, 0xFF67707F);
        if (core.needsBios()) {
            var files = bios.getOrDefault(core.id(), List.of());
            if (files.isEmpty()) {
                context.drawTextWithShadow(tr, Text.translatable("settings.retrocraft.cores.requires_bios"), idEnd + 6, ry + 16, BAD);
            } else {
                context.drawTextWithShadow(tr, files.size() + " BIOS", idEnd + 6, ry + 16, GOOD);
            }
        }
        int edge = x + w - 10;
        if (core.needsBios()) {
            boolean open = expanded.contains(core.id());
            boolean chipHot = mx >= edge - 72 && mx < edge - 28 && hot;
            rounded(context, edge - 72, ry + 7, 44, 16, chipHot ? CONTROL_HOVER : CONTROL, 3);
            draw(context, GLYPH_CHIP, edge - 69, ry + 11, open || chipHot ? ACCENT : MUTED);
            context.drawTextWithShadow(tr, open ? "BIOS ▾" : "BIOS ▸", edge - 57, ry + 11, open || chipHot ? TEXT : MUTED);
        }
    }

    private void renderBios(DrawContext context, TextRenderer tr, KnownCores.Core core, List<SettingsPackets.RomList.Entry> files, int index, int ry, int mx, int my) {
        int ix = x + 18;
        boolean hot = mx >= ix && mx < x + w - 6 && my >= ry && my < ry + BIOS_ROW;
        if (hot) rounded(context, ix - 4, ry, w - 6 - ix + 8, BIOS_ROW, 0x0FFFFFFF, 3);
        int edge = x + w - 10;
        if (index == files.size()) {
            draw(context, GLYPH_PLUS, ix, ry + 5, pickingCore != null ? MUTED : ACCENT);
            context.drawTextWithShadow(tr, Text.translatable("settings.retrocraft.cores.upload_bios"), ix + 12, ry + 5, pickingCore != null ? MUTED : TEXT);
            return;
        }
        var file = files.get(index);
        draw(context, GLYPH_FILE, ix, ry + 5, 0xFF8B93A6);
        context.drawTextWithShadow(tr, tr.trimToWidth(file.name(), w - 150), ix + 12, ry + 5, TEXT);
        context.drawTextWithShadow(tr, humanSize(file.size()), edge - 18 - tr.getWidth(humanSize(file.size())), ry + 5, MUTED);
        if (hot) draw(context, GLYPH_TRASH, edge - 11, ry + 5, BAD);
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KiB";
        return String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0);
    }
}
