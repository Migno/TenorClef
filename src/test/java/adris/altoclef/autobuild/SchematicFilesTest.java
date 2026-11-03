package adris.altoclef.autobuild;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SchematicFilesTest {

    @Test
    void resolvesByNameExtensionCaseAndPrefix(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("House.litematic"), "x");
        Files.writeString(dir.resolve("tower.schem"), "x");
        Files.writeString(dir.resolve("tower_big.schematic"), "x");
        Files.writeString(dir.resolve("notes.txt"), "x");
        File d = dir.toFile();
        assertEquals("House.litematic", SchematicFiles.resolve(d, "House.litematic").getName());
        assertEquals("House.litematic", SchematicFiles.resolve(d, "House").getName());
        assertEquals("House.litematic", SchematicFiles.resolve(d, "house").getName());
        assertEquals("tower.schem", SchematicFiles.resolve(d, "tower").getName());
        assertEquals("tower_big.schematic", SchematicFiles.resolve(d, "tower_b").getName());
        assertNull(SchematicFiles.resolve(d, "to"), "two files start with it");
        assertNull(SchematicFiles.resolve(d, "notes.txt"));
        assertNull(SchematicFiles.resolve(d, "../escape"));
        assertEquals(3, SchematicFiles.list(d).size());
    }

    @Test
    void coordinates() {
        assertTrue(SchematicFiles.isCoord("12"));
        assertTrue(SchematicFiles.isCoord("-3"));
        assertTrue(SchematicFiles.isCoord("~"));
        assertTrue(SchematicFiles.isCoord("~-2"));
        assertFalse(SchematicFiles.isCoord("90x"));
        assertFalse(SchematicFiles.isCoord("lr"));
        assertEquals(12, SchematicFiles.coord("12", 100));
        assertEquals(100, SchematicFiles.coord("~", 100));
        assertEquals(105, SchematicFiles.coord("~5", 100));
        assertEquals(98, SchematicFiles.coord("~-2", 100));
    }
}
