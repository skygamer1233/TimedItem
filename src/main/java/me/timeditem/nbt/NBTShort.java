package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;

public class NBTShort implements NBTTag {
    private short value;

    public NBTShort() {
    }

    public NBTShort(short value) {
        this.value = value;
    }

    public short getValue() {
        return value;
    }

    public void setValue(short value) {
        this.value = value;
    }

    @Override
    public byte getId() {
        return 2;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeShort(value);
    }

    @Override
    public NBTTag copy() {
        return new NBTShort(value);
    }

    @Override
    public String toString() {
        return value + "s";
    }
}
