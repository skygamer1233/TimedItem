package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;

public class NBTDouble implements NBTTag {
    private double value;

    public NBTDouble() {
    }

    public NBTDouble(double value) {
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    @Override
    public byte getId() {
        return 6;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeDouble(value);
    }

    @Override
    public NBTTag copy() {
        return new NBTDouble(value);
    }

    @Override
    public String toString() {
        return value + "d";
    }
}
