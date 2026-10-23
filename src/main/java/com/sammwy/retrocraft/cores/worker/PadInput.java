package com.sammwy.retrocraft.cores.worker;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Virtual controller state only. Keyboard and mouse assignments never leave the client. */
public record PadInput(int buttons, short leftX, short leftY, short rightX, short rightY) {
    public static final PadInput IDLE = new PadInput(0, (short) 0, (short) 0, (short) 0, (short) 0);

    public PadInput {
        buttons &= 0xffff;
    }

    public static PadInput digital(int buttons) {
        return new PadInput(buttons, (short) 0, (short) 0, (short) 0, (short) 0);
    }

    public short state(int device, int index, int id) {
        if ((device & 255) == 1 && index == 0)
            return (short) (id == 256 ? buttons : id >= 0 && id < 16 ? (buttons >>> id) & 1 : 0);
        if ((device & 255) == 5 && index == 2 && id >= 0 && id < 16)
            return (short) (((buttons >>> id) & 1) * 32767);
        if ((device & 255) == 5 && (id == 0 || id == 1)) {
            if (index == 0) return id == 0 ? leftX : leftY;
            if (index == 1) return id == 0 ? rightX : rightY;
        }
        return 0;
    }

    public void write(DataOutput out) throws IOException {
        out.writeShort(buttons);
        out.writeShort(leftX);
        out.writeShort(leftY);
        out.writeShort(rightX);
        out.writeShort(rightY);
    }

    public static PadInput read(DataInput in) throws IOException {
        return new PadInput(
                in.readUnsignedShort(),
                in.readShort(),
                in.readShort(),
                in.readShort(),
                in.readShort());
    }
}
