# Retrocraft

Play real retro games inside Minecraft.

Retrocraft adds handhelds, home consoles, arcade cabinets and CRT televisions to the game, all backed by actual [libretro](https://www.libretro.com/) emulator cores. Drop your ROMs on the server, grab a cartridge from the creative menu, plug it into a console and play — alone on a Game Boy in your pocket, or with friends gathered around a TV in your base.

- Minecraft **1.21.1** · Fabric · Java 21
- Works on dedicated servers and in singleplayer

## What's in the box

| System | Hardware in game | Core |
| --- | --- | --- |
| NES | NES Handheld, NES Console | fceumm |
| SNES | SNES Console | snes9x |
| Game Boy / Game Boy Color | Game Boy, Game Boy Color | gambatte |
| Game Boy Advance | Game Boy Advance | mgba |
| Mega Drive / Genesis | Mega Drive, Genesis | genesis_plus_gx |
| Nintendo 64 | Nintendo 64 | parallel_n64 |
| Arcade | Generic machine with swappable ROMs; one permanent-ROM cabinet per catalog game | mame2003 |

Plus three televisions to hook your consoles up to: a **Portable TV** (1 block), a **CRT Television** (2×1.5) and a big **Console Television** (3×2).

Every console, cartridge and TV has its own pixel-art model and texture. Top-loading consoles show the cartridge sitting in its slot; the NES front loader encloses it behind the door. Handheld screens and power indicators change when switched on. Disc-based systems have 3D game discs, including the smaller GameCube format. Disc colors identify the system: black GameCube, light blue Wii, blue/purple PS2, white PlayStation and orange Dreamcast.

## Handhelds

Hold a Game Boy, GBA or NES Handheld in your hand and right-click:

- Powered off → opens the console inventory with a single cartridge slot.
- Insert a cartridge, hit **Power on**, and the game HUD opens right away.
- Close the HUD whenever you want. The game keeps running on the server and picks up exactly where you left it the next time you open it.
- Other players nearby hear your game's audio coming from you — even with the HUD closed, even if you drop the handheld on the ground.

Stash it in a chest and the game is suspended; take it back out and it resumes.

## Home consoles and TVs

Home consoles are blocks. Place one, place a TV within 12 blocks, insert a cartridge (right-click the console with it) and power it on. The console grabs the nearest free TV and the game shows up on its screen, with sound coming from the set.

Anyone can walk up and watch. Docked consoles provide up to four controller slots; **Take control** claims the next free slot. The first slot holds the hybrid emulation lease, while each player drives their own pad. Handhelds stay single-player. Arcade cabinets have a built-in CRT and need three blocks of vertical space. The generic arcade accepts swappable ROM boards. Each arcade ROM also appears as a dedicated cabinet in **RetroCraft - Arcades**: its internal ROM is permanent, and breaking the cabinet drops the same game variant without a separate board. Cabinet artwork follows the set name, so revisions share their family design; unrecognized sets use the generic artwork with a personalized marquee. Hardware lives in **RetroCraft - Consoles**, and ROM items in **RetroCraft - Games**.

Break the console or eject the cartridge and the TV is free again. Unloading the chunk suspends the game; it comes back when someone's around to watch.

## Controls

Press **F8 → Client → Controls**, choose a console, then click a control and press its new key or mouse button. **Escape** cancels recording, **Delete/Backspace** clears a binding, and **Defaults** restores that console's defaults. **Apply** saves changes locally in `config/retrocraft/client.json`. Each console has its own bindings. Shared keys are marked in red. F8 and Escape remain reserved for the menus.

| Default keys | Classic pads | Pads with analog sticks |
| --- | --- | --- |
| Arrow keys | D-pad | D-pad |
| Z / X | B / A | Bottom / right face button (Cross / Circle on PlayStation) |
| A / S | Y / X | — |
| C / V | — | Left / top face button (Square / Triangle on PlayStation) |
| Q / W | L / R | — |
| Q / E | — | L1 / R1 |
| 1 / 3 | — | L2 / R2, where present |
| WASD | — | Left stick |
| IJKL | — | Right stick, C stick or N64 C buttons |
| Left Shift / Right Ctrl | — | L3 / R3 on PlayStation |
| Enter / Right Shift | Start / Select | Start / Select, where present |
| Escape | Close the HUD | Close the HUD |

The in-game controller cards show console buttons and live stick positions. PlayStation symbols and stacked shoulder buttons match the pad layout. Key hints reflect your personal bindings and appear only on your own card. The server receives virtual controller buttons and axes; keyboard/mouse codes and binding profiles stay on your client.

## How it works (short version)

Emulation is **hybrid**. While you're actively playing, the core runs locally on your client so local input avoids a network round trip; when you close the HUD or walk away, the save state is handed back to the server, which keeps running the game for anyone still watching or listening. Periodic checkpoints let the server recover the latest completed save after a crash or disconnect. The interval and transfer time determine how much progress can be recovered.

Each emulator core runs in its own isolated process, so one misbehaving game can't take the others down with it.

Cores are downloaded on demand from the [official libretro buildbot](https://buildbot.libretro.com/nightly/) for Linux, Windows and macOS. Only the cores your ROM library actually needs are fetched, and the hash of each core is pinned after the first download. No ROMs, BIOS files or cores ship with the mod.

## Setup

1. Build the jar:

   ```sh
   ./gradlew build
   ```

2. Install `build/libs/retrocraft-0.1.0.jar` together with Fabric API on the server and on every client.

3. Put your ROMs in `config/retrocraft/roms/` on the server. Detection uses file extensions and, for shared disc formats such as ISO and CHD, platform folders (`ps1`, `ps2`, `dreamcast`, `wii`, `gamecube`). ROM headers and archive contents are never inspected during discovery. Known MAME ZIP basenames stay intact. Games use content hashes for identity.

4. Start the world. Cartridges for every ROM found appear in the **Retrocraft** creative tab, alongside the consoles and TVs.

Added more ROMs later? Run `/retrocraft scan` (op level 2) and the catalog is rebuilt and pushed to connected players without a restart.

### Arcade notes

The default arcade core is MAME 2003, so ROM sets must match its 0.78 database. Sets must be complete, self-contained ZIPs including their BIOS where needed.

### Configuration

`config/retrocraft/server.json` is created on first launch. Press **F8**, use `/retrocraft gui` or `/rc gui`, or select the Mod Menu config button to access **Client** preferences without OP. Personal volume, video frame limit, world screens, handheld previews, controller HUD and camera focus are saved in `config/retrocraft/client.json`. The **Server** section and all server mutations require operator level 2. The defaults are sensible; the knobs you're most likely to touch:

```json
{
  "audioRadius": 16,
  "streamFps": 20,
  "maxActiveInstances": 8,
  "maxRomSizeMiB": 64
}
```

The `cores` key stores the mappings to the trusted, compatible core pool. Personal options can suppress ambient audio, video and thumbnails at the server, so disabling them also avoids the corresponding network traffic. Video textures obey the personal frame limit; server streams run at up to Minecraft's 20 ticks per second. Audio is delivered independently of the video frame rate.

## Development

```sh
./gradlew spotlessApply
./gradlew build
./gradlew runGameTest
RETROCRAFT_TEST_CORE=/path/to/fceumm_libretro.so ./gradlew test
```

`build` runs formatting checks, JUnit tests and headless Fabric GameTests for concurrent cartridge access and arcade variants. `runGameTest` runs the Minecraft regression by itself. Test code and the test mod are excluded from the release jar.

Native NES tests use an original homebrew ROM and require `RETROCRAFT_TEST_CORE`. N64 tests additionally accept `RETROCRAFT_TEST_N64_CORE` and `RETROCRAFT_TEST_N64_ROM`. Download tests require `RETROCRAFT_TEST_DOWNLOAD=true`; MAME tests require their corresponding `RETROCRAFT_TEST_MAME_*` variables. Optional native tests are skipped when their inputs are absent. Committed textures and models require no asset-generation tools to build. For asset development, `python3 tools/generate_models.py` rebuilds them, `--check` verifies reproducibility, and `python3 tools/check_models.py` checks references, geometry and screen alignment. `python3 tools/preview_models.py` writes review images to `tmp/model-previews/`. These optional tools use Pillow; previews also use NumPy.

See [the optimization review](docs/OPTIMIZATION_REVIEW.md) for design constraints, regression coverage and the latest validation scope.

Consoles and cartridges are registered through `ConsoleRegistry`. `ConsoleType` distinguishes `HANDHELD`, `DOCKED` and `ARCADE`; the two placed types share multiplayer and world-video paths, while only `DOCKED` uses televisions. Adding a new system means registering a cartridge with a ROM discovery strategy and one or more consoles that accept it.

## License

MIT. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
