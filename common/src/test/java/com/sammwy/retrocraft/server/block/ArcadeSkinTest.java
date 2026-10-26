package com.sammwy.retrocraft.server.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArcadeSkinTest {
    @Test
    void revisionsAndReadableNamesShareTheirFamilyArtwork() {
        for (String name :
                new String[] {"pacman", "pacmanf.zip", "puckman", "mspacmnf", "Ms. Pac-Man (US)"})
            assertEquals(ArcadeSkin.PACMAN, ArcadeSkin.of(name), name);
        for (String name : new String[] {"tetris2", "atetris", "atetrisb.zip", "Tetris (Japan)"})
            assertEquals(ArcadeSkin.TETRIS, ArcadeSkin.of(name), name);
        for (String name :
                new String[] {
                    "invaders", "invadpt2", "sisv2", "Space Invaders (CV)", "MAME/INVADERS.ZIP"
                }) assertEquals(ArcadeSkin.SPACE_INVADERS, ArcadeSkin.of(name), name);
        assertEquals(ArcadeSkin.DONKEY_KONG, ArcadeSkin.of("dkongjr"));
        assertEquals(ArcadeSkin.GALAGA, ArcadeSkin.of("galagamw"));
        assertEquals(ArcadeSkin.STREET_FIGHTER, ArcadeSkin.of("sf2ceua"));
        assertEquals(
                ArcadeSkin.STREET_FIGHTER,
                ArcadeSkin.of("Super Street Fighter II: The New Challengers (World 930911)"));
        assertEquals(ArcadeSkin.METAL_SLUG, ArcadeSkin.of("mslug3h"));
        assertEquals(ArcadeSkin.METAL_SLUG, ArcadeSkin.of("Metal Slug - Super Vehicle-001"));
        assertEquals(ArcadeSkin.MORTAL_KOMBAT, ArcadeSkin.of("mkla1"));
        assertEquals(ArcadeSkin.MORTAL_KOMBAT, ArcadeSkin.of("Ultimate Mortal Kombat 3 (rev 1.2)"));
        assertEquals(ArcadeSkin.BUBBLE_BOBBLE, ArcadeSkin.of("bublbobr"));
    }

    @Test
    void unknownAndUnrelatedSetsDoNotBorrowAnotherGamesArtwork() {
        for (String name :
                new String[] {
                    "", "unknown", "sfish2", "pacland", "spacewar", "mkartag2", "not-pacman"
                }) assertEquals(ArcadeSkin.GENERIC, ArcadeSkin.of(name), name);
    }
}
