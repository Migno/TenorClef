package adris.altoclef.autobuild;

import java.util.Map;
import java.util.Set;

/**
 * Block state to items, without Minecraft classes: a state is its block id, its properties as strings, and the item
 * id the game maps the block to ({@code Block.asItem()}, {@code minecraft:air} when it has none).
 * <p>
 * The game's own mapping already covers wall variants (wall torch to torch, wall sign to sign) and crops (wheat to
 * wheat seeds); the rules here handle what it gets wrong for building: second halves that come free with the first
 * (door tops, bed heads), states that take several items (double slabs, candles, sea pickles, snow layers), blocks
 * the builder cannot make at all (liquids, portals, potted plants, farmland), and blocks with no item of their own.
 */
public final class BlockItems {

    public enum Kind {
        /** Needs {@link Need#items()}. */
        ITEM,
        /** Needs nothing: air, or the half of a two-block thing that the other half places. */
        NONE,
        /** The builder cannot place it: {@link Need#reason()} says why. */
        SKIP
    }

    public record Need(Kind kind, Map<String, Integer> items, String reason) {
        static final Need NOTHING = new Need(Kind.NONE, Map.of(), null);

        static Need item(String id, int count) {
            return new Need(Kind.ITEM, Map.of(id, count), null);
        }

        static Need skip(String reason) {
            return new Need(Kind.SKIP, Map.of(), reason);
        }

        /** Total item count, for summaries. */
        public int total() {
            return items.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private static final String AIR = "minecraft:air";

    private static final Set<String> EMPTY = Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
            "minecraft:piston_head", "minecraft:moving_piston");

    private static final Map<String, String> SKIPPED = Map.ofEntries(
            Map.entry("minecraft:water", "liquid"),
            Map.entry("minecraft:lava", "liquid"),
            Map.entry("minecraft:bubble_column", "liquid"),
            Map.entry("minecraft:fire", "fire"),
            Map.entry("minecraft:soul_fire", "fire"),
            Map.entry("minecraft:nether_portal", "portal"),
            Map.entry("minecraft:end_portal", "portal"),
            Map.entry("minecraft:end_gateway", "portal"),
            Map.entry("minecraft:frosted_ice", "frosted ice"),
            Map.entry("minecraft:farmland", "needs a hoe on dirt"),
            Map.entry("minecraft:dirt_path", "needs a shovel on dirt"));

    /** Blocks whose {@code asItem()} is air but that are placed with this item. */
    private static final Map<String, String> ITEM_OF = Map.ofEntries(
            Map.entry("minecraft:kelp_plant", "minecraft:kelp"),
            Map.entry("minecraft:cave_vines", "minecraft:glow_berries"),
            Map.entry("minecraft:cave_vines_plant", "minecraft:glow_berries"),
            Map.entry("minecraft:weeping_vines_plant", "minecraft:weeping_vines"),
            Map.entry("minecraft:twisting_vines_plant", "minecraft:twisting_vines"),
            Map.entry("minecraft:big_dripleaf_stem", "minecraft:big_dripleaf"),
            Map.entry("minecraft:bamboo_sapling", "minecraft:bamboo"),
            Map.entry("minecraft:tall_seagrass", "minecraft:seagrass"),
            Map.entry("minecraft:melon_stem", "minecraft:melon_seeds"),
            Map.entry("minecraft:attached_melon_stem", "minecraft:melon_seeds"),
            Map.entry("minecraft:pumpkin_stem", "minecraft:pumpkin_seeds"),
            Map.entry("minecraft:attached_pumpkin_stem", "minecraft:pumpkin_seeds"),
            Map.entry("minecraft:redstone_wire", "minecraft:redstone"),
            Map.entry("minecraft:tripwire", "minecraft:string"),
            Map.entry("minecraft:cocoa", "minecraft:cocoa_beans"),
            Map.entry("minecraft:sweet_berry_bush", "minecraft:sweet_berries"),
            Map.entry("minecraft:wheat", "minecraft:wheat_seeds"),
            Map.entry("minecraft:carrots", "minecraft:carrot"),
            Map.entry("minecraft:potatoes", "minecraft:potato"),
            Map.entry("minecraft:beetroots", "minecraft:beetroot_seeds"),
            Map.entry("minecraft:torchflower_crop", "minecraft:torchflower_seeds"),
            Map.entry("minecraft:pitcher_crop", "minecraft:pitcher_pod"),
            Map.entry("minecraft:powder_snow", "minecraft:powder_snow_bucket"));

