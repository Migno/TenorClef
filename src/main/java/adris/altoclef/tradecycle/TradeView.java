package adris.altoclef.tradecycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** MC-free presentation helpers shared by the chat table, the overlay and the index screen. */
public final class TradeView {

    private TradeView() {
    }

    public enum Tint {
        LOCKED_WANTED(0xFF3FD35A), LOCKED_OTHER(0xFF3F8CFF), REROLLABLE(0xFFFFD23F), UNKNOWN(0xFF9A9A9A);

        public final int argb;

        Tint(int argb) {
            this.argb = argb;
        }
    }

    /** Green = LOCKED with a wanted book, blue = LOCKED other, yellow = REROLLABLE, grey = UNKNOWN/MISSING. */
    public static Tint tint(VillagerRecord r, Collection<WantEntry> wanted, int bestTolerance) {
        switch (r.status) {
            case LOCKED -> {
                for (TradeOffer o : r.books())
                    for (WantEntry w : wanted)
                        if (TradeMatcher.matches(w, o, bestTolerance)) return Tint.LOCKED_WANTED;
                return Tint.LOCKED_OTHER;
            }
            case REROLLABLE -> {
                return Tint.REROLLABLE;
            }
            default -> {
                return Tint.UNKNOWN;
            }
        }
    }

    public static boolean isBest(TradeOffer o, int bestTolerance) {
        return o.isBook() && BookPricing.isBest(o.price(), o.level, o.doublePrice, bestTolerance);
    }

    /** Overlay label lines: one per book ("Mending I – 12 em BEST"), then a status line. */
    public static List<String> labelLines(VillagerRecord r, int bestTolerance) {
        List<String> lines = new ArrayList<>();
        for (TradeOffer o : r.books()) lines.add(o.shortLabel() + (isBest(o, bestTolerance) ? "  BEST" : ""));
        if (lines.isEmpty() && r.lastScanned > 0) lines.add("no book");
        lines.add(statusText(r));
        return lines;
    }

    public static String statusText(VillagerRecord r) {
        return switch (r.status) {
            case LOCKED -> "[LOCKED] lvl " + r.level + (r.xp > 0 ? " xp " + r.xp : "");
            case REROLLABLE -> "[reroll] novice";
            case MISSING -> "[missing]";
            default -> "[unknown]";
        };
    }

    /** One table row per villager book (or one per villager with no book). */
    public record Row(VillagerRecord villager, TradeOffer book, double distance) {
        public String enchantKey() {
            return book == null ? "~" : book.enchantment + "#" + (100 - book.level);
        }

        public int priceKey() {
            return book == null ? Integer.MAX_VALUE : book.price();
        }
    }

    public enum Sort {ENCHANTMENT, PRICE, DISTANCE, STATUS}

    public static List<Row> rows(Collection<VillagerRecord> records, double px, double py, double pz, Sort sort) {
        List<Row> rows = new ArrayList<>();
        for (VillagerRecord r : records) {
            double d = Math.sqrt(r.distanceSq(px, py, pz));
            List<TradeOffer> books = r.books();
            if (books.isEmpty()) rows.add(new Row(r, null, d));
            else for (TradeOffer b : books) rows.add(new Row(r, b, d));
        }
        Comparator<Row> byDist = Comparator.comparingDouble(Row::distance);
        Comparator<Row> c = switch (sort) {
            case ENCHANTMENT -> Comparator.comparing(Row::enchantKey).thenComparingInt(Row::priceKey).thenComparing(byDist);
            case PRICE -> Comparator.comparingInt(Row::priceKey).thenComparing(Row::enchantKey).thenComparing(byDist);
            case DISTANCE -> byDist.thenComparing(Row::enchantKey);
            case STATUS -> Comparator.<Row>comparingInt(x -> x.villager().status.ordinal()).thenComparing(Row::enchantKey).thenComparing(byDist);
        };
        rows.sort(c);
        return rows;
    }

    /** "owned 3 / wanted 5 / missing 2". */
    public static String summary(Collection<WantEntry> wanted, Collection<VillagerRecord> records, int bestTolerance) {
        int owned = TradeMatcher.owned(records).size();
        int missing = TradeMatcher.missing(wanted, records, bestTolerance).size();
        return "owned " + owned + " / wanted " + wanted.size() + " / missing " + missing;
    }

    public static String pos(VillagerRecord r) {
        return r.x + " " + r.y + " " + r.z;
    }
}
