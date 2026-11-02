package adris.altoclef.trackers;

import adris.altoclef.multiversion.RecipeVer;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.multiversion.recipemanager.RecipeManagerWrapper;
import adris.altoclef.multiversion.recipemanager.WrappedRecipeEntry;
import adris.altoclef.util.RecipeTarget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
//#if MC < 260000
import net.minecraft.recipe.*;
//#else
//$$ import net.minecraft.world.item.crafting.CraftingRecipe;
//$$ import net.minecraft.world.item.crafting.Ingredient;
//$$ import net.minecraft.world.item.crafting.CustomRecipe;
//#endif

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

// TODO remove those ugly "ensureUpdate" statements, realistically we only need to update only upon joining a world
public class CraftingRecipeTracker extends Tracker{


    private final HashMap<Item, List<adris.altoclef.util.CraftingRecipe>> itemRecipeMap = new HashMap<>();
    private final HashMap<adris.altoclef.util.CraftingRecipe, ItemStack> recipeResultMap = new HashMap<>();

    private boolean shouldRebuild;

    public CraftingRecipeTracker(TrackerManager manager) {
        super(manager);
        shouldRebuild = true;
    }

    public List<adris.altoclef.util.CraftingRecipe> getRecipeForItem(Item item) {
        ensureUpdated();

        if (!hasRecipeForItem(item)) {
            mod.logWarning("trying to access recipe for unknown item: "+item);
            return null;
        }

        return itemRecipeMap.get(item);
    }

    public adris.altoclef.util.CraftingRecipe getFirstRecipeForItem(Item item) {
        ensureUpdated();

        if (!hasRecipeForItem(item)) {
            mod.logWarning("trying to access recipe for unknown item: "+item);
            return null;
        }

        return itemRecipeMap.get(item).get(0);
    }

    public List<RecipeTarget> getRecipeTarget(Item item, int targetCount) {
        ensureUpdated();

        List<RecipeTarget> targets = new ArrayList<>();
        List<adris.altoclef.util.CraftingRecipe> recipes = getRecipeForItem(item);
        if (recipes == null) return targets;

        for (adris.altoclef.util.CraftingRecipe recipe : recipes) {
            targets.add(new RecipeTarget(item, targetCount, recipe));
        }

        return targets;
    }

    public RecipeTarget getFirstRecipeTarget(Item item, int targetCount) {
        ensureUpdated();

        return new RecipeTarget(item, targetCount, getFirstRecipeForItem(item));
    }

    public boolean hasRecipeForItem(Item item) {
        ensureUpdated();
        return itemRecipeMap.containsKey(item);
    }

    public ItemStack getRecipeResult(adris.altoclef.util.CraftingRecipe recipe) {
        ensureUpdated();

        if (!hasRecipe(recipe)) {
            mod.logWarning("Trying to get result for unknown recipe: "+recipe);
            return null;
        }
        ItemStack result = recipeResultMap.get(recipe);

        return new ItemStack(result.getItem(), result.getCount());
    }

    public boolean hasRecipe(adris.altoclef.util.CraftingRecipe recipe) {
        ensureUpdated();
        return recipeResultMap.containsKey(recipe);
    }


    @Override
    protected void updateState() {
        if (!shouldRebuild) return;

        // rebuild once we are in game
        if (!AltoClef.inGame()) return;

        ClientPlayNetworkHandler networkHandler =  MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler == null) return;

        //#if MC >= 12102
        //$$ // 1.21.2+: servers no longer sync recipes to the client; altoclef's own recipe catalogue is used instead.
        //$$ TaskCatalogue.getCraftingRecipes().forEach((item, recipe) -> {
        //$$     itemRecipeMap.put(item, List.of(recipe));
        //$$     recipeResultMap.put(recipe, new ItemStack(item, recipe.outputCount()));
        //$$ });
        //#else
        RecipeManagerWrapper recipeManager = RecipeManagerWrapper.of(networkHandler.getRecipeManager());

        for (WrappedRecipeEntry recipe : recipeManager.values()) {
            if (!(recipe.value() instanceof net.minecraft.recipe.CraftingRecipe craftingRecipe)) continue;

            // not implemented for now because it isn't needed (I hope xd)
            if (craftingRecipe instanceof SpecialCraftingRecipe) continue;

            // the arguments shouldn't be used, we can just pass null
            ItemStack result = RecipeVer.getOutput(craftingRecipe, null);
            Item[][] altoclefRecipeItems = getShapedCraftingRecipe(craftingRecipe.getIngredients());

            adris.altoclef.util.CraftingRecipe altoclefRecipe = adris.altoclef.util.CraftingRecipe.newShapedRecipe(altoclefRecipeItems, result.getCount());

            if (itemRecipeMap.containsKey(result.getItem())) {
                itemRecipeMap.get(result.getItem()).add(altoclefRecipe);
            } else {
                List<adris.altoclef.util.CraftingRecipe> recipes = new ArrayList<>();
                recipes.add(altoclefRecipe);

                itemRecipeMap.put(result.getItem(), recipes);
            }

            recipeResultMap.put(altoclefRecipe, result);
        }
        //#endif

        itemRecipeMap.replaceAll((k,v) -> Collections.unmodifiableList(v));

        shouldRebuild = false;
    }

    // TODO adjust for small recipes
    // it is always shaped, but that doesn't matter for shapeless
    // the second dimension of the array is for different types of items (eq. logs)
    private static Item[][] getShapedCraftingRecipe(List<Ingredient> ingredients) {
        Item[][] result = new Item[9][];
        int x = 0;

        for (Ingredient ingredient : ingredients) {
                //#if MC >= 12102
                //$$ ItemStack[] stacks = new ItemStack[0];
                //#else
                ItemStack[] stacks = ingredient.getMatchingStacks();
                //#endif
            Item[] items = new Item[stacks.length];

            for (int i = 0; i < stacks.length; i++) {
                ItemStack stack = stacks[i];
                if (stack.getCount() > 1) {
                    throw new IllegalStateException("recipe needs more then one item on a slot... well... shit (ingredients: " + ingredient + ")");
                }

                items[i] = stack.getItem();
            }

            if (stacks.length != 0) {
                // FIXME this is so stupid, but TaskCatalogue is kinda setup this way, so it would require a rewrite to allow for multiple resource :')
                result[x] = new Item[]{items[0]};
            } else {
                result[x] = null;
            }

            x++;
        }


        return result;
    }

    @Override
    protected void reset() {
       shouldRebuild = true;
       itemRecipeMap.clear();
       recipeResultMap.clear();
    }

    @Override
    protected boolean isDirty() {
        return shouldRebuild;
    }
}
