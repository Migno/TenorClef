package adris.altoclef.tradecycle.mc;

import adris.altoclef.tradecycle.EnchantInfo;
import adris.altoclef.tradecycle.TradeIndex;
import adris.altoclef.tradecycle.TradeOffer;
import adris.altoclef.tradecycle.VillagerRecord;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 26.3 world/registry/merchant reads shared by the scan, the cycle and the overlay. */
final class TradeWorld {

    private TradeWorld() {
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** {server, dimension} for the index file, or null when not in a world. */
    static String[] worldKey() {
        Minecraft mc = mc();
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
        return new String[]{server, mc.level.dimension().identifier().toString()};
    }

    static boolean isLibrarian(Villager v) {
        return v != null && v.getVillagerData().profession().is(VillagerProfession.LIBRARIAN);
    }

    static boolean isUnemployed(Villager v) {
        return v != null && v.getVillagerData().profession().is(VillagerProfession.NONE);
    }

    static Villager villager(String uuid) {
        Minecraft mc = mc();
        if (mc.level == null || uuid == null) return null;
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Villager v && id.equals(v.getUUID()) && v.isAlive()) return v;
        }
        return null;
    }

    static List<Villager> librariansNear(double radius) {
        List<Villager> out = new ArrayList<>();
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null) return out;
        double r2 = radius * radius;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Villager v && v.isAlive() && !v.isBaby() && isLibrarian(v) && v.distanceToSqr(mc.player) <= r2) {
                out.add(v);
            }
        }
        out.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
        return out;
    }

    static boolean isLectern(BlockPos p) {
        Level l = mc().level;
        return l != null && p != null && l.getBlockState(p).is(Blocks.LECTERN);
    }

    static BlockPos pos(int[] p) {
        return p == null || p.length < 3 ? null : new BlockPos(p[0], p[1], p[2]);
    }

    static int[] arr(BlockPos p) {
        return p == null ? null : new int[]{p.getX(), p.getY(), p.getZ()};
    }

    /**
     * Lectern linked to this villager: the previously linked one if it still stands, else the nearest lectern
     * within {@code radius} that is not linked to another indexed villager. Job sites aren't synced to the
     * client, so the link is inferred and the cycle verifies it (profession must drop after breaking).
     */
    static BlockPos findLectern(Villager v, VillagerRecord prev, TradeIndex index, int radius) {
        if (prev != null && prev.lectern != null) {
            BlockPos p = pos(prev.lectern);
            if (isLectern(p) && p.distSqr(v.blockPosition()) <= (radius + 2) * (radius + 2)) return p;
        }
        Set<BlockPos> taken = new HashSet<>();
        for (VillagerRecord r : index.all()) {
            if (r.lectern != null && !r.uuid.equals(v.getUUID().toString())) taken.add(pos(r.lectern));
        }
        BlockPos c = v.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-radius, -2, -radius), c.offset(radius, 2, radius))) {
            if (!isLectern(p) || taken.contains(p)) continue;
            double d = p.distSqr(c);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    static String itemId(ItemStack s) {
        if (s == null || s.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    static String enchantId(Holder<Enchantment> h) {
        return h.unwrapKey().map(k -> k.identifier().toString()).orElse(null);
    }

    static TradeOffer parse(MerchantOffer offer) {
        TradeOffer.Raw raw = new TradeOffer.Raw();
        ItemStack result = offer.getResult();
        raw.resultId = itemId(result);
        raw.resultCount = result.getCount();
        ItemEnchantments stored = result.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) {
            Map<String, Integer> m = new LinkedHashMap<>();
            Set<String> dbl = new HashSet<>();
            for (Object2IntMap.Entry<Holder<Enchantment>> e : stored.entrySet()) {
                String id = enchantId(e.getKey());
                if (id == null) continue;
                m.put(id, e.getIntValue());
                if (e.getKey().is(EnchantmentTags.DOUBLE_TRADE_PRICE)) dbl.add(id);
            }
            raw.storedEnchantments = m;
            raw.doublePriceIds = dbl;
        }
        ItemStack base = offer.getBaseCostA();
        raw.costAId = itemId(base);
        raw.baseCostACount = base.getCount();
        raw.costACount = offer.getCostA().getCount();
        raw.specialPriceDiff = offer.getSpecialPriceDiff();
        ItemStack b = offer.getCostB();
        raw.costBId = itemId(b);
        raw.costBCount = b.isEmpty() ? 0 : b.getCount();
        raw.uses = offer.getUses();
        raw.maxUses = offer.getMaxUses();
        return TradeOffer.parse(raw);
    }

    static TradeIndex.Observation observe(Villager v, MerchantMenu menu, BlockPos lectern) {
        BlockPos p = v.blockPosition();
        TradeIndex.Observation o = new TradeIndex.Observation(v.getUUID().toString(), p.getX(), p.getY(), p.getZ());
        o.lectern = arr(lectern);
        if (menu != null) {
            List<TradeOffer> offers = new ArrayList<>();
            for (MerchantOffer mo : menu.getOffers()) {
                TradeOffer t = parse(mo);
                if (t != null) offers.add(t);
            }
            // The merchant screen's level is authoritative; fall back to the synced entity data.
            int level = menu.getTraderLevel() > 0 ? menu.getTraderLevel() : v.getVillagerData().level();
            o.withOffers(level, menu.getTraderXp(), offers);
        }
        return o;
    }

    static Optional<HolderSet.Named<Enchantment>> tag(net.minecraft.tags.TagKey<Enchantment> key) {
        Minecraft mc = mc();
        if (mc.level == null) return Optional.empty();
        return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
    }

    static EnchantInfo info(Holder<Enchantment> h) {
        String id = enchantId(h);
        return id == null ? null : new EnchantInfo(id, h.value().getMaxLevel(), h.is(EnchantmentTags.DOUBLE_TRADE_PRICE));
    }

    static List<EnchantInfo> tradeable() {
        List<EnchantInfo> out = new ArrayList<>();
        tag(EnchantmentTags.TRADEABLE).ifPresent(set -> {
            for (Holder<Enchantment> h : set) {
                EnchantInfo i = info(h);
                if (i != null) out.add(i);
            }
        });
        out.sort((a, b) -> a.id().compareTo(b.id()));
        return out;
    }

    static EnchantInfo enchant(String id) {
        Minecraft mc = mc();
        if (mc.level == null || id == null) return null;
        try {
            var lookup = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var key = net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, net.minecraft.resources.Identifier.parse(id));
            return lookup.get(key).map(TradeWorld::info).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- inventory ----

    static int count(Player p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.is(item)) n += s.getCount();
        ItemStack off = p.getOffhandItem();
        if (off.is(item)) n += off.getCount();
        return n;
    }

    static int durabilityLeft(ItemStack s) {
        return s.isDamageableItem() ? s.getMaxDamage() - s.getDamageValue() : Integer.MAX_VALUE;
    }

    private static final Item[] AXES_BEST_FIRST = {Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.IRON_AXE,
            Items.COPPER_AXE, Items.STONE_AXE, Items.GOLDEN_AXE, Items.WOODEN_AXE};

    /** Best axe with more than {@code minDurability} uses left, or null. Ranked by tier, then durability. */
    static Item bestAxe(Player p, int minDurability) {
        Collection<ItemStack> inv = p.getInventory().getNonEquipmentItems();
        for (Item tier : AXES_BEST_FIRST) {
            for (ItemStack s : inv) if (s.is(tier) && durabilityLeft(s) > minDurability) return tier;
        }
        for (ItemStack s : inv) if (s.is(ItemTags.AXES) && durabilityLeft(s) > minDurability) return s.getItem();
        return null;
    }

    static boolean hasAnyAxe(Player p) {
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.is(ItemTags.AXES)) return true;
        return false;
    }
}
