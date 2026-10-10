package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;

public class NBTLong implements NBTTag {
    private long value;

    public NBTLong() {
    }

    public NBTLong(long value) {
        this.value = value;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    @Override
    public byte getId() {
        return 4;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeLong(value);
    }

    @Override
    public NBTTag copy() {
        return new NBTLong(value);
    }

    @Override
    public String toString() {
        return value + "L";
    }
}
