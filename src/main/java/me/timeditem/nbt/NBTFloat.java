package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;

public class NBTFloat implements NBTTag {
    private float value;

    public NBTFloat() {
    }

    public NBTFloat(float value) {
        this.value = value;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
    }

    @Override
    public byte getId() {
        return 5;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeFloat(value);
    }

    @Override
    public NBTTag copy() {
        return new NBTFloat(value);
    }

    @Override
    public String toString() {
        return value + "f";
    }
}
