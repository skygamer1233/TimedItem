package me.timeditem.nbt;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class NBTIO {

    private NBTIO() {
    }

    public static NBTCompound readFile(File file) throws IOException {
        try (InputStream rawIn = new BufferedInputStream(new FileInputStream(file))) {
            InputStream in = rawIn;
            rawIn.mark(2);
            int b1 = rawIn.read();
            int b2 = rawIn.read();
            rawIn.reset();

            if (b1 == 0x1f && b2 == 0x8b) {
                in = new GZIPInputStream(rawIn);
            }

            try (DataInputStream dis = new DataInputStream(in)) {
                byte rootType = dis.readByte();
                if (rootType != 10) {
                    throw new IOException("Root tag is not TAG_Compound (id=" + rootType + ")");
                }
                dis.readUTF(); // Root tag name, typically empty
                NBTCompound root = new NBTCompound();
                readCompoundPayload(root, dis);
                return root;
            }
        }
    }

    public static void writeFile(NBTCompound root, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File tempFile = new File(parent, file.getName() + ".tmp");
        try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(tempFile));
             GZIPOutputStream gzos = new GZIPOutputStream(fos);
             DataOutputStream dos = new DataOutputStream(gzos)) {
            dos.writeByte(10); // TAG_Compound
            dos.writeUTF("");  // Root tag name
            root.writePayload(dos);
            dos.flush();
        }

        try {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static NBTTag readTagPayload(byte type, DataInput in) throws IOException {
        return switch (type) {
            case 0 -> new NBTEnd();
            case 1 -> new NBTByte(in.readByte());
            case 2 -> new NBTShort(in.readShort());
            case 3 -> new NBTInt(in.readInt());
            case 4 -> new NBTLong(in.readLong());
            case 5 -> new NBTFloat(in.readFloat());
            case 6 -> new NBTDouble(in.readDouble());
            case 7 -> {
                int len = in.readInt();
                byte[] bytes = new byte[len];
                in.readFully(bytes);
                yield new NBTByteArray(bytes);
            }
            case 8 -> new NBTString(in.readUTF());
            case 9 -> {
                byte elemType = in.readByte();
                int size = in.readInt();
                NBTList list = new NBTList(elemType);
                for (int i = 0; i < size; i++) {
                    list.add(readTagPayload(elemType, in));
                }
                yield list;
            }
            case 10 -> {
                NBTCompound compound = new NBTCompound();
                readCompoundPayload(compound, in);
                yield compound;
            }
            case 11 -> {
                int len = in.readInt();
                int[] ints = new int[len];
                for (int i = 0; i < len; i++) {
                    ints[i] = in.readInt();
                }
                yield new NBTIntArray(ints);
            }
            case 12 -> {
                int len = in.readInt();
                long[] longs = new long[len];
                for (int i = 0; i < len; i++) {
                    longs[i] = in.readLong();
                }
                yield new NBTLongArray(longs);
            }
            default -> throw new IOException("Unsupported NBT tag type: " + type);
        };
    }

    private static void readCompoundPayload(NBTCompound compound, DataInput in) throws IOException {
        while (true) {
            byte type = in.readByte();
            if (type == 0) {
                break; // TAG_End
            }
            String name = in.readUTF();
            NBTTag tag = readTagPayload(type, in);
            compound.put(name, tag);
        }
    }
}
