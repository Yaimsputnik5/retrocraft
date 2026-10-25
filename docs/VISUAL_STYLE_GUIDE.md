# Visual Style Guide

Hardware should be recognizable from its shape, proportions, and arrangement of parts, even without labels. Translate the object into cubes, stepped contours, and crisp pixels while preserving its defining physical features.

The order of visual priority is **silhouette → proportions → distinctive parts → materials and color → printed details**. Resolve each level before adding detail to the next.

## Shape and Proportions

- Start from front, side, and rear references. Establish the relationship between width, height, and depth, along with screen positions, controls, and empty space.
- Build an unmarked blockout first. It should communicate what kind of object this is and how it is used.
- Preserve defining asymmetries. The placement of a slot, port, lid, or button group can identify the hardware more effectively than a logo.
- Simplify curves into a few deliberate steps. Use crosses for directional pads and stepped profiles for buttons and rounded outlines.
- Give shells, lids, and edges believable thickness. A handheld needs a solid body; a disc needs a thin profile.
- Use geometry for features that change the silhouette, create visible relief, or explain construction. Small inscriptions and perforations can use textures.
- Inspect the volume from every side. The rear, underside, and side panels should belong to the same finished object.
- Avoid clusters of tiny parts that disappear when the model is reduced. Detail should contribute recognition, depth, or function.

## Scale, Orientation, and Geometry

Model units and interface pixels are separate coordinate systems. Convert explicitly when moving between them.

| Convention | Rule |
| --- | --- |
| World scale | 16 model units equal one block. |
| Axes | X: width; Y: height; Z: depth. |
| Front of placed hardware | North, toward −Z. |
| Front of handhelds and game media | South, toward +Z. |
| Base texture density | 4 texels per model unit. |
| Model UVs | Coordinates normalized to the atlas's 0–16 UV space. |
| Local JSON element rotation | One axis per element; angles of −45°, −22.5°, 0°, 22.5°, or 45°. |

- Respect the format limits: element coordinates between −16 and 32, positive volume thickness, and UVs within the texture. Split larger objects into parts with their own transforms.
- Keep visual density consistent between neighboring faces. Enlarging a face should not turn its markings into oversized pixels.
- Use small, justified offsets between overlapping surfaces to prevent z-fighting. Labels, glass, and controls must remain attached to their supporting surfaces.
- Connect mechanical parts: shafts inside their mounts, buttons seated in the shell, and cartridges inserted into appropriately sized slots.
- Match selection and collision bounds to the placed shape, including orientation and protruding parts.

## Hinges and Articulated Parts

The pose should represent an open object in use. Set the physical opening in the model; use the camera to present that shape.

- Place the pivot on the hinge's actual axis and keep both halves connected.
- Examine the side profile first. The base and lid should form a clear opening, with the base lying flatter and the lid raised.
- As a visual starting point, explore an interior opening of roughly 110°–135° and adjust it to the reference. A pose approaching 180° needs a specific reason in the design.
- Do not flatten both halves into nearly the same plane simply to expose every screen. Adjust the object's presentation while retaining its depth.
- Rotate each half as a complete assembly: shell, bezel, glass, printed details, and video. Tilting the shell must also move the image attached to it.
- Check that the lid does not intersect the base and that the hinge remains connected as the pose changes.
- Review the opening from the side, in three-quarter view, and in hand. The same pose should look believable in all three views.

## Color, Materials, and Lighting

Use mostly plain surfaces and a restrained palette. Changes in plane and Minecraft's lighting should explain the volume.

| Surface | Treatment |
| --- | --- |
| Plastic | Uniform base color, with subtle variations for edges, seams, and separate parts. |
| Dark plastic | Slightly tinted blacks; distinguish the body, recesses, and controls through value. |
| Metal and contacts | Restrained grays or warm tones, with short highlights and crisp edges. |
| Unlit glass | A dark surface or the panel's characteristic tint; a subtle graphic reflection may help define it. |
| Lit indicators | Small, bright areas that remain readable in shadow. |
| Decorative prints | Pixel art compositions with enough contrast against their supporting surface. |

