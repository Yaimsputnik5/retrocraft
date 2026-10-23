# Optimization and correctness review

This review preserves deterministic ROM discovery: filenames and platform directories select systems. No header sniffing or archive inspection is performed during discovery. Matching files are still hashed for identity. Small files use full SHA-256; disc-sized files retain the existing bounded prefix-plus-size identity. Explicit BIOS archive installation remains supported.

## Runtime changes

| Area | Change | Effect |
| --- | --- | --- |
| Native video | Reusable scanline buffers replace per-pixel JNA reads | Native crossings fall from width × height to height per converted frame. |
| Server video | Capture follows stream cadence, capped at 20 Hz | Avoids converting and copying every emulated frame when only a slower stream is needed. |
| Rendering | Ordered background decoding publishes only its latest picture | Catch-up does not enqueue a GPU upload for every intermediate frame. |
| Rendering | Personal texture update limit and empty-delta picture reuse | Bounds upload cadence and avoids uploads for unchanged remote pictures. |
| Video compression | Dense tile updates become keyframes | Avoids tile-index overhead when a full RGB frame is smaller. |
| Transfers | Exact bounded assembly buffers and fewer intermediate compression copies | Avoids growing transfer buffers and an extra full-size copy at completion. |
| Audio | Audio delivery runs independently of video FPS | A low video rate no longer discards most of the audio. |
| Audio | Two queued chunks, smaller aligned device buffer, gain applied during playback | Bounds backlog and removes sample scaling from the game thread. |
| Network | Personal audio, ambient video and preview subscriptions | Disabled features stop their corresponding server traffic. Active play screens can still receive video. |
| Network | Owner-only hybrid play avoids video relay to the owner | Avoids encoding, uploading and recompressing a picture already available locally. |
| Input | Local buttons are sampled at render cadence | Local emulation need not wait for the next 20 Hz game tick. |
| Input | Immutable published input masks and stale-pad resets | Workers see complete input snapshots; stale remote presses are released. |
| World discovery | Entity load/unload tracking after one initial scan | Stops traversing every loaded entity once per second to find dropped handhelds. |
| File operations | Bounded server library executor; asynchronous local listings and mutations | Moves directory scans and potentially large deletes out of game/UI ticks. |
| Uploads | Bounded reads and hashing off-thread; closed-screen guard | Prevents a delayed file read from starting a cancelled remote upload. |
| Cores | Reentrant preparation lock and closed HTTP clients | Avoids holding a monitor across downloads and releases download resources. |

These are structural work/allocation reductions, not a claim of a measured overall FPS or RAM percentage improvement. Native core workload, ROM, audience size and hardware still dominate many scenarios.

## Correctness and configuration

- Client preferences are stored separately in `config/retrocraft/client.json`. F8 is rebindable. `/rc gui`, `/retrocraft gui` and Mod Menu also open settings. Personal volume, rendering, previews, HUD, camera focus and texture cadence are available without OP.
- Server commands and every administrative packet continue to check operator level 2. Integrated multiplayer uses the server backend as well.
- Shared console menus now read the host cartridge directly. Independent menu snapshots could previously withdraw the same cartridge twice.
- Periodic server snapshots and battery saves now run. The configured autosave interval was previously unused. Non-snapshot cores can save battery RAM without requesting unsupported savestates.
- Interrupted checkpoint rotation recovers the previous validated snapshot if the active file is missing. Existing active snapshots take priority over backups.
- Live checkpoint interval changes reach active client emulators. Switching to server emulation requests an orderly ownership return.
- Soft configuration resends refresh client readiness; configured video/preview ranges are accepted consistently.
- Controller rosters include current pad masks. Stale presses are cleared for hybrid hosts as well as server workers.
- Frozen textures are released on disconnect. Repeated identical thumbnails refresh their lifetime without decoding and uploading them again.
- Compressed frames reject trailing data. PNG geometry is checked before native decoding. BIOS write failures no longer report readiness.
- Library roots cannot be renamed or deleted through relative aliases. Missing paths are checked against existing symlink ancestors. BIOS extraction preserves existing files and enforces extraction budgets.
- Server configuration validates null/malformed mappings before accessing them and saves GUI edits before replacing the live configuration.
- Settings requests and uploads have timeouts. Hidden tabs park their controls, denial text wraps, footer buttons adapt to narrow windows, and four-player HUD clusters scale without overlapping.
- Java sources use a reproducible Spotless format, checked by `build`, with qualified call sites converted to imports.

## Validation

Validated on Linux with Java 21, Minecraft 1.21.1 and Fabric Loader 0.16.14:

- `spotlessApply build` with real NES and N64 cores: **62 JUnit tests passed, 3 optional tests skipped**.
- Fabric GameTest: **1 required test passed**, exercising two simultaneous menu adapters against the same cartridge and checking that it cannot be withdrawn twice.
- NES native tests cover audio/video, independent processes, exact save/resume frames, prepare/commit, periodic/final handoff, battery-only saves and live checkpoint interval changes.
- N64 native tests cover save/restore across worker restarts, saving before the first requested frame and shutdown without creating an unintended snapshot.
- Additional regressions cover RGB formats and pitch padding, partial-edge tiles, dense/empty deltas, corrupt compression, transfer ordering/checksums, client/server configuration, interrupted checkpoint recovery, library traversal/collisions and PNG bounds.
- `runClient` in an isolated run directory: opened Client settings without cheats, checked the Server permission denial, played the original NES homebrew in a handheld, observed client ownership and orderly return, saved personal volume to 25%, and verified that the server configuration file was unchanged.
- Inspected the release jar: it contains the client config, shared inventory and worker runtime, and excludes the GameTest mod/classes.

The skipped tests were the opt-in official core download test and two MAME native tests. This review did not exercise a live session with multiple remote Minecraft clients or establish WAN bandwidth/latency benchmarks. The worker supports software-rendered video; hardware-context support is outside this validation.

## Reproduction

```sh
./gradlew spotlessApply
RETROCRAFT_TEST_CORE=/path/to/fceumm_libretro.so \
RETROCRAFT_TEST_N64_CORE=/path/to/parallel_n64_libretro.so \
RETROCRAFT_TEST_N64_ROM=/path/to/game.z64 \
./gradlew build
```

`./gradlew runGameTest` runs only the Minecraft inventory regression. `./gradlew runClient -PretrocraftRunDir=run-review` uses an isolated development run directory. Runtime files, cores and ROMs are not included in the release jar.
