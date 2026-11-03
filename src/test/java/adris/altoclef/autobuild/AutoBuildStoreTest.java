package adris.altoclef.autobuild;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AutoBuildStoreTest {

    @Test
    void stateRoundTrips(@TempDir Path dir) {
        AutoBuildStore store = new AutoBuildStore(dir);
        assertNull(store.loadState());
        AutoBuildState s = new AutoBuildState();
        s.file = "house.litematic";
        s.origin = new int[]{10, 64, -5};
        s.rotation = "CLOCKWISE_90";
        s.mirror = "FRONT_BACK";
        s.skipItems = List.of("minecraft:dragon_egg");
        s.remaining = Map.of("minecraft:stone", 12);
        s.phase = "BUILD";
        store.saveState(s);
        AutoBuildState back = store.loadState();
        assertEquals("house.litematic", back.file);
        assertArrayEquals(new int[]{10, 64, -5}, back.origin);
        assertEquals("CLOCKWISE_90", back.rotation);
        assertEquals("FRONT_BACK", back.mirror);
        assertEquals(List.of("minecraft:dragon_egg"), back.skipItems);
        assertEquals(12, back.remaining.get("minecraft:stone"));
        assertEquals("BUILD", back.phase);
        assertTrue(back.updatedAt > 0);
        store.clearState();
        assertNull(store.loadState());
    }

    @Test
    void brokenStateFileIsIgnored(@TempDir Path dir) throws Exception {
        AutoBuildStore store = new AutoBuildStore(dir);
        Files.createDirectories(dir);
        Files.writeString(store.statePath(), "{ not json");
        assertNull(store.loadState());
    }

    @Test
    void configDefaultsAndSet(@TempDir Path dir) {
        AutoBuildStore store = new AutoBuildStore(dir);
        AutoBuildConfig c = store.config();
        assertFalse(c.clearArea);
        assertNull(c.set("clearArea", "on"));
        assertTrue(c.clearArea);
        assertNull(c.set("reserveSlots", "99"));
        assertEquals(30, c.reserveSlots);
        assertNotNull(c.set("reserveSlots", "many"));
        assertNotNull(c.set("nope", "1"));
        store.saveConfig();
        store.reloadConfig();
        assertTrue(store.config().clearArea);
    }

    @Test
    void rotationAndMirrorNames() {
        assertEquals("NONE", AutoBuildState.parseRotation("0"));
        assertEquals("CLOCKWISE_90", AutoBuildState.parseRotation("90"));
        assertEquals("CLOCKWISE_180", AutoBuildState.parseRotation("180"));
        assertEquals("COUNTERCLOCKWISE_90", AutoBuildState.parseRotation("ccw90"));
        assertNull(AutoBuildState.parseRotation("45"));
        assertEquals("LEFT_RIGHT", AutoBuildState.parseMirror("lr"));
        assertEquals("FRONT_BACK", AutoBuildState.parseMirror("front_back"));
        assertNull(AutoBuildState.parseMirror("diagonal"));
    }
}
