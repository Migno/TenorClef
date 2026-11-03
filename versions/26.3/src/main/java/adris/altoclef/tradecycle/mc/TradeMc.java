package adris.altoclef.tradecycle.mc;

import adris.altoclef.AltoClef;
import adris.altoclef.tradecycle.EnchantInfo;
import adris.altoclef.tradecycle.TradeStore;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.List;

/**
 * 26.3 implementation of the trade system's Minecraft side (replaces the stub in {@code src/main/java}).
 * Registers the Controls → TenorClef keybinds, follows server/dimension changes to load the right index,
 * keeps villager positions fresh and draws the overlay.
 */
public final class TradeMc {

    public static final String UNSUPPORTED = "the trade system needs the Minecraft 26.3 build";

    private static KeyMapping overlayKey;
    private static KeyMapping screenKey;
    private static boolean overlayOn;
    private static int tick;
    private static boolean positionsDirty;
    private static long lastPositionSave;

    private TradeMc() {
    }

    public static boolean supported() {
        return true;
    }

    public static void init() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("altoclef", "tenorclef"));
        overlayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.altoclef.trades_overlay", InputConstants.KEY_O, cat));
        screenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.altoclef.trades_screen", InputConstants.KEY_P, cat));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                onTick(mc);
            } catch (Throwable t) {
                if (tick % 200 == 0) t.printStackTrace();
            }
        });
        LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> {
            if (!overlayOn) return;
            try {
                TradeOverlay.render();
            } catch (Throwable t) {
                if (tick % 200 == 0) t.printStackTrace();
            }
        });
    }

    private static void onTick(Minecraft mc) {
        tick++;
        TradeStore store = TradeStore.get();
        String[] key = TradeWorld.worldKey();
        if (key == null) {
            if (store.hasWorld()) store.switchTo(null, null);
            return;
        }
        store.switchTo(key[0], key[1]);
        while (overlayKey.consumeClick()) {
            overlayOn = !overlayOn;
            if (mc.player != null) mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Trade overlay " + (overlayOn ? "on" : "off")));
        }
        while (screenKey.consumeClick()) openScreen();
        if (tick % 20 == 0 && mc.level != null) {
            long now = System.currentTimeMillis();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof Villager v) {
                    var p = v.blockPosition();
                    if (store.index().seen(v.getUUID().toString(), p.getX(), p.getY(), p.getZ(), now)) positionsDirty = true;
                }
            }
            if (positionsDirty) {
                store.touch();
                // Wandering villagers would otherwise rewrite the file every second.
                if (now - lastPositionSave > 10_000) {
                    store.saveIndex();
                    lastPositionSave = now;
                    positionsDirty = false;
                }
            }
        }
    }

    public static String startScan(AltoClef mod, int radius, Runnable onFinish) {
        if (mod.getPlayer() == null) return "not in a world";
        mod.runUserTask(new TradeScanTask(Math.max(1, radius)), onFinish);
        return null;
    }

    public static String startCycle(AltoClef mod, int radius, boolean upgrade, Runnable onFinish) {
        if (mod.getPlayer() == null) return "not in a world";
        if (TradeStore.get().config().wanted.isEmpty()) return "wanted list is empty (@trades want add <enchant> ... or @trades want all [best])";
        mod.runUserTask(new TradeCycleTask(Math.max(1, radius), upgrade), onFinish);
        return null;
    }

    public static EnchantInfo enchant(String id) {
        return TradeWorld.enchant(id);
    }

    public static List<EnchantInfo> tradeable() {
        return TradeWorld.tradeable();
    }

    public static double[] playerPos() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : new double[]{p.getX(), p.getY(), p.getZ()};
    }

    public static boolean toggleOverlay() {
        overlayOn = !overlayOn;
        return overlayOn;
    }

    public static boolean overlayOn() {
        return overlayOn;
    }

    public static void openScreen() {
        Minecraft mc = Minecraft.getInstance();
        // Chat commands run while the chat screen is closing; open on the next tick.
        mc.execute(() -> mc.setScreenAndShow(new TradeIndexScreen()));
    }
}
