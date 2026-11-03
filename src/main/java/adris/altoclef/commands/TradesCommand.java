package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tradecycle.BookPricing;
import adris.altoclef.tradecycle.EnchantIds;
import adris.altoclef.tradecycle.EnchantInfo;
import adris.altoclef.tradecycle.TradeCycleConfig;
import adris.altoclef.tradecycle.TradeIndex;
import adris.altoclef.tradecycle.TradeMatcher;
import adris.altoclef.tradecycle.TradeStore;
import adris.altoclef.tradecycle.TradeView;
import adris.altoclef.tradecycle.VillagerRecord;
import adris.altoclef.tradecycle.WantEntry;
import adris.altoclef.tradecycle.mc.TradeMc;

import java.util.List;

/**
 * Librarian index, overlay and reroll/lock cycle.
 * <pre>
 * @trades scan [radius]                       index librarians (default 32)
 * @trades cycle [radius] [upgrade]            reroll REROLLABLE librarians until every wanted book is owned
 * @trades list | owned | missing
 * @trades forget &lt;uuid-prefix|all&gt;
 * @trades want add &lt;enchant&gt; [minLevel] [maxPrice|best]
 * @trades want remove &lt;enchant&gt; | list | clear
 * @trades want all [best]                     every #minecraft:tradeable enchantment at max level
 * @trades tolerance &lt;n&gt;                       best-price tolerance (emeralds)
 * @trades upgrade on|off                      default for cycle upgrade mode
 * @trades overlay | gui | reload
 * </pre>
 */
public class TradesCommand extends Command {

    private String rest = "";

    public TradesCommand() {
        super(List.of("trades", "trade"), "Librarian trades: scan|cycle|list|owned|missing|forget|want|tolerance|upgrade|overlay|gui",
                new StringArg("action", "list"));
    }

    @Override
    public void run(AltoClef mod, String line, Runnable onFinish) throws CommandException {
        String[] p = line.trim().split("\\s+", 3);
        rest = p.length > 2 ? p[2].trim() : "";
        super.run(mod, p.length > 1 ? p[0] + " " + p[1] : p[0], onFinish);
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String action = parser.get(String.class).toLowerCase();
        String[] a = rest.isEmpty() ? new String[0] : rest.split("\\s+");
        TradeStore store = TradeStore.get();
        TradeCycleConfig cfg = store.config();
        int tol = cfg.bestTolerance;
        switch (action) {
            case "scan" -> {
                int radius = intArg(a, 0, 32);
                String err = TradeMc.startScan(mod, radius, this::finish);
                if (err != null) {
                    mod.log("trades: " + err);
                    finish();
                }
                return;
            }
            case "cycle" -> {
                int radius = 32;
                boolean upgrade = cfg.upgrade;
                for (String s : a) {
                    if (s.equalsIgnoreCase("upgrade")) upgrade = true;
                    else if (s.equalsIgnoreCase("noupgrade")) upgrade = false;
                    else radius = parseInt(s, radius);
                }
                String err = TradeMc.startCycle(mod, radius, upgrade, this::finish);
                if (err != null) {
                    mod.log("trades: " + err);
                    finish();
                }
                return;
            }
            case "list" -> list(mod, store);
            case "owned" -> {
                List<TradeMatcher.Owned> owned = TradeMatcher.owned(store.index().all());
                if (owned.isEmpty()) mod.log("trades: no books on LOCKED librarians yet");
                for (TradeMatcher.Owned o : owned) {
                    mod.log(" " + o.offer().shortLabel() + (TradeView.isBest(o.offer(), tol) ? " BEST" : "")
                            + " @ " + TradeView.pos(o.villager()) + " [" + o.villager().shortId() + "]");
                }
            }
            case "missing" -> {
                List<WantEntry> missing = TradeMatcher.missing(cfg.wanted, store.index().all(), tol);
                if (cfg.wanted.isEmpty()) mod.log("trades: wanted list is empty (@trades want add <enchant> ...)");
                else if (missing.isEmpty()) mod.log("trades: every wanted book is owned");
                else {
                    mod.log("trades: missing " + missing.size() + "/" + cfg.wanted.size());
                    for (WantEntry w : missing) mod.log(" " + w.describe(tol));
                }
            }
            case "forget" -> {
                if (a.length == 0) {
                    mod.log("trades: forget <uuid-prefix|all>");
                } else {
                    int n = store.index().forget(a[0]);
                    store.saveIndex();
                    mod.log("trades: forgot " + n + " villager(s)");
                }
            }
            case "want", "wants" -> want(mod, store, cfg, a);
            case "tolerance" -> {
                if (a.length > 0) {
                    cfg.bestTolerance = Math.max(0, parseInt(a[0], cfg.bestTolerance));
                    store.saveConfig();
                }
                mod.log("trades: bestTolerance = " + cfg.bestTolerance);
            }
            case "upgrade" -> {
                if (a.length > 0) {
                    cfg.upgrade = a[0].equalsIgnoreCase("on") || a[0].equalsIgnoreCase("true");
                    store.saveConfig();
                }
                mod.log("trades: upgrade = " + (cfg.upgrade ? "on" : "off"));
            }
            case "overlay" -> mod.log("trades: overlay " + (TradeMc.toggleOverlay() ? "on" : "off"));
            case "gui", "screen" -> TradeMc.openScreen();
            case "reload" -> {
                store.reloadConfig();
                mod.log("trades: reloaded " + store.configPath());
            }
            default -> mod.log("trades: scan|cycle|list|owned|missing|forget|want|tolerance|upgrade|overlay|gui|reload");
        }
        finish();
    }

