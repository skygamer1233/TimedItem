package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;

public class NBTInt implements NBTTag {
    private int value;

    public NBTInt() {
    }

    public NBTInt(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    @Override
    public byte getId() {
        return 3;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(value);
    }

    @Override
    public NBTTag copy() {
        return new NBTInt(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
