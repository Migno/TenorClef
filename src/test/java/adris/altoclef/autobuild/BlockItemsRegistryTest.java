package adris.altoclef.autobuild;

//#if MC >= 260000
//$$ import net.minecraft.SharedConstants;
//$$ import net.minecraft.core.registries.BuiltInRegistries;
//$$ import net.minecraft.resources.Identifier;
//$$ import net.minecraft.server.Bootstrap;
//$$ import net.minecraft.world.item.Items;
//$$ import net.minecraft.world.level.block.Block;
//$$ import net.minecraft.world.level.block.state.BlockState;
//$$ import net.minecraft.world.level.block.state.properties.Property;
//$$ import org.junit.jupiter.api.BeforeAll;
//$$ import org.junit.jupiter.api.Test;
//$$
//$$ import java.util.HashMap;
//$$ import java.util.Map;
//$$ import java.util.Set;
//$$ import java.util.TreeSet;
//$$
//$$ import static org.junit.jupiter.api.Assertions.*;
//$$
//$$ /** {@link BlockItems} against every block state of the 26.3 registry. */
//$$ class BlockItemsRegistryTest {
//$$
//$$     @BeforeAll
//$$     static void bootstrap() {
//$$         SharedConstants.tryDetectVersion();
//$$         Bootstrap.bootStrap();
//$$     }
//$$
//$$     private static <T extends Comparable<T>> String value(BlockState s, Property<T> p) {
//$$         return p.getName(s.getValue(p));
//$$     }
//$$
//$$     private static BlockItems.Need need(BlockState s) {
//$$         Map<String, String> props = new HashMap<>();
//$$         for (Property<?> p : s.getProperties()) props.put(p.getName(), value(s, p));
//$$         return BlockItems.needFor(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString(), props,
//$$                 BuiltInRegistries.ITEM.getKey(s.getBlock().asItem()).toString());
//$$     }
//$$
//$$     /** Every item a state maps to exists, and only blocks no player can place have no item at all. */
//$$     @Test
//$$     void everyStateMapsToARealItem() {
//$$         Set<String> noItem = new TreeSet<>();
//$$         for (Block b : BuiltInRegistries.BLOCK) {
//$$             for (BlockState s : b.getStateDefinition().getPossibleStates()) {
//$$                 BlockItems.Need n = need(s);
//$$                 if (n.kind() == BlockItems.Kind.ITEM) {
//$$                     for (String id : n.items().keySet()) {
//$$                         var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
//$$                         assertNotEquals(Items.AIR, item, id + " for " + s);
//$$                     }
//$$                 } else if (n.kind() == BlockItems.Kind.SKIP && "no item".equals(n.reason())) {
//$$                     noItem.add(BuiltInRegistries.BLOCK.getKey(b).toString());
//$$                 }
//$$             }
//$$         }
//$$         System.out.println("autobuild: blocks with no item: " + noItem);
//$$         // nothing a survival builder could be asked to place, or a rule is missing
//$$         for (String id : noItem) {
//$$             assertFalse(id.endsWith("_torch") || id.endsWith("_sign") || id.endsWith("_banner") || id.endsWith("_plant")
//$$                     || id.endsWith("_stem") || id.endsWith("_fan"), "no item for " + id);
//$$         }
//$$     }
//$$
//$$     @Test
//$$     void wallVariantsAndHalvesFromTheRealGame() {
//$$         var torch = need(net.minecraft.world.level.block.Blocks.WALL_TORCH.defaultBlockState());
//$$         assertEquals(Map.of("minecraft:torch", 1), torch.items());
//$$         var sign = need(net.minecraft.world.level.block.Blocks.OAK_WALL_SIGN.defaultBlockState());
//$$         assertEquals(Map.of("minecraft:oak_sign", 1), sign.items());
//$$         var doorTop = net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState()
//$$                 .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
//$$         assertEquals(BlockItems.Kind.NONE, need(doorTop).kind());
//$$         var slab = net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState()
//$$                 .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.DOUBLE);
//$$         assertEquals(Map.of("minecraft:oak_slab", 2), need(slab).items());
//$$         assertEquals(Map.of("minecraft:wheat_seeds", 1), need(net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState()).items());
//$$     }
//$$ }
//#endif
