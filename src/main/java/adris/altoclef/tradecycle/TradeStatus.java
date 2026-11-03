package adris.altoclef.tradecycle;

public enum TradeStatus {
    /** XP &gt; 0 or level &gt; novice: trades are fixed, the lectern must never be broken. */
    LOCKED,
    /** Novice with 0 XP: breaking the lectern rerolls the trades. */
    REROLLABLE,
    /** Not scanned yet, or the last scan failed. */
    UNKNOWN,
    /** Was indexed but not found on the last scan of its area. */
    MISSING;

    /** Status from the merchant screen's level/XP. Level is 1-based (1 = novice). */
    public static TradeStatus of(int level, int xp) {
        if (xp > 0 || level > 1) return LOCKED;
        if (level < 1) return UNKNOWN;
        return REROLLABLE;
    }
}
