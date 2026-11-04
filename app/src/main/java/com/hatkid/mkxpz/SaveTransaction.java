package com.hatkid.mkxpz;

import java.io.*;
import java.util.*;

/** Validates the entire restore before touching a save; keeps an on-disk rollback. */
public final class SaveTransaction {
    public static final long LIMIT = 20L * 1024 * 1024;
    private SaveTransaction() {}

    public static byte[] read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) { return read(in, LIMIT); }
    }
    public static byte[] read(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[65536]; int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + (long)n > limit) throw new IOException("El archivo supera el límite permitido");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
    public static File child(File root, String name) throws IOException {
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/")
            || name.contains("\\") || name.contains(":") || name.indexOf('\0') >= 0)
            throw new IOException("Nombre de archivo no válido");
        File dest = new File(root, name);
        if (!dest.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))
            throw new IOException("El archivo sale de la carpeta seleccionada");
        // Reject links even if they point to a sibling within the same directory.
        if (!dest.getCanonicalFile().equals(new File(root.getCanonicalFile(), name)))
            throw new IOException("No se restauran enlaces");
        return dest;
    }
    public static synchronized int restore(File target, File backupRoot, Map<String, byte[]> files) throws IOException {
        if (files.isEmpty() || files.size() > 128) throw new IOException("Respaldo vacío o con demasiados archivos");
        Set<String> names = new HashSet<>(); long size = 0;
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            child(target, e.getKey());
            if (!names.add(e.getKey().toLowerCase(Locale.ROOT))) throw new IOException("Archivos duplicados");
            size += e.getValue().length;
            if (size > LIMIT) throw new IOException("El respaldo supera 20 MB");
        }
        if (!target.isDirectory() && !target.mkdirs()) throw new IOException("No se puede crear la carpeta de partidas");
        if (!backupRoot.isDirectory() && !backupRoot.mkdirs()) throw new IOException("No se puede crear el respaldo de seguridad");
        File journal = new File(backupRoot, "antes-restaurar-" + System.currentTimeMillis() + "-" + UUID.randomUUID());
        if (!journal.mkdir()) throw new IOException("No se puede guardar la copia de seguridad");
        File staged = new File(journal, "nuevos");
        if (!staged.mkdir()) throw new IOException("No se puede preparar la restauración");
        List<String> changed = new ArrayList<>();
        try {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                File dest = child(target, e.getKey());
                if (dest.exists()) {
                    if (!dest.isFile()) throw new IOException("El destino no es un archivo");
                    copy(dest, child(journal, e.getKey()));
                }
                try (FileOutputStream out = new FileOutputStream(child(staged, e.getKey()))) {
                    out.write(e.getValue()); out.getFD().sync();
                }
            }
            for (String name : files.keySet()) {
                File dest = child(target, name);
                // Keep the journal even on success; restoring never discards the previous save.
                changed.add(name);
                if (!child(staged, name).renameTo(dest)) throw new IOException("No se pudo reemplazar " + name);
            }
            staged.delete();
            return files.size();
        } catch (IOException failure) {
            for (String name : changed) {
                try {
                    File old = child(journal, name), dest = child(target, name);
                    if (old.isFile()) copy(old, dest);
                    else if (dest.exists() && !dest.delete()) throw new IOException("No se pudo revertir " + name);
                } catch (IOException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }
    public static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] bytes = new byte[65536]; int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            out.getFD().sync();
        }
    }
}
