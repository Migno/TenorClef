package adris.altoclef.autobuild.mc;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.autobuild.AutoBuildConfig;
import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.util.ItemTarget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Item ids, inventory counts and whether the catalogue ({@code @get}) can get an item by itself. */
final class McItems {

    private static Map<Item, CraftingRecipe> recipes;

    private McItems() {
    }

    static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** The item with this id, or null when there is none. */
    static Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(key);
        return item == null || item == Items.AIR ? null : item;
    }

    static int count(AltoClef mod, Item item) {
        try {
            return mod.getItemStorage().getItemCountInventoryOnly(item);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Inventory counts of these item ids. */
    static Map<String, Integer> have(AltoClef mod, Iterable<String> ids) {
        Map<String, Integer> out = new HashMap<>();
        for (String id : ids) {
            Item item = item(id);
            if (item != null) out.put(id, count(mod, item));
        }
        return out;
    }

    static Map<String, Integer> stackSizes(Iterable<String> ids) {
        Map<String, Integer> out = new HashMap<>();
        for (String id : ids) {
            Item item = item(id);
            if (item != null) out.put(id, item.getDefaultMaxStackSize());
        }
        return out;
    }

    /** Empty slots of the 36 main inventory slots (hotbar included, armor and offhand not). */
    static int freeSlots(Player p) {
        List<ItemStack> main = p.getInventory().getNonEquipmentItems();
        int free = 0;
        for (int i = 0; i < Math.min(36, main.size()); i++) if (main.get(i).isEmpty()) free++;
        return free;
    }

    /** Null when {@code @get} can produce the item here, else why not. */
    static String whyNotAuto(Item item, AutoBuildConfig cfg) {
        if (item == null) return "not an item";
        if (!TaskCatalogue.taskExists(item)) return "no way to get it known";
        if (!TaskCatalogue.obtainableNow(item)) return "its mob does not spawn here";
        if (!cfg.huntMobDrops && needsMobDrop(item, 2)) return "mob drop";
        return null;
    }

    /** Killing a mob gets it, or one of the ingredients of its recipe (each slot every alternative), {@code depth} deep. */
    private static boolean needsMobDrop(Item item, int depth) {
        if (TaskCatalogue.isMobDrop(item)) return true;
        if (depth <= 0) return false;
        CraftingRecipe r = recipes().get(item);
        if (r == null) return false;
        for (ItemTarget slot : r.getSlots()) {
            if (slot == null || slot.getMatches() == null || slot.getMatches().length == 0) continue;
            boolean all = true;
            for (Item m : slot.getMatches()) {
                if (m == null || !needsMobDrop(m, depth - 1)) {
                    all = false;
                    break;
                }
            }
            if (all) return true;
        }
        return false;
    }

    private static synchronized Map<Item, CraftingRecipe> recipes() {
        if (recipes == null) {
            try {
                recipes = TaskCatalogue.getCraftingRecipes();
            } catch (Throwable t) {
                recipes = Map.of();
            }
        }
        return recipes;
    }
}
