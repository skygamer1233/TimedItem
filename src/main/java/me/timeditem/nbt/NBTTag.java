package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;

public interface NBTTag {
    byte getId();
    void writePayload(DataOutput out) throws IOException;
    NBTTag copy();
}
