package adris.altoclef.tradecycle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TradeIndexTest {

    static TradeIndex.Observation obs(String uuid, int x, int level, int xp, TradeOffer... offers) {
        return new TradeIndex.Observation(uuid, x, 64, 0).withOffers(level, xp, List.of(offers));
    }

    @Test
    void statusFromLevelAndXp() {
        assertEquals(TradeStatus.REROLLABLE, TradeStatus.of(1, 0));
        assertEquals(TradeStatus.LOCKED, TradeStatus.of(1, 3));
        assertEquals(TradeStatus.LOCKED, TradeStatus.of(2, 0));
        assertEquals(TradeStatus.UNKNOWN, TradeStatus.of(0, 0));
    }

    @Test
    void observeStoresEverything() {
        TradeIndex idx = new TradeIndex();
        TradeIndex.Observation o = obs("u1", 10, 1, 0, TradeMatcherTest.offer("minecraft:mending", 1, 12, true));
        o.lectern = new int[]{11, 64, 0};
        VillagerRecord r = idx.observe(o, 1000);
        assertEquals(TradeStatus.REROLLABLE, r.status);
        assertEquals(1000, r.lastScanned);
        assertArrayEquals(new int[]{11, 64, 0}, r.lectern);
        assertEquals(1, r.books().size());
    }

    @Test
    void lockedIsSticky() {
        TradeIndex idx = new TradeIndex();
        idx.observe(obs("u1", 0, 1, 5), 1);
        assertEquals(TradeStatus.LOCKED, idx.get("u1").status);
        // A bogus later read (novice, 0 xp) must not make it rerollable again.
        idx.observe(obs("u1", 0, 1, 0), 2);
        assertEquals(TradeStatus.LOCKED, idx.get("u1").status);
        // Failed read keeps LOCKED too.
        idx.observe(new TradeIndex.Observation("u1", 0, 64, 0), 3);
        assertEquals(TradeStatus.LOCKED, idx.get("u1").status);
    }

    @Test
    void failedReadIsUnknownUnlessLocked() {
        TradeIndex idx = new TradeIndex();
        idx.observe(obs("u1", 0, 1, 0), 1);
        idx.observe(new TradeIndex.Observation("u1", 0, 64, 0), 2);
        assertEquals(TradeStatus.UNKNOWN, idx.get("u1").status);
        VillagerRecord fresh = idx.observe(new TradeIndex.Observation("u2", 0, 64, 0), 2);
        assertEquals(TradeStatus.UNKNOWN, fresh.status);
        assertEquals(0, fresh.lastScanned);
    }

    @Test
    void rescanMarksMissingInsideRadiusOnly() {
        TradeIndex idx = new TradeIndex();
        idx.observe(obs("near", 5, 1, 0), 1);
        idx.observe(obs("locked", 6, 2, 10, TradeMatcherTest.offer("minecraft:mending", 1, 10, true)), 1);
        idx.observe(obs("far", 500, 1, 0), 1);
        idx.observe(obs("seen", 3, 1, 0), 1);

        TradeIndex.ScanSummary s = idx.applyScan(List.of(obs("seen", 4, 1, 0), obs("new", 2, 1, 0)), 0, 64, 0, 32, 10);
        assertEquals(2, s.updated());
        assertEquals(2, s.markedMissing());
        assertEquals(TradeStatus.MISSING, idx.get("near").status);
        assertEquals(TradeStatus.MISSING, idx.get("locked").status);
        assertEquals(1, idx.get("locked").books().size(), "missing villagers keep their data");
        assertEquals(TradeStatus.REROLLABLE, idx.get("far").status, "outside the scan radius: untouched");
        assertEquals(4, idx.get("seen").x, "position updated");
        assertNotNull(idx.get("new"));
        assertEquals(5, idx.villagers.size(), "nothing deleted");
    }

    @Test
    void missingVillagerComesBackWithOldStatus() {
        TradeIndex idx = new TradeIndex();
        idx.observe(obs("l", 0, 3, 50), 1);
        idx.applyScan(List.of(), 0, 64, 0, 10, 2);
        assertEquals(TradeStatus.MISSING, idx.get("l").status);
        assertTrue(idx.seen("l", 1, 64, 0, 3));
        assertEquals(TradeStatus.LOCKED, idx.get("l").status);
        // And a later novice-looking read can't unlock it.
        idx.applyScan(List.of(), 0, 64, 0, 10, 4);
        idx.observe(obs("l", 0, 1, 0), 5);
        assertEquals(TradeStatus.LOCKED, idx.get("l").status);
    }

    @Test
    void forget() {
        TradeIndex idx = new TradeIndex();
        idx.observe(obs("abc-1", 0, 1, 0), 1);
        idx.observe(obs("abd-2", 0, 1, 0), 1);
        assertEquals(1, idx.forget("ABC"));
        assertEquals(0, idx.forget("zzz"));
        assertEquals(1, idx.forget("all"));
        assertTrue(idx.villagers.isEmpty());
        assertEquals(0, idx.forget(""));
    }

    @Test
    void persistenceRoundTrip(@TempDir Path dir) throws Exception {
        TradeStore store = new TradeStore(dir);
        store.config().putWant(WantEntry.best("mending", 1, true));
        store.config().bestTolerance = 2;
        store.saveConfig();
        store.switchTo("mc.example.com:25565", "minecraft:overworld");
        store.index().observe(obs("u1", 7, 2, 1, TradeMatcherTest.offer("minecraft:mending", 1, 11, true)), 5);
        store.saveIndex();

        Path file = dir.resolve("trades").resolve("mc.example.com_25565").resolve("minecraft_overworld.json");
        assertTrue(Files.isRegularFile(file), "index written per server + dimension");
        assertTrue(Files.isRegularFile(dir.resolve("tradecycle.json")));

        TradeStore again = new TradeStore(dir);
        assertEquals(2, again.config().bestTolerance);
        assertEquals("minecraft:mending", again.config().wanted.get(0).enchantment);
        assertTrue(again.config().wanted.get(0).best);
        again.switchTo("mc.example.com:25565", "minecraft:overworld");
        VillagerRecord r = again.index().get("u1");
        assertEquals(TradeStatus.LOCKED, r.status);
        assertEquals(11, r.books().get(0).price());
        assertTrue(TradeMatcher.isOwned(again.config().wanted.get(0), again.index().all(), again.config().bestTolerance),
                "11 em is within best 10 + tolerance 2");

        again.switchTo("mc.example.com:25565", "minecraft:the_nether");
        assertTrue(again.index().villagers.isEmpty(), "other dimension has its own index");
    }

    @Test
    void sanitizeNames() {
        assertEquals("minecraft_the_nether", TradeStore.sanitize("minecraft:the_nether"));
        assertEquals("singleplayer_my_world", TradeStore.sanitize("singleplayer_My World"));
        assertEquals("unknown", TradeStore.sanitize(null));
        assertEquals("_.._x", TradeStore.sanitize("/../x"));
    }

    @Test
    void configSanitize() {
        TradeCycleConfig c = new TradeCycleConfig();
        c.wanted.add(null);
        WantEntry bad = new WantEntry();
        c.wanted.add(bad);
        WantEntry shortName = new WantEntry();
        shortName.enchantment = "Mending";
        shortName.minLevel = 0;
        c.wanted.add(shortName);
        c.maxDelayMs = -5;
        c.sanitize();
        assertEquals(1, c.wanted.size());
        assertEquals("minecraft:mending", c.wanted.get(0).enchantment);
        assertEquals(1, c.wanted.get(0).minLevel);
        assertTrue(c.maxDelayMs >= c.minDelayMs);
    }
}
