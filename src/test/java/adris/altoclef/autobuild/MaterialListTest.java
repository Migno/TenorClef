package adris.altoclef.autobuild;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MaterialListTest {

    private static final Map<String, String> NO = Map.of();

    private static void cell(MaterialList m, int y, String want, String current) {
        m.addCell(y, want, NO, want, current, NO, current.endsWith(":air"));
    }

    @Test
    void countsOnlyWhatIsStillToPlace() {
        MaterialList m = new MaterialList();
        cell(m, 0, "minecraft:stone", "minecraft:air");
        cell(m, 0, "minecraft:stone", "minecraft:stone"); // already there
        cell(m, 1, "minecraft:stone", "minecraft:dirt");  // wrong block, broken then placed
        cell(m, 1, "minecraft:air", "minecraft:air");
        cell(m, 2, "minecraft:air", "minecraft:grass_block"); // to clear, needs no item
        assertEquals(Map.of("minecraft:stone", 2), m.needed());
        assertEquals(5, m.cells());
        assertEquals(2, m.placed());
        assertEquals(2, m.toPlace());
        assertEquals(2, m.toClear());
        assertFalse(m.done());
    }

    @Test
    void orderIsLowestLayerFirst() {
        MaterialList m = new MaterialList();
        cell(m, 5, "minecraft:glass", "minecraft:air");
        cell(m, 0, "minecraft:cobblestone", "minecraft:air");
        cell(m, 2, "minecraft:oak_planks", "minecraft:air");
        cell(m, 1, "minecraft:glass", "minecraft:air");
        assertEquals(List.of("minecraft:cobblestone", "minecraft:glass", "minecraft:oak_planks"), m.byFirstNeeded());
    }

    @Test
    void missingSubtractsInventory() {
        MaterialList m = new MaterialList();
        for (int i = 0; i < 10; i++) cell(m, 0, "minecraft:stone", "minecraft:air");
        for (int i = 0; i < 3; i++) cell(m, 1, "minecraft:glass", "minecraft:air");
        Map<String, Integer> missing = m.missing(Map.of("minecraft:stone", 4, "minecraft:glass", 7));
        assertEquals(Map.of("minecraft:stone", 6), missing);
    }

    @Test
    void skippedCellsAreReported() {
        MaterialList m = new MaterialList();
        m.addCell(0, "minecraft:water", Map.of("level", "0"), "minecraft:air", "minecraft:air", NO, true);
        m.addCell(0, "minecraft:water", Map.of("level", "0"), "minecraft:air", "minecraft:air", NO, true);
        assertEquals(Map.of("liquid: minecraft:water", 2), m.skipped());
        assertTrue(m.needed().isEmpty());
        assertTrue(m.done());
    }

    @Test
    void emptyIsDone() {
        assertTrue(new MaterialList().done());
    }
}