    /** Properties that say how many items one block holds. */
    private static final Map<String, String> COUNT_PROPERTY = Map.of(
            "minecraft:sea_pickle", "pickles",
            "minecraft:turtle_egg", "eggs",
            "minecraft:snow", "layers",
            "minecraft:pink_petals", "flower_amount",
            "minecraft:wildflowers", "flower_amount",
            "minecraft:leaf_litter", "segment_amount");

    private BlockItems() {
    }

    /** {@code oak_log} to {@code minecraft:oak_log}; null stays null. */
    public static String id(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase(java.util.Locale.ROOT);
        return t.indexOf(':') < 0 ? "minecraft:" + t : t;
    }

    /** What placing this state takes. */
    public static Need needFor(String blockId, Map<String, String> props, String asItemId) {
        String block = id(blockId);
        Map<String, String> p = props == null ? Map.of() : props;
        if (block == null || EMPTY.contains(block)) return Need.NOTHING;
        String skip = SKIPPED.get(block);
        if (skip != null) return Need.skip(skip);
        if (block.startsWith("minecraft:potted_")) return Need.skip("potted plant");
        if (block.endsWith("candle_cake")) return Need.skip("candle on a cake");
        // the upper half of a door, tall flower or tall grass, and a bed's head, come with the other half
        if ("upper".equals(p.get("half")) || "head".equals(p.get("part"))) return Need.NOTHING;
        String item = itemOf(block, asItemId);
        if (item == null) return Need.skip("no item");
        if (block.endsWith("_slab") && "double".equals(p.get("type"))) return Need.item(item, 2);
        return Need.item(item, Math.max(1, countOf(block, p)));
    }

    /**
     * What is still to place where the world has {@code current}: nothing when it is the desired block already, the
     * difference when it is a part of it (a single slab where a double is wanted, two candles of four), else all of
     * {@link #needFor}.
     */
    public static Need remaining(String blockId, Map<String, String> props, String asItemId,
                                 String currentBlockId, Map<String, String> currentProps) {
        Need full = needFor(blockId, props, asItemId);
        if (full.kind() != Kind.ITEM || !id(blockId).equals(id(currentBlockId))) return full;
        Map<String, String> want = props == null ? Map.of() : props;
        Map<String, String> have = currentProps == null ? Map.of() : currentProps;
        String block = id(blockId);
        if (block.endsWith("_slab")) {
            boolean wantDouble = "double".equals(want.get("type"));
            boolean haveDouble = "double".equals(have.get("type"));
            return wantDouble && !haveDouble ? Need.item(full.items().keySet().iterator().next(), 1) : Need.NOTHING;
        }
        int wantN = countOf(block, want), haveN = countOf(block, have);
        if (wantN > haveN && haveN > 0) {
            return Need.item(full.items().keySet().iterator().next(), wantN - haveN);
        }
        return Need.NOTHING;
    }

    private static String itemOf(String block, String asItemId) {
        String as = id(asItemId);
        if (as != null && !as.equals(AIR)) return as;
        String known = ITEM_OF.get(block);
        if (known != null) return known;
        // wall-mounted variants share the standing item (the game maps these itself, this is the fallback)
        String[][] wall = {{"_wall_torch", "_torch"}, {"_wall_hanging_sign", "_hanging_sign"}, {"_wall_sign", "_sign"},
                {"_wall_banner", "_banner"}, {"_wall_skull", "_skull"}, {"_wall_head", "_head"}, {"_wall_fan", "_fan"}};
        if (block.equals("minecraft:wall_torch")) return "minecraft:torch";
        for (String[] w : wall) {
            if (block.endsWith(w[0])) return block.substring(0, block.length() - w[0].length()) + w[1];
        }
        return null;
    }

    private static int countOf(String block, Map<String, String> p) {
        String prop = COUNT_PROPERTY.get(block);
        if (prop == null && block.endsWith("candle")) prop = "candles";
        if (prop == null) return 1;
        try {
            return Integer.parseInt(p.getOrDefault(prop, "1"));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
