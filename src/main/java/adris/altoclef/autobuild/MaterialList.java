package adris.altoclef.autobuild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a schematic still needs, filled one cell at a time from {@link BlockItems#remaining}. Items keep the lowest
 * y they are needed at, so {@link #byFirstNeeded()} lists them in about the order the builder (bottom up) uses them.
 */
public final class MaterialList {

    private final Map<String, Integer> need = new HashMap<>();
    private final Map<String, Integer> firstY = new HashMap<>();
    /** Skip reason and block, e.g. "liquid: minecraft:water", to how many cells. */
    private final Map<String, Integer> skipped = new TreeMap<>();
    private int cells, placed, toPlace, toClear;

    /** One schematic cell. {@code current*} is what the world has there now. */
    public void addCell(int y, String blockId, Map<String, String> props, String asItemId,
                        String currentBlockId, Map<String, String> currentProps, boolean currentIsAir) {
        cells++;
        BlockItems.Need n = BlockItems.remaining(blockId, props, asItemId, currentBlockId, currentProps);
        switch (n.kind()) {
            case ITEM -> {
                toPlace++;
                if (!currentIsAir && !BlockItems.id(blockId).equals(BlockItems.id(currentBlockId))) toClear++;
                n.items().forEach((item, c) -> {
                    need.merge(item, c, Integer::sum);
                    firstY.merge(item, y, Math::min);
                });
            }
            case NONE -> {
                if (BlockItems.needFor(blockId, props, asItemId).kind() == BlockItems.Kind.NONE
                        && !currentIsAir && isAir(blockId)) {
                    toClear++; // air wanted, something there: the builder breaks it
                } else {
                    placed++;
                }
            }
            case SKIP -> skipped.merge(n.reason() + ": " + BlockItems.id(blockId), 1, Integer::sum);
        }
    }

    /** A cell not built on purpose, e.g. its item was {@code @build skip}ped. */
    public void addSkipped(String reason, String blockId) {
        cells++;
        skipped.merge(reason + ": " + BlockItems.id(blockId), 1, Integer::sum);
    }

    private static boolean isAir(String blockId) {
        String b = BlockItems.id(blockId);
        return b.equals("minecraft:air") || b.equals("minecraft:cave_air") || b.equals("minecraft:void_air");
    }

    /** Item id to count still to place, in first-needed order. */
    public Map<String, Integer> needed() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String item : byFirstNeeded()) out.put(item, need.get(item));
        return out;
    }

    /** Needed minus {@code have} (item id to count in the inventory), only what is still short. */
    public Map<String, Integer> missing(Map<String, Integer> have) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String item : byFirstNeeded()) {
            int short_ = need.get(item) - have.getOrDefault(item, 0);
            if (short_ > 0) out.put(item, short_);
        }
        return out;
    }

    /** Items by the lowest y they are needed at, then by id. */
    public List<String> byFirstNeeded() {
        List<String> items = new ArrayList<>(need.keySet());
        items.sort((a, b) -> {
            int c = Integer.compare(firstY.get(a), firstY.get(b));
            return c != 0 ? c : a.compareTo(b);
        });
        return items;
    }

    public Map<String, Integer> skipped() {
        return skipped;
    }

    public int cells() {
        return cells;
    }

    /** Cells that already hold what they should. */
    public int placed() {
        return placed;
    }

    /** Cells still to place a block in. */
    public int toPlace() {
        return toPlace;
    }

    /** Cells holding a wrong block the builder has to break first. */
    public int toClear() {
        return toClear;
    }

    public int totalItems() {
        return need.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean done() {
        return toPlace == 0 && toClear == 0;
    }
}
