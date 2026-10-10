package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;

public class NBTString implements NBTTag {
    private String value;

    public NBTString() {
        this.value = "";
    }

    public NBTString(String value) {
        this.value = value != null ? value : "";
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value != null ? value : "";
    }

    @Override
    public byte getId() {
        return 8;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeUTF(value);
    }

    @Override
    public NBTTag copy() {
        return new NBTString(value);
    }

    @Override
    public String toString() {
        return "\"" + value + "\"";
    }
}
