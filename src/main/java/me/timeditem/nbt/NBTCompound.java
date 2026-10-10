package me.timeditem.nbt;

import java.io.DataOutput;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class NBTCompound implements NBTTag {
    private final Map<String, NBTTag> tags = new LinkedHashMap<>();

    public int size() {
        return tags.size();
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    public boolean has(String key) {
        return tags.containsKey(key);
    }

    public NBTTag get(String key) {
        return tags.get(key);
    }

    public void put(String key, NBTTag tag) {
        if (tag != null) {
            tags.put(key, tag);
        }
    }

    public NBTTag remove(String key) {
        return tags.remove(key);
    }

    public Set<String> keySet() {
        return tags.keySet();
    }

    public Set<Map.Entry<String, NBTTag>> entrySet() {
        return tags.entrySet();
    }

    public Byte getByte(String key) {
        NBTTag tag = tags.get(key);
        if (tag instanceof NBTByte b) {
            return b.getValue();
        }
        return null;
    }

    public Short getShort(String key) {
        NBTTag tag = tags.get(key);
        if (tag instanceof NBTShort s) {
            return s.getValue();
        }
        return null;
    }

    public Integer getInt(String key) {
        NBTTag tag = tags.get(key);
        if (tag instanceof NBTInt i) {
            return i.getValue();
        }
        return null;
    }

    public Long getLong(String key) {
        NBTTag tag = tags.get(key);
        if (tag instanceof NBTLong l) {
            return l.getValue();
        }
        if (tag instanceof NBTInt i) {
            return (long) i.getValue();
        }
        if (tag instanceof NBTString s) {
            try {
                return Long.parseLong(s.getValue());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public Float getFloat(String key) {
        NBTTag tag = tags.get(key);
        if (tag instanceof NBTFloat f) {
            return f.getValue();
        }
        return null;
    }

    public Double getDouble(String key) {
        NBTTag tag = tags.get(key);
        if (tag instanceof NBTDouble d) {
            return d.getValue();
        }
        return null;
    }

    public String getString(String key) {
        NBTTag tag = tags.get(key);
        if (tag instanceof NBTString s) {
            return s.getValue();
        }
        return null;
    }

    public NBTCompound getCompound(String key) {
        NBTTag tag = tags.get(key);
        if (tag instanceof NBTCompound c) {
            return c;
        }
        return null;
    }

    public NBTList getList(String key) {
        NBTTag tag = tags.get(key);
        if (tag instanceof NBTList l) {
            return l;
        }
        return null;
    }

    public void putByte(String key, byte value) {
        tags.put(key, new NBTByte(value));
    }

    public void putShort(String key, short value) {
        tags.put(key, new NBTShort(value));
    }

    public void putInt(String key, int value) {
        tags.put(key, new NBTInt(value));
    }

    public void putLong(String key, long value) {
        tags.put(key, new NBTLong(value));
    }

    public void putFloat(String key, float value) {
        tags.put(key, new NBTFloat(value));
    }

    public void putDouble(String key, double value) {
        tags.put(key, new NBTDouble(value));
    }

    public void putString(String key, String value) {
        tags.put(key, new NBTString(value));
    }

    @Override
    public byte getId() {
        return 10;
    }

    @Override
    public void writePayload(DataOutput out) throws IOException {
        for (Map.Entry<String, NBTTag> entry : tags.entrySet()) {
            out.writeByte(entry.getValue().getId());
            out.writeUTF(entry.getKey());
            entry.getValue().writePayload(out);
        }
        out.writeByte(0); // TAG_End
    }

    @Override
    public NBTTag copy() {
        NBTCompound copy = new NBTCompound();
        for (Map.Entry<String, NBTTag> entry : tags.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }
}
