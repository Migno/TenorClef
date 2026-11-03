package adris.altoclef.autobuild;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BatchPlannerTest {

    private static final String STONE = "minecraft:stone", GLASS = "minecraft:glass", PEARL = "minecraft:ender_pearl",
            DOOR = "minecraft:oak_door";

    @Test
    void everythingFits() {
        List<BatchPlanner.Entry> b = BatchPlanner.plan(List.of(STONE, GLASS), Map.of(STONE, 100, GLASS, 10), Map.of(),
                Map.of(), 10);
        assertEquals(List.of(new BatchPlanner.Entry(STONE, 100), new BatchPlanner.Entry(GLASS, 10)), b);
    }

    @Test
    void firstNeededGetsTheSlotsFirst() {
        // 3 slots: stone wants 5 stacks, gets 3, glass gets nothing
        List<BatchPlanner.Entry> b = BatchPlanner.plan(List.of(STONE, GLASS), Map.of(STONE, 320, GLASS, 10), Map.of(),
                Map.of(), 3);
        assertEquals(List.of(new BatchPlanner.Entry(STONE, 192)), b);
    }

    @Test
    void partialStacksAreToppedUpWithoutASlot() {
        // 40 stone held: 24 more fit in that stack, then 1 free slot for glass
        List<BatchPlanner.Entry> b = BatchPlanner.plan(List.of(STONE, GLASS), Map.of(STONE, 20, GLASS, 30),
                Map.of(STONE, 40), Map.of(), 1);
        assertEquals(List.of(new BatchPlanner.Entry(STONE, 20), new BatchPlanner.Entry(GLASS, 30)), b);
    }

    @Test
    void noFreeSlotsStillTopsUp() {
        List<BatchPlanner.Entry> b = BatchPlanner.plan(List.of(STONE, GLASS), Map.of(STONE, 100, GLASS, 5),
                Map.of(STONE, 60), Map.of(), 0);
        assertEquals(List.of(new BatchPlanner.Entry(STONE, 4)), b);
    }

    @Test
    void smallStackSizes() {
        // pearls stack to 16
        List<BatchPlanner.Entry> b = BatchPlanner.plan(List.of(PEARL, DOOR), Map.of(PEARL, 40, DOOR, 3), Map.of(),
                Map.of(PEARL, 16, DOOR, 64), 3);
        assertEquals(List.of(new BatchPlanner.Entry(PEARL, 40)), b); // 3 slots: 16+16+8
        b = BatchPlanner.plan(List.of(PEARL, DOOR), Map.of(PEARL, 40, DOOR, 3), Map.of(), Map.of(PEARL, 16, DOOR, 64), 4);
        assertEquals(List.of(new BatchPlanner.Entry(PEARL, 40), new BatchPlanner.Entry(DOOR, 3)), b);
    }

    @Test
    void nothingMissingNothingPlanned() {
        assertTrue(BatchPlanner.plan(List.of(STONE), Map.of(), Map.of(), Map.of(), 10).isEmpty());
        assertTrue(BatchPlanner.plan(List.of(STONE), Map.of(STONE, 0), Map.of(), Map.of(), 10).isEmpty());
    }

    @Test
    void total() {
        assertEquals(15, BatchPlanner.total(List.of(new BatchPlanner.Entry(STONE, 10), new BatchPlanner.Entry(GLASS, 5))));
    }
}
