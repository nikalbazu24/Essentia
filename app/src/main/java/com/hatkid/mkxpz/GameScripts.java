package com.hatkid.mkxpz;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * The game's scripts file (Data/Scripts.rxdata / .rvdata / .rvdata2), taken from
 * the game folder or, for packed games, from inside Game.rgssad / .rgss2a /
 * .rgss3a. Used to send it for review when a game has problems.
 */
public final class GameScripts
{
    private static final String[] NAMES = { "Scripts.rxdata", "Scripts.rvdata", "Scripts.rvdata2" };
    private static final String[] ARCHIVES = { "Game.rgssad", "Game.rgss2a", "Game.rgss3a" };

    private GameScripts() { }

    /** Copies the scripts file to {@code dest}. Returns false if the game has none. */
    public static boolean extract(File gameDir, File dest) throws IOException
    {
        File data = child(gameDir, "Data");
        if (data != null) {
            for (String n : NAMES) {
                File f = child(data, n);
                if (f != null && f.isFile()) {
                    copy(new FileInputStream(f), dest);
                    return true;
                }
            }
        }
        for (String a : ARCHIVES) {
            File f = child(gameDir, a);
            if (f != null && f.isFile() && fromArchive(f, dest)) return true;
        }
        return false;
    }

    private static File child(File dir, String name)
    {
        File exact = new File(dir, name);
        if (exact.exists()) return exact;
        String[] list = dir.list();
        if (list == null) return null;
        for (String n : list) if (n.equalsIgnoreCase(name)) return new File(dir, n);
        return null;
    }

    private static void copy(InputStream in, File dest) throws IOException
    {
        try (InputStream i = in; OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = i.read(buf)) > 0) out.write(buf, 0, r);
        }
    }

    private static boolean isScripts(String name)
    {
        String n = name.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        String base = slash >= 0 ? n.substring(slash + 1) : n;
        for (String s : NAMES) if (base.equalsIgnoreCase(s)) return true;
        return false;
    }

    // ------------------------------------------------------------ RGSS archives

    private static int le(DataInputStream in) throws IOException
    {
        int b0 = in.readUnsignedByte(), b1 = in.readUnsignedByte(), b2 = in.readUnsignedByte(), b3 = in.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static boolean fromArchive(File archive, File dest) throws IOException
    {
        try (RandomAccessFile raf = new RandomAccessFile(archive, "r")) {
            byte[] head = new byte[8];
            raf.readFully(head);
            if (!new String(head, 0, 6, StandardCharsets.US_ASCII).equals("RGSSAD")) return false;
            return head[7] == 3 ? fromV3(raf, dest) : fromV1(raf, dest);
        }
    }

    /** XP / VX: every entry is encrypted with a running key. */
    private static boolean fromV1(RandomAccessFile raf, File dest) throws IOException
    {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(raf.getFD()), 1 << 16));
        long pos = 8;
        int key = 0xDEADCAFE;
        try {
            while (true) {
                int nameLen = le(in) ^ key; key = key * 7 + 3; pos += 4;
                if (nameLen <= 0 || nameLen > 4096) return false;
                byte[] name = new byte[nameLen];
                in.readFully(name);
                for (int i = 0; i < nameLen; i++) { name[i] ^= (byte) (key & 0xFF); key = key * 7 + 3; }
                pos += nameLen;
                int size = le(in) ^ key; key = key * 7 + 3; pos += 4;
                String n = new String(name, StandardCharsets.UTF_8);
                if (isScripts(n)) {
                    decryptTo(in, size, key, dest);
                    return true;
                }
                long skipped = 0;
                while (skipped < size) {
                    long s = in.skip(size - skipped);
                    if (s <= 0) throw new EOFException();
                    skipped += s;
                }
                pos += size;
            }
        } catch (EOFException e) {
            return false;
        }
    }

    /** VX Ace: a table of entries, each with its own key. */
    private static boolean fromV3(RandomAccessFile raf, File dest) throws IOException
    {
        raf.seek(8);
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(raf.getFD())));
        int key = le(in) * 9 + 3;
        while (true) {
            int offset = le(in) ^ key;
            if (offset == 0) return false;
            int size = le(in) ^ key;
            int fileKey = le(in) ^ key;
            int nameLen = le(in) ^ key;
            if (nameLen <= 0 || nameLen > 4096) return false;
            byte[] name = new byte[nameLen];
            in.readFully(name);
            for (int i = 0; i < nameLen; i++) name[i] ^= (byte) ((key >>> (8 * (i % 4))) & 0xFF);
            if (isScripts(new String(name, StandardCharsets.UTF_8))) {
                raf.seek(offset & 0xFFFFFFFFL);
                DataInputStream data = new DataInputStream(new BufferedInputStream(new FileInputStream(raf.getFD())));
                decryptTo(data, size, fileKey, dest);
                return true;
            }
        }
    }

    private static void decryptTo(DataInputStream in, int size, int key, File dest) throws IOException
    {
        try (OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            int done = 0;
            int k = key;
            while (done < size) {
                int n = Math.min(buf.length, size - done);
                in.readFully(buf, 0, n);
                for (int i = 0; i < n; i++) {
                    int j = (done + i) % 4;
                    buf[i] ^= (byte) ((k >>> (8 * j)) & 0xFF);
                    if (j == 3) k = k * 7 + 3;
                }
                out.write(buf, 0, n);
                done += n;
            }
        }
    }
}
