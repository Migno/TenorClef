package adris.altoclef.autobuild;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static adris.altoclef.autobuild.BlockItems.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockItemsTest {

    private static BlockItems.Need need(String block, String asItem, String... props) {
        return BlockItems.needFor(block, props(props), asItem);
    }

    private static Map<String, String> props(String... kv) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void plainBlockIsItsItem() {
        BlockItems.Need n = need("minecraft:stone_bricks", "minecraft:stone_bricks");
        assertEquals(ITEM, n.kind());
        assertEquals(Map.of("minecraft:stone_bricks", 1), n.items());
    }

    @Test
    void idsWithoutNamespace() {
        assertEquals(Map.of("minecraft:oak_log", 1), need("oak_log", "oak_log", "axis", "y").items());
    }

    @Test
    void airAndPistonPartsNeedNothing() {
        assertEquals(NONE, need("minecraft:air", "minecraft:air").kind());
        assertEquals(NONE, need("minecraft:cave_air", "minecraft:air").kind());
        assertEquals(NONE, need("minecraft:piston_head", "minecraft:air", "facing", "up").kind());
    }

    @Test
    void doorTopAndBedHeadComeWithTheOtherHalf() {
        assertEquals(NONE, need("minecraft:oak_door", "minecraft:oak_door", "half", "upper").kind());
        assertEquals(Map.of("minecraft:oak_door", 1), need("minecraft:oak_door", "minecraft:oak_door", "half", "lower").items());
        assertEquals(NONE, need("minecraft:red_bed", "minecraft:red_bed", "part", "head").kind());
        assertEquals(Map.of("minecraft:red_bed", 1), need("minecraft:red_bed", "minecraft:red_bed", "part", "foot").items());
        assertEquals(NONE, need("minecraft:tall_grass", "minecraft:tall_grass", "half", "upper").kind());
    }

    @Test
    void stairsAndTrapdoorHalvesAreNotDoubleBlocks() {
        assertEquals(ITEM, need("minecraft:oak_stairs", "minecraft:oak_stairs", "half", "top").kind());
        assertEquals(ITEM, need("minecraft:oak_trapdoor", "minecraft:oak_trapdoor", "half", "bottom").kind());
    }

    @Test
    void doubleSlabIsTwo() {
        assertEquals(Map.of("minecraft:oak_slab", 2), need("minecraft:oak_slab", "minecraft:oak_slab", "type", "double").items());
        assertEquals(Map.of("minecraft:oak_slab", 1), need("minecraft:oak_slab", "minecraft:oak_slab", "type", "top").items());
    }

    @Test
    void waterloggedBlockNeedsOnlyTheBlock() {
        BlockItems.Need n = need("minecraft:oak_slab", "minecraft:oak_slab", "type", "bottom", "waterlogged", "true");
        assertEquals(Map.of("minecraft:oak_slab", 1), n.items());
    }

    @Test
    void countedStates() {
        assertEquals(Map.of("minecraft:candle", 3), need("minecraft:candle", "minecraft:candle", "candles", "3").items());
        assertEquals(Map.of("minecraft:red_candle", 4), need("minecraft:red_candle", "minecraft:red_candle", "candles", "4").items());
        assertEquals(Map.of("minecraft:sea_pickle", 2), need("minecraft:sea_pickle", "minecraft:sea_pickle", "pickles", "2").items());
        assertEquals(Map.of("minecraft:snow", 5), need("minecraft:snow", "minecraft:snow", "layers", "5").items());
        assertEquals(Map.of("minecraft:turtle_egg", 3), need("minecraft:turtle_egg", "minecraft:turtle_egg", "eggs", "3").items());
        assertEquals(Map.of("minecraft:pink_petals", 4), need("minecraft:pink_petals", "minecraft:pink_petals", "flower_amount", "4").items());
    }

    @Test
    void blocksWithoutTheirOwnItem() {
        assertEquals(Map.of("minecraft:kelp", 1), need("minecraft:kelp_plant", "minecraft:air").items());
        assertEquals(Map.of("minecraft:redstone", 1), need("minecraft:redstone_wire", "minecraft:air", "power", "0").items());
        assertEquals(Map.of("minecraft:string", 1), need("minecraft:tripwire", "minecraft:air").items());
        assertEquals(Map.of("minecraft:glow_berries", 1), need("minecraft:cave_vines_plant", "minecraft:air").items());
        assertEquals(Map.of("minecraft:wheat_seeds", 1), need("minecraft:wheat", "minecraft:air", "age", "7").items());
    }

    @Test
    void wallVariantsFallBackToTheStandingItem() {
        assertEquals(Map.of("minecraft:torch", 1), need("minecraft:wall_torch", "minecraft:air").items());
        assertEquals(Map.of("minecraft:soul_torch", 1), need("minecraft:soul_wall_torch", "minecraft:air").items());
        assertEquals(Map.of("minecraft:oak_sign", 1), need("minecraft:oak_wall_sign", "minecraft:air").items());
        assertEquals(Map.of("minecraft:oak_hanging_sign", 1), need("minecraft:oak_wall_hanging_sign", "minecraft:air").items());
        assertEquals(Map.of("minecraft:skeleton_skull", 1), need("minecraft:skeleton_wall_skull", "minecraft:air").items());
        // the game's own mapping wins when it has one
        assertEquals(Map.of("minecraft:torch", 1), need("minecraft:wall_torch", "minecraft:torch").items());
    }

    @Test
    void unbuildable() {
        assertEquals(SKIP, need("minecraft:water", "minecraft:air", "level", "0").kind());
        assertEquals("liquid", need("minecraft:lava", "minecraft:air").reason());
        assertEquals(SKIP, need("minecraft:nether_portal", "minecraft:air").kind());
        assertEquals(SKIP, need("minecraft:potted_poppy", "minecraft:air").kind());
        assertEquals(SKIP, need("minecraft:farmland", "minecraft:farmland").kind());
        assertEquals(SKIP, need("minecraft:red_candle_cake", "minecraft:air").kind());
        assertEquals("no item", need("minecraft:some_mod_block", "minecraft:air").reason());
    }

    @Test
    void remainingWhenAlreadyThere() {
        assertEquals(NONE, BlockItems.remaining("minecraft:stone", Map.of(), "minecraft:stone", "minecraft:stone", Map.of()).kind());
        // a different block: all of it, the builder breaks the old one
        assertEquals(Map.of("minecraft:stone", 1),
                BlockItems.remaining("minecraft:stone", Map.of(), "minecraft:stone", "minecraft:dirt", Map.of()).items());
        // facing differs, still the same block: the builder fixes orientation, no new item
        assertEquals(NONE, BlockItems.remaining("minecraft:oak_stairs", props("facing", "north"), "minecraft:oak_stairs",
                "minecraft:oak_stairs", props("facing", "east")).kind());
    }

    @Test
    void remainingPartialSlabsAndCandles() {
        assertEquals(Map.of("minecraft:oak_slab", 1), BlockItems.remaining("minecraft:oak_slab", props("type", "double"),
                "minecraft:oak_slab", "minecraft:oak_slab", props("type", "bottom")).items());
        assertEquals(NONE, BlockItems.remaining("minecraft:oak_slab", props("type", "double"),
                "minecraft:oak_slab", "minecraft:oak_slab", props("type", "double")).kind());
        assertEquals(Map.of("minecraft:candle", 2), BlockItems.remaining("minecraft:candle", props("candles", "4"),
                "minecraft:candle", "minecraft:candle", props("candles", "2")).items());
    }
}
