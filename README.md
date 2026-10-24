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
| PlayStation | PlayStation Console | beetle_psx |
| PlayStation 2 | PlayStation 2 Console | pcsx2 |
| Dreamcast | Dreamcast Console | flycast |
| GameCube | GameCube Console | dolphin |
| Wii | Wii Console | dolphin |
| Arcade | Generic machine with swappable ROMs; one permanent-ROM cabinet per catalog game | mame2003 |

Plus three televisions to hook your consoles up to: a **Portable TV** (1 block), a **CRT Television** (2×1.5) and a big **Console Television** (3×2).

Every console, cartridge and TV has its own pixel-art model and texture. Top-loading consoles show the cartridge sitting in its slot; the NES front loader encloses it behind the door. Handheld screens and power indicators change when switched on. Disc-based systems have 3D game discs, including the smaller GameCube format. Disc colors identify the system: black GameCube, light blue Wii, blue/purple PS2, white PlayStation and orange Dreamcast.

## Handhelds

Hold a Game Boy, Game Boy Color, GBA or NES Handheld in your hand and right-click:

- Powered off → opens the console inventory with a single cartridge slot.
- Insert a cartridge, hit **Power on**, and the game HUD opens right away.
- Close the HUD whenever you want. The game keeps running on the server and picks up exactly where you left it the next time you open it.
- Powered handhelds render a live LCD preview directly on the item model in your hand, in inventories, on the ground and in item frames.
- Other players nearby hear your game's audio coming from you — even with the HUD closed, even if you drop the handheld on the ground.

Stash it in a chest and the game is suspended; take it back out and it resumes.

## Home consoles and TVs

Home consoles are blocks. Place one, place a TV within 12 blocks, insert a cartridge or disc (right-click the console with it) and power it on. The console grabs the nearest free TV and the game shows up on its screen, with sound coming from the set.

Anyone can walk up and watch. Docked consoles and arcades provide up to four controller slots; **Take control** claims the next free slot. The first slot holds the hybrid emulation lease, while each player drives their own pad. Handhelds stay single-player.

When you open an in-world play screen on a TV or arcade cabinet, the camera smoothly glides to frame the screen glass and hides your hand; use the top-right **`[-]` / `[+]`** buttons (or **`-` / `+`** keys) to step zoom between **50% and 400%**, alongside quick **Power off** and **Eject** buttons.

Arcade cabinets have a built-in CRT and need three blocks of vertical space. The generic arcade accepts swappable ROM boards. Each arcade ROM also appears as a dedicated cabinet in **RetroCraft - Arcades**: its internal ROM is permanent, and breaking the cabinet drops the same game variant without a separate board. Cabinet artwork follows the MAME set name or its parent set across nine themed cabinet families (**Pac-Man**, **Tetris**, **Space Invaders**, **Donkey Kong**, **Galaga**, **Street Fighter**, **Metal Slug**, **Mortal Kombat** and **Bubble Bobble**); unrecognized sets use the generic artwork with a personalized marquee. Hardware lives in **RetroCraft - Consoles**, and ROM items in **RetroCraft - Games**.

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
| `-` / `=` (or Numpad `-` / `+`) | Zoom out / in on world screens | Zoom out / in on world screens |
| F8 | Open settings | Open settings |
| Escape | Close the HUD | Close the HUD |

The in-game controller cards show console buttons and live stick positions. PlayStation symbols and stacked shoulder buttons match the pad layout. Key hints reflect your personal bindings and appear only on your own card. The server receives virtual controller buttons and axes; keyboard/mouse codes and binding profiles stay on your client.

## How it works (short version)

Emulation is **hybrid** by default (and can be switched to `server` or `client` in settings). While you're actively playing, the core runs locally on your client so local input avoids a network round trip; when you close the HUD or walk away, the save state is handed back to the server, which keeps running the game for anyone still watching or listening. Periodic checkpoints let the server recover the latest completed save after a crash or disconnect. The interval and transfer time determine how much progress can be recovered.

Durable per-game saves live under `config/retrocraft/saves/` (scoped per `player`, `global` or `none`): live `.state` snapshots keep powered consoles running across restarts, while battery `.sav` SRAM persists permanently even after powering a console off.

Each emulator core runs in its own isolated process, so one misbehaving game can't take the others down with it.

