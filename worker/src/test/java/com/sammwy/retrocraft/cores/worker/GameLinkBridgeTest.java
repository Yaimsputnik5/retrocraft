package com.sammwy.retrocraft.cores.worker;

import static org.junit.jupiter.api.Assertions.*;

import java.net.Socket;

import org.junit.jupiter.api.Test;

class GameLinkBridgeTest {
    @Test
    void relaysSerialBytesBothWaysAndUnplugUnblocksThePeer() throws Exception {
        try (var bridge = GameLinkBridge.open(0, 0);
                var a = new Socket("127.0.0.1", bridge.port());
                var b = new Socket("127.0.0.1", bridge.port())) {
            a.setSoTimeout(3000);
            b.setSoTimeout(3000);
            a.getOutputStream().write(new byte[] {0x12, 0});
            assertArrayEquals(new byte[] {0x12, 0}, b.getInputStream().readNBytes(2));
            b.getOutputStream().write(new byte[] {0x34, (byte) 128});
            assertArrayEquals(new byte[] {0x34, (byte) 128}, a.getInputStream().readNBytes(2));
            bridge.close();
            assertEquals(-1, a.getInputStream().read());
            assertEquals(-1, b.getInputStream().read());
        }
    }
}
