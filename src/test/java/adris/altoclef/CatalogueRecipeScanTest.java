package adris.altoclef;

//#if MC >= 260000
//$$ import adris.altoclef.util.CraftingRecipe;
//$$ import adris.altoclef.util.ItemTarget;
//$$ import com.google.gson.JsonArray;
//$$ import com.google.gson.JsonElement;
//$$ import com.google.gson.JsonObject;
//$$ import com.google.gson.JsonParser;
//$$ import net.minecraft.SharedConstants;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.core.registries.BuiltInRegistries;
//$$ import net.minecraft.server.Bootstrap;
//$$ import net.minecraft.world.item.Item;
//$$ import net.minecraft.world.item.Items;
//$$ import org.junit.jupiter.api.BeforeAll;
//$$ import org.junit.jupiter.api.Test;
//$$
//$$ import java.io.IOException;
//$$ import java.io.InputStreamReader;
//$$ import java.lang.reflect.Field;
//$$ import java.net.JarURLConnection;
//$$ import java.net.URL;
//$$ import java.nio.charset.StandardCharsets;
//$$ import java.nio.file.Files;
//$$ import java.nio.file.Path;
//$$ import java.util.ArrayList;
//$$ import java.util.Enumeration;
//$$ import java.util.List;
//$$ import java.util.Map;
//$$ import java.util.TreeMap;
//$$ import java.util.jar.JarEntry;
//$$ import java.util.jar.JarFile;
//$$
//$$ import static org.junit.jupiter.api.Assertions.*;
//$$
//$$ /**
//$$  * Compares the catalogue's crafting recipes with the ones shipped in the Minecraft jar, and writes what it finds to
//$$  * build/reports/recipe-scan.txt. Servers don't sync recipes since 1.21.2, so the catalogue is all CraftingRecipeTracker has.
//$$  */
//$$ public class CatalogueRecipeScanTest {
//$$
//$$     private record VanillaRecipe(String name, List<List<String>> ingredients, int count) {
//$$     }
//$$
//$$     @BeforeAll
//$$     static void bootstrap() throws ReflectiveOperationException {
//$$         SharedConstants.tryDetectVersion();
//$$         Bootstrap.bootStrap();
//$$
//$$         // Building a task reads Minecraft.getInstance().player, so it needs a client to exist. This one has no player.
//$$         Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
//$$         theUnsafe.setAccessible(true);
//$$         Field instance = Minecraft.class.getDeclaredField("instance");
//$$         instance.setAccessible(true);
//$$         instance.set(null, ((sun.misc.Unsafe) theUnsafe.get(null)).allocateInstance(Minecraft.class));
//$$     }
//$$
//$$     @Test
//$$     void bucketRecipeResolves() {
//$$         CraftingRecipe bucket = TaskCatalogue.getCraftingRecipes().get(Items.BUCKET);
//$$         assertNotNull(bucket);
//$$         assertTrue(bucket.isBig());
//$$         assertEquals(1, bucket.outputCount());
//$$     }
//$$
//$$     @Test
//$$     void everyGamerCraftResolves() {
//$$         Map<Item, CraftingRecipe> recipes = TaskCatalogue.getCraftingRecipes();
//$$         // the items BeatMinecraftTask (@gamer) builds a CraftItemPriorityTask for
//$$         Item[] gamerCrafted = {Items.BUCKET, Items.SHIELD, Items.FLINT_AND_STEEL, Items.DIAMOND_SWORD,
//$$                 Items.GOLDEN_HELMET, Items.STONE_SWORD, Items.STONE_AXE, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS,
//$$                 Items.DIAMOND_BOOTS, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE};
//$$         for (Item item : gamerCrafted) {
//$$             assertNotNull(recipes.get(item), "no recipe for " + id(item));
//$$         }
//$$     }
//$$
//$$     @Test
//$$     void scan() throws IOException {
//$$         Map<Item, CraftingRecipe> recipes = TaskCatalogue.getCraftingRecipes();
//$$         Map<String, List<VanillaRecipe>> vanilla = loadVanillaCraftingRecipes();
//$$
//$$         List<String> taskFailed = new ArrayList<>();
//$$         List<String> otherTask = new ArrayList<>();
//$$         List<String> notCatalogued = new ArrayList<>();
//$$         List<String> unknownIngredient = new ArrayList<>();
//$$         List<String> noVanillaRecipe = new ArrayList<>();
//$$         List<String> differsFromVanilla = new ArrayList<>();
//$$         int catalogued = 0;
//$$
//$$         for (Item item : BuiltInRegistries.ITEM) {
//$$             String id = id(item);
//$$             List<VanillaRecipe> forItem = vanilla.get(id);
//$$             if (TaskCatalogue.taskExists(item)) {
//$$                 catalogued++;
//$$                 try {
//$$                     String task = TaskCatalogue.getItemTask(item, 1).getClass().getSimpleName();
//$$                     if (forItem != null && !recipes.containsKey(item)) otherTask.add(id + " (" + task + ")");
//$$                 } catch (RuntimeException e) {
//$$                     taskFailed.add(id + ": " + e);
//$$                 }
//$$             } else if (forItem != null) {
//$$                 notCatalogued.add(id);
//$$             }
//$$
//$$             CraftingRecipe recipe = recipes.get(item);
//$$             if (recipe == null) continue;
//$$             for (ItemTarget slot : recipe.getSlots()) {
//$$                 if (slot.isCatalogueItem() && slot.isEmpty()) {
//$$                     unknownIngredient.add(id + " needs " + slot.getCatalogueName());
//$$                 }
//$$             }
//$$             if (forItem == null) {
//$$                 noVanillaRecipe.add(id);
//$$             } else if (forItem.stream().noneMatch(v -> sameIngredients(recipe, v))) {
//$$                 differsFromVanilla.add(id + ": catalogue " + recipe + " vs vanilla " + forItem);
//$$             } else if (forItem.stream().noneMatch(v -> sameIngredients(recipe, v) && v.count == recipe.outputCount())) {
//$$                 differsFromVanilla.add(id + ": catalogue makes " + recipe.outputCount() + " vs vanilla " + forItem);
//$$             }
//$$         }
//$$
//$$         StringBuilder out = new StringBuilder();
//$$         out.append("catalogued items: ").append(catalogued).append('\n');
//$$         out.append("items with a recipe for CraftingRecipeTracker: ").append(recipes.size()).append('\n');
//$$         out.append("vanilla crafted outputs: ").append(vanilla.size()).append('\n');
//$$         section(out, "catalogued task could not be built", taskFailed);
//$$         section(out, "recipe has an ingredient the catalogue doesn't know", unknownIngredient);
//$$         section(out, "recipe differs from vanilla", differsFromVanilla);
//$$         section(out, "catalogue recipe with no vanilla crafting recipe", noVanillaRecipe);
//$$         section(out, "craftable and catalogued, but no recipe for the tracker (custom task)", otherTask);
//$$         section(out, "craftable but not catalogued", notCatalogued);
//$$
//$$         Path report = Path.of("build", "reports", "recipe-scan.txt");
//$$         Files.createDirectories(report.getParent());
//$$         Files.writeString(report, out.toString());
//$$
//$$         assertTrue(taskFailed.isEmpty(), "catalogued tasks failed to build: " + taskFailed);
//$$     }
//$$
//$$     private static void section(StringBuilder out, String title, List<String> lines) {
//$$         out.append("\n== ").append(title).append(" (").append(lines.size()).append(") ==\n");
//$$         lines.stream().sorted().forEach(line -> out.append(line).append('\n'));
//$$     }
//$$
//$$     private static String id(Item item) {
//$$         return BuiltInRegistries.ITEM.getKey(item).toString();
//$$     }
//$$
//$$     // Tags aren't loaded here, so an ingredient that is a tag matches any slot.
//$$     private static boolean sameIngredients(CraftingRecipe recipe, VanillaRecipe vanilla) {
//$$         List<ItemTarget> slots = new ArrayList<>();
//$$         for (ItemTarget slot : recipe.getSlots()) {
//$$             if (!slot.isEmpty() || slot.isCatalogueItem()) slots.add(slot);
//$$         }
//$$         if (slots.size() != vanilla.ingredients.size()) return false;
//$$
//$$         for (List<String> ingredient : vanilla.ingredients) {
//$$             if (ingredient.stream().anyMatch(s -> s.startsWith("#"))) continue;
//$$             boolean found = false;
//$$             for (int i = 0; i < slots.size() && !found; ++i) {
//$$                 for (Item match : slots.get(i).getMatches()) {
//$$                     if (ingredient.contains(id(match))) {
//$$                         slots.remove(i);
//$$                         found = true;
//$$                         break;
//$$                     }
//$$                 }
//$$             }
//$$             if (!found) return false;
//$$         }
//$$         return true;
//$$     }
//$$
//$$     private static Map<String, List<VanillaRecipe>> loadVanillaCraftingRecipes() throws IOException {
//$$         URL bucket = Item.class.getClassLoader().getResource("data/minecraft/recipe/bucket.json");
//$$         assertNotNull(bucket, "recipes aren't on the test classpath");
//$$
//$$         Map<String, List<VanillaRecipe>> recipes = new TreeMap<>();
//$$         JarFile jar = ((JarURLConnection) bucket.openConnection()).getJarFile();
//$$         Enumeration<JarEntry> entries = jar.entries();
//$$         while (entries.hasMoreElements()) {
//$$             JarEntry entry = entries.nextElement();
//$$             if (!entry.getName().startsWith("data/minecraft/recipe/") || !entry.getName().endsWith(".json")) continue;
//$$
//$$             JsonObject json;
//$$             try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
//$$                 json = JsonParser.parseReader(reader).getAsJsonObject();
//$$             }
//$$             String type = json.get("type").getAsString();
//$$             if (!type.equals("minecraft:crafting_shaped") && !type.equals("minecraft:crafting_shapeless") && !type.equals("minecraft:crafting_transmute")) continue;
//$$
//$$             List<List<String>> ingredients = new ArrayList<>();
//$$             if (json.has("pattern")) {
//$$                 JsonObject key = json.getAsJsonObject("key");
//$$                 for (JsonElement row : json.getAsJsonArray("pattern")) {
//$$                     for (char c : row.getAsString().toCharArray()) {
//$$                         if (c != ' ') ingredients.add(ingredient(key.get(String.valueOf(c))));
//$$                     }
//$$                 }
//$$             } else if (json.has("ingredients")) {
//$$                 for (JsonElement element : json.getAsJsonArray("ingredients")) ingredients.add(ingredient(element));
//$$             } else {
//$$                 ingredients.add(ingredient(json.get("input")));
//$$                 ingredients.add(ingredient(json.get("material")));
//$$             }
//$$
//$$             JsonElement result = json.get("result");
//$$             // map_cloning: its result is whatever the input was
//$$             if (result.isJsonObject() && !result.getAsJsonObject().has("id")) continue;
//$$             String output =result.isJsonObject() ? result.getAsJsonObject().get("id").getAsString() : result.getAsString();
//$$             int count = result.isJsonObject() && result.getAsJsonObject().has("count") ? result.getAsJsonObject().get("count").getAsInt() : 1;
//$$             String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
//$$             recipes.computeIfAbsent(output, k -> new ArrayList<>()).add(new VanillaRecipe(name, ingredients, count));
//$$         }
//$$         return recipes;
//$$     }
//$$
//$$     private static List<String> ingredient(JsonElement element) {
//$$         List<String> options = new ArrayList<>();
//$$         if (element.isJsonArray()) {
//$$             JsonArray array = element.getAsJsonArray();
//$$             for (JsonElement option : array) options.add(option.getAsString());
//$$         } else {
//$$             options.add(element.getAsString());
//$$         }
//$$         return options;
//$$     }
//$$ }
//#endif
