package adris.altoclef.tradecycle;

import java.util.Map;
import java.util.Objects;

/**
 * One merchant offer as stored in the index (plain fields so Gson can persist it).
 * For enchanted books {@link #enchantment}/{@link #level} hold the stored enchantment.
 */
public class TradeOffer {

    public String result;          // e.g. minecraft:enchanted_book
    public int resultCount = 1;
    public String enchantment;     // e.g. minecraft:mending, null for non-books
    public int level;
    /** True when the enchantment is in #minecraft:double_trade_price (treasure), as seen in the registry. */
    public boolean doublePrice;
    public String costA;           // e.g. minecraft:emerald
    public int baseCostA;          // base price (what rerolling decides)
    public int costACount;         // current price incl. special price / demand
    public int specialPriceDiff;
    public String costB;           // e.g. minecraft:book, null if none
    public int costBCount;
    public int uses;
    public int maxUses;

    public boolean isBook() {
        return enchantment != null && !enchantment.isEmpty();
    }

    /** Price compared against wants: the base emerald cost. */
    public int price() {
        return baseCostA;
    }

    /**
     * Raw offer data copied off a {@code MerchantOffer}, kept MC-free so parsing is testable.
     * {@code storedEnchantments} is the book's stored-enchantment map (id → level), may be null.
     */
    public static final class Raw {
        public String resultId;
        public int resultCount = 1;
        public Map<String, Integer> storedEnchantments;
        public java.util.Set<String> doublePriceIds;
        public String costAId;
        public int baseCostACount;
        public int costACount;
        public int specialPriceDiff;
        public String costBId;
        public int costBCount;
        public int uses;
        public int maxUses;
    }

    /**
     * Offer parsing. Books keep their highest-level stored enchantment (vanilla trades carry exactly one).
     * Unknown/modded ids are kept verbatim (normalized). Empty or air cost B is stored as null.
     */
    public static TradeOffer parse(Raw raw) {
        if (raw == null || raw.resultId == null) return null;
        TradeOffer o = new TradeOffer();
        o.result = raw.resultId;
        o.resultCount = raw.resultCount;
        o.costA = raw.costAId;
        o.baseCostA = raw.baseCostACount;
        o.costACount = raw.costACount;
        o.specialPriceDiff = raw.specialPriceDiff;
        boolean hasB = raw.costBId != null && raw.costBCount > 0 && !raw.costBId.equals("minecraft:air");
        o.costB = hasB ? raw.costBId : null;
        o.costBCount = hasB ? raw.costBCount : 0;
        o.uses = raw.uses;
        o.maxUses = raw.maxUses;
        if (raw.storedEnchantments != null && !raw.storedEnchantments.isEmpty()) {
            String best = null;
            int bestLevel = 0;
            for (Map.Entry<String, Integer> e : raw.storedEnchantments.entrySet()) {
                String id = EnchantIds.normalize(e.getKey());
                int lvl = e.getValue() == null ? 0 : e.getValue();
                if (id == null) continue;
                if (best == null || lvl > bestLevel || (lvl == bestLevel && id.compareTo(best) < 0)) {
                    best = id;
                    bestLevel = lvl;
                }
            }
            if (best != null) {
                o.enchantment = best;
                o.level = Math.max(1, bestLevel);
                o.doublePrice = raw.doublePriceIds != null && raw.doublePriceIds.contains(best);
            }
        }
        return o;
    }

    /** "Mending I – 12 em". */
    public String shortLabel() {
        if (!isBook()) return EnchantIds.displayName(result) + " – " + baseCostA + " em";
        return EnchantIds.bookName(enchantment, level) + " – " + baseCostA + " em";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TradeOffer t)) return false;
        return level == t.level && baseCostA == t.baseCostA && Objects.equals(result, t.result)
                && Objects.equals(enchantment, t.enchantment) && Objects.equals(costA, t.costA)
                && Objects.equals(costB, t.costB);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result, enchantment, level, baseCostA, costA, costB);
    }

    @Override
    public String toString() {
        return shortLabel();
    }
}
