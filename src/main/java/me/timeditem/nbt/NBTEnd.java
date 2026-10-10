package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;

public class NBTEnd implements NBTTag {
    @Override
    public byte getId() {
        return 0;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        // No payload
    }

    @Override
    public NBTTag copy() {
        return new NBTEnd();
    }
}
