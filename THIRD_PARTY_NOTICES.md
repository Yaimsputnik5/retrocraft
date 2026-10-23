# Third-party components

The bundled worker includes [JNA 5.15.0](https://github.com/java-native-access/jna/tree/5.15.0), available under Apache License 2.0 or LGPL 2.1 or later. This distribution uses the Apache License 2.0 option. JNA's embedded license and notices are preserved in the worker JAR. See the `licenses/` directory for the Apache 2.0 license text.

The Fabric Loader, Fabric API, Minecraft and libretro cores are separate runtime dependencies. No libretro core or third-party ROM is distributed by this project. FCEUmm was used to validate the native integration; its source is available at https://github.com/libretro/libretro-fceumm under its own license.

`retrocraft/mame2003-plus-machines.txt` contains machine identifiers extracted from the [official MAME 2003-Plus driver registry](https://github.com/libretro/mame2003-plus-libretro/blob/master/src/driver.c) on 2026-10-22. It contains names only, with no driver source or ROM data. The corresponding emulator is downloaded separately from libretro's official buildbot.

Selected arcade cabinet sprites and logotypes, kept in `tools/art/arcade/` and incorporated into the generated arcade texture atlases, are artwork from Metal Slug (Nazca/SNK), Street Fighter II (Capcom), Mortal Kombat (Midway), Donkey Kong (Nintendo), Galaga (Namco) and Bubble Bobble (Taito). This game artwork remains the property of its original creators and rights holders and is not covered by this repository's MIT license. Sources, archive credits and extraction details are recorded in [the arcade art notes](tools/art/arcade/README.md) and `tools/art/arcade/sources.json`.
