package adris.altoclef.tradecycle.mc;

import adris.altoclef.tradecycle.TradeCycleConfig;
import adris.altoclef.tradecycle.TradeStore;
import adris.altoclef.tradecycle.TradeView;
import adris.altoclef.tradecycle.VillagerRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Through-wall overlay drawn with 26.3 gizmos from {@code LevelRenderEvents.BEFORE_GIZMOS}: an outline box per
 * indexed villager coloured by status, floating label lines and a line to the linked lectern.
 * Label text and colours are rebuilt only when {@link TradeStore#version()} changes.
 */
final class TradeOverlay {

    private record Entry(VillagerRecord rec, int color, List<String> lines, int[] lineColors) {
    }

    private static long cachedVersion = -1;
    private static List<Entry> cache = new ArrayList<>();

    private TradeOverlay() {
    }

    private static void rebuild(TradeStore store) {
        TradeCycleConfig c = store.config();
        List<Entry> out = new ArrayList<>();
        for (VillagerRecord r : store.index().all()) {
            int color = TradeView.tint(r, c.wanted, c.bestTolerance).argb;
            List<String> lines = TradeView.labelLines(r, c.bestTolerance);
            int[] colors = new int[lines.size()];
            for (int i = 0; i < lines.size(); i++) colors[i] = i == lines.size() - 1 ? color : (lines.get(i).endsWith("BEST") ? 0xFF55FF55 : 0xFFFFFFFF);
            out.add(new Entry(r, color, lines, colors));
        }
        cache = out;
        cachedVersion = store.version();
    }

    static void render() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        TradeStore store = TradeStore.get();
        if (store.version() != cachedVersion) rebuild(store);
        if (cache.isEmpty()) return;

        Map<UUID, Villager> loaded = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) if (e instanceof Villager v) loaded.put(v.getUUID(), v);

        double maxD = store.config().overlayDistance;
        double maxD2 = maxD * maxD;
        Vec3 eye = mc.player.getEyePosition();
        try (var ignored = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            for (Entry en : cache) {
                VillagerRecord r = en.rec();
                Villager v = null;
                try {
                    v = loaded.get(UUID.fromString(r.uuid));
                } catch (IllegalArgumentException ignoredBadId) {
                }
                AABB box = v != null ? v.getBoundingBox()
                        : new AABB(r.x + 0.2, r.y, r.z + 0.2, r.x + 0.8, r.y + 1.95, r.z + 0.8);
                Vec3 top = new Vec3((box.minX + box.maxX) / 2, box.maxY, (box.minZ + box.maxZ) / 2);
                double d2 = top.distanceToSqr(eye);
                if (d2 > maxD2) continue;

                Gizmos.cuboid(box.inflate(0.05), GizmoStyle.stroke(en.color(), 2.0f)).setAlwaysOnTop();
                if (r.lectern != null) {
                    Vec3 lp = new Vec3(r.lectern[0] + 0.5, r.lectern[1] + 0.9, r.lectern[2] + 0.5);
                    Gizmos.line(new Vec3(top.x, box.minY + 1.0, top.z), lp, en.color(), 1.5f).setAlwaysOnTop();
                    Gizmos.cuboid(new AABB(r.lectern[0], r.lectern[1], r.lectern[2], r.lectern[0] + 1, r.lectern[1] + 1, r.lectern[2] + 1).inflate(0.02),
                            GizmoStyle.stroke((en.color() & 0x00FFFFFF) | 0x80000000, 1.0f)).setAlwaysOnTop();
                }
                // Scale with distance so labels stay readable far away.
                float scale = (float) (0.35 * Math.max(1.0, Math.sqrt(d2) / 10.0));
                double step = scale * 0.6;
                List<String> lines = en.lines();
                for (int i = 0; i < lines.size(); i++) {
                    Vec3 at = top.add(0, 0.35 + step * (lines.size() - 1 - i), 0);
                    Gizmos.billboardText(lines.get(i), at, TextGizmo.Style.forColorAndCentered(en.lineColors()[i]).withScale(scale)).setAlwaysOnTop();
                }
            }
        }
    }
}
