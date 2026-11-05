package com.hatkid.mkxpz;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** Local launch records shared by the library and the separate Ruby process. */
final class GameLaunchState {
    private static final String[] PACKS = {"Game.rgssad", "Game.rgss2a", "Game.rgss3a"};
    private static final String[] SCRIPTS = {"Scripts.rxdata", "Scripts.rvdata", "Scripts.rvdata2"};

    static final class Inspection {
        final String version;
        final String missing;

        Inspection(String version, String missing) {
            this.version = version;
            this.missing = missing;
        }
    }

    static final class Attempt {
        final String id;
        final String version;
        final String engine;
        final String screen;
        final String requestedScreen;
        final boolean frontend;
        final boolean learnClassic;

        Attempt(JSONObject data) {
            id = data.optString("id");
            version = data.optString("version");
            engine = data.optString("engine");
            screen = data.optString("screen");
            requestedScreen = data.optString("requestedScreen");
            frontend = data.optBoolean("frontend");
            learnClassic = data.optBoolean("learnClassic");
        }

        boolean matches(String version, String requestedScreen) {
            return this.version.equals(version) && this.requestedScreen.equals(requestedScreen);
        }
    }

    private GameLaunchState() { }

    static Inspection inspect(File game) {
        File ini = Games.findChild(game, "Game.ini");
        File config = Games.findChild(game, "mkxp.json");
        File data = Games.findChild(game, "Data");
        StringBuilder version = new StringBuilder();
        String gameVersion = GameUpdater.localVersion(game);
        if (gameVersion != null) version.append(gameVersion);
        stamp(version, ini);
        stamp(version, config);
        boolean packed = false;
        for (String name : PACKS) {
            File file = Games.findChild(game, name);
            stamp(version, file);
            packed |= usable(file);
        }
        boolean scripts = false;
        for (String name : SCRIPTS) {
            File file = Games.findChild(data, name);
            stamp(version, file);
            scripts |= usable(file);
        }
        String configured = configuredScripts(ini);
        if (!configured.isEmpty()) {
            File file = game;
            for (String part : configured.split("/")) file = Games.findChild(file, part);
            stamp(version, file);
            if (!packed && !usable(config) && !usable(file))
                return new Inspection(version.toString(), configured);
            scripts |= usable(file);
        }
        // Packed and custom mkxp-z games can use a different script layout.
        String missing = packed || usable(config) || scripts ? null : "Data/Scripts";
        return new Inspection(version.toString(), missing);
    }

    private static String configuredScripts(File ini) {
        if (!usable(ini) || ini.length() > 65536) return "";
        try (java.io.InputStream in = new java.io.FileInputStream(ini)) {
            String section = "";
            for (String line : new String(SaveTransaction.read(in, 65536), StandardCharsets.UTF_8).split("\\r?\\n")) {
                String text = line.replace("\uFEFF", "").trim();
                if (text.startsWith("[") && text.endsWith("]")) section = text.substring(1, text.length() - 1);
                int equal = text.indexOf('=');
                if (!section.equalsIgnoreCase("Game") || equal < 0
                    || !text.substring(0, equal).trim().equalsIgnoreCase("Scripts")) continue;
                String path = text.substring(equal + 1).trim().replace('\\', '/');
                if (path.startsWith("\"") && path.endsWith("\"") && path.length() >= 2)
                    path = path.substring(1, path.length() - 1);
                if (path.startsWith("/") || path.indexOf(':') >= 0) return "";
                for (String part : path.split("/")) if (part.equals("..")) return "";
                return path;
            }
        } catch (Exception ignored) { }
        return "";
    }

    private static boolean usable(File file) {
        return file != null && file.isFile() && file.canRead() && file.length() > 0;
    }

    private static void stamp(StringBuilder out, File file) {
        if (file != null && file.isFile()) {
            out.append('|').append(file.getName()).append(':').append(file.length()).append(':').append(file.lastModified());
        }
    }

    static String begin(Context context, String path, String version, String engine, String screen,
                        String requestedScreen, boolean frontend, boolean learnClassic) {
        String id = UUID.randomUUID().toString();
        try {
            JSONObject data = new JSONObject().put("id", id).put("version", version)
                .put("engine", engine).put("screen", screen).put("requestedScreen", requestedScreen)
                .put("frontend", frontend).put("learnClassic", learnClassic);
            write(file(context, path, "launch"), data);
        } catch (Exception ignored) { }
        return id;
    }

    static Attempt interrupted(Context context, String path) {
        JSONObject data = read(file(context, path, "launch"));
        return data.has("id") ? new Attempt(data) : null;
    }

    static Attempt working(Context context, String path) {
        JSONObject data = read(file(context, path, "working"));
        return data.has("id") ? new Attempt(data) : null;
    }

    static void complete(Context context, String path, String id, boolean reachedFrame) {
        if (path == null || id == null) return;
        AtomicFile pending = file(context, path, "launch");
        JSONObject data = read(pending);
        if (!id.equals(data.optString("id"))) return;
        if (reachedFrame) {
            try {
                write(file(context, path, "working"), data);
                if (data.optBoolean("learnClassic")) write(file(context, path, "classic"), data);
            } catch (Exception ignored) { }
        }
        pending.delete();
    }

    static boolean learnedClassic(Context context, String path) {
        JSONObject data = read(file(context, path, "classic"));
        return data.has("id") && data.optString("version").equals(inspect(new File(path)).version);
    }

    static void forgetClassic(Context context, String path) {
        file(context, path, "classic").delete();
    }

    static void forgetScreen(Context context, String path) {
        file(context, path, "working").delete();
        file(context, path, "launch").delete();
    }

    static boolean rubyCompatibilityError(String error) {
        if (error == null || error.contains("widescreen.rb")) return false;
        int separator = error.indexOf("\n\n");
        if (separator < 0) return false;
        String heading = error.substring(0, separator);
        String message = error.substring(separator + 2).toLowerCase(Locale.ROOT);
        return (heading.contains(": SyntaxError occured.") && (message.contains("invalid retry")
            || (message.contains("unexpected ':'") && message.matches("(?s).*\\bwhen\\s+[^\\n]+:.*"))))
            || (heading.contains(": NoMethodError occured.") && (message.contains("undefined method `critical")
            || (message.contains("undefined method `each") && message.contains(":string"))));
    }

    private static AtomicFile file(Context context, String path, String kind) {
        return new AtomicFile(new File(GameProgress.file(context, path).getPath() + "." + kind));
    }

    private static JSONObject read(AtomicFile file) {
        try (java.io.InputStream in = file.openRead()) {
            return new JSONObject(new String(SaveTransaction.read(in, 16384), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static void write(AtomicFile file, JSONObject data) throws Exception {
        FileOutputStream out = file.startWrite();
        try {
            out.write(data.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (Exception failure) {
            file.failWrite(out);
            throw failure;
        }
    }
}