- Treat shell, bezel, and accent as separate color roles. Concentrate accents on controls, markings, and signals.
- Allow stronger colors in decoration and buttons when they define the object's identity. Leave quiet areas around them.
- Avoid random noise, uniform grime, smooth gradients, and photographic reflections on plastic.
- Do not bake large lighting shadows into faces. Reserve drawn contrast for seams, small relief details, and printed artwork.
- Check colors under Minecraft lighting. An unshaded atlas alone does not represent the final appearance.
- Let the shell respond to environmental light. Keep video and luminous indicators readable in dark surroundings.

## Textures and 2D Artwork

- Draw on a defined grid, with hard edges and readable pixel clusters.
- Use nearest-neighbor scaling for artwork incorporated into assets. Avoid antialiased outlines and transparency halos.
- Keep detail scale consistent. A small printed mark may need extra resolution to remain readable without increasing detail across the entire shell.
- Give logos, labels, and stripes breathing room. Do not stretch them to fill a surface or accidentally cut them across an edge.
- Render physical text with consistent pixel lettering. If a legend does not fit, adjust the composition or wording before compressing the characters.
- Orient each print for its face and intended viewing direction. Pay particular attention to upper faces, rear faces, and rotated surfaces.
- Keep artwork within its printing areas: labels, side panels, marquees, and control panels. Decoration should preserve the object's construction.
- Preserve the characteristic shapes and palettes of source artwork. Adapt its composition to the surface without distorting characters or symbols.
- Keep the artwork inputs required to regenerate assets and record their provenance. Store complete sheets and extraction experiments in `tmp/`.

Use power-of-two atlas sizes, separated regions, and a one-texel edge extrusion to prevent UV bleeding. Reuse identical regions and crop the atlas to the required size.

## Shells, Cabinets, and Game Media

- Home consoles should sit convincingly on their base or feet. Respect the placement of lids, trays, and slots.
- Televisions need proportional cabinet depth, bezels, and supports. Keep the video surface contained within the glass.
- Cabinets should read as continuous constructions: plinth, body, control panel, screen, and upper section. Avoid open seams between separately rendered parts.
- Give controls enough physical room to operate. Joysticks and buttons must not intrude into the screen or pass through the control panel.
- Distinguish cartridges and cards through their outline, thickness, notches, and contacts. Labels complement that shape.
- Give discs a real hole in their geometry and a consistent stepped outline. Preserve identifying shell colors on the rim and rear as well as the printed face.
- Present inserted media at the correct orientation, scale, and depth. Enclosed loading mechanisms should hide it where appropriate.

## Screens, Video, and Thumbnails

Glass, video, and interactive regions must describe the same physical surface.

- Define each screen's bounds, orientation, and pivot explicitly. Update video rendering and its validation whenever the geometry changes.
- Place video just ahead of the glass to prevent z-fighting without visibly detaching it from the bezel.
- Preserve each screen's native aspect ratio. Center the image and use dark bars when the available space has a different ratio.
- Crop each screen from its source region. Exclude framebuffer gaps and outer margins before fitting it to the glass.
- On devices with multiple screens, preserve their order, individual proportions, and differences in size. Each panel should show its own portion of the content.
- Apply the same rules to live video and thumbnails. Lower resolution must preserve cropping, orientation, and framing.
- Keep pixels crisp when reducing content. Add filters, glow, scanlines, or reflections over gameplay only as an explicit visual decision.
- Keep geometry and framing consistent between powered and unpowered states. Change the surfaces that communicate power unless a mechanical movement is intentional.
- Use distinct test patterns for each screen, with grids and orientation markers. Check cropping, inversion, stretching, screen order, and alignment with the glass.

## Gameplay Bezels and Interface

A gameplay bezel is a compact screen surround. Keep it narrow and consistent with neighboring gameplay frames, using the hardware palette and restrained surface details. Reserve full device silhouettes, grips, directional pads, analog sticks, and face-button clusters for the hardware models.

