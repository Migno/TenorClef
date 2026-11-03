package adris.altoclef.tradecycle.mc;

import adris.altoclef.AltoClef;
import adris.altoclef.tradecycle.EnchantInfo;

import java.util.List;

/**
 * Minecraft side of the librarian trade system. This file is the stub every Minecraft line compiles;
 * the real implementation lives in {@code versions/26.3/src/main/java/adris/altoclef/tradecycle/mc/TradeMc.java},
 * which the preprocessor uses instead of this one for the 26.3 module.
 */
public final class TradeMc {

    public static final String UNSUPPORTED = "the trade system needs the Minecraft 26.3 build";

    private TradeMc() {
    }

    public static boolean supported() {
        return false;
    }

    /** Keybinds, overlay, world-change hook. Called from {@code AltoClef.onInitialize}. */
    public static void init() {
    }

    /** @return null when started, else the reason it could not start */
    public static String startScan(AltoClef mod, int radius, Runnable onFinish) {
        return UNSUPPORTED;
    }

    /** @return null when started, else the reason it could not start */
    public static String startCycle(AltoClef mod, int radius, boolean upgrade, Runnable onFinish) {
        return UNSUPPORTED;
    }

    /** Registry info for an enchantment id, or null if unknown / not in a world. */
    public static EnchantInfo enchant(String id) {
        return null;
    }

    /** Every enchantment in {@code #minecraft:tradeable}, read from the live registry. */
    public static List<EnchantInfo> tradeable() {
        return List.of();
    }

    /** Player position {x, y, z} or null. */
    public static double[] playerPos() {
        return null;
    }

    public static boolean toggleOverlay() {
        return false;
    }

    public static void openScreen() {
    }
}
