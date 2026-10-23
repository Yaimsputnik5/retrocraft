package com.sammwy.retrocraft.server.block;

import java.util.Locale;

import com.sammwy.retrocraft.roms.discovery.ArcadeSets;

/** Resolves MAME short set IDs and readable names to shared cabinet artwork. */
public enum ArcadeSkin {
    GENERIC("mame_console"),
    PACMAN("mame_pacman_console"),
    TETRIS("mame_tetris_console"),
    SPACE_INVADERS("mame_space_invaders_console"),
    DONKEY_KONG("mame_donkey_kong_console"),
    GALAGA("mame_galaga_console"),
    STREET_FIGHTER("mame_street_fighter_console"),
    METAL_SLUG("mame_metal_slug_console"),
    MORTAL_KOMBAT("mame_mortal_kombat_console"),
    BUBBLE_BOBBLE("mame_bubble_bobble_console");

    private final String model;

    ArcadeSkin(String model) {
        this.model = model;
    }

    public String model() {
        return model;
    }

    public static ArcadeSkin of(String name) {
        if (name == null || name.isBlank()) return GENERIC;
        var machine = ArcadeSets.machine(name);
        if (machine != null) {
            ArcadeSkin skin = match(machine.id());
            if (skin != GENERIC) return skin;
            if (machine.parent() != null) {
                skin = match(machine.parent());
                if (skin != GENERIC) return skin;
            }
        }
        String id = name.replace('\\', '/');
        id = id.substring(id.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (id.endsWith(".zip")) id = id.substring(0, id.length() - 4);
        return match(id.replaceAll("[^a-z0-9]", ""));
    }

    private static ArcadeSkin match(String id) {
        if (id.startsWith("pacman")
                || id.startsWith("puckman")
                || id.startsWith("mspac")
                || id.startsWith("mspuck")
                || id.startsWith("pacplus")
                || id.startsWith("pacnpal")) return PACMAN;
        if (id.startsWith("tetris") || id.startsWith("atetris") || id.startsWith("tetrisp"))
            return TETRIS;
        if (id.startsWith("spaceinv")
                || id.startsWith("invader")
                || id.startsWith("invadpt")
                || id.startsWith("invad2ct")
                || id.startsWith("sinvem")
                || id.startsWith("sisv")
                || id.startsWith("sitv")
                || id.startsWith("spcewar")
                || id.startsWith("spceking")
                || id.startsWith("spcewars")) return SPACE_INVADERS;
        if (id.startsWith("dkong") || id.startsWith("donkeykong")) return DONKEY_KONG;
        if (id.startsWith("galaga") || id.startsWith("galagam") || id.startsWith("galagao"))
            return GALAGA;
        if (id.startsWith("sf2")
                || id.startsWith("ssf2")
                || id.startsWith("hsf2")
                || id.startsWith("sfa")
                || id.startsWith("sfz")
                || id.startsWith("sfiii")
                || id.startsWith("streetfighter")
                || id.equals("sf")
                || id.matches("sf[1jpu].*")) return STREET_FIGHTER;
        if (id.startsWith("mslug") || id.startsWith("metalslug")) return METAL_SLUG;
        if (id.startsWith("mortalkombat")
                || id.equals("mk")
                || id.matches("mk[1234].*")
                || id.startsWith("mkr")
                || id.startsWith("mkla")
                || id.startsWith("umk3")) return MORTAL_KOMBAT;
        if (id.startsWith("bublbobl")
                || id.startsWith("bublbobr")
                || id.startsWith("bubblebobble")
                || id.startsWith("boblbobl")) return BUBBLE_BOBBLE;
        return GENERIC;
    }
}
