package adris.altoclef.tradecycle;

import java.util.ArrayList;
import java.util.List;

/** {@code config/tenorclef/tradecycle.json}. */
public class TradeCycleConfig {

    public List<WantEntry> wanted = new ArrayList<>();
    /** Extra emeralds accepted over the vanilla minimum for {@code best} entries. */
    public int bestTolerance = 0;
    /** Allow a REROLLABLE villager to lock a cheaper copy of a book we already own above best price. */
    public boolean upgrade = false;
    /** Max lectern breaks per villager in one cycle run. */
    public int attemptCap = 500;
    /** Randomized pause between actions, milliseconds. */
    public int minDelayMs = 150;
    public int maxDelayMs = 450;
    /** Stop when the best axe has this much durability left or less. */
    public int axeMinDurability = 10;
    /** Give up waiting for the librarian profession after this many seconds. */
    public int professionTimeoutSec = 30;
    /** Overlay render distance in blocks. */
    public double overlayDistance = 96;
    /** Search radius for the lectern next to a villager. */
    public int lecternSearchRadius = 4;

    /** Fills missing/invalid values after Gson load. */
    public TradeCycleConfig sanitize() {
        if (wanted == null) wanted = new ArrayList<>();
        wanted.removeIf(w -> w == null || EnchantIds.normalize(w.enchantment) == null);
        for (WantEntry w : wanted) {
            w.enchantment = EnchantIds.normalize(w.enchantment);
            if (w.minLevel < 1) w.minLevel = 1;
        }
        if (bestTolerance < 0) bestTolerance = 0;
        if (attemptCap < 1) attemptCap = 500;
        if (minDelayMs < 0) minDelayMs = 0;
        if (maxDelayMs < minDelayMs) maxDelayMs = minDelayMs;
        if (axeMinDurability < 0) axeMinDurability = 0;
        if (professionTimeoutSec < 5) professionTimeoutSec = 5;
        if (overlayDistance <= 0) overlayDistance = 96;
        if (lecternSearchRadius < 1) lecternSearchRadius = 4;
        return this;
    }

    /** Adds or replaces the entry for this enchantment. */
    public void putWant(WantEntry w) {
        wanted.removeIf(e -> e.enchantment.equals(w.enchantment));
        wanted.add(w);
    }

    public boolean removeWant(String enchantment) {
        String id = EnchantIds.normalize(enchantment);
        return wanted.removeIf(e -> e.enchantment.equals(id));
    }
}
