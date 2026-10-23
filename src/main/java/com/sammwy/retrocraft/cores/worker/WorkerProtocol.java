package com.sammwy.retrocraft.cores.worker;

public final class WorkerProtocol {
    public static final int MAGIC = 0x52434e31;
    public static final int RUN = 1, SAVE = 2, QUIT = 3, BATTERY = 4, CAPTURE = 5;
    public static final int PORTS = 4;
    public static final int MAX_AUDIO = 262_144;
    public static final int MAX_STATE = 32 * 1024 * 1024;

    private WorkerProtocol() {}
}
