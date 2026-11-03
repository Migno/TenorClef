package adris.altoclef.tradecycle.mc;

import adris.altoclef.AltoClef;
import adris.altoclef.multiversion.ScreenVer;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tradecycle.EnchantIds;
import adris.altoclef.tradecycle.TradeCycleConfig;
import adris.altoclef.tradecycle.TradeIndex;
import adris.altoclef.tradecycle.TradeMatcher;
import adris.altoclef.tradecycle.TradeOffer;
import adris.altoclef.tradecycle.TradeStatus;
import adris.altoclef.tradecycle.TradeStore;
import adris.altoclef.tradecycle.TradeView;
import adris.altoclef.tradecycle.VillagerRecord;
import adris.altoclef.tradecycle.WantEntry;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * {@code @trades cycle}: rerolls REROLLABLE librarians (break lectern → pick up → place back → wait for the
 * profession) until each wanted book is locked on some villager by buying it once.
 * <p>
 * Safety: only villagers whose live trade screen showed novice + 0 XP are ever touched; the level is re-checked
 * on the entity right before each break, LOCKED is sticky in the index, lecterns linked to LOCKED villagers are
 * refused, and pathing is told not to break any other lectern while this task runs.
 */
class TradeCycleTask extends Task {

    private enum State {PICK, READ, BUY, CONFIRM, BREAK, PICKUP, PLACE, WAIT_PROFESSION, DONE}

    private static final Random RNG = new Random();

    private final int radius;
    private final boolean upgrade;

    private State state = State.PICK;
    private int ticks;
    private int stateStart;
    private int waitUntil;

    private String uuid;
    private BlockPos lectern;
    private volatile BlockPos breakTarget;
    private ReadOffersTask read;
    private TradeMatcher.Match match;
    private int buyStep;
    private int buyStepAt;
    private int buyTries;
    private int lecternsBefore;
    private boolean sawUnemployed;
    private float lastHealth = -1;
    private String stopReason;

    private final Map<String, Integer> runAttempts = new LinkedHashMap<>();
    private final Set<String> givenUp = new HashSet<>();
    private final List<String> newlyLocked = new ArrayList<>();
    private int attemptCounter;
    private boolean summaryPrinted;

    TradeCycleTask(int radius, boolean upgrade) {
        this.radius = radius;
        this.upgrade = upgrade;
    }

    private static TradeStore store() {
        return TradeStore.get();
    }

    private static TradeCycleConfig cfg() {
        return store().config();
    }

    private static void log(String s) {
        AltoClef.getInstance().log("trades: " + s);
    }

    @Override
    protected void onStart() {
        AltoClef mod = AltoClef.getInstance();
        mod.getBehaviour().push();
        // Pathing may never break a lectern other than the one this cycle is rerolling right now.
        mod.getBehaviour().avoidBlockBreaking(p -> !p.equals(breakTarget) && TradeWorld.isLectern(p));
        lastHealth = mod.getPlayer().getHealth();
        state = State.PICK;
        log("cycle started (radius " + radius + (upgrade ? ", upgrade" : "") + "), "
                + TradeView.summary(cfg().wanted, store().index().all(), cfg().bestTolerance));
    }

    @Override
    protected Task onTick() {
        if (state == State.DONE) return null;
        ticks++;
        LocalPlayer player = AltoClef.getInstance().getPlayer();
        if (player == null) {
            endCycle("left the world");
            return null;
        }
        float hp = player.getHealth();
        if (lastHealth >= 0 && hp < lastHealth - 0.01f) {
            endCycle("player took damage (" + String.format("%.1f", lastHealth) + " -> " + String.format("%.1f", hp) + ")");
            return null;
        }
        lastHealth = hp;

        Villager v = uuid == null ? null : TradeWorld.villager(uuid);
        if (v != null && TradeWorld.isUnemployed(v)) sawUnemployed = true;
        if (ticks < waitUntil) return null;

        return switch (state) {
            case PICK -> pick();
            case READ -> afterRead(v);
            case BUY -> buy(v);
            case CONFIRM -> afterConfirm(v);
            case BREAK -> breakLectern(v);
            case PICKUP -> pickup();
            case PLACE -> place();
            case WAIT_PROFESSION -> waitProfession(v);
            case DONE -> null;
        };
    }

