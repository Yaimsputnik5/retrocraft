package com.sammwy.retrocraft.cores.worker;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Joins two Gambatte network clients on the Minecraft server. The listener exists before either
 * core connects, and closing the cable unblocks a native core waiting for its partner's byte.
 */
public final class GameLinkBridge implements AutoCloseable {
    private final ServerSocket listener;
    private final Socket[] peers = new Socket[2];
    private final AtomicBoolean closed = new AtomicBoolean();

    private GameLinkBridge(ServerSocket listener) throws IOException {
        this.listener = listener;
        listener.setSoTimeout(60_000);
        Thread.ofVirtual()
                .name("retrocraft-game-link")
                .start(
                        () -> {
                            try {
                                for (int i = 0; i < peers.length; i++) {
                                    Socket peer = listener.accept();
                                    peer.setTcpNoDelay(true);
                                    synchronized (peers) {
                                        if (closed.get()) {
                                            peer.close();
                                            return;
                                        }
                                        peers[i] = peer;
                                    }
                                }
                                listener.close();
                                Thread.ofVirtual()
                                        .name("retrocraft-game-link-return")
                                        .start(() -> copy(peers[1], peers[0]));
                                copy(peers[0], peers[1]);
                            } catch (IOException ex) {
                                close();
                            }
                        });
    }

    public static GameLinkBridge open(int first, int last) throws IOException {
        IOException failure = null;
        for (int port = first; port <= last; port++) {
            try {
                return new GameLinkBridge(
                        new ServerSocket(port, 2, InetAddress.getByName("127.0.0.1")));
            } catch (IOException ex) {
                failure = ex;
            }
        }
        throw new IOException("No free Game Link port", failure);
    }

    public int port() {
        return listener.getLocalPort();
    }

    public boolean closed() {
        return closed.get();
    }

    private void copy(Socket source, Socket target) {
        try {
            source.getInputStream().transferTo(target.getOutputStream());
        } catch (IOException ignored) {
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            listener.close();
        } catch (IOException ignored) {
        }
        synchronized (peers) {
            for (Socket peer : peers)
                if (peer != null)
                    try {
                        peer.close();
                    } catch (IOException ignored) {
                    }
        }
    }
}
