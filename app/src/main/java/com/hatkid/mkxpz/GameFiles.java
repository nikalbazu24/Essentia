package com.hatkid.mkxpz;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Filesystem-only deletion policy, also exercised against real temporary files in JVM tests. */
final class GameFiles
{
    private GameFiles() { }

    static boolean contains(File parent, File child) throws IOException
    {
        String p = parent.getCanonicalPath(), c = child.getCanonicalPath();
        return c.equals(p) || c.startsWith(p.endsWith(File.separator) ? p : p + File.separator);
    }

    static boolean linked(File file) throws IOException
    {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent == null) return false;
        File nominal = new File(parent.getCanonicalFile(), file.getName());
        return !nominal.equals(file.getCanonicalFile());
    }

    static boolean gameFolder(File folder)
    {
        String[] names = folder.list();
        if (names == null) return false;
        for (String n : names) {
            if (n.equalsIgnoreCase("Game.ini") || n.equalsIgnoreCase("Game.exe")
                || n.equalsIgnoreCase("mkxp.json") || n.equalsIgnoreCase("Game.rgssad")
                || n.equalsIgnoreCase("Game.rgss2a") || n.equalsIgnoreCase("Game.rgss3a")) {
                if (new File(folder, n).isFile()) return true;
            }
        }
        return false;
    }

    /** Rejects storage roots, common/shared folders, and every other registered game's tree. */
    static File validate(File candidate, List<File> volumes, List<File> protectedFolders,
                         List<File> otherGames, boolean requireGame) throws IOException
    {
        if (!candidate.isAbsolute() || linked(candidate)) throw new IOException("Not a regular game directory");
        File root = candidate.getCanonicalFile();
        boolean allowed = false;
        for (File volume : volumes) {
            if (!root.equals(volume.getCanonicalFile()) && contains(volume, root)) allowed = true;
        }
        if (!allowed || !root.isDirectory()) throw new IOException("Outside game storage");
        for (File protectedFolder : protectedFolders) {
            if (contains(root, protectedFolder)) throw new IOException("Shared or protected folder");
        }
        for (File other : otherGames) {
            if (contains(root, other) || contains(other, root)) throw new IOException("Overlaps another game");
        }
        if (requireGame && !gameFolder(root)) throw new IOException("Game marker missing");
        inspect(root, root, 0);
        return root;
    }

    /** Preflight the whole tree before deleting anything. Symlinks are leaves, never traversed. */
    private static void inspect(File root, File node, int depth) throws IOException
    {
        if (!node.equals(root) && !contains(root, node.getParentFile())) throw new IOException("Parent changed");
        if (linked(node)) return;
        if (!contains(root, node) || depth > 128) throw new IOException("Unsafe directory tree");
        if (!node.isDirectory()) return;
        if (depth > 0 && gameFolder(node)) throw new IOException("Nested game directory");
        File[] children = node.listFiles();
        if (children == null) throw new IOException("Cannot list directory");
        for (File child : children) inspect(root, child, depth + 1);
    }

    /** Deletes an already validated tree, checking containment again at each level. */
    static void delete(File root) throws IOException
    {
        if (linked(root) || !root.equals(root.getCanonicalFile())) throw new IOException("Directory changed");
        erase(root, root, 0);
    }

    private static void erase(File root, File node, int depth) throws IOException
    {
        if (!node.equals(root) && !contains(root, node.getParentFile())) throw new IOException("Parent changed");
        boolean link = linked(node);
        if (!link) {
            if (!contains(root, node) || depth > 128) throw new IOException("Directory changed");
            if (node.isDirectory()) {
                if (depth > 0 && gameFolder(node)) throw new IOException("Nested game directory");
                File[] children = node.listFiles();
                if (children == null) throw new IOException("Cannot list directory");
                // Keep Game.ini/Game.exe until the rest is gone, so interrupted deletion remains identifiable.
                java.util.Arrays.sort(children, (a, b) -> Boolean.compare(marker(a), marker(b)));
                for (File child : children) erase(root, child, depth + 1);
            }
        }
        if (!node.delete()) throw new IOException("Could not delete " + node.getName());
    }

    private static boolean marker(File f)
    {
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        return n.equals("game.ini") || n.equals("game.exe") || n.equals("mkxp.json") || n.startsWith("game.rgss");
    }
}
