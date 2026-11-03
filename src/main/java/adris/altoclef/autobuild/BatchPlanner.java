package adris.altoclef.autobuild;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Picks the next gathering batch: as much of each missing item as fits, in the order given (first needed first),
 * using the room left in stacks the inventory already holds, then free slots.
 */
public final class BatchPlanner {

    /** Gather {@code amount} more of {@code item}. */
    public record Entry(String item, int amount) {
    }

    private BatchPlanner() {
    }

    /**
     * @param order      items in the order to gather them
     * @param missing    item id to count still short (after what the inventory holds)
     * @param have       item id to count in the inventory now
     * @param stackSize  item id to its max stack size (64 when absent)
     * @param freeSlots  empty inventory slots that may be filled
     */
    public static List<Entry> plan(List<String> order, Map<String, Integer> missing, Map<String, Integer> have,
                                   Map<String, Integer> stackSize, int freeSlots) {
        List<Entry> out = new ArrayList<>();
        int slots = Math.max(0, freeSlots);
        for (String item : order) {
            int want = missing.getOrDefault(item, 0);
            if (want <= 0) continue;
            int stack = Math.max(1, stackSize.getOrDefault(item, 64));
            int held = have.getOrDefault(item, 0);
            int room = held % stack == 0 ? 0 : stack - held % stack; // top up the last partial stack first
            int take = Math.min(want, room);
            int rest = want - take;
            int slotsFor = Math.min(slots, (rest + stack - 1) / stack);
            take += Math.min(rest, slotsFor * stack);
            slots -= slotsFor;
            if (take > 0) out.add(new Entry(item, take));
        }
        return out;
    }

    /** Total item count of a batch. */
    public static int total(List<Entry> batch) {
        return batch.stream().mapToInt(Entry::amount).sum();
    }
}
