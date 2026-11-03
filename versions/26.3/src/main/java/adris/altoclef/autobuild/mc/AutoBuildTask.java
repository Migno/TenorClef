package adris.altoclef.autobuild.mc;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.autobuild.AutoBuildConfig;
import adris.altoclef.autobuild.AutoBuildState;
import adris.altoclef.autobuild.AutoBuildStore;
import adris.altoclef.autobuild.BatchPlanner;
import adris.altoclef.autobuild.MaterialList;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import baritone.api.process.IBuilderProcess;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code @build}: scan the schematic against the world, gather what is missing in batches that fit the inventory
 * ({@code @get}'s catalogue, taking from known chests first), let Ostinato's builder place it, and repeat until
 * nothing is left. Items the catalogue cannot get are asked for in chat and their blocks are left out of the build
 * until they turn up in the inventory.
 * <p>
 * The builder is stopped (not paused) between batches: a paused builder still holds Baritone, so gathering could
 * not path. Each start re-scans the world, so placed blocks are never redone.
 */
class AutoBuildTask extends Task {

    enum Phase {CLEAR, PLAN, GATHER, BUILD, WAIT_ITEMS, DONE}

    /** Builder rounds in a row that placed nothing before giving up on what is left. */
    private static final int MAX_IDLE_ROUNDS = 3;
    /** In BUILD, re-scan this often to see whether the builder is getting anywhere. */
    private static final int PROGRESS_SCAN_TICKS = 20 * 60;

    final AutoBuildState state;
    private BuildTarget target;
    private Phase phase;
    private int ticks;
    private int phaseStart;

    private MaterialList scan;
    private final Map<String, String> manualWhy = new LinkedHashMap<>();
    private final Set<String> stalled = new HashSet<>();
    private final Set<String> announced = new HashSet<>();
    private int lastAskTick = -1;

    private List<BatchPlanner.Entry> batch = List.of();
    private int batchIdx;
    private int gatherTarget;
    private int gatherBest;
    private int gatherBestAt;
    private Task gatherTask;

    private boolean builderStarted;
    private int lastWork = -1;
    private int lastProgressScan;
    private int idleRounds;

    private boolean replan;
    boolean cancelled;

    AutoBuildTask(AutoBuildState state) {
        this.state = state;
    }

    private static AutoBuildStore store() {
        return AutoBuildStore.get();
    }

    private static AutoBuildConfig cfg() {
        return store().config();
    }

    private static void log(String s) {
        AltoClef.getInstance().log("build: " + s);
    }

    private IBuilderProcess builder() {
        return AltoClef.getInstance().getClientBaritone().getBuilderProcess();
    }

    @Override
    protected void onStart() {
        AutoBuildMc.current = this;
        AltoClef mod = AltoClef.getInstance();
        mod.getBehaviour().push();
        try {
            target = BuildTarget.load(state);
        } catch (BuildTarget.LoadException e) {
            end("cannot load " + state.label() + ": " + e.getMessage(), true);
            return;
        }
        // gathering (mining stone, pillaring) must not take apart or wall up the build; the builder itself may
        mod.getBehaviour().avoidBlockBreaking(this::guarded);
        mod.getBehaviour().avoidBlockPlacing(this::guarded);
        state.paused = false;
        if (state.startedAt == 0) state.startedAt = System.currentTimeMillis();
        log("building " + state.label() + " (" + target.size() + ") at " + target.min().toShortString()
                + (state.litematica ? "" : ", rotation " + state.rotation + ", mirror " + state.mirror));
        go(cfg().clearArea && !state.cleared ? Phase.CLEAR : Phase.PLAN);
    }

    private boolean guarded(net.minecraft.core.BlockPos p) {
        BuildTarget t = target;
        Phase ph = phase;
        if (t == null || ph == Phase.BUILD || ph == Phase.CLEAR) return false;
        var lo = t.min();
        var hi = t.max();
        return p.getX() >= lo.getX() && p.getX() <= hi.getX() && p.getY() >= lo.getY() && p.getY() <= hi.getY()
                && p.getZ() >= lo.getZ() && p.getZ() <= hi.getZ();
    }

    private void go(Phase p) {
        phase = p;
        phaseStart = ticks;
        state.phase = p.name();
        store().saveState(state);
    }

    @Override
    protected Task onTick() {
        ticks++;
        if (phase == Phase.DONE || AltoClef.getInstance().getPlayer() == null) return null;
        if (replan && phase != Phase.CLEAR) {
            replan = false;
            stopBuilder();
            gatherTask = null;
            go(Phase.PLAN);
        }
        return switch (phase) {
            case CLEAR -> clear();
            case PLAN -> plan();
            case GATHER -> gather();
            case BUILD -> build();
            case WAIT_ITEMS -> waitItems();
            case DONE -> null;
        };
    }

    /** {@code @build skip} or new items handed over: plan again on the next tick. */
    void requestReplan() {
        replan = true;
    }

    // ------------------------------------------------------------------ clear

    private Task clear() {
        IBuilderProcess b = builder();
        if (!builderStarted) {
            log("clearing " + target.min().toShortString() + " .. " + target.max().toShortString());
            target.startClear(b);
            builderStarted = true;
            return null;
        }
        if (b.isActive() && !b.isPaused()) {
            setDebugState("Clearing the area");
            return null;
        }
        if (b.isPaused()) log("could not clear everything, building anyway");
        stopBuilder();
        state.cleared = true;
        go(Phase.PLAN);
        return null;
    }

    // ------------------------------------------------------------------ plan

    private Task plan() {
        AltoClef mod = AltoClef.getInstance();
        scan = target.scan(mod.getWorld(), new HashSet<>(state.skipItems));
        Map<String, Integer> needed = scan.needed();
        state.remaining = new LinkedHashMap<>(needed);
        if (scan.done()) {
            end("finished " + state.label(), false);
            return null;
        }
        protect(needed.keySet());
        Map<String, Integer> have = McItems.have(mod, needed.keySet());
        Map<String, Integer> missing = scan.missing(have);

        Map<String, Integer> auto = new LinkedHashMap<>();
        Map<String, Integer> manual = new LinkedHashMap<>();
        manualWhy.clear();
        for (Map.Entry<String, Integer> e : missing.entrySet()) {
            Item item = McItems.item(e.getKey());
            String why = stalled.contains(e.getKey()) ? "gathering it got stuck" : McItems.whyNotAuto(item, cfg());
            if (why == null) {
                auto.put(e.getKey(), e.getValue());
            } else {
                manual.put(e.getKey(), e.getValue());
                manualWhy.put(e.getKey(), why);
            }
        }
        state.manual = manual;
        askForManual(false);

        int free = Math.max(0, McItems.freeSlots(mod.getPlayer()) - cfg().reserveSlots);
        batch = BatchPlanner.plan(new ArrayList<>(auto.keySet()), auto, have, McItems.stackSizes(auto.keySet()), free);
        boolean canPlace = needed.keySet().stream().anyMatch(i -> have.getOrDefault(i, 0) > 0) || scan.toClear() > 0;
        log("left: " + scan.toPlace() + " to place, " + scan.toClear() + " to clear, " + scan.placed() + " done"
                + (target.unloadedCells > 0 ? " (" + target.unloadedCells + " cells in unloaded chunks)" : "")
                + "; short " + missing.values().stream().mapToInt(Integer::intValue).sum() + " items");

        if (!batch.isEmpty()) {
            log("gathering " + BatchPlanner.total(batch) + " items: " + describe(batch));
            batchIdx = 0;
            startEntry(mod);
            go(Phase.GATHER);
        } else if (canPlace) {
            go(Phase.BUILD);
        } else if (!auto.isEmpty()) {
            end("inventory full (" + cfg().reserveSlots + " slots kept free): make room, then @build resume", true);
        } else {
            go(Phase.WAIT_ITEMS);
        }
        return null;
    }

    private void protect(Set<String> ids) {
        List<Item> items = new ArrayList<>();
        for (String id : ids) {
            Item it = McItems.item(id);
            if (it != null) items.add(it);
        }
        try {
            AltoClef.getInstance().getBehaviour().addProtectedItems(items.toArray(new Item[0]));
        } catch (Throwable ignored) {
        }
    }

    /** Lists the items the bot cannot get, once per item (again every 5 minutes while waiting). */
    private void askForManual(boolean again) {
        if (state.manual.isEmpty()) return;
        boolean fresh = state.manual.keySet().stream().anyMatch(i -> !announced.contains(i));
        if (!fresh && !again) return;
        announced.addAll(state.manual.keySet());
        lastAskTick = ticks;
        log("I can't get these by myself: " + state.manual.entrySet().stream()
                .map(e -> e.getValue() + "x " + shortId(e.getKey()) + " (" + manualWhy.getOrDefault(e.getKey(), "?") + ")")
                .collect(Collectors.joining(", ")));
        log("put them in my inventory and I use them, or '@build skip <item>' to leave those blocks out."
                + (phase == Phase.WAIT_ITEMS ? "" : " Building the rest meanwhile."));
    }

    // ------------------------------------------------------------------ gather

    private void startEntry(AltoClef mod) {
        BatchPlanner.Entry e = batch.get(batchIdx);
        Item item = McItems.item(e.item());
        int now = McItems.count(mod, item);
        gatherTarget = now + e.amount();
        gatherBest = now;
        gatherBestAt = ticks;
        gatherTask = TaskCatalogue.getItemTask(item, gatherTarget);
        if (gatherTask instanceof ResourceTask rt && cfg().useContainers) rt.setAllowContainers(true);
    }

    private Task gather() {
        AltoClef mod = AltoClef.getInstance();
        if (batchIdx >= batch.size()) {
            go(Phase.BUILD);
            return null;
        }
        BatchPlanner.Entry e = batch.get(batchIdx);
        Item item = McItems.item(e.item());
        int have = McItems.count(mod, item);
        boolean next = have >= gatherTarget;
        if (!next && have > gatherBest) {
            gatherBest = have;
            gatherBestAt = ticks;
        }
        if (!next && (gatherTask == null || gatherTask.isFinished() || ticks - gatherBestAt > cfg().gatherStallSec * 20)) {
            stalled.add(e.item());
            log("gathering " + shortId(e.item()) + " got stuck at " + have + "/" + gatherTarget + ", I'll ask for the rest");
            next = true;
        }
        if (next) {
            batchIdx++;
            gatherTask = null;
            if (batchIdx >= batch.size()) {
                go(Phase.BUILD);
                return null;
            }
            startEntry(mod);
        }
        BatchPlanner.Entry cur = batch.get(batchIdx);
        setDebugState("Gathering " + shortId(cur.item()) + " (" + (batchIdx + 1) + "/" + batch.size() + ")");
        return gatherTask;
    }

    // ------------------------------------------------------------------ build

    private Task build() {
        IBuilderProcess b = builder();
        if (!builderStarted) {
            Set<String> excluded = new HashSet<>(state.skipItems);
            AltoClef mod = AltoClef.getInstance();
            // items still to be handed over: the builder would stop at the first such block
            for (String id : state.manual.keySet()) {
                Item it = McItems.item(id);
                if (it == null || McItems.count(mod, it) == 0) excluded.add(id);
            }
            target.startBuilder(b, excluded);
            builderStarted = true;
            lastProgressScan = ticks;
            if (lastWork < 0) lastWork = work();
            setDebugState("Building");
            return null;
        }
        boolean stopped = !b.isActive() || b.isPaused();
        if (!stopped && ticks - lastProgressScan < PROGRESS_SCAN_TICKS) {
            setDebugState("Building");
            return null;
        }
        // the builder stopped (out of blocks, or done), or a minute went by: has anything been placed?
        lastProgressScan = ticks;
        int w = work();
        if (w < lastWork) {
            idleRounds = 0;
        } else {
            idleRounds++;
        }
        lastWork = w;
        if (idleRounds >= MAX_IDLE_ROUNDS) {
            end("the builder placed nothing in " + MAX_IDLE_ROUNDS + " rounds; " + w + " cells are left that it cannot do"
                    + " (look at them with @build status), then @build resume", true);
            return null;
        }
        if (stopped) {
            stopBuilder();
            go(Phase.PLAN);
        } else if (idleRounds > 0) {
            stopBuilder(); // running but stuck: start it again from a fresh scan
            go(Phase.PLAN);
        }
        return null;
    }

    /** Cells still to place or clear, from a fresh scan. */
    private int work() {
        MaterialList m = target.scan(AltoClef.getInstance().getWorld(), new HashSet<>(state.skipItems));
        scan = m;
        return m.toPlace() + m.toClear();
    }

    private void stopBuilder() {
        if (!builderStarted) return;
        builderStarted = false;
        try {
            builder().onLostControl();
            AltoClef.getInstance().getClientBaritone().getPathingBehavior().cancelEverything();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ wait for handed-over items

    private Task waitItems() {
        AltoClef mod = AltoClef.getInstance();
        if (ticks % 20 == 0) {
            for (String id : state.manual.keySet()) {
                Item it = McItems.item(id);
                if (it != null && McItems.count(mod, it) > 0) {
                    log("got " + shortId(id) + ", building on");
                    go(Phase.PLAN);
                    return null;
                }
            }
        }
        if (ticks - lastAskTick > 20 * 300) askForManual(true);
        setDebugState("Waiting for " + state.manual.size() + " item type(s) to be handed over");
        return null;
    }

    // ------------------------------------------------------------------ end

    /** Ends the task. {@code keep}: leave the state saved (paused) so {@code @build resume} carries on. */
    private void end(String reason, boolean keep) {
        stopBuilder();
        phase = Phase.DONE;
        log(reason);
        if (scan != null && !scan.skipped().isEmpty()) {
            log("not built: " + scan.skipped().entrySet().stream().map(e -> e.getValue() + "x " + e.getKey())
                    .collect(Collectors.joining(", ")));
        }
        if (keep) {
            state.paused = true;
            state.phase = "STOPPED";
            store().saveState(state);
        } else {
            store().clearState();
        }
    }

    @Override
    public boolean isFinished() {
        return phase == Phase.DONE;
    }

    @Override
    protected void onStop(Task interruptTask) {
        stopBuilder();
        try {
            AltoClef.getInstance().getBehaviour().pop();
        } catch (Throwable ignored) {
        }
        if (AutoBuildMc.current == this) AutoBuildMc.current = null;
        if (cancelled) {
            store().clearState();
            log("cancelled " + state.label());
        } else if (phase != Phase.DONE) {
            state.paused = true;
            store().saveState(state);
            log("paused " + state.label() + " (" + (interruptTask == null ? "stopped" : "interrupted") + "), @build resume carries on");
        }
    }

    /** Lines for {@code @build status}. */
    List<String> status() {
        List<String> out = new ArrayList<>();
        out.add(state.label() + ": " + (phase == null ? "starting" : phase.name().toLowerCase())
                + (target == null ? "" : " at " + target.min().toShortString() + " (" + target.size() + ")"));
        if (scan != null) {
            out.add(" " + scan.toPlace() + " to place, " + scan.toClear() + " to clear, " + scan.placed() + " done of " + scan.cells());
        }
        if (phase == Phase.GATHER && batchIdx < batch.size()) {
            out.add(" gathering " + shortId(batch.get(batchIdx).item()) + " (" + (batchIdx + 1) + "/" + batch.size() + "), batch " + describe(batch));
        }
        out.addAll(AutoBuildMc.describeState(state));
        if (scan != null && !scan.skipped().isEmpty()) {
            out.add(" not built: " + scan.skipped().entrySet().stream().map(e -> e.getValue() + "x " + e.getKey())
                    .collect(Collectors.joining(", ")));
        }
        return out;
    }

    static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    private static String describe(List<BatchPlanner.Entry> b) {
        return b.stream().map(e -> e.amount() + "x " + shortId(e.item())).collect(Collectors.joining(", "));
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof AutoBuildTask t && t.state == state;
    }

    @Override
    protected String toDebugString() {
        return "Autobuild " + state.label() + " " + (phase == null ? "" : phase.name());
    }
}
