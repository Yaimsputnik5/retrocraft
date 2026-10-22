package com.sammwy.retrocraft;

import com.sammwy.retrocraft.roms.*;
import com.sammwy.retrocraft.cores.transfer.BlobTransfer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RomCacheTest {
    @TempDir Path root;
    @Test void aReceivedRomIsAvailableForLaterOwnershipTransfersWithoutWritingItAgain() throws Exception {
        byte[] bytes = HomebrewRom.bytes(); String hash = BlobTransfer.hash(bytes);
        assertNull(RomCache.find(root, hash, "game.nes"));
        Path stored = RomCache.acceptNamed(root, hash, "game.nes", bytes);
        var timestamp = Files.getLastModifiedTime(stored);
        assertEquals(stored, RomCache.find(root, hash, "game.nes"));
        assertEquals(timestamp, Files.getLastModifiedTime(stored));
    }
    @Test void anotherServerOrRenamedRomReusesTheSameHashAndKeepsTheRequiredBasename() throws Exception {
        byte[] bytes = HomebrewRom.bytes(); String hash = BlobTransfer.hash(bytes);
        RomCache.acceptNamed(root, hash, "old_title.nes", bytes);
        Path renamed = RomCache.find(root, hash, "new_title.nes");
        assertNotNull(renamed); assertEquals("new_title.nes", renamed.getFileName().toString());
        assertArrayEquals(bytes, Files.readAllBytes(renamed));
    }
    @Test void corruptCachedContentIsAMissAndCanBeReplacedByAVerifiedDownload() throws Exception {
        byte[] bytes = HomebrewRom.bytes(); String hash = BlobTransfer.hash(bytes);
        Path stored = RomCache.acceptNamed(root, hash, "game.nes", bytes); Files.write(stored, new byte[]{1,2,3});
        assertNull(RomCache.find(root, hash, "game.nes"));
        assertNull(RomCache.find(root, hash, "other.nes"));
        RomCache.acceptNamed(root, hash, "game.nes", bytes);
        assertEquals(stored, RomCache.find(root, hash, "game.nes"));
    }
    @Test void remoteNamesCannotEscapeTheHashCacheAndMismatchedBytesAreRejected() {
        byte[] bytes = HomebrewRom.bytes(); String hash = BlobTransfer.hash(bytes);
        assertThrows(IOException.class, () -> RomCache.find(root, hash, "../escape.nes"));
        assertThrows(IOException.class, () -> RomCache.find(root, "../../bad", "game.nes"));
        assertThrows(IOException.class, () -> RomCache.acceptNamed(root, hash, "game.nes", new byte[]{1,2,3}));
    }
}
