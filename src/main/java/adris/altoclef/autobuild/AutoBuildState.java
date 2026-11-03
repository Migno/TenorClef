package adris.altoclef.autobuild;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The build in progress, saved to {@code config/tenorclef/autobuild_state.json} on every phase change so
 * {@code @build resume} carries on after a relog or crash. What is still to place is not trusted from here: the
 * world is scanned again on resume, {@link #remaining} is only the last scan's result for {@code @build status}.
 */
public class AutoBuildState {

    /** Schematic file name in the schematics folder; null for a Litematica placement. */
    public String file;
    public boolean litematica;
    public int litematicaIndex;
    /** World position of the schematic's corner. */
    public int[] origin = new int[3];
    /** NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 (Minecraft's Rotation names). */
    public String rotation = "NONE";
    /** NONE, LEFT_RIGHT, FRONT_BACK (Minecraft's Mirror names). */
    public String mirror = "NONE";
    /** Server and dimension the build is in, so resume refuses another world. */
    public String world;
    /** Items the player said to leave out ({@code @build skip}): their blocks are not built. */
    public List<String> skipItems = new ArrayList<>();
    /** Item id to count still needed after the last scan. */
    public Map<String, Integer> remaining = new LinkedHashMap<>();
    /** Item id to count that could not be gathered automatically, with no stock in the inventory. */
    public Map<String, Integer> manual = new LinkedHashMap<>();
    public boolean cleared;
    /** Set by {@code @build pause}; cleared by resume. */
    public boolean paused;
    public String phase = "START";
    public long startedAt;
    public long updatedAt;

    public AutoBuildState sanitize() {
        if (origin == null || origin.length != 3) origin = new int[3];
        if (rotation == null) rotation = "NONE";
        if (mirror == null) mirror = "NONE";
        if (skipItems == null) skipItems = new ArrayList<>();
        if (remaining == null) remaining = new LinkedHashMap<>();
        if (manual == null) manual = new LinkedHashMap<>();
        if (phase == null) phase = "START";
        return this;
    }

    public String label() {
        return litematica ? "litematica placement " + (litematicaIndex + 1) : file;
    }

    /** {@code 0}, {@code 90}, {@code cw90}, {@code clockwise_90}, ... to Minecraft's Rotation name, or null. */
    public static String parseRotation(String s) {
        if (s == null) return null;
        return switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "0", "none" -> "NONE";
            case "90", "cw90", "cw", "clockwise_90" -> "CLOCKWISE_90";
            case "180", "cw180", "clockwise_180" -> "CLOCKWISE_180";
            case "270", "-90", "ccw90", "ccw", "counterclockwise_90" -> "COUNTERCLOCKWISE_90";
            default -> null;
        };
    }

    /** {@code none}, {@code lr}, {@code left_right}, {@code fb}, {@code front_back} to Minecraft's Mirror name, or null. */
    public static String parseMirror(String s) {
        if (s == null) return null;
        return switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "none", "no" -> "NONE";
            case "lr", "left_right", "leftright", "x" -> "LEFT_RIGHT";
            case "fb", "front_back", "frontback", "z" -> "FRONT_BACK";
            default -> null;
        };
    }
}
