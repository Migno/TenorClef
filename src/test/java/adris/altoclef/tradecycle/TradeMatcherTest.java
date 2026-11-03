package adris.altoclef.tradecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TradeMatcherTest {

    static TradeOffer offer(String id, int level, int price, boolean dbl) {
        TradeOffer.Raw r = TradeOfferParseTest.book(id, level, price, price);
        TradeOffer o = TradeOffer.parse(r);
        o.doublePrice = dbl;
        return o;
    }

    static VillagerRecord villager(String uuid, TradeStatus status, TradeOffer... offers) {
        VillagerRecord v = new VillagerRecord(uuid);
        v.status = status;
        v.level = status == TradeStatus.LOCKED ? 2 : 1;
        v.offers = new ArrayList<>(List.of(offers));
        return v;
    }

    @Test
    void levelPriceAndShortNames() {
        WantEntry w = new WantEntry("sharpness", 4, 20);
        assertTrue(TradeMatcher.matches(w, offer("minecraft:sharpness", 4, 20, false), 0));
        assertTrue(TradeMatcher.matches(w, offer("minecraft:sharpness", 5, 15, false), 0), "higher level is fine");
        assertFalse(TradeMatcher.matches(w, offer("minecraft:sharpness", 3, 10, false), 0), "level too low");
        assertFalse(TradeMatcher.matches(w, offer("minecraft:sharpness", 4, 21, false), 0), "too expensive");
        assertFalse(TradeMatcher.matches(w, offer("minecraft:smite", 5, 5, false), 0), "other enchantment");
    }

    @Test
    void toleranceOnlyAppliesToBestEntries() {
        WantEntry best = WantEntry.best("minecraft:mending", 1, true); // min 10
        assertEquals(10, best.maxPrice);
        assertFalse(TradeMatcher.matches(best, offer("minecraft:mending", 1, 11, true), 0));
        assertTrue(TradeMatcher.matches(best, offer("minecraft:mending", 1, 11, true), 1));
        WantEntry plain = new WantEntry("minecraft:mending", 1, 10);
        assertFalse(TradeMatcher.matches(plain, offer("minecraft:mending", 1, 11, true), 5), "tolerance ignored for plain wants");
    }

    @Test
    void treasureBestUsesDoubledMinimum() {
        WantEntry w = WantEntry.best("minecraft:frost_walker", 2, true);
        assertEquals(16, w.maxPrice);
        WantEntry s = WantEntry.best("minecraft:sharpness", 5, false);
        assertEquals(17, s.maxPrice);
    }

    @Test
    void unknownIdsNeverMatchOtherBooks() {
        WantEntry w = new WantEntry("othermod:thing", 1, 64);
        assertFalse(TradeMatcher.matches(w, offer("minecraft:mending", 1, 10, true), 0));
        assertTrue(TradeMatcher.matches(w, offer("othermod:thing", 1, 10, false), 0));
        assertFalse(TradeMatcher.matches(null, offer("minecraft:mending", 1, 10, true), 0));
        TradeOffer notBook = new TradeOffer();
        notBook.result = "minecraft:bookshelf";
        assertFalse(TradeMatcher.matches(w, notBook, 0));
    }

    @Test
    void ownedAndMissing() {
        List<VillagerRecord> idx = List.of(
                villager("a", TradeStatus.LOCKED, offer("minecraft:mending", 1, 15, true)),
                villager("b", TradeStatus.REROLLABLE, offer("minecraft:sharpness", 5, 17, false)),
                villager("c", TradeStatus.LOCKED, offer("minecraft:unbreaking", 3, 30, false)));
        assertEquals(2, TradeMatcher.owned(idx).size(), "only LOCKED villagers own books");
        List<WantEntry> wanted = List.of(new WantEntry("mending", 1, 20), new WantEntry("sharpness", 5, 64),
                new WantEntry("unbreaking", 3, 20));
        List<WantEntry> missing = TradeMatcher.missing(wanted, idx, 0);
        assertEquals(2, missing.size());
        assertEquals("minecraft:sharpness", missing.get(0).enchantment, "REROLLABLE offers don't count");
        assertEquals("minecraft:unbreaking", missing.get(1).enchantment, "owned at 30 but want <= 20");
    }

    @Test
    void lockTargetForMissingBook() {
        List<VillagerRecord> idx = List.of(villager("a", TradeStatus.LOCKED, offer("minecraft:mending", 1, 15, true)));
        List<WantEntry> wanted = List.of(new WantEntry("mending", 1, 20), new WantEntry("sharpness", 5, 30));
        List<TradeOffer> offers = List.of(offer("minecraft:sharpness", 5, 25, false));
        TradeMatcher.Match m = TradeMatcher.findLockTarget(offers, wanted, idx, 0, false);
        assertNotNull(m);
        assertEquals("minecraft:sharpness", m.want().enchantment);
        assertEquals(0, m.offerIndex());
        assertFalse(m.upgrade());
    }

    @Test
    void justLockedBookIsNoLongerATarget() {
        List<VillagerRecord> idx = new ArrayList<>();
        List<WantEntry> wanted = List.of(new WantEntry("mending", 1, 20));
        List<TradeOffer> offers = List.of(offer("minecraft:mending", 1, 12, true));
        assertNotNull(TradeMatcher.findLockTarget(offers, wanted, idx, 0, false));
        idx.add(villager("x", TradeStatus.LOCKED, offer("minecraft:mending", 1, 12, true)));
        assertNull(TradeMatcher.findLockTarget(offers, wanted, idx, 0, false));
        assertTrue(TradeMatcher.allDone(wanted, idx, 0, false));
    }

    @Test
    void upgradeLocksOnlyStrictlyCheaperCopy() {
        List<VillagerRecord> idx = List.of(villager("a", TradeStatus.LOCKED, offer("minecraft:mending", 1, 30, true)));
        List<WantEntry> wanted = List.of(new WantEntry("mending", 1, 64));
        List<TradeOffer> cheaper = List.of(offer("minecraft:mending", 1, 20, true));
        List<TradeOffer> same = List.of(offer("minecraft:mending", 1, 30, true));
        assertNull(TradeMatcher.findLockTarget(cheaper, wanted, idx, 0, false), "without upgrade the owned book is done");
        TradeMatcher.Match m = TradeMatcher.findLockTarget(cheaper, wanted, idx, 0, true);
        assertNotNull(m);
        assertTrue(m.upgrade());
        assertNull(TradeMatcher.findLockTarget(same, wanted, idx, 0, true));
        assertFalse(TradeMatcher.allDone(wanted, idx, 0, true), "30 is above the best price 10");
        assertTrue(TradeMatcher.allDone(wanted, idx, 20, true), "tolerance 20 makes 30 count as best");
    }

    @Test
    void upgradeIgnoresBooksAlreadyAtBest() {
        List<VillagerRecord> idx = List.of(villager("a", TradeStatus.LOCKED, offer("minecraft:mending", 1, 10, true)));
        List<WantEntry> wanted = List.of(new WantEntry("mending", 1, 64));
        assertNull(TradeMatcher.findLockTarget(List.of(offer("minecraft:mending", 1, 10, true)), wanted, idx, 0, true));
        assertTrue(TradeMatcher.allDone(wanted, idx, 0, true));
    }

    @Test
    void tintFollowsStatus() {
        List<WantEntry> wanted = List.of(new WantEntry("mending", 1, 20));
        assertEquals(TradeView.Tint.LOCKED_WANTED, TradeView.tint(villager("a", TradeStatus.LOCKED, offer("minecraft:mending", 1, 15, true)), wanted, 0));
        assertEquals(TradeView.Tint.LOCKED_OTHER, TradeView.tint(villager("b", TradeStatus.LOCKED, offer("minecraft:smite", 1, 5, false)), wanted, 0));
        assertEquals(TradeView.Tint.REROLLABLE, TradeView.tint(villager("c", TradeStatus.REROLLABLE), wanted, 0));
        assertEquals(TradeView.Tint.UNKNOWN, TradeView.tint(villager("d", TradeStatus.MISSING), wanted, 0));
        assertEquals(TradeView.Tint.UNKNOWN, TradeView.tint(villager("e", TradeStatus.UNKNOWN), wanted, 0));
    }

    @Test
    void labelsMarkBest() {
        VillagerRecord v = villager("a", TradeStatus.REROLLABLE, offer("minecraft:mending", 1, 10, true));
        v.lastScanned = 1;
        List<String> lines = TradeView.labelLines(v, 0);
        assertEquals("Mending I – 10 em  BEST", lines.get(0));
        assertEquals("[reroll] novice", lines.get(1));
    }
}
