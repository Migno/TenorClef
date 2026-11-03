package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.autobuild.AutoBuildConfig;
import adris.altoclef.autobuild.AutoBuildState;
import adris.altoclef.autobuild.AutoBuildStore;
import adris.altoclef.autobuild.SchematicFiles;
import adris.altoclef.autobuild.mc.AutoBuildMc;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;

import java.io.File;
import java.util.List;

/**
 * Builds a schematic completely, gathering the materials.
 * <pre>
 * @build &lt;file&gt; [x y z] [rotation] [mirror]   file from the schematics folder; origin defaults to your feet
 * @build litematica [n]                       the n-th (default first) Litematica placement, as placed
 * @build status | pause | resume | cancel
 * @build skip &lt;item&gt; | unskip &lt;item&gt;         leave out / build again the blocks of an item
 * @build list                                 schematic files
 * @build set [key value]                      clearArea, reserveSlots, gatherStallSec, huntMobDrops, useContainers
 * </pre>
 * Rotation: 0, 90, 180, 270 (clockwise; also cw90, ccw90). Mirror: none, lr (left_right), fb (front_back).
 * Coordinates may be {@code ~} or {@code ~n}, relative to your feet.
 */
public class AutoBuildCommand extends Command {

    private String rest = "";

    public AutoBuildCommand() {
        super(List.of("build", "autobuild"), "Build a schematic, gathering all materials: <file> [x y z] [rot] [mirror] | litematica [n] | status | pause | resume | cancel | skip <item> | list | set",
                new StringArg("action", "status"));
    }

    @Override
    public void run(AltoClef mod, String line, Runnable onFinish) throws CommandException {
        String[] p = line.trim().split("\\s+", 3);
        rest = p.length > 2 ? p[2].trim() : "";
        super.run(mod, p.length > 1 ? p[0] + " " + p[1] : p[0], onFinish);
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String action = parser.get(String.class);
        String[] a = rest.isEmpty() ? new String[0] : rest.split("\\s+");
        switch (action.toLowerCase(java.util.Locale.ROOT)) {
            case "status" -> AutoBuildMc.status().forEach(l -> mod.log("build: " + l));
            case "pause" -> report(mod, AutoBuildMc.pause(mod), null);
            case "cancel" -> report(mod, AutoBuildMc.cancel(mod), null);
            case "resume" -> {
                String err = AutoBuildMc.resume(mod, this::finish);
                if (err == null) return; // finishes with the task
                report(mod, err, null);
            }
            case "skip", "unskip" -> {
                if (a.length == 0) {
                    mod.log("build: " + action + " <item>");
                } else {
                    mod.log("build: " + AutoBuildMc.skip(a[0], action.equalsIgnoreCase("skip")));
                }
            }
            case "list" -> list(mod);
            case "set" -> set(mod, a);
            case "litematica" -> {
                AutoBuildState s = new AutoBuildState();
                s.litematica = true;
                s.litematicaIndex = a.length > 0 ? Math.max(0, parseInt(a[0], 1) - 1) : 0;
                if (!AutoBuildMc.supported()) {
                    report(mod, AutoBuildMc.UNSUPPORTED, null);
                } else if (!AutoBuildMc.litematicaPresent()) {
                    report(mod, "Litematica is not loaded", null);
                } else {
                    String err = AutoBuildMc.start(mod, s, this::finish);
                    if (err == null) return;
                    report(mod, err, null);
                }
            }
            default -> {
                String err = startFile(mod, action, a);
                if (err == null) return;
                report(mod, err, null);
            }
        }
        finish();
    }

    private String startFile(AltoClef mod, String name, String[] a) {
        if (!AutoBuildMc.supported()) return AutoBuildMc.UNSUPPORTED;
        File dir = AutoBuildMc.schematicsDir();
        File file = SchematicFiles.resolve(dir, name);
        if (file == null) return "no schematic '" + name + "' in " + dir.getAbsolutePath() + " (@build list)";
        int[] feet = AutoBuildMc.playerFeet();
        if (feet == null) return "not in a world";
        AutoBuildState s = new AutoBuildState();
        s.file = file.getName();
        s.origin = feet.clone();
        int i = 0;
        if (a.length >= 3 && SchematicFiles.isCoord(a[0]) && SchematicFiles.isCoord(a[1]) && SchematicFiles.isCoord(a[2])) {
            for (int k = 0; k < 3; k++) s.origin[k] = SchematicFiles.coord(a[k], feet[k]);
            i = 3;
        }
        for (; i < a.length; i++) {
            String rot = AutoBuildState.parseRotation(a[i]);
            String mir = AutoBuildState.parseMirror(a[i]);
            if (rot != null) s.rotation = rot;
            else if (mir != null) s.mirror = mir;
            else return "don't know '" + a[i] + "': rotation 0|90|180|270, mirror none|lr|fb";
        }
        return AutoBuildMc.start(mod, s, this::finish);
    }

    private void list(AltoClef mod) {
        File dir = AutoBuildMc.schematicsDir();
        List<File> files = SchematicFiles.list(dir);
        if (files.isEmpty()) {
            mod.log("build: no schematics in " + dir.getAbsolutePath() + " (.litematic, .schem, .schematic)");
            return;
        }
        mod.log("build: " + files.size() + " schematic(s) in " + dir.getAbsolutePath());
        for (File f : files) mod.log("  " + f.getName() + "  " + Math.max(1, f.length() / 1024) + "k");
    }

    private void set(AltoClef mod, String[] a) {
        AutoBuildStore store = AutoBuildStore.get();
        AutoBuildConfig c = store.config();
        if (a.length >= 2) {
            String err = c.set(a[0], a[1]);
            if (err != null) {
                mod.log("build: " + err);
                return;
            }
            store.saveConfig();
        } else if (a.length == 1 && a[0].equalsIgnoreCase("reload")) {
            store.reloadConfig();
            c = store.config();
        }
        mod.log("build: " + c.describe());
    }

    private static void report(AltoClef mod, String err, String ok) {
        if (err != null) mod.log("build: " + err);
        else if (ok != null) mod.log("build: " + ok);
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
