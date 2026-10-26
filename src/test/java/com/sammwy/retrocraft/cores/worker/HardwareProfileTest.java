package com.sammwy.retrocraft.cores.worker;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

class HardwareProfileTest {
    @Test
    void roundTripsOptionsDevicesAndPointer() {
        var profile =
                HardwareProfile.options(
                                "fceumm_zapper_mode", "touchscreen", "mode", "Network Server")
                        .device(1, 258)
                        .pointerFrom(0);
        var decoded = HardwareProfile.decode(profile.encode());
        assertEquals(profile, decoded);
        assertEquals("Network Server", decoded.options().get("mode"));
        assertEquals(258, decoded.devices().get(1));
        assertTrue(decoded.aims());
        assertEquals(HardwareProfile.NONE, HardwareProfile.decode(""));
        assertEquals("", HardwareProfile.NONE.encode());
    }

    @Test
    void laterProfilesWinWhenMerged() {
        var base = HardwareProfile.options("a", "1", "b", "1").device(0, 1);
        var merged = base.merge(HardwareProfile.options("b", "2").device(0, 5));
        assertEquals(Map.of("a", "1", "b", "2"), merged.options());
        assertEquals(5, merged.devices().get(0));
        assertFalse(merged.aims());
        assertSame(base, base.merge(HardwareProfile.NONE));
    }

    @Test
    void rejectsValuesThatCouldEscapeTheEncoding() {
        for (String bad :
                new String[] {
                    "o:key", "o:=x", "x:1", "d:9=1", "d:0=-1", "d:a=b", "p:4", "o:k=a\nb"
                })
            assertThrows(IllegalArgumentException.class, () -> HardwareProfile.decode(bad), bad);
        assertThrows(IllegalArgumentException.class, () -> HardwareProfile.options("k", "a;b"));
        assertThrows(IllegalArgumentException.class, () -> HardwareProfile.options("../k", "a"));
        assertThrows(
                IllegalArgumentException.class,
                () -> HardwareProfile.decode("o:k=" + "x".repeat(HardwareProfile.MAX_ENCODED)));
    }
}
