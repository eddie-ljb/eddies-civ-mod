package de.etiennebader.civ.server.construction;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimaler NBT-Leser für Bauplandateien. Compounds werden zu {@code Map<String,Object>}, Listen zu {@code List},
 * Zahlen zu {@code Number}, Byte-, Int- und Long-Felder zu {@code byte[]}, {@code int[]}, {@code long[]}.
 */
final class Nbt {

    /** Obergrenze für entpackte Daten, gegen Zip-Bomben. */
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final int MAX_DEPTH = 64;

    private Nbt() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readGzip(InputStream in) throws IOException {
        InputStream limited = new LimitedInputStream(new GZIPInputStream(in), MAX_BYTES);
        try (DataInputStream data = new DataInputStream(limited)) {
            byte type = data.readByte();
            if (type != 10) {
                throw new IOException("Keine NBT-Datei (Wurzel ist kein Compound)");
            }
            data.readUTF();
            return (Map<String, Object>) readPayload(data, type, 0);
        }
    }

    private static Object readPayload(DataInputStream in, byte type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT zu tief verschachtelt");
        }
        switch (type) {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7: {
                byte[] bytes = new byte[checkLength(in.readInt(), 1)];
                in.readFully(bytes);
                return bytes;
            }
            case 8:
                return in.readUTF();
            case 9: {
                byte elementType = in.readByte();
                int length = in.readInt();
                if (length < 0 || length > (1 << 24)) {
                    throw new IOException("Ungültige Listenlänge");
                }
                List<Object> list = new ArrayList<>();
                for (int i = 0; i < length; i++) {
                    list.add(readPayload(in, elementType, depth + 1));
                }
                return list;
            }
            case 10: {
                Map<String, Object> map = new HashMap<>();
                for (byte next = in.readByte(); next != 0; next = in.readByte()) {
                    String name = in.readUTF();
                    map.put(name, readPayload(in, next, depth + 1));
                }
                return map;
            }
            case 11: {
                int[] ints = new int[checkLength(in.readInt(), 4)];
                for (int i = 0; i < ints.length; i++) {
                    ints[i] = in.readInt();
                }
                return ints;
            }
            case 12: {
                long[] longs = new long[checkLength(in.readInt(), 8)];
                for (int i = 0; i < longs.length; i++) {
                    longs[i] = in.readLong();
                }
                return longs;
            }
            default:
                throw new IOException("Unbekannter NBT-Typ " + type);
        }
    }

    private static int checkLength(int length, int bytesEach) throws IOException {
        if (length < 0 || (long) length * bytesEach > MAX_BYTES) {
            throw new IOException("Ungültige Arraylänge");
        }
        return length;
    }

    /** Bricht ab, wenn mehr als das Limit entpackt wird. */
    private static final class LimitedInputStream extends InputStream {
        private final InputStream in;
        private long left;

        LimitedInputStream(InputStream in, long limit) {
            this.in = in;
            this.left = limit;
        }

        @Override
        public int read() throws IOException {
            if (left-- <= 0) {
                throw new IOException("Datei entpackt zu groß");
            }
            return in.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (left <= 0) {
                throw new IOException("Datei entpackt zu groß");
            }
            int read = in.read(buffer, offset, (int) Math.min(length, left));
            if (read > 0) {
                left -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
