package adris.altoclef.tradecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BookPricingTest {

    @Test
    void rangeMatchesVanillaFormula() {
        // 2 + nextInt(5 + 10L) + 3L
        assertEquals(5, BookPricing.minPrice(1, false));
        assertEquals(19, BookPricing.maxPrice(1, false));
        assertEquals(8, BookPricing.minPrice(2, false));
        assertEquals(32, BookPricing.maxPrice(2, false));
        assertEquals(11, BookPricing.minPrice(3, false));
        assertEquals(45, BookPricing.maxPrice(3, false));
        assertEquals(14, BookPricing.minPrice(4, false));
        assertEquals(58, BookPricing.maxPrice(4, false));
        assertEquals(17, BookPricing.minPrice(5, false));
        assertEquals(64, BookPricing.maxPrice(5, false), "71 clamps to the emerald stack size");
    }

    @Test
    void treasureDoublesBeforeClamp() {
        assertEquals(10, BookPricing.minPrice(1, true), "Mending I best is 10");
        assertEquals(38, BookPricing.maxPrice(1, true));
        assertEquals(16, BookPricing.minPrice(2, true), "Frost Walker II");
        assertEquals(22, BookPricing.minPrice(3, true), "Soul Speed III");
        assertEquals(64, BookPricing.maxPrice(3, true));
    }

    @Test
    void bestWithTolerance() {
        assertTrue(BookPricing.isBest(5, 1, false, 0));
        assertFalse(BookPricing.isBest(6, 1, false, 0));
        assertTrue(BookPricing.isBest(7, 1, false, 2));
        assertFalse(BookPricing.isBest(13, 1, true, 2));
        assertTrue(BookPricing.isBest(12, 1, true, 2));
        assertTrue(BookPricing.isBest(5, 1, false, -3), "negative tolerance is treated as 0");
    }

    @Test
    void levelBelowOneTreatedAsOne() {
        assertEquals(BookPricing.minPrice(1, false), BookPricing.minPrice(0, false));
    }
}
