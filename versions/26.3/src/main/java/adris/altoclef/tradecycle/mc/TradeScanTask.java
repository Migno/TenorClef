package adris.altoclef.tradecycle.mc;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tradecycle.TradeIndex;
import adris.altoclef.tradecycle.TradeStore;
import adris.altoclef.tradecycle.TradeView;
import adris.altoclef.tradecycle.VillagerRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** {@code @trades scan [radius]}: visit every loaded librarian in range, read its trades, merge into the index. */
class TradeScanTask extends Task {

    private final int radius;
    private final Deque<String> queue = new ArrayDeque<>();
    private final List<TradeIndex.Observation> found = new ArrayList<>();
    private Vec3 center;
    private ReadOffersTask current;
    private String currentId;
    private boolean done;
    private int total;

    TradeScanTask(int radius) {
        this.radius = radius;
    }

    @Override
    protected void onStart() {
        AltoClef mod = AltoClef.getInstance();
        center = mod.getPlayer().position();
        queue.clear();
        found.clear();
        for (Villager v : TradeWorld.librariansNear(radius)) queue.add(v.getUUID().toString());
        total = queue.size();
        done = false;
        mod.log("trades: scanning " + total + " librarian(s) within " + radius + " blocks");
    }

    @Override
    protected Task onTick() {
        if (done) return null;
        if (current != null && current.isFinished()) {
            record(current);
            current = null;
        }
        if (current == null) {
            currentId = queue.poll();
            if (currentId == null) {
                finishScan();
                return null;
            }
            current = new ReadOffersTask(currentId, false);
        }
        setDebugState("Scanning " + (total - queue.size()) + "/" + total);
        return current;
    }

    private void record(ReadOffersTask t) {
        AltoClef mod = AltoClef.getInstance();
        TradeStore store = TradeStore.get();
        Villager v = TradeWorld.villager(currentId);
        if (v == null) {
            mod.log("trades: " + currentId.substring(0, 8) + " " + (t.failure() == null ? "vanished" : t.failure()));
            return;
        }
        VillagerRecord prev = store.index().get(currentId);
        BlockPos lectern = TradeWorld.findLectern(v, prev, store.index(), store.config().lecternSearchRadius);
        TradeIndex.Observation o = TradeWorld.observe(v, t.menu(), lectern);
        found.add(o);
        VillagerRecord r = store.index().observe(o, System.currentTimeMillis());
        store.saveIndex();
        String books = r.books().isEmpty() ? "no book" : String.join(", ", r.books().stream().map(b -> b.shortLabel()).toList());
        mod.log("trades: " + r.shortId() + " " + r.status + " " + (t.failure() != null ? "(" + t.failure() + ")" : books)
                + " @ " + TradeView.pos(r));
    }

    private void finishScan() {
        done = true;
        TradeStore store = TradeStore.get();
        // Observations were merged one by one; this pass only marks the ones that weren't found as MISSING.
        TradeIndex.ScanSummary s = store.index().applyScan(found, center.x, center.y, center.z, radius, System.currentTimeMillis());
        store.saveIndex();
        AltoClef.getInstance().log("trades: scan done, " + s.updated() + " read, " + s.failed() + " failed, "
                + s.markedMissing() + " marked MISSING; " + TradeView.summary(store.config().wanted, store.index().all(), store.config().bestTolerance));
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected void onStop(Task interruptTask) {
        if (!done && interruptTask == null) {
            TradeStore.get().saveIndex();
            AltoClef.getInstance().log("trades: scan stopped after " + found.size() + "/" + total);
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof TradeScanTask t && t.radius == radius;
    }

    @Override
    protected String toDebugString() {
        return "Trade scan r=" + radius;
    }
}
