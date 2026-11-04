package com.hatkid.mkxpz;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Talks to the running game through two small files (read by essentia_actions.rb):
 * commands go in (turbo, screenshot, open the team...), answers come back.
 */
public final class GameLink
{
    private GameLink() { }

    public static File commandFile(Context c) { return new File(c.getCacheDir(), "essentia_cmd.txt"); }
    public static File answerFile(Context c) { return new File(c.getCacheDir(), "essentia_ack.txt"); }

    /** Forgets anything left over from a previous game. */
    public static void reset(Context c)
    {
        //noinspection ResultOfMethodCallIgnored
        commandFile(c).delete();
        //noinspection ResultOfMethodCallIgnored
        answerFile(c).delete();
    }

    public static synchronized void send(Context c, String command)
    {
        try (FileOutputStream out = new FileOutputStream(commandFile(c), true)) {
            out.write((command + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    /** Answers written by the game since the last call (oldest first). */
    public static synchronized List<String> answers(Context c)
    {
        File f = answerFile(c);
        if (!f.isFile()) return Collections.emptyList();
        File reading = new File(f.getPath() + ".read");
        if (!f.renameTo(reading)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        try (FileInputStream in = new FileInputStream(reading)) {
            for (String line : new String(SaveTransaction.read(in, 65536), StandardCharsets.UTF_8).split("\n")) {
                if (!line.trim().isEmpty()) out.add(line.trim());
            }
        } catch (Exception ignored) { }
        //noinspection ResultOfMethodCallIgnored
        reading.delete();
        return out;
    }
}
