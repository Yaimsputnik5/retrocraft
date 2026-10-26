package com.sammwy.retrocraft;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.*;
import java.util.HexFormat;
import java.util.concurrent.*;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sammwy.retrocraft.cores.WorkerProcess;
import com.sammwy.retrocraft.cores.worker.GameLinkBridge;
import com.sammwy.retrocraft.cores.worker.HardwareProfile;
import com.sammwy.retrocraft.server.console.component.Components;

class GameLinkIntegrationTest {
    @TempDir Path root;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void twoRealGambatteWorkersExchangeSerialBytesWithoutRestartingTheGames(
            boolean peerStartsLinked) throws Exception {
        String configured = System.getenv("RETROCRAFT_TEST_GB_CORE");
        assumeTrue(
                configured != null,
                "Set RETROCRAFT_TEST_GB_CORE to a network-enabled Gambatte core");
        Path core = Path.of(configured), jar = Path.of(System.getProperty("retrocraft.workerJar"));
        Path masterRom = Files.write(root.resolve("master.gb"), rom(0x12, 0x81));
        Path slaveRom = Files.write(root.resolve("slave.gb"), rom(0x34, 0x80));
        try (var bridge = GameLinkBridge.open(Components.LINK_PORTS[0], Components.LINK_PORTS[1]);
                var master =
                        new WorkerProcess(
                                jar,
                                core,
                                masterRom,
                                root.resolve("master/snapshot.bin"),
                                root.resolve("master.log"));
                var slave =
                        new WorkerProcess(
                                jar,
                                core,
                                slaveRom,
                                root.resolve("slave/snapshot.bin"),
                                root.resolve("slave"),
                                root.resolve("slave.log"),
                                null,
                                peerStartsLinked
                                        ? Components.gambatteLink(false, bridge.port())
                                        : HardwareProfile.NONE);
                var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10; i++) {
                master.advance(0);
                slave.advance(0);
            }
            assertFalse(master.options(HardwareProfile.options("nonexistent_option", "enabled")));
            assertTrue(master.options(Components.gambatteLink(false, bridge.port())));
            master.advance(0);
            assertTrue(slave.options(Components.gambatteLink(false, bridge.port())));
            slave.advance(0);
            // Arm the external-clock endpoint before the master starts its transfer.
            slave.advance(1 << 3);
            var a =
                    threads.submit(
                            () -> {
                                for (int i = 0; i < 120; i++) master.advance(1 << 3);
                                return true;
                            });
            var b =
                    threads.submit(
                            () -> {
                                for (int i = 0; i < 120; i++) slave.advance(1 << 3);
                                return true;
                            });
            try {
                a.get(15, TimeUnit.SECONDS);
                b.get(15, TimeUnit.SECONDS);
            } finally {
                bridge.close();
            }
            master.saveBattery();
            slave.saveBattery();
            byte[] masterRam = Files.readAllBytes(root.resolve("master/battery.sav"));
            byte[] slaveRam = Files.readAllBytes(root.resolve("slave/battery.sav"));
            assertEquals(0x42, masterRam[1]);
            assertEquals(0x42, slaveRam[1]);
            assertEquals(0x34, Byte.toUnsignedInt(masterRam[0]));
            assertEquals(0x12, Byte.toUnsignedInt(slaveRam[0]));
            var disconnected = HardwareProfile.options("gambatte_gb_link_mode", "Not Connected");
            assertTrue(master.options(disconnected));
            assertTrue(slave.options(disconnected));
            master.advance(0);
            slave.advance(0);
        }
    }

    /** Tiny original MBC1 homebrew: wait for Start, exchange one serial byte, record it in SRAM. */
    private static byte[] rom(int value, int clock) {
        byte[] rom = new byte[32768];
        rom[0x100] = (byte) 0xc3;
        rom[0x101] = 0x50;
        rom[0x102] = 1;
        byte[] logo =
                HexFormat.of()
                        .parseHex(
                                "ceed6666cc0d000b03730083000c000d0008111f8889000edccc6ee6ddddd999bbbb67636e0eecccdddc999fbbb9333e");
        System.arraycopy(logo, 0, rom, 0x104, logo.length);
        rom[0x147] = 3;
        rom[0x149] = 2;
        byte[] program =
                HexFormat.of()
                        .parseHex(
                                String.format(
                                        "f3afe0403e0aea0000afea00a0ea01a03e10e000f000cb5f20fa3e08eaffffafe00f3e%02xe0013e%02xe00276f001ea00a03e42ea01a018fe",
                                        value, clock));
        System.arraycopy(program, 0, rom, 0x150, program.length);
        int checksum = 0;
        for (int i = 0x134; i <= 0x14c; i++) checksum = checksum - Byte.toUnsignedInt(rom[i]) - 1;
        rom[0x14d] = (byte) checksum;
        return rom;
    }
}
