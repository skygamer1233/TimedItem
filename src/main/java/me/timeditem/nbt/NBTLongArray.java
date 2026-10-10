package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public class NBTLongArray implements NBTTag {
    private long[] value;

    public NBTLongArray() {
        this.value = new long[0];
    }

    public NBTLongArray(long[] value) {
        this.value = value != null ? value : new long[0];
    }

    public long[] getValue() {
        return value;
    }

    public void setValue(long[] value) {
        this.value = value;
    }

    @Override
    public byte getId() {
        return 12;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(value.length);
        for (long l : value) {
            out.writeLong(l);
        }
    }

    @Override
    public NBTTag copy() {
        return new NBTLongArray(Arrays.copyOf(value, value.length));
    }
}