    private void list(AltoClef mod, TradeStore store) {
        TradeIndex idx = store.index();
        TradeCycleConfig cfg = store.config();
        if (idx.villagers.isEmpty()) {
            mod.log("trades: index empty for " + store.worldKey() + " (@trades scan)");
            return;
        }
        double[] p = TradeMc.playerPos();
        List<TradeView.Row> rows = TradeView.rows(idx.all(), p == null ? 0 : p[0], p == null ? 0 : p[1], p == null ? 0 : p[2], TradeView.Sort.STATUS);
        mod.log("trades " + store.worldKey() + ": " + idx.villagers.size() + " librarian(s), "
                + TradeView.summary(cfg.wanted, idx.all(), cfg.bestTolerance));
        for (TradeView.Row r : rows) {
            VillagerRecord v = r.villager();
            String book = r.book() == null ? (v.lastScanned > 0 ? "no book" : "-") : r.book().shortLabel()
                    + (TradeView.isBest(r.book(), cfg.bestTolerance) ? " BEST" : "");
            mod.log(String.format(" %-10s %-8s %-26s %s %4.0fm", v.status, v.shortId(), book, TradeView.pos(v),
                    p == null ? 0.0 : r.distance()));
        }
    }

    private void want(AltoClef mod, TradeStore store, TradeCycleConfig cfg, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase() : "list";
        int tol = cfg.bestTolerance;
        switch (sub) {
            case "add" -> {
                if (a.length < 2) {
                    mod.log("trades: want add <enchant> [minLevel] [maxPrice|best]");
                    return;
                }
                String id = EnchantIds.normalize(a[1]);
                EnchantInfo info = TradeMc.enchant(id);
                if (info == null && TradeMc.supported()) {
                    mod.log("trades: '" + id + "' is not a known enchantment here; adding it anyway");
                } else if (info != null && !isTradeable(info.id())) {
                    mod.log("trades: note " + id + " is not in #minecraft:tradeable, librarians won't offer it");
                }
                int maxLevel = info == null ? 1 : info.maxLevel();
                int minLevel = a.length > 2 ? parseInt(a[2], maxLevel) : maxLevel;
                boolean best = a.length > 3 && a[3].equalsIgnoreCase("best");
                WantEntry w;
                if (best) {
                    w = WantEntry.best(id, minLevel, info != null && info.doublePrice());
                } else {
                    w = new WantEntry(id, minLevel, a.length > 3 ? parseInt(a[3], BookPricing.MAX_EMERALDS) : BookPricing.MAX_EMERALDS);
                }
                cfg.putWant(w);
                store.saveConfig();
                mod.log("trades: want " + w.describe(tol));
            }
            case "remove", "rm", "del" -> {
                if (a.length < 2) {
                    mod.log("trades: want remove <enchant>");
                    return;
                }
                boolean removed = cfg.removeWant(a[1]);
                store.saveConfig();
                mod.log("trades: " + (removed ? "removed " : "not in list: ") + EnchantIds.normalize(a[1]));
            }
            case "clear" -> {
                int n = cfg.wanted.size();
                cfg.wanted.clear();
                store.saveConfig();
                mod.log("trades: cleared " + n + " want(s)");
            }
            case "all" -> {
                boolean best = a.length > 1 && a[1].equalsIgnoreCase("best");
                List<EnchantInfo> all = TradeMc.tradeable();
                if (all.isEmpty()) {
                    mod.log("trades: " + (TradeMc.supported() ? "join a world first (the tradeable tag is read from the registry)" : TradeMc.UNSUPPORTED));
                    return;
                }
                for (EnchantInfo e : all) {
                    cfg.putWant(best ? WantEntry.best(e.id(), e.maxLevel(), e.doublePrice())
                            : new WantEntry(e.id(), e.maxLevel(), BookPricing.MAX_EMERALDS));
                }
                store.saveConfig();
                mod.log("trades: wanted list now has " + cfg.wanted.size() + " entries (" + all.size()
                        + " tradeable" + (best ? ", best price" + (tol > 0 ? " +" + tol : "") : "") + ")");
            }
            default -> {
                if (cfg.wanted.isEmpty()) {
                    mod.log("trades: wanted list empty");
                    return;
                }
                mod.log("trades: " + cfg.wanted.size() + " wanted, " + TradeView.summary(cfg.wanted, store.index().all(), tol));
                for (WantEntry w : cfg.wanted) {
                    TradeMatcher.Owned have = TradeMatcher.bestOwned(w, store.index().all(), tol);
                    mod.log(" " + w.describe(tol) + (have == null ? "  MISSING" : "  owned " + have.offer().price() + " em"));
                }
            }
        }
    }

    private static boolean isTradeable(String id) {
        for (EnchantInfo e : TradeMc.tradeable()) if (e.id().equals(id)) return true;
        return TradeMc.tradeable().isEmpty();
    }

    private static int intArg(String[] a, int i, int def) {
        return a.length > i ? parseInt(a[i], def) : def;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
