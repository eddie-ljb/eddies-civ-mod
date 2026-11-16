package de.etiennebader.civ.fabric.map;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/** Minimaler PNG-Kodierer und -Dekodierer für RGBA-Bilder ohne Abhängigkeit von AWT. */
public final class Png {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private Png() {
    }

    /** Kodiert {@code argb} (Zeile für Zeile) als PNG mit Alphakanal. */
    public static byte[] encode(int width, int height, int[] argb) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflate = new DeflaterOutputStream(raw, new Deflater(6))) {
                byte[] row = new byte[1 + width * 4];
                for (int y = 0; y < height; y++) {
                    row[0] = 0;
                    for (int x = 0; x < width; x++) {
                        int pixel = argb[y * width + x];
                        int at = 1 + x * 4;
                        row[at] = (byte) (pixel >> 16);
                        row[at + 1] = (byte) (pixel >> 8);
                        row[at + 2] = (byte) pixel;
                        row[at + 3] = (byte) (pixel >>> 24);
                    }
                    deflate.write(row);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(SIGNATURE);
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(header);
            data.writeInt(width);
            data.writeInt(height);
            data.writeByte(8);
            data.writeByte(6);
            data.writeByte(0);
            data.writeByte(0);
            data.writeByte(0);
            chunk(out, "IHDR", header.toByteArray());
            chunk(out, "IDAT", raw.toByteArray());
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Liest ein mit {@link #encode} geschriebenes Bild zurück (nur Filter 0, RGBA 8 Bit).
     *
     * @return die Pixel oder {@code null}, wenn das Format nicht passt
     */
    public static int[] decode(byte[] png, int width, int height) {
        try {
            ByteArrayOutputStream idat = new ByteArrayOutputStream();
            int at = SIGNATURE.length;
            while (at + 8 <= png.length) {
                int length = ((png[at] & 0xFF) << 24) | ((png[at + 1] & 0xFF) << 16) | ((png[at + 2] & 0xFF) << 8)
                        | (png[at + 3] & 0xFF);
                String type = new String(png, at + 4, 4, StandardCharsets.US_ASCII);
                if (type.equals("IHDR")) {
                    int w = ((png[at + 8] & 0xFF) << 24) | ((png[at + 9] & 0xFF) << 16) | ((png[at + 10] & 0xFF) << 8)
                            | (png[at + 11] & 0xFF);
                    if (w != width || png[at + 17] != 6) {
                        return null;
                    }
                } else if (type.equals("IDAT")) {
                    idat.write(png, at + 8, length);
                }
                at += 12 + length;
            }
            byte[] raw = new InflaterInputStream(new java.io.ByteArrayInputStream(idat.toByteArray()), new Inflater())
                    .readAllBytes();
            int stride = 1 + width * 4;
            if (raw.length < stride * height) {
                return null;
            }
            int[] argb = new int[width * height];
            for (int y = 0; y < height; y++) {
                if (raw[y * stride] != 0) {
                    return null;
                }
                for (int x = 0; x < width; x++) {
                    int p = y * stride + 1 + x * 4;
                    argb[y * width + x] = ((raw[p + 3] & 0xFF) << 24) | ((raw[p] & 0xFF) << 16) | ((raw[p + 1] & 0xFF) << 8)
                            | (raw[p + 2] & 0xFF);
                }
            }
            return argb;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream stream = new DataOutputStream(out);
        stream.writeInt(data.length);
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        stream.write(name);
        stream.write(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        stream.writeInt((int) crc.getValue());
    }
}
