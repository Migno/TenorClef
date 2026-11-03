package adris.altoclef.tradecycle;

import java.util.ArrayList;
import java.util.List;

/** One librarian in the index, keyed by entity UUID. */
public class VillagerRecord {

    public String uuid;
    public int x, y, z;
    /** Linked lectern, null if none was found next to the villager. */
    public int[] lectern;
    public int level;
    public int xp;
    public List<TradeOffer> offers = new ArrayList<>();
    public TradeStatus status = TradeStatus.UNKNOWN;
    /** Epoch millis of the last successful offer read; 0 = never. */
    public long lastScanned;
    /** Epoch millis the entity was last seen at all. */
    public long lastSeen;
    /** Total reroll attempts the cycle has spent on this villager. */
    public int attempts;

    public VillagerRecord() {
    }

    public VillagerRecord(String uuid) {
        this.uuid = uuid;
    }

    public List<TradeOffer> books() {
        List<TradeOffer> out = new ArrayList<>();
        if (offers != null) for (TradeOffer o : offers) if (o != null && o.isBook()) out.add(o);
        return out;
    }

    public double distanceSq(double px, double py, double pz) {
        double dx = x + 0.5 - px, dy = y - py, dz = z + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    public String shortId() {
        return uuid == null ? "?" : uuid.substring(0, Math.min(8, uuid.length()));
    }

    public VillagerRecord copy() {
        VillagerRecord r = new VillagerRecord(uuid);
        r.x = x;
        r.y = y;
        r.z = z;
        r.lectern = lectern == null ? null : lectern.clone();
        r.level = level;
        r.xp = xp;
        r.offers = offers == null ? new ArrayList<>() : new ArrayList<>(offers);
        r.status = status;
        r.lastScanned = lastScanned;
        r.lastSeen = lastSeen;
        r.attempts = attempts;
        return r;
    }
}