Cores are downloaded on demand from the [official libretro buildbot](https://buildbot.libretro.com/nightly/) for Linux, Windows and macOS (`x86_64` and `aarch64`). Only the cores your ROM library actually needs are fetched, and the hash of each core is pinned after the first download. Background core, ROM and BIOS transfers show live progress cards in the top-right corner of the screen. No ROMs, BIOS files or cores ship with the mod.

## Setup

1. Build the jar:

   ```sh
   ./gradlew build
   ```

2. Install `build/libs/retrocraft-0.1.0.jar` together with Fabric API on the server and on every client.

3. Put your ROMs in `config/retrocraft/roms/` on the server (or upload them in-game via **F8 → Server → ROM Library** by picking files or dragging and dropping them onto the window). Detection uses deterministic file extensions and, for shared formats, platform folders (`megadrive`/`genesis`, `ps1`/`psx`/`playstation`, `ps2`/`playstation2`, `dreamcast`/`dc`, `wii`, `gamecube`/`ngc`). Console-unique formats (`.nes`, `.sfc`/`.smc`, `.gb`, `.gbc`, `.gba`, `.md`/`.gen`/`.smd`, `.z64`/`.n64`/`.v64`, `.pbp`, `.cso`/`.zso`, `.cdi`/`.gdi`, `.wbfs`/`.rvz`/`.wad`/`.dol`, `.gcm`/`.gcz`/`.nkit`) match anywhere; shared disc images (`.iso`, `.chd`, `.cue`, `.img`) match inside their platform folder. ROM headers and archive contents are never inspected during discovery. Known MAME ZIP basenames stay intact and resolve to their full arcade titles. Games use content hashes for identity.

4. Start the world. Cartridges and discs for every ROM found appear in **RetroCraft - Games**, arcade variants in **RetroCraft - Arcades**, and consoles and TVs in **RetroCraft - Consoles**.

Added more ROMs later? Use the in-game **ROM Library** browser (which supports creating folders, renaming, moving, deleting, uploading and rescanning) or run `/retrocraft scan` (or `/retrocraft scan onlynews` to index only newly added files, op level 2) to rebuild and push the catalog to connected players without a restart.

### BIOS and Arcade notes

Systems that require firmware (**PlayStation**, **PlayStation 2**, **Dreamcast**, **GameCube / Wii** and **Arcade** sets that depend on a system BIOS) are managed under **F8 → Server → Cores** (`config/retrocraft/bios/<core>/`, or `config/retrocraft/bios/pcsx2/bios/` for PS2). If you power on a console whose BIOS is missing, an actionable **BIOS required** prompt opens and lets operators jump straight to that core's BIOS uploader. Installed BIOS files are synchronized automatically to hybrid clients when needed.

The default arcade core is MAME 2003, backed by a built-in MAME 0.78 / 2003-Plus machine and BIOS index:
- Known short ZIP names (e.g. `mslug.zip`, `pacman.zip`, `sf2.zip`) are recognized automatically and displayed with their full arcade title.
- Required arcade BIOS archives (such as `neogeo.zip`, `pgm.zip`, `skns.zip`, `cvs.zip`, `decocass.zip`, `playch10.zip`, `nss.zip`, `megatech.zip`, `megaplay.zip`, `stvbios.zip`, `cpzn1.zip`, `cpzn2.zip`, `tps.zip`, `taitofx1.zip`) can be uploaded in the BIOS manager, placed alongside ROMs, or extracted automatically from merged ROM/BIOS ZIPs; Neo-Geo BIOS archives are normalized so both MAME 0.78 and modern member names work.
- If a clone set is missing its parent ROM, Retrocraft reports the exact parent `.zip` and title required in the same folder.

### Configuration and commands

`config/retrocraft/server.json` is created on first launch. Press **F8**, use `/retrocraft gui` or `/rc gui`, or select the Mod Menu config button to access **Client** preferences without OP. Personal volume, video frame limit, world screens, handheld previews, controller HUD and camera focus are saved in `config/retrocraft/client.json`. The **Server** section (**Emulation**, **Streaming**, **Sessions**, **Cores** and **ROM Library**) and all server mutations require operator level 2.

Commands (`/retrocraft` or `/rc`):
- `/rc gui` — open the Retrocraft settings screen
- `/rc scan [onlynews]` — rescan the entire ROM library, or only newly added files (op level 2)
- `/rc reload` — reload `config/retrocraft/server.json` from disk (op level 2)
- `/rc config [<key> [<value>]]` — inspect or update any server config key or `cores.<cartridge>` mapping (op level 2)

The defaults in `server.json` are sensible; the knobs you're most likely to touch:

```json
{
  "emulationMode": "hybrid",
  "saveMode": "player",
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

`build` runs formatting checks, JUnit tests and headless Fabric GameTests for concurrent cartridge access, controller packets and arcade variants. `runGameTest` runs the Minecraft regression by itself. Test code and the test mod are excluded from the release jar.

Native NES tests use an original homebrew ROM and require `RETROCRAFT_TEST_CORE`. N64 tests additionally accept `RETROCRAFT_TEST_N64_CORE` and `RETROCRAFT_TEST_N64_ROM`. Download tests require `RETROCRAFT_TEST_DOWNLOAD=true`; MAME tests require their corresponding `RETROCRAFT_TEST_MAME_*` variables. Optional native tests are skipped when their inputs are absent. Committed textures and models require no asset-generation tools to build. For asset development, `python3 tools/generate_models.py` rebuilds them, `--check` verifies reproducibility, and `python3 tools/check_models.py` checks references, geometry and screen alignment. `python3 tools/preview_models.py` writes review images to `tmp/model-previews/`. These optional tools use Pillow; previews also use NumPy.

Consoles and cartridges are registered through `ConsoleRegistry`. `ConsoleType` distinguishes `HANDHELD`, `DOCKED` and `ARCADE`; the two placed types share multiplayer and world-video paths, while only `DOCKED` uses televisions. Adding a new system means registering a cartridge with a ROM discovery strategy and one or more consoles that accept it.

## License

MIT. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
