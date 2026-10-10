package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public class NBTByteArray implements NBTTag {
    private byte[] value;

    public NBTByteArray() {
        this.value = new byte[0];
    }

    public NBTByteArray(byte[] value) {
        this.value = value != null ? value : new byte[0];
    }

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }

    @Override
    public byte getId() {
        return 7;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeInt(value.length);
        out.write(value);
    }

    @Override
    public NBTTag copy() {
        return new NBTByteArray(Arrays.copyOf(value, value.length));
    }
}