- Author each handheld frame as a background PNG and a transparent overlay PNG. Register their source dimensions and fixed border insets through the handheld HUD API; draw video between the layers. Use nine-slice scaling to retain crisp corners and border thickness at every window size.
- Build the artwork in layers: outer shell, inner bezel, glass edge, and video area. Keep the overlay window transparent unless a deliberate effect requires otherwise.
- Retain the physical object's color hierarchy. Limit decoration to small details in the narrow frame, such as a stripe or indicator. Keep gameplay frames free of decorative branding and button legends.
- Draw corners as pixel steps, with thin upper highlights and restrained lower shadows.
- Size margins in GUI pixels. Prevent them from growing with the framebuffer until they consume an excessive part of the screen.
- Keep decoration outside the video and touch regions. Labels and indicators should remain readable without competing with gameplay.
- Give each screen its own bezel in multi-screen views. Support vertical and horizontal arrangements, as well as large/small screen hierarchies, without stretching screens or overlapping their frames.
- Calculate composition after reserving room for the title, actions, and controls. Review high GUI scales and small windows.
- When video is already displayed on a world surface, keep the HUD light enough to leave that object visible.

The general interface uses dark backgrounds, subtly differentiated panels, and a violet accent for selection and prominent actions. Gameplay bezels retain the hardware's palette. Reuse shared interface components and color roles.

- Use Minecraft's typeface for interface text. Reserve texture-drawn lettering for physical printed details.
- Make icons small pixel silhouettes with recognizable meanings. Provide tooltips when they have no text labels.
- Distinguish idle, hover, focus, selected, and disabled states through restrained value and accent changes. Maintain label contrast in every state.
- Group related controls and keep alignment and spacing consistent. Titles must remain clear of action buttons.
- Use brief appearance, scale, or response animations. Elements should settle onto their intended pixel grid.
- Make clipping and interactive regions follow interface transforms.

## Touch Interaction

- Derive drawing and hit testing from the same layout geometry.
- Convert local touch-panel coordinates into framebuffer input coordinates, including margins and crops.
- Preserve that conversion when rearranging or resizing screens. Changing the layout must not change which gameplay point is touched.
- Do not accept touch input on the bezel, empty bars, or a screen without touch capability.
- Check pressing, dragging, leaving and reentering the panel, releasing, losing focus, and changing layouts. Touch pressure must never remain stuck.
- Use a cursor with moderate contrast that does not conceal the interaction point.

## Production and Review

Edit the generators and artwork inputs that produce the assets. Regenerate the corresponding JSON and PNG files; any manual adjustment to generated output must also be represented in its reproducible source.

```bash
python3 tools/generate_models.py
python3 tools/generate_models.py --check
python3 tools/check_models.py
python3 tools/generate_hud_frames.py --check
python3 tools/preview_models.py --output tmp/style-review/front
python3 tools/preview_models.py --output tmp/style-review/back --view back
python3 tools/preview_models.py --output tmp/style-review/straight --view straight
python3 tools/preview_models.py --output tmp/style-review/on --powered
```

The software preview helps assess silhouettes, UVs, and composition. Confirm the result inside Minecraft afterward: lighting, depth, item transforms, video, and controls depend on the actual renderer.

Before considering a visual change complete:

- Compare the asset with neighboring objects at the same scale and camera angle.
- Review front, side, rear, and three-quarter views. Check the physical opening of articulated objects.
- Inspect the object at its actual inventory size, in both hands, placed or dropped, and in an item frame where applicable.
- Check readability in light and shadow, in both powered and unpowered states.
- Look for disconnected parts, reversed faces, twisted UVs, filled holes, and flickering surfaces.
- Test video and thumbnails on every screen. Confirm that each surface follows the transforms of its own model assembly.
- Review gameplay bezels in small and large windows, in every available arrangement. Test touch regions if their geometry changed.
- Save comparable screenshots and smoke-test results in `tmp/`. Keep only the required final sources and assets in production paths.

Automated validation checks technical constraints. Visual acceptance also requires a recognizable shape, a believable pose, and clearly readable content at the intended size.
