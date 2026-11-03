package adris.altoclef.autobuild;

/** {@code config/tenorclef/autobuild.json}, changed with {@code @build set <key> <value>}. */
public class AutoBuildConfig {

    /** Break everything in the schematic's bounding box before building. */
    public boolean clearArea = false;
    /** Inventory slots a batch leaves empty (tools, food, pickups while gathering). */
    public int reserveSlots = 4;
    /** Give up gathering an item after this many seconds without its count going up, and ask for it. */
    public int gatherStallSec = 300;
    /** Hunt mobs for drops (slime balls, string, ...). Off: those items are asked for. */
    public boolean huntMobDrops = false;
    /** Let gathering take items from chests the bot has already seen before mining or crafting them. */
    public boolean useContainers = true;

    public AutoBuildConfig sanitize() {
        if (reserveSlots < 0) reserveSlots = 0;
        if (reserveSlots > 30) reserveSlots = 30;
        if (gatherStallSec < 30) gatherStallSec = 30;
        return this;
    }

    /** @return null when set, else what is wrong */
    public String set(String key, String value) {
        String v = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            switch (key.toLowerCase(java.util.Locale.ROOT)) {
                case "cleararea" -> clearArea = bool(v);
                case "reserveslots" -> reserveSlots = Integer.parseInt(v);
                case "gatherstallsec" -> gatherStallSec = Integer.parseInt(v);
                case "huntmobdrops" -> huntMobDrops = bool(v);
                case "usecontainers" -> useContainers = bool(v);
                default -> {
                    return "unknown setting " + key + " (clearArea, reserveSlots, gatherStallSec, huntMobDrops, useContainers)";
                }
            }
        } catch (IllegalArgumentException e) {
            return "bad value '" + value + "' for " + key;
        }
        sanitize();
        return null;
    }

    private static boolean bool(String v) {
        return switch (v) {
            case "true", "on", "yes", "1" -> true;
            case "false", "off", "no", "0" -> false;
            default -> throw new IllegalArgumentException(v);
        };
    }

    public String describe() {
        return "clearArea=" + clearArea + " reserveSlots=" + reserveSlots + " gatherStallSec=" + gatherStallSec
                + " huntMobDrops=" + huntMobDrops + " useContainers=" + useContainers;
    }
}
