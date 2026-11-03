package adris.altoclef.tradecycle;

/**
 * Vanilla 26.3 librarian enchanted-book price range, taken from the game code:
 * <ul>
 *   <li>{@code data/minecraft/villager_trade/librarian/N/emerald_and_book_enchanted_book.json}: wants
 *       {@code minecraft:emerald} with count 0 plus {@code minecraft:book}, applies
 *       {@code enchant_randomly} over {@code #minecraft:tradeable} with {@code include_additional_cost_component},
 *       and lists {@code double_trade_price_enchantments: #minecraft:double_trade_price} (= {@code #minecraft:treasure}).</li>
 *   <li>{@code EnchantRandomlyFunction.enchantItem}: level = {@code Mth.nextInt(rand, minLevel, maxLevel)},
 *       {@code ADDITIONAL_TRADE_COST = 2 + rand.nextInt(5 + level * 10) + 3 * level}.</li>
 *   <li>{@code VillagerTrade.getOffer}: cost = wants.count (0) + additional cost, doubled when any stored
 *       enchantment is in the double-price set; {@code TradeCost.toItemCost} clamps to {@code [0, 64]}
 *       (emerald max stack size).</li>
 * </ul>
 * So the base emerald price lies in {@code [(2+3L)·m, (6+13L)·m]} clamped to 64, with m = 2 for treasure.
 * Prices here are the offer's <em>base</em> cost; reputation/demand only shift the special price.
 */
public final class BookPricing {

    public static final int MAX_EMERALDS = 64;

    private BookPricing() {
    }

    public static int multiplier(boolean doublePrice) {
        return doublePrice ? 2 : 1;
    }

    /** Lowest base price a librarian can roll for this book. */
    public static int minPrice(int level, boolean doublePrice) {
        int lvl = Math.max(1, level);
        return clamp((2 + 3 * lvl) * multiplier(doublePrice));
    }

    /** Highest base price a librarian can roll for this book (random part maxes at 4 + 10L). */
    public static int maxPrice(int level, boolean doublePrice) {
        int lvl = Math.max(1, level);
        return clamp((2 + (4 + 10 * lvl) + 3 * lvl) * multiplier(doublePrice));
    }

    /** True if {@code price} is at the best possible price, allowing {@code tolerance} extra emeralds. */
    public static boolean isBest(int price, int level, boolean doublePrice, int tolerance) {
        return price <= minPrice(level, doublePrice) + Math.max(0, tolerance);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(MAX_EMERALDS, v));
    }
}
