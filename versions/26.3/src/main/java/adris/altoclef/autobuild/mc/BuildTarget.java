package adris.altoclef.autobuild.mc;

import adris.altoclef.autobuild.AutoBuildState;
import adris.altoclef.autobuild.BlockItems;
import adris.altoclef.autobuild.MaterialList;
import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.process.IBuilderProcess;
import baritone.api.schematic.IStaticSchematic;
import baritone.api.schematic.ISchematic;
import baritone.api.schematic.MaskSchematic;
import baritone.api.schematic.MirroredSchematic;
import baritone.api.schematic.RotatedSchematic;
import baritone.api.schematic.format.ISchematicFormat;
import baritone.api.utils.Pair;
import baritone.utils.schematic.litematica.LitematicaHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A loaded schematic placed in the world: scans it against the world into a {@link MaterialList} and starts
 * Ostinato's builder on it, leaving out the blocks whose items are not to be had.
 * <p>
 * A file is handed to the builder un-oriented with {@code buildSchematicRotation/Mirror} set for that one call
 * (the builder orients it, exactly as {@code #build} does); a Litematica placement comes oriented already, so the
 * builder gets it with both settings at NONE.
 */
final class BuildTarget {

    /** The block, its properties and its item, as plain strings for {@link BlockItems}. */
    record Spec(String block, Map<String, String> props, String asItem, BlockItems.Need need) {
    }

    final String name;
    /** What the builder is given (it orients a file's schematic itself). */
    private final ISchematic forBuilder;
    /** World-aligned: cell (x, y, z) is at {@link #corner} + (x, y, z). */
    private final ISchematic oriented;
    private final Vec3i builderOrigin;
    final BlockPos corner;
    private final Rotation rotation;
    private final Mirror mirror;
    private final Map<BlockState, Spec> specs = new java.util.concurrent.ConcurrentHashMap<>(); // the builder masks from its path thread too
    int unloadedCells;

    private BuildTarget(String name, ISchematic forBuilder, ISchematic oriented, Vec3i builderOrigin, BlockPos corner,
                        Rotation rotation, Mirror mirror) {
        this.name = name;
        this.forBuilder = forBuilder;
        this.oriented = oriented;
        this.builderOrigin = builderOrigin;
        this.corner = corner;
        this.rotation = rotation;
        this.mirror = mirror;
    }

    static File schematicsDir() {
        return new File(Minecraft.getInstance().gameDirectory, "schematics");
    }

    /** Loads the schematic of a saved build. Fills in origin/rotation for a Litematica placement. */
    static BuildTarget load(AutoBuildState s) throws LoadException {
        if (s.litematica) {
            if (!LitematicaHelper.isLitematicaPresent()) throw new LoadException("Litematica is not loaded");
            if (!LitematicaHelper.hasLoadedSchematic(s.litematicaIndex)) {
                throw new LoadException("Litematica has no placement " + (s.litematicaIndex + 1));
            }
            Pair<IStaticSchematic, Vec3i> p = LitematicaHelper.getSchematic(s.litematicaIndex);
            Vec3i o = p.second();
            s.origin = new int[]{o.getX(), o.getY(), o.getZ()};
            return new BuildTarget("litematica " + (s.litematicaIndex + 1), p.first(), p.first(), o,
                    corner(o, p.first()), Rotation.NONE, Mirror.NONE);
        }
        File f = new File(schematicsDir(), s.file);
        if (!f.isFile()) throw new LoadException("no file " + f.getAbsolutePath());
        Optional<ISchematicFormat> format = BaritoneAPI.getProvider().getSchematicSystem().getByFile(f);
        if (format.isEmpty()) throw new LoadException("unsupported schematic format: " + f.getName());
        IStaticSchematic raw;
        try (InputStream in = new FileInputStream(f)) {
            raw = format.get().parse(in);
        } catch (Exception e) {
            throw new LoadException("could not read " + f.getName() + ": " + e);
        }
        Rotation rot = Rotation.valueOf(s.rotation);
        Mirror mir = Mirror.valueOf(s.mirror);
        ISchematic or = raw;
        if (mir != Mirror.NONE) or = new MirroredSchematic(or, mir);
        if (rot != Rotation.NONE) or = new RotatedSchematic(or, rot);
        Vec3i origin = new Vec3i(s.origin[0], s.origin[1], s.origin[2]);
        return new BuildTarget(f.getName(), raw, or, origin, corner(origin, raw), rot, mir);
    }

    /** The builder moves its corner by these settings ({@code BuilderProcess.build}), so the scan must too. */
    private static BlockPos corner(Vec3i origin, ISchematic given) {
        Settings st = BaritoneAPI.getSettings();
        return new BlockPos(origin.getX() + (st.schematicOrientationX.value ? given.widthX() : 0),
                origin.getY() + (st.schematicOrientationY.value ? given.heightY() : 0),
                origin.getZ() + (st.schematicOrientationZ.value ? given.lengthZ() : 0));
    }

    /** Lowest corner and size of the build in the world. */
    BlockPos min() {
        return corner;
    }

    BlockPos max() {
        return corner.offset(oriented.widthX() - 1, oriented.heightY() - 1, oriented.lengthZ() - 1);
    }

    String size() {
        return oriented.widthX() + "x" + oriented.heightY() + "x" + oriented.lengthZ();
    }

    /** What every cell still needs, given the world as the client sees it. Cells whose item is {@code leftOut} are counted as skipped. */
    MaterialList scan(Level level, Set<String> leftOut) {
        MaterialList m = new MaterialList();
        unloadedCells = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        // the builder treats these as correct wherever they are wanted, so they are no work
        List<net.minecraft.world.level.block.Block> builderSkips = BaritoneAPI.getSettings().buildSkipBlocks.value;
        for (int y = 0; y < oriented.heightY(); y++) {
            for (int x = 0; x < oriented.widthX(); x++) {
                for (int z = 0; z < oriented.lengthZ(); z++) {
                    p.set(corner.getX() + x, corner.getY() + y, corner.getZ() + z);
                    BlockState cur;
                    if (level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) {
                        cur = level.getBlockState(p);
                    } else {
                        cur = air;
                        unloadedCells++;
                    }
                    if (!oriented.inSchematic(x, y, z, cur)) continue;
                    BlockState want = oriented.desiredState(x, y, z, cur, List.of());
                    if (want == null) continue;
                    Spec w = spec(want);
                    if (!builderSkips.isEmpty() && builderSkips.contains(want.getBlock())) {
                        m.addSkipped("in buildSkipBlocks", w.block());
                        continue;
                    }
                    if (w.need().kind() == BlockItems.Kind.ITEM && !leftOut.isEmpty()
                            && w.need().items().keySet().stream().anyMatch(leftOut::contains)) {
                        m.addSkipped("left out", w.block());
                        continue;
                    }
                    Spec c = spec(cur);
                    m.addCell(y, w.block(), w.props(), w.asItem(), c.block(), c.props(), cur.isAir());
                }
            }
        }
        return m;
    }

    /**
     * Starts Ostinato's builder, leaving out cells it cannot do anything with: unbuildable blocks (liquids,
     * portals...) and blocks whose item is in {@code excluded} (skipped, or still to be handed over).
     */
    void startBuilder(IBuilderProcess builder, Set<String> excluded) {
        ISchematic masked = new MaskSchematic(forBuilder) {
            @Override
            protected boolean partOfMask(int x, int y, int z, BlockState cur) {
                BlockState want = forBuilder.desiredState(x, y, z, cur, List.of());
                if (want == null) return false;
                BlockItems.Need n = spec(want).need();
                if (n.kind() == BlockItems.Kind.SKIP) return false;
                return n.kind() != BlockItems.Kind.ITEM || n.items().keySet().stream().noneMatch(excluded::contains);
            }
        };
        Settings st = BaritoneAPI.getSettings();
        Rotation oldRot = st.buildSchematicRotation.value;
        Mirror oldMir = st.buildSchematicMirror.value;
        st.buildSchematicRotation.value = rotation;
        st.buildSchematicMirror.value = mirror;
        try {
            builder.build("autobuild " + name, masked, builderOrigin);
        } finally {
            st.buildSchematicRotation.value = oldRot;
            st.buildSchematicMirror.value = oldMir;
        }
    }

    void startClear(IBuilderProcess builder) {
        builder.clearArea(min(), max());
    }

    Spec spec(BlockState state) {
        Spec s = specs.get(state);
        if (s == null) {
            String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            Map<String, String> props = new HashMap<>();
            for (Property<?> prop : state.getProperties()) props.put(prop.getName(), valueName(state, prop));
            String asItem = McItems.id(state.getBlock().asItem());
            s = new Spec(block, props, asItem, BlockItems.needFor(block, props, asItem));
            specs.put(state, s);
        }
        return s;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    static final class LoadException extends Exception {
        LoadException(String message) {
            super(message);
        }
    }
}
