package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public class NBTIntArray implements NBTTag {
    private int[] value;

    public NBTIntArray() {
        this.value = new int[0];
    }

    public NBTIntArray(int[] value) {
        this.value = value != null ? value : new int[0];
    }

    public int[] getValue() {
        return value;
    }

    public void setValue(int[] value) {
        this.value = value;
    }

    @Override
    public byte getId() {
        return 11;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(value.length);
        for (int i : value) {
            out.writeInt(i);
        }
    }

    @Override
    public NBTTag copy() {
        return new NBTIntArray(Arrays.copyOf(value, value.length));
    }
}
