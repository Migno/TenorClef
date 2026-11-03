package adris.altoclef.tradecycle.mc;

import adris.altoclef.AltoClef;
import adris.altoclef.multiversion.InteractionHelper;
import adris.altoclef.multiversion.ScreenVer;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;

/**
 * Walks to a villager, opens its trade screen and waits for the offers packet. The caller reads
 * {@link #menu()} once {@link #isFinished()}; with {@code keepOpen} the screen stays open (for buying).
 */
class ReadOffersTask extends Task {

    private static final int TIMEOUT_TICKS = 20 * 25;
    private static final double REACH = 2.8;

    private final String uuid;
    private final boolean keepOpen;
    private int ticks;
    private int lastClick = -100;
    private int offersSeenAt = -1;
    private MerchantMenu menu;
    private String failure;

    ReadOffersTask(String uuid, boolean keepOpen) {
        this.uuid = uuid;
        this.keepOpen = keepOpen;
    }

    MerchantMenu menu() {
        return menu;
    }

    String failure() {
        return failure;
    }

    @Override
    protected void onStart() {
        ticks = 0;
        menu = null;
        failure = null;
        offersSeenAt = -1;
        Screen s = ScreenVer.current(Minecraft.getInstance());
        if (s instanceof MerchantScreen) StorageHelper.closeScreen();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (menu != null || failure != null) return null;
        ticks++;
        if (ticks > TIMEOUT_TICKS) {
            failure = "timed out opening the trade screen";
            StorageHelper.closeScreen();
            return null;
        }
        Villager v = TradeWorld.villager(uuid);
        if (v == null) {
            failure = "villager not loaded";
            return null;
        }
        Screen screen = ScreenVer.current(mc);
        if (screen instanceof MerchantScreen ms) {
            MerchantMenu m = ms.getMenu();
            if (!m.getOffers().isEmpty()) {
                if (offersSeenAt < 0) offersSeenAt = ticks;
                // Level/XP arrive in the same packet as the offers; give it two ticks to settle.
                if (ticks - offersSeenAt >= 2) {
                    menu = m;
                    if (!keepOpen) {
                        // Keep a detached copy alive: values are read from the menu object, which survives closing.
                        StorageHelper.closeScreen();
                    }
                }
            }
            setDebugState("Reading offers");
            return null;
        }
        if (v.distanceTo(mod.getPlayer()) > REACH) {
            setDebugState("Walking to librarian");
            return new GetToEntityTask(v, 2.0);
        }
        if (mod.getClientBaritone().getPathingBehavior().isPathing()) {
            mod.getClientBaritone().getPathingBehavior().cancelEverything();
        }
        LookHelper.lookAt(mod, v.getEyePosition());
        if (ticks - lastClick >= 15) {
            lastClick = ticks;
            InteractionHelper.interactEntity(mc.gameMode, mod.getPlayer(), v, InteractionHand.MAIN_HAND);
        }
        setDebugState("Opening trade screen");
        return null;
    }

    @Override
    public boolean isFinished() {
        return menu != null || failure != null;
    }

    @Override
    protected void onStop(Task interruptTask) {
        if (!keepOpen || menu == null) {
            Screen s = ScreenVer.current(Minecraft.getInstance());
            if (s instanceof MerchantScreen) StorageHelper.closeScreen();
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ReadOffersTask t && t.uuid.equals(uuid) && t.keepOpen == keepOpen;
    }

    @Override
    protected String toDebugString() {
        return "Read trades of " + uuid.substring(0, 8);
    }
}