    private void go(State s) {
        state = s;
        stateStart = ticks;
        int min = cfg().minDelayMs, max = Math.max(min, cfg().maxDelayMs);
        int ms = min + (max > min ? RNG.nextInt(max - min + 1) : 0);
        waitUntil = ticks + Math.round(ms / 50f);
    }

    private int inState() {
        return ticks - stateStart;
    }

    // ---------------------------------------------------------------- pick

    private Task pick() {
        TradeCycleConfig c = cfg();
        TradeIndex idx = store().index();
        if (c.wanted.isEmpty()) {
            endCycle("wanted list is empty (@trades want add <enchant> ... or @trades want all [best])");
            return null;
        }
        if (TradeMatcher.allDone(c.wanted, idx.all(), c.bestTolerance, upgrade)) {
            endCycle("all wanted books owned");
            return null;
        }
        LocalPlayer p = AltoClef.getInstance().getPlayer();
        String bestId = null;
        double bestD = Double.MAX_VALUE;
        for (Villager v : TradeWorld.librariansNear(radius)) {
            String id = v.getUUID().toString();
            if (givenUp.contains(id)) continue;
            VillagerRecord r = idx.get(id);
            if (r != null && r.status == TradeStatus.LOCKED) continue;
            if (v.getVillagerData().level() > 1) continue;
            if (runAttempts.getOrDefault(id, 0) >= c.attemptCap) continue;
            double d = v.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                bestId = id;
            }
        }
        if (bestId == null) {
            endCycle("no REROLLABLE librarians left within " + radius + " blocks");
            return null;
        }
        uuid = bestId;
        lectern = null;
        buyTries = 0;
        startRead(true);
        return read;
    }

    private void startRead(boolean keepOpen) {
        read = new ReadOffersTask(uuid, keepOpen);
        go(State.READ);
        waitUntil = ticks; // reading is the action; no extra delay
    }

    private void giveUp(String why) {
        log(shortId() + " skipped: " + why);
        givenUp.add(uuid);
        StorageHelper.closeScreen();
        uuid = null;
        go(State.PICK);
    }

    private String shortId() {
        return uuid == null ? "?" : uuid.substring(0, 8);
    }

    /** Merges what the trade screen showed into the index. */
    private VillagerRecord recordRead(Villager v, MerchantMenu menu) {
        TradeStore s = store();
        VillagerRecord prev = s.index().get(uuid);
        BlockPos lp = TradeWorld.findLectern(v, prev, s.index(), cfg().lecternSearchRadius);
        VillagerRecord r = s.index().observe(TradeWorld.observe(v, menu, lp), System.currentTimeMillis());
        s.saveIndex();
        lectern = TradeWorld.pos(r.lectern);
        return r;
    }

    // ---------------------------------------------------------------- read / decide

    private Task afterRead(Villager v) {
        if (!read.isFinished()) return read;
        if (read.failure() != null || v == null) {
            giveUp(read.failure() != null ? read.failure() : "villager not loaded");
            return null;
        }
        VillagerRecord r = recordRead(v, read.menu());
        if (r.status != TradeStatus.REROLLABLE) {
            giveUp("is " + r.status + " (level " + r.level + ", xp " + r.xp + ")");
            return null;
        }
        TradeCycleConfig c = cfg();
        match = TradeMatcher.findLockTarget(r.offers, c.wanted, store().index().all(), c.bestTolerance, upgrade);
        String books = r.books().isEmpty() ? "no book" : String.join(", ", r.books().stream().map(TradeOffer::shortLabel).toList());
        int n = runAttempts.getOrDefault(uuid, 0);
        if (match != null) {
            log("#" + (++attemptCounter) + " " + shortId() + " try " + n + ": " + books + " -> LOCK"
                    + (match.upgrade() ? " (cheaper copy)" : ""));
            buyStep = 0;
            go(State.BUY);
            return null;
        }
        log("#" + (++attemptCounter) + " " + shortId() + " try " + n + ": " + books + " -> reroll");
        StorageHelper.closeScreen();
        if (lectern == null) {
            giveUp("no lectern found within " + c.lecternSearchRadius + " blocks");
            return null;
        }
        if (n >= c.attemptCap) {
            giveUp("attempt cap " + c.attemptCap + " reached");
            return null;
        }
        go(State.BREAK);
        return null;
    }

    // ---------------------------------------------------------------- buy (lock)

    private Task buy(Villager v) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!(ScreenVer.current(mc) instanceof MerchantScreen ms)) {
            if (buyStep >= 2) { // trade done, screen closed with the book in inventory
                startConfirm();
                return null;
            }
            startRead(true); // screen got closed: reopen, re-decide
            return read;
        }
        MerchantMenu menu = ms.getMenu();
        int idx = menuIndex(menu, match.offer());
        if (idx < 0) {
            StorageHelper.closeScreen();
            startRead(true);
            return read;
        }
        MerchantOffer offer = menu.getOffers().get(idx);
        switch (buyStep) {
            case 0 -> {
                ItemStack a = offer.getCostA(), b = offer.getCostB();
                int haveA = TradeWorld.count(p, a.getItem()), haveB = b.isEmpty() ? 0 : TradeWorld.count(p, b.getItem());
                if (haveA < a.getCount() || (!b.isEmpty() && haveB < b.getCount())) {
                    StorageHelper.closeScreen();
                    endCycle("need " + a.getCount() + " " + name(a) + (b.isEmpty() ? "" : " + " + b.getCount() + " " + name(b))
                            + " to lock " + match.offer().shortLabel() + " (have " + haveA + (b.isEmpty() ? "" : " / " + haveB) + ")");
                    return null;
                }
                if (emptyInventorySlot(menu) < 0) {
                    StorageHelper.closeScreen();
                    endCycle("inventory full, no room for the book");
                    return null;
                }
                menu.setSelectionHint(idx);
                menu.tryMoveItems(idx);
                mc.getConnection().send(new ServerboundSelectTradePacket(idx));
                buyStep = 1;
                buyStepAt = ticks;
            }
            case 1 -> {
                if (ticks - buyStepAt < 4) return null;
                ItemStack res = menu.getSlot(2).getItem();
                if (!res.isEmpty()) {
                    mc.gameMode.handleContainerInput(menu.containerId, 2, 0, ContainerInput.PICKUP, p);
                    buyStep = 2;
                    buyStepAt = ticks;
                } else if (ticks - buyStepAt > 40) {
                    StorageHelper.closeScreen();
                    retryBuy("result slot stayed empty");
                }
            }
            case 2 -> {
                if (ticks - buyStepAt < 4) return null;
                if (!menu.getCarried().isEmpty()) {
                    int slot = emptyInventorySlot(menu);
                    if (slot >= 0) mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, p);
                }
                buyStep = 3;
                buyStepAt = ticks;
            }
            default -> {
                if (ticks - buyStepAt < 4) return null;
                StorageHelper.closeScreen(); // returns leftover payment to the inventory
                startConfirm();
            }
        }
        return null;
    }

    private void retryBuy(String why) {
        if (++buyTries >= 3) {
            giveUp("could not buy (" + why + ")");
        } else {
            log(shortId() + " buy retry " + buyTries + ": " + why);
            startRead(true);
        }
    }

    private void startConfirm() {
        read = new ReadOffersTask(uuid, false);
        go(State.CONFIRM);
    }

    private Task afterConfirm(Villager v) {
        if (!read.isFinished()) return read;
        if (read.failure() != null || v == null) {
            giveUp("could not re-open to confirm the lock: " + (read.failure() != null ? read.failure() : "villager gone"));
            return null;
        }
        VillagerRecord r = recordRead(v, read.menu());
        if (r.status == TradeStatus.LOCKED) {
            String line = match.offer().shortLabel() + " @ " + TradeView.pos(r) + " [" + r.shortId() + "]";
            newlyLocked.add(line);
            log("LOCKED " + line + "; " + TradeView.summary(cfg().wanted, store().index().all(), cfg().bestTolerance));
            uuid = null;
            go(State.PICK);
        } else {
            retryBuy("villager still has 0 XP");
        }
        return null;
    }

    private static int menuIndex(MerchantMenu menu, TradeOffer want) {
        for (int i = 0; i < menu.getOffers().size(); i++) {
            TradeOffer t = TradeWorld.parse(menu.getOffers().get(i));
            if (t != null && t.isBook() && t.enchantment.equals(want.enchantment) && t.level == want.level && t.baseCostA == want.baseCostA)
                return i;
        }
        return -1;
    }

    /** Merchant menu slots: 0,1 payment, 2 result, 3..38 player inventory. */
    private static int emptyInventorySlot(MerchantMenu menu) {
        for (int i = 3; i < Math.min(39, menu.slots.size()); i++) if (!menu.getSlot(i).hasItem()) return i;
        return -1;
    }

    private static String name(ItemStack s) {
        return EnchantIds.displayName(TradeWorld.itemId(s)).toLowerCase();
    }

    // ---------------------------------------------------------------- reroll

    private String refuseBreak(Villager v) {
        if (v == null) return "villager not loaded";
        if (!TradeWorld.isLibrarian(v)) return "not a librarian any more";
        if (v.getVillagerData().level() != 1) return "entity level is " + v.getVillagerData().level();
        VillagerRecord r = store().index().get(uuid);
        if (r == null || r.status != TradeStatus.REROLLABLE || r.level != 1 || r.xp != 0) {
            return "index says " + (r == null ? "unknown" : r.status + " lvl " + r.level + " xp " + r.xp);
        }
        if (System.currentTimeMillis() - r.lastScanned > 120_000) return "trade data is stale";
        if (!TradeWorld.isLectern(lectern)) return "linked lectern is gone";
        for (VillagerRecord o : store().index().all()) {
            if (o.uuid.equals(uuid) || o.lectern == null) continue;
            if (lectern.equals(TradeWorld.pos(o.lectern)) && o.status != TradeStatus.REROLLABLE) {
                return "lectern is linked to " + o.status + " villager " + o.shortId();
            }
        }
        // Any experienced villager standing closer to this lectern than our target may own it.
        double mine = v.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(lectern));
        for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
            if (!(e instanceof Villager w) || w == v || !TradeWorld.isLibrarian(w)) continue;
            VillagerRecord wr = store().index().get(w.getUUID().toString());
            boolean locked = w.getVillagerData().level() > 1 || (wr != null && wr.status == TradeStatus.LOCKED);
            if (locked && w.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(lectern)) < mine) {
                return "LOCKED villager " + w.getUUID().toString().substring(0, 8) + " is closer to this lectern";
            }
        }
        return null;
    }

    private Task breakLectern(Villager v) {
        AltoClef mod = AltoClef.getInstance();
        if (breakTarget == null) {
            // Live re-check right before the break.
            String no = refuseBreak(v);
            if (no != null) {
                giveUp("refusing to break lectern: " + no);
                return null;
            }
            Item axe = TradeWorld.bestAxe(mod.getPlayer(), cfg().axeMinDurability);
            if (axe == null) {
                endCycle(TradeWorld.hasAnyAxe(mod.getPlayer()) ? "axe durability at or below " + cfg().axeMinDurability : "no axe in inventory");
                return null;
            }
            lecternsBefore = TradeWorld.count(mod.getPlayer(), Items.LECTERN);
            sawUnemployed = false;
            breakTarget = lectern;
        }
        if (!TradeWorld.isLectern(lectern)) {
            breakTarget = null;
            runAttempts.merge(uuid, 1, Integer::sum);
            VillagerRecord r = store().index().get(uuid);
            if (r != null) {
                r.attempts++;
                store().saveIndex();
            }
            go(State.PICKUP);
            return null;
        }
        if (inState() > 20 * 30) {
            breakTarget = null;
            endCycle("could not break the lectern at " + lectern.toShortString());
            return null;
        }
        Item axe = TradeWorld.bestAxe(mod.getPlayer(), cfg().axeMinDurability);
        if (axe == null) {
            breakTarget = null;
            endCycle("axe durability at or below " + cfg().axeMinDurability);
            return null;
        }
        if (mod.getPlayer().blockPosition().closerThan(lectern, 5)) mod.getSlotHandler().forceEquipItem(axe);
        setDebugState("Breaking lectern " + lectern.toShortString());
        return new DestroyBlockTask(lectern);
    }

    private Task pickup() {
        LocalPlayer p = AltoClef.getInstance().getPlayer();
        int have = TradeWorld.count(p, Items.LECTERN);
        if (have > lecternsBefore || (inState() > 20 * 10 && have > 0)) {
            go(State.PLACE);
            return null;
        }
        if (inState() > 20 * 10) {
            endCycle("no lectern: the drop was not picked up and none is in the inventory");
            return null;
        }
        setDebugState("Picking up lectern");
        return new PickupDroppedItemTask(Items.LECTERN, lecternsBefore + 1, false);
    }

    private Task place() {
        if (TradeWorld.isLectern(lectern)) {
            go(State.WAIT_PROFESSION);
            return null;
        }
        if (TradeWorld.count(AltoClef.getInstance().getPlayer(), Items.LECTERN) == 0) {
            endCycle("no lectern in inventory to place back at " + lectern.toShortString());
            return null;
        }
        if (inState() > 20 * 25) {
            endCycle("could not place the lectern back at " + lectern.toShortString());
            return null;
        }
        setDebugState("Placing lectern " + lectern.toShortString());
        return new PlaceBlockTask(lectern, new Block[]{Blocks.LECTERN}, false, false);
    }

    private Task waitProfession(Villager v) {
        if (v == null) {
            giveUp("villager vanished while waiting for its profession");
            return null;
        }
        if (TradeWorld.isLibrarian(v)) {
            if (sawUnemployed) {
                startRead(true);
                return read;
            }
            if (inState() > 60) {
                // It never lost the profession: that lectern was not its job site.
                VillagerRecord r = store().index().get(uuid);
                if (r != null) {
                    r.lectern = null;
                    store().saveIndex();
                }
                giveUp("kept its profession, so the lectern at " + lectern.toShortString() + " is not its job site");
                return null;
            }
        } else if (!TradeWorld.isUnemployed(v)) {
            VillagerRecord r = store().index().get(uuid);
            if (r != null) {
                r.status = TradeStatus.UNKNOWN;
                store().saveIndex();
            }
            giveUp("took another profession");
            return null;
        }
        if (inState() > cfg().professionTimeoutSec * 20) {
            giveUp("librarian profession did not return within " + cfg().professionTimeoutSec + "s");
            return null;
        }
        setDebugState("Waiting for librarian profession");
        return null;
    }

    // ---------------------------------------------------------------- end

    private void endCycle(String reason) {
        stopReason = reason;
        state = State.DONE;
        breakTarget = null;
        printSummary();
    }

    private void printSummary() {
        if (summaryPrinted) return;
        summaryPrinted = true;
        TradeCycleConfig c = cfg();
        log("cycle finished: " + (stopReason == null ? "stopped" : stopReason));
        if (newlyLocked.isEmpty()) log(" newly locked: none");
        else for (String s : newlyLocked) log(" newly locked: " + s);
        if (!runAttempts.isEmpty()) {
            StringBuilder sb = new StringBuilder(" attempts:");
            runAttempts.forEach((k, n) -> sb.append(' ').append(k, 0, 8).append('=').append(n));
            log(sb.toString());
        }
        List<WantEntry> missing = TradeMatcher.missing(c.wanted, store().index().all(), c.bestTolerance);
        if (missing.isEmpty()) log(" still missing: none");
        else log(" still missing (" + missing.size() + "): " + String.join(", ",
                missing.stream().map(w -> w.describe(c.bestTolerance)).toList()));
    }

    @Override
    public boolean isFinished() {
        return state == State.DONE;
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef mod = AltoClef.getInstance();
        breakTarget = null;
        try {
            mod.getBehaviour().pop();
        } catch (Throwable ignored) {
        }
        StorageHelper.closeScreen();
        store().saveIndex();
        if (state != State.DONE) {
            stopReason = interruptTask == null ? "stopped by user" : "interrupted by " + interruptTask;
            printSummary();
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof TradeCycleTask t && t.radius == radius && t.upgrade == upgrade;
    }

    @Override
    protected String toDebugString() {
        return "Trade cycle " + state + (uuid == null ? "" : " " + shortId());
    }
}
