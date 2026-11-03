package adris.altoclef.tradecycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Villager index for one server + dimension. Persisted as JSON by {@link TradeStore}. */
public class TradeIndex {

    public Map<String, VillagerRecord> villagers = new LinkedHashMap<>();

    /** Fresh data about one villager. {@code read} = the trade screen was opened and offers were read. */
    public static final class Observation {
        public String uuid;
        public int x, y, z;
        public int[] lectern;
        public boolean read;
        public int level;
        public int xp;
        public List<TradeOffer> offers;

        public Observation(String uuid, int x, int y, int z) {
            this.uuid = uuid;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Observation withOffers(int level, int xp, List<TradeOffer> offers) {
            this.read = true;
            this.level = level;
            this.xp = xp;
            this.offers = offers == null ? new ArrayList<>() : new ArrayList<>(offers);
            return this;
        }
    }

    public record ScanSummary(int updated, int failed, int markedMissing) {
    }

    public Collection<VillagerRecord> all() {
        return villagers.values();
    }

    public VillagerRecord get(String uuid) {
        return villagers.get(uuid);
    }

    /**
     * Merges one observation. LOCKED is sticky: vanilla never unlocks a villager, so a later read can't
     * turn a LOCKED villager back into a rerollable one (protects its lectern).
     */
    public VillagerRecord observe(Observation obs, long now) {
        VillagerRecord existing = villagers.get(obs.uuid);
        boolean wasLocked = existing != null && (existing.status == TradeStatus.LOCKED || existing.level > 1 || existing.xp > 0);
        VillagerRecord r = existing != null ? existing : new VillagerRecord(obs.uuid);
        villagers.put(obs.uuid, r);
        r.x = obs.x;
        r.y = obs.y;
        r.z = obs.z;
        r.lastSeen = now;
        if (obs.lectern != null) r.lectern = obs.lectern.clone();
        if (obs.read) {
            r.level = obs.level;
            r.xp = obs.xp;
            r.offers = new ArrayList<>(obs.offers);
            r.lastScanned = now;
            TradeStatus s = TradeStatus.of(obs.level, obs.xp);
            r.status = wasLocked ? TradeStatus.LOCKED : s;
        } else {
            r.status = wasLocked ? TradeStatus.LOCKED : TradeStatus.UNKNOWN;
        }
        return r;
    }

    /** Position-only update when a villager is seen without reading its trades. */
    public boolean seen(String uuid, int x, int y, int z, long now) {
        VillagerRecord r = villagers.get(uuid);
        if (r == null) return false;
        boolean moved = r.x != x || r.y != y || r.z != z;
        r.x = x;
        r.y = y;
        r.z = z;
        r.lastSeen = now;
        if (r.status == TradeStatus.MISSING) {
            r.status = r.lastScanned > 0 ? TradeStatus.of(r.level, r.xp) : TradeStatus.UNKNOWN;
            return true;
        }
        return moved;
    }

    /**
     * Index merge on rescan: every found villager is merged, and indexed villagers whose last position is
     * within {@code radius} of the scan centre but weren't found become MISSING (kept, never deleted).
     */
    public ScanSummary applyScan(List<Observation> found, double cx, double cy, double cz, double radius, long now) {
        Set<String> seenIds = new HashSet<>();
        int updated = 0, failed = 0;
        for (Observation o : found) {
            seenIds.add(o.uuid);
            observe(o, now);
            if (o.read) updated++;
            else failed++;
        }
        int missing = 0;
        double r2 = radius * radius;
        for (VillagerRecord r : villagers.values()) {
            if (seenIds.contains(r.uuid) || r.status == TradeStatus.MISSING) continue;
            if (r.distanceSq(cx, cy, cz) <= r2) {
                r.status = TradeStatus.MISSING;
                missing++;
            }
        }
        return new ScanSummary(updated, failed, missing);
    }

    /** Removes villagers whose UUID starts with {@code prefix} (or all with "all"). Returns how many. */
    public int forget(String prefix) {
        if (prefix == null || prefix.isBlank()) return 0;
        if (prefix.equalsIgnoreCase("all")) {
            int n = villagers.size();
            villagers.clear();
            return n;
        }
        String p = prefix.toLowerCase();
        List<String> drop = new ArrayList<>();
        for (String k : villagers.keySet()) if (k.toLowerCase().startsWith(p)) drop.add(k);
        drop.forEach(villagers::remove);
        return drop.size();
    }

    public List<VillagerRecord> byStatus(TradeStatus status) {
        List<VillagerRecord> out = new ArrayList<>();
        for (VillagerRecord r : villagers.values()) if (r.status == status) out.add(r);
        return out;
    }
}
