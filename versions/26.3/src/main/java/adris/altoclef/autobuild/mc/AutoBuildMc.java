package adris.altoclef.autobuild.mc;

import adris.altoclef.AltoClef;
import adris.altoclef.autobuild.AutoBuildState;
import adris.altoclef.autobuild.AutoBuildStore;
import adris.altoclef.autobuild.BlockItems;
import baritone.utils.schematic.litematica.LitematicaHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 26.3 implementation of the autobuild command's Minecraft side (replaces the stub in {@code src/main/java}).
 */
public final class AutoBuildMc {

    public static final String UNSUPPORTED = "@build needs the Minecraft 26.3 build";

    /** The running build task, or null. */
    static volatile AutoBuildTask current;

    private AutoBuildMc() {
    }

    public static boolean supported() {
        return true;
    }

    public static File schematicsDir() {
        return BuildTarget.schematicsDir();
    }

    /** Player feet {x, y, z}, or null when not in a world. */
    public static int[] playerFeet() {
        var p = Minecraft.getInstance().player;
        if (p == null) return null;
        var b = p.blockPosition();
        return new int[]{b.getX(), b.getY(), b.getZ()};
    }

    public static boolean litematicaPresent() {
        try {
            return LitematicaHelper.isLitematicaPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Server (or singleplayer world) and dimension, e.g. {@code mc.example.com|minecraft:overworld}. */
    public static String worldKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        String server;
        ServerData sd = mc.getCurrentServer();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            server = "singleplayer_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        } else if (sd != null && sd.ip != null) {
            server = sd.ip;
        } else {
            server = "unknown_server";
        }
        return server + "|" + mc.level.dimension().identifier();
    }

    public static boolean running() {
        return current != null;
    }

    /** @return null when started, else why not */
    public static String start(AltoClef mod, AutoBuildState s, Runnable onFinish) {
        if (mod.getPlayer() == null) return "not in a world";
        if (current != null) return "a build is running (" + current.state.label() + "): @build pause or @build cancel first";
        if (s.litematica && !litematicaPresent()) return "Litematica is not loaded";
        AutoBuildState old = AutoBuildStore.get().loadState();
        if (old != null) mod.log("build: replacing the saved build of " + old.label());
        s.world = worldKey();
        s.sanitize();
        AutoBuildStore.get().saveState(s);
        mod.runUserTask(new AutoBuildTask(s), onFinish);
        return null;
    }

    /** @return null when resumed, else why not */
    public static String resume(AltoClef mod, Runnable onFinish) {
        if (mod.getPlayer() == null) return "not in a world";
        if (current != null) return "already building " + current.state.label();
        AutoBuildState s = AutoBuildStore.get().loadState();
        if (s == null) return "no saved build to resume";
        String here = worldKey();
        if (s.world != null && !s.world.equals(here)) return "the saved build is in " + s.world + ", you are in " + here;
        s.paused = false;
        mod.runUserTask(new AutoBuildTask(s), onFinish);
        return null;
    }

    /** Stops the running build, keeping it saved. */
    public static String pause(AltoClef mod) {
        if (current == null) {
            return AutoBuildStore.get().loadState() != null ? "not running; the saved build waits for @build resume" : "no build running";
        }
        mod.cancelUserTask();
        return null;
    }

    public static String cancel(AltoClef mod) {
        AutoBuildTask t = current;
        if (t != null) {
            t.cancelled = true;
            mod.cancelUserTask();
            AutoBuildStore.get().clearState();
            return null;
        }
        AutoBuildState s = AutoBuildStore.get().loadState();
        AutoBuildStore.get().clearState();
        return s == null ? "no build to cancel" : "dropped the saved build of " + s.label();
    }

    /** {@code add}: leave the blocks of this item out; else build them again. @return what happened */
    public static String skip(String itemArg, boolean add) {
        String id = BlockItems.id(itemArg);
        if (McItems.item(id) == null) return "no item " + id;
        AutoBuildTask t = current;
        AutoBuildState s = t != null ? t.state : AutoBuildStore.get().loadState();
        if (s == null) return "no build running or saved";
        boolean changed = add ? !s.skipItems.contains(id) && s.skipItems.add(id) : s.skipItems.remove(id);
        AutoBuildStore.get().saveState(s);
        if (t != null && changed) t.requestReplan();
        return (add ? (changed ? "leaving out " : "already leaving out ") : (changed ? "building again: " : "was not left out: ")) + id;
    }

    public static List<String> status() {
        AutoBuildTask t = current;
        if (t != null) return t.status();
        AutoBuildState s = AutoBuildStore.get().loadState();
        if (s == null) return List.of("no build running or saved");
        List<String> out = new ArrayList<>();
        out.add(s.label() + ": saved, " + (s.paused ? "paused" : "not running") + " (phase " + s.phase + "), @build resume carries on");
        out.addAll(describeState(s));
        return out;
    }

    static List<String> describeState(AutoBuildState s) {
        List<String> out = new ArrayList<>();
        out.add(" origin " + s.origin[0] + " " + s.origin[1] + " " + s.origin[2]
                + (s.litematica ? " (from Litematica)" : ", rotation " + s.rotation + ", mirror " + s.mirror)
                + (s.cleared ? ", area cleared" : ""));
        if (!s.remaining.isEmpty()) out.add(" still to place: " + top(s.remaining, 10));
        if (!s.manual.isEmpty()) out.add(" asked for: " + top(s.manual, 10));
        if (!s.skipItems.isEmpty()) out.add(" left out: " + s.skipItems.stream().map(AutoBuildTask::shortId).collect(Collectors.joining(", ")));
        return out;
    }

    private static String top(Map<String, Integer> m, int n) {
        String s = m.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).limit(n)
                .map(e -> e.getValue() + "x " + AutoBuildTask.shortId(e.getKey())).collect(Collectors.joining(", "));
        return m.size() > n ? s + ", +" + (m.size() - n) + " more" : s;
    }
}
