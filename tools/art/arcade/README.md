# Arcade print inputs

These are selected, transparent sprite frames and logotypes used by
`tools/arcade_art.py`. They preserve the game characters' original pixel shapes
and palettes. The layouts, cabinet colors, borders and control legends are
composed by the generator. Galaga's logo receives a cabinet-specific ink color.

The PNG inputs are local and committed so asset regeneration works offline.
`sources.json` records each source URL, the downloaded source's SHA-256, and
the extraction crop or frame where applicable. Only the selected artwork is
kept here; full sprite sheets and working previews belong in `tmp/`.

| Family | Selected artwork | Archive sources |
| --- | --- | --- |
| Metal Slug | Marco Rossi, SV-001, title logo | [nathancy/Metal-Slug](https://github.com/nathancy/Metal-Slug), [hadiyatahir/MetalSlugRemake](https://github.com/hadiyatahir/MetalSlugRemake). The SV-001 sheet is credited to The Huntress on [The Spriters Resource](https://www.spriters-resource.com/neo_geo_ngcd/ms/asset/32521/). |
| Street Fighter II | Ryu's arcade stance and logo | [The Fighters Generation](https://www.fightersgeneration.com/characters3/ryu-a.html), [game page](https://www.fightersgeneration.com/games/sf2.html) |
| Mortal Kombat | Scorpion's MKII stance; original Mortal Kombat title lettering | [Mortal Kombat Warehouse: Scorpion](https://www.mortalkombatwarehouse.com/mk2/scorpion/), [original title](https://www.mortalkombatwarehouse.com/mk1/) |
| Donkey Kong | Donkey Kong, Jumpman, barrel | [maxbgrove/donkeykong-cis110](https://github.com/maxbgrove/donkeykong-cis110) |
| Galaga | Boss Galaga, butterfly, bee, fighter and logo | [ihalseide/Galaga](https://github.com/ihalseide/Galaga) |
| Bubble Bobble | Bub, Bob and title logo | [erinrrr/Bubble-Bobble-Sprite](https://github.com/erinrrr/Bubble-Bobble-Sprite). The source sheet credits Random Talking Bush. |

The game artwork remains the property of its original creators and rights
holders (Nazca/SNK, Capcom, Midway, Nintendo, Namco and Taito). It is not
covered by Retrocraft's MIT license. Archive or repository credits identify
where a copy was obtained; they do not imply that the archive created or owns
the underlying game artwork. See `THIRD_PARTY_NOTICES.md`.
