package com.sammwy.retrocraft.server.console;

/** Physical form only. Console identities and cartridge compatibility are registered data. */
public enum ConsoleType {
    HANDHELD,
    DOCKED,
    ARCADE;

    public boolean isPlaced() {
        return this != HANDHELD;
    }
}
