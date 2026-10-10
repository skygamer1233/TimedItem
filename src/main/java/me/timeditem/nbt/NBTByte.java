package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;

public class NBTByte implements NBTTag {
    private byte value;

    public NBTByte() {
    }

    public NBTByte(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public void setValue(byte value) {
        this.value = value;
    }

    @Override
    public byte getId() {
        return 1;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeByte(value);
    }

    @Override
    public NBTTag copy() {
        return new NBTByte(value);
    }

    @Override
    public String toString() {
        return value + "b";
    }
}
