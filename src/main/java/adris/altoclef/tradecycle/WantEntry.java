package adris.altoclef.tradecycle;

/** A wanted book: enchantment at {@code minLevel} or higher for at most {@code maxPrice} emeralds (base price). */
public class WantEntry {

    public String enchantment;
    public int minLevel = 1;
    public int maxPrice = BookPricing.MAX_EMERALDS;
    /** Created by {@code want ... best}: maxPrice is the vanilla minimum, the config tolerance is added on top. */
    public boolean best;

    public WantEntry() {
    }

    public WantEntry(String enchantment, int minLevel, int maxPrice) {
        this.enchantment = EnchantIds.normalize(enchantment);
        this.minLevel = Math.max(1, minLevel);
        this.maxPrice = maxPrice;
    }

    public static WantEntry best(String enchantment, int level, boolean doublePrice) {
        WantEntry w = new WantEntry(enchantment, level, BookPricing.minPrice(level, doublePrice));
        w.best = true;
        return w;
    }

    /** Price limit used by matching: maxPrice, plus the tolerance for best-price entries. */
    public int effectiveMaxPrice(int bestTolerance) {
        return best ? maxPrice + Math.max(0, bestTolerance) : maxPrice;
    }

    public String describe(int bestTolerance) {
        String s = EnchantIds.bookName(enchantment, minLevel) + (minLevel > 1 ? "+" : "") + " <= " +effectiveMaxPrice(bestTolerance) + " em";
        return best ? s + " (best" + (bestTolerance > 0 ? " +" + bestTolerance : "") + ")" : s;
    }
}
