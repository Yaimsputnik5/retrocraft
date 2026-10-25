# Handheld HUD renderers

Register cartridges and hardware through `ConsoleRegistry` in the common initializer. Register each handheld's screen presentation through `ConsoleHudRegistry` in a **client initializer**, after its hardware exists. Docked consoles and arcade machines keep their existing world renderers: a nearby television for docked hardware and the cabinet's own screen for arcade machines.

The handheld pipeline is:

```text
runtime context → layout → background.png → cropped video panels → overlay.png
```

`ConsoleScreen` owns the game session, header, player controls, framebuffer texture and touch delivery. A registered `ConsoleHudRenderer` owns the screen composition and its two decoration passes. Built-in renderers and texture pairs are registered in `DefaultConsoleHuds`; the host screen contains no per-handheld drawing branches.

## Register a texture frame

Call this from a `ClientModInitializer`, after registering `example:portable` as a `HANDHELD` console:

```java
var frame = new HudFrame(
        Identifier.of("example", "textures/gui/portable/background.png"),
        Identifier.of("example", "textures/gui/portable/overlay.png"),
        64, 64,
        new HudInsets(12, 12, 12, 12));

ConsoleHudRegistry.register(
        Identifier.of("example", "portable"),
        new TexturedConsoleHudRenderer(
                ConsoleHudConfig.single(240, 160, 0xff101218), frame));
```

The classes are public under `com.sammwy.retrocraft.client.hud`. Registering an unknown console or a non-handheld console fails immediately. Registering an existing handheld ID replaces its renderer for the next opened HUD. Built-in defaults preserve any renderer registered earlier by another client initializer. Register renderers on the client thread; these classes are not common/server entry points.

Each frame has exactly two PNG resources. Source width and height describe those PNGs, not the framebuffer. `HudInsets(left, top, right, bottom)` defines both the fixed GUI-pixel space around the video and the source texture's nine-slice borders. The source centre must have positive dimensions.

The background is drawn beneath the video. The overlay is drawn above it with alpha blending. Keep the overlay's video window transparent. Both textures are nine-sliced: corners retain their size, edges stretch in one direction and the centre stretches in both. This keeps frames narrow as the window or GUI scale changes. Resource packs can replace either PNG; normal resource reloads pick up their artwork. If the source dimensions or border widths change, update the registered `HudFrame` too.

Built-in PNGs are under `assets/retrocraft/textures/gui/console_hud/<console>/`. Regenerate them with:

```bash
python3 tools/generate_hud_frames.py
python3 tools/generate_hud_frames.py --check
```

The generator reads insets from `DefaultConsoleHuds` and validates that every built-in registration has artwork. Its pixel artwork is original, reproducible and independent of the 3D model generator.

## Runtime context and custom layouts

`ConsoleHudContext` supplies the session UUID, full console identifier, available viewport, actual framebuffer dimensions, selected arrangement, controller status, mouse position and render delta. Rectangle positions and sizes are **GUI pixels**; texture source coordinates are texels. The viewport already excludes the title, action buttons and player-control legend.

`layout(context)` returns a `ConsoleHudLayout` with one or more `Panel` records:

- `video`: the video rectangle in GUI pixels.
- `frame`: the surrounding texture rectangle, containing the video.
- `crop`: normalized UV bounds in the entire core framebuffer.
- `touch`: whether this panel accepts pointer input.

The default implementation computes layout every render from the current viewport and framebuffer dimensions. It preserves the native aspect ratio. Extend `TexturedConsoleHudRenderer` to override `layout(context)` or a decoration pass; implement `ConsoleHudRenderer` for a completely custom composition. For example, `context.viewport().x()` and `.width()` let a renderer position panels relative to the space available at that moment. No edits to `ConsoleScreen` are required.

The host calls `renderer.render(...)`, which calculates one composition and uses it for background, video and overlay in that order. The same composition is retained for hit testing until the next render. Resize and arrangement changes invalidate it. Touch input only hits a panel's video rectangle, and its `crop` converts the local pointer to the full framebuffer. Pressing the border or an upper non-touch panel sends no touch pressure.

## Dual-screen handhelds

Use a `ConsoleHudConfig` with a `DualScreen` descriptor and a namespaced layout preference key:

```java
var config = new ConsoleHudConfig(
        400, 480, 0xff101218,
        DualScreen.THREEDS, "example:portable");
ConsoleHudRegistry.register(id, new TexturedConsoleHudRenderer(config, frame));
```

The default renderer provides vertical, horizontal, top-large and bottom-large arrangements. It crops each native screen independently, including the narrower 3DS touch panel, and applies the two texture layers separately to each panel. Generic layout preference keys persist independently in the client's `handheldLayouts` map. Existing NDS/3DS preferences remain readable.

A custom layout must mark only its actual touch surface and provide its correct framebuffer crop. Use the returned panel geometry for both display and input; no separate pointer-coordinate rules are needed.

## World screens

`TvRenderer`, `DockedConsoleRenderer`, camera focus and nearby-TV selection keep their existing responsibilities. The handheld registry rejects these hardware types. When world video is disabled in client settings, `WorldConsoleFrame` provides the existing flat TV/arcade presentation; it is separate from the handheld renderer registry.
