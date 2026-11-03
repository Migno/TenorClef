package adris.altoclef.tradecycle;

import java.util.Locale;

/**
 * Registry-id helpers for enchantments. Accepts short names ({@code mending}, {@code Fire Aspect},
 * {@code fire-aspect}) and full ids ({@code minecraft:mending}); always yields the full lowercase id.
 */
public final class EnchantIds {

    private EnchantIds() {
    }

    /** {@code "Mending"} → {@code "minecraft:mending"}; null/blank → null. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (s.isEmpty()) return null;
        if (s.indexOf(':') < 0) s = "minecraft:" + s;
        if (s.startsWith(":") || s.endsWith(":")) return null;
        return s;
    }

    /** {@code "minecraft:fire_aspect"} → {@code "Fire Aspect"}. Unknown namespaces keep their path only. */
    public static String displayName(String id) {
        if (id == null || id.isEmpty()) return "?";
        String path = id.substring(id.indexOf(':') + 1);
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /** 1 → I … 10 → X; anything outside 1..10 is printed as a number. */
    public static String roman(int level) {
        String[] r = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 1 && level <= r.length ? r[level - 1] : Integer.toString(level);
    }

    /** {@code "Mending I"}. */
    public static String bookName(String id, int level) {
        return displayName(id) + " " + roman(level);
    }
}
