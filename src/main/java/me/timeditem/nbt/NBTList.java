package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NBTList implements NBTTag, Iterable<NBTTag> {
    private byte typeId;
    private final List<NBTTag> tags = new ArrayList<>();

    public NBTList() {
        this.typeId = 0;
    }

    public NBTList(byte typeId) {
        this.typeId = typeId;
    }

    public byte getElementTypeId() {
        return typeId;
    }

    public void setElementTypeId(byte typeId) {
        this.typeId = typeId;
    }

    public int size() {
        return tags.size();
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    public NBTTag get(int index) {
        return tags.get(index);
    }

    public void add(NBTTag tag) {
        if (tags.isEmpty() && typeId == 0) {
            typeId = tag.getId();
        }
        tags.add(tag);
    }

    public NBTTag remove(int index) {
        return tags.remove(index);
    }

    public boolean remove(NBTTag tag) {
        return tags.remove(tag);
    }

    public void clear() {
        tags.clear();
    }

    @Override
    public Iterator<NBTTag> iterator() {
        return tags.iterator();
    }

    @Override
    public byte getId() {
        return 9;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        out.writeByte(typeId);
        out.writeInt(tags.size());
        for (NBTTag tag : tags) {
            tag.writePayload(out);
        }
    }

    @Override
    public NBTTag copy() {
        NBTList copy = new NBTList(typeId);
        for (NBTTag tag : tags) {
            copy.add(tag.copy());
        }
        return copy;
    }
}
