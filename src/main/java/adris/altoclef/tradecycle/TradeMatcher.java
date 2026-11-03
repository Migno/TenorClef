package adris.altoclef.tradecycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Pure match logic. "Owned" means a book offer on a LOCKED villager; a want is satisfied when a LOCKED
 * villager sells that enchantment at {@code minLevel}+ for at most the want's effective max price.
 */
public final class TradeMatcher {

    private TradeMatcher() {
    }

    /** One book offer on a LOCKED villager. */
    public record Owned(VillagerRecord villager, TradeOffer offer) {
    }

    /** What a villager should be locked for. {@code upgrade} = we already own it, this one is cheaper. */
    public record Match(WantEntry want, TradeOffer offer, int offerIndex, boolean upgrade) {
    }

    /** Book, same enchantment id, level ≥ minLevel, base price ≤ effective max price. */
    public static boolean matches(WantEntry w, TradeOffer o, int bestTolerance) {
        return matchesIgnoringPrice(w, o) && o.price() <= w.effectiveMaxPrice(bestTolerance);
    }

    public static boolean matchesIgnoringPrice(WantEntry w, TradeOffer o) {
        if (w == null || o == null || !o.isBook() || w.enchantment == null) return false;
        return EnchantIds.normalize(w.enchantment).equals(o.enchantment) && o.level >= w.minLevel;
    }

    /** Every book offer on LOCKED villagers. */
    public static List<Owned> owned(Collection<VillagerRecord> records) {
        List<Owned> out = new ArrayList<>();
        for (VillagerRecord r : records) {
            if (r.status != TradeStatus.LOCKED) continue;
            for (TradeOffer o : r.books()) out.add(new Owned(r, o));
        }
        return out;
    }

    /** Cheapest LOCKED offer that satisfies {@code w} (price included), or null. */
    public static Owned bestOwned(WantEntry w, Collection<VillagerRecord> records, int bestTolerance) {
        Owned best = null;
        for (Owned o : owned(records)) {
            if (!matches(w, o.offer(), bestTolerance)) continue;
            if (best == null || o.offer().price() < best.offer().price()) best = o;
        }
        return best;
    }

    public static boolean isOwned(WantEntry w, Collection<VillagerRecord> records, int bestTolerance) {
        return bestOwned(w, records, bestTolerance) != null;
    }

    /** Wanted entries with no satisfying LOCKED offer. */
    public static List<WantEntry> missing(Collection<WantEntry> wanted, Collection<VillagerRecord> records, int bestTolerance) {
        List<WantEntry> out = new ArrayList<>();
        for (WantEntry w : wanted) if (!isOwned(w, records, bestTolerance)) out.add(w);
        return out;
    }

    /** True if an owned copy is above the vanilla best price (+ tolerance) for its level. */
    public static boolean ownedAboveBest(Owned o, int bestTolerance) {
        TradeOffer t = o.offer();
        return !BookPricing.isBest(t.price(), t.level, t.doublePrice, bestTolerance);
    }

    /**
     * Picks the offer to lock this villager for. A missing want always qualifies; with {@code upgrade},
     * a want we own above best price qualifies when this offer is strictly cheaper than the owned copy.
     * Wants are tried in config order, so the first wanted book wins if a villager sells two.
     */
    public static Match findLockTarget(List<TradeOffer> offers, Collection<WantEntry> wanted,
                                       Collection<VillagerRecord> records, int bestTolerance, boolean upgrade) {
        if (offers == null) return null;
        for (WantEntry w : wanted) {
            Owned have = bestOwned(w, records, bestTolerance);
            for (int i = 0; i < offers.size(); i++) {
                TradeOffer o = offers.get(i);
                if (!matches(w, o, bestTolerance)) continue;
                if (have == null) return new Match(w, o, i, false);
                if (upgrade && ownedAboveBest(have, bestTolerance) && o.price() < have.offer().price()) {
                    return new Match(w, o, i, true);
                }
            }
        }
        return null;
    }

    /** True once every want is satisfied and (with upgrade) nothing owned sits above best price. */
    public static boolean allDone(Collection<WantEntry> wanted, Collection<VillagerRecord> records, int bestTolerance, boolean upgrade) {
        for (WantEntry w : wanted) {
            Owned have = bestOwned(w, records, bestTolerance);
            if (have == null) return false;
            if (upgrade && ownedAboveBest(have, bestTolerance)) return false;
        }
        return true;
    }
}
