package adris.altoclef.autobuild;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Finding schematic files by a short name, and {@code ~}-relative coordinates. */
public final class SchematicFiles {

    public static final List<String> EXTENSIONS = List.of(".litematic", ".schem", ".schematic");

    private SchematicFiles() {
    }

    public static boolean isSchematic(String name) {
        String l = name.toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(l::endsWith);
    }

    /** Schematic files in {@code dir}, sorted by name. */
    public static List<File> list(File dir) {
        File[] files = dir.listFiles((d, n) -> isSchematic(n));
        if (files == null) return List.of();
        List<File> out = new ArrayList<>(Arrays.asList(files));
        out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    /**
     * The file for {@code name}: exact, then with each extension, then a case-insensitive match, then the only file
     * whose name starts with it. Null when none or ambiguous.
     */
    public static File resolve(File dir, String name) {
        if (name == null || name.isBlank() || name.contains("..")) return null;
        // from the listing, not new File(dir, name): on Windows that "exists" in any case and keeps the typed name
        List<File> all = list(dir);
        for (File f : all) if (f.getName().equals(name)) return f;
        for (String ext : EXTENSIONS) {
            for (File f : all) if (f.getName().equals(name + ext)) return f;
        }
        String l = name.toLowerCase(Locale.ROOT);
        for (File f : all) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.equals(l)) return f;
            for (String ext : EXTENSIONS) if (n.equals(l + ext)) return f;
        }
        List<File> prefix = all.stream().filter(f -> f.getName().toLowerCase(Locale.ROOT).startsWith(l)).toList();
        return prefix.size() == 1 ? prefix.get(0) : null;
    }

    /** {@code 12}, {@code -3}, {@code ~}, {@code ~5}, {@code ~-2}. */
    public static boolean isCoord(String s) {
        if (s == null || s.isEmpty()) return false;
        String t = s.startsWith("~") ? s.substring(1) : s;
        if (t.isEmpty()) return s.startsWith("~");
        try {
            Integer.parseInt(t);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static int coord(String s, int base) {
        if (s.startsWith("~")) {
            String t = s.substring(1);
            return base + (t.isEmpty() ? 0 : Integer.parseInt(t));
        }
        return Integer.parseInt(s);
    }
}
