package adris.altoclef.autobuild.mc;

import adris.altoclef.AltoClef;
import adris.altoclef.autobuild.AutoBuildState;

import java.io.File;
import java.util.List;

/**
 * Minecraft side of {@code @build}. This file is the stub every Minecraft line compiles; the real implementation
 * lives in {@code versions/26.3/src/main/java/adris/altoclef/autobuild/mc/AutoBuildMc.java}, which the
 * preprocessor uses instead of this one for the 26.3 module.
 */
public final class AutoBuildMc {

    public static final String UNSUPPORTED = "@build needs the Minecraft 26.3 build";

    private AutoBuildMc() {
    }

    public static boolean supported() {
        return false;
    }

    public static File schematicsDir() {
        return new File("schematics");
    }

    public static int[] playerFeet() {
        return null;
    }

    public static boolean litematicaPresent() {
        return false;
    }

    public static String worldKey() {
        return null;
    }

    public static boolean running() {
        return false;
    }

    public static String start(AltoClef mod, AutoBuildState s, Runnable onFinish) {
        return UNSUPPORTED;
    }

    public static String resume(AltoClef mod, Runnable onFinish) {
        return UNSUPPORTED;
    }

    public static String pause(AltoClef mod) {
        return UNSUPPORTED;
    }

    public static String cancel(AltoClef mod) {
        return UNSUPPORTED;
    }

    public static String skip(String item, boolean add) {
        return UNSUPPORTED;
    }

    public static List<String> status() {
        return List.of(UNSUPPORTED);
    }
}
