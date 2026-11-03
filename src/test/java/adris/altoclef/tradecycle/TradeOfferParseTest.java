package adris.altoclef.tradecycle;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TradeOfferParseTest {

    static TradeOffer.Raw book(String id, int level, int base, int current) {
        TradeOffer.Raw r = new TradeOffer.Raw();
        r.resultId = "minecraft:enchanted_book";
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put(id, level);
        r.storedEnchantments = m;
        r.doublePriceIds = Set.of("minecraft:mending", "minecraft:frost_walker");
        r.costAId = "minecraft:emerald";
        r.baseCostACount = base;
        r.costACount = current;
        r.costBId = "minecraft:book";
        r.costBCount = 1;
        r.maxUses = 12;
        return r;
    }

    @Test
    void parsesBook() {
        TradeOffer o = TradeOffer.parse(book("minecraft:mending", 1, 12, 9));
        assertTrue(o.isBook());
        assertEquals("minecraft:mending", o.enchantment);
        assertEquals(1, o.level);
        assertTrue(o.doublePrice);
        assertEquals(12, o.price(), "matching uses the base price, not the discounted one");
        assertEquals(9, o.costACount);
        assertEquals("minecraft:book", o.costB);
        assertEquals("Mending I – 12 em", o.shortLabel());
    }

    @Test
    void nonTreasureIsNotDoubled() {
        TradeOffer o = TradeOffer.parse(book("minecraft:sharpness", 5, 30, 30));
        assertFalse(o.doublePrice);
        assertEquals("Sharpness V – 30 em", o.shortLabel());
    }

    @Test
    void nonBookOffer() {
        TradeOffer.Raw r = new TradeOffer.Raw();
        r.resultId = "minecraft:bookshelf";
        r.costAId = "minecraft:emerald";
        r.baseCostACount = 9;
        r.costBId = "minecraft:air";
        r.costBCount = 0;
        TradeOffer o = TradeOffer.parse(r);
        assertFalse(o.isBook());
        assertNull(o.enchantment);
        assertNull(o.costB, "air cost B is dropped");
    }

    @Test
    void multipleEnchantmentsKeepHighestLevel() {
        TradeOffer.Raw r = book("minecraft:unbreaking", 1, 10, 10);
        r.storedEnchantments.put("minecraft:efficiency", 4);
        TradeOffer o = TradeOffer.parse(r);
        assertEquals("minecraft:efficiency", o.enchantment);
        assertEquals(4, o.level);
    }

    @Test
    void unknownIdsAreKept() {
        TradeOffer o = TradeOffer.parse(book("SomeMod:Vein_Miner", 2, 20, 20));
        assertEquals("somemod:vein_miner", o.enchantment);
        assertFalse(o.doublePrice);
    }

    @Test
    void nullInputs() {
        assertNull(TradeOffer.parse(null));
        assertNull(TradeOffer.parse(new TradeOffer.Raw()));
    }

    @Test
    void enchantIdNormalization() {
        assertEquals("minecraft:mending", EnchantIds.normalize("Mending"));
        assertEquals("minecraft:fire_aspect", EnchantIds.normalize("fire aspect"));
        assertEquals("minecraft:fire_aspect", EnchantIds.normalize("Fire-Aspect"));
        assertEquals("minecraft:mending", EnchantIds.normalize("minecraft:mending"));
        assertEquals("mymod:thing", EnchantIds.normalize("mymod:thing"));
        assertNull(EnchantIds.normalize("  "));
        assertNull(EnchantIds.normalize(":x"));
        assertEquals("Bane Of Arthropods", EnchantIds.displayName("minecraft:bane_of_arthropods"));
        assertEquals("IV", EnchantIds.roman(4));
        assertEquals("12", EnchantIds.roman(12));
    }
}
