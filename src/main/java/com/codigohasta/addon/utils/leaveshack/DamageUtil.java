package com.codigohasta.addon.utils.leaveshack;

import com.codigohasta.addon.modules.GlobalSetting;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ClipContext;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import static meteordevelopment.meteorclient.MeteorClient.mc;


public class DamageUtil {
    // Explosion damage

    public static float calculateDamage(BlockPos pos, LivingEntity entity) {
        return DamageUtil.explosionDamage(entity, null, new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5), 12);
    }

    public static float calculateDamage(Vec3 pos, LivingEntity entity) {
        return DamageUtil.explosionDamage(entity, null, pos, 12);
    }

    public static final RaycastFactory HIT_FACTORY = (context, blockPos) -> {
        BlockState blockState = mc.level.getBlockState(blockPos);
        if (blockState.getBlock().getExplosionResistance() < 600) return null;

        return blockState.getCollisionShape(mc.level, blockPos).clip(context.start(), context.end(), blockPos);
    };

    public static float explosionDamage(LivingEntity target, Vec3 targetPos, AABB targetBox, Vec3 explosionPos, float power, RaycastFactory raycastFactory) {
        double modDistance = distance(targetPos.x, targetPos.y, targetPos.z, explosionPos.x, explosionPos.y, explosionPos.z);
        if (modDistance > power) return 0f;

        double exposure = getExposure(explosionPos, targetBox, raycastFactory);
        double impact = (1 - (modDistance / power)) * exposure;
        float damage = (int) ((impact * impact + impact) / 2 * 7 * 12 + 1);

        return calculateReductionsExplosion(damage, target, mc.level.damageSources().explosion(null));
    }

    public static float anchorDamage(LivingEntity target, LivingEntity predict, Vec3 anchor) {
        return overridingExplosionDamage(target, predict, anchor, 10f, BlockPos.containing(anchor), Blocks.AIR.defaultBlockState());
    }

    public static float overridingExplosionDamage(LivingEntity target, LivingEntity predict, Vec3 explosionPos, float power, BlockPos overridePos, BlockState overrideState) {
        return explosionDamage(target, predict, explosionPos, power, getOverridingHitFactory(overridePos, overrideState));
    }

    private static float explosionDamage(LivingEntity target, LivingEntity predict, Vec3 explosionPos, float power, RaycastFactory raycastFactory) {
        if (target == null) return 0f;
        if (target instanceof Player player && getGameMode(player) == GameType.CREATIVE) return 0f;

        return explosionDamage(target, predict != null ? new Vec3(predict.getX(), predict.getY(), predict.getZ()) : new Vec3(target.getX(), target.getY(), target.getZ()), predict != null ? predict.getBoundingBox() : target.getBoundingBox(), explosionPos, power, raycastFactory);
    }

    public static float explosionDamage(LivingEntity target, LivingEntity predict, Vec3 explosionPos, float power) {
        if (target == null) return 0f;
        if (target instanceof Player player && getGameMode(player) == GameType.CREATIVE) return 0f;

        return explosionDamage(target, predict != null ? new Vec3(predict.getX(), predict.getY(), predict.getZ()) : new Vec3(target.getX(), target.getY(), target.getZ()), predict != null ? predict.getBoundingBox() : target.getBoundingBox(), explosionPos, power, HIT_FACTORY);
    }

    public static RaycastFactory getOverridingHitFactory(BlockPos overridePos, BlockState overrideState) {
        return (context, blockPos) -> {
            BlockState blockState;
            if (blockPos.equals(overridePos)) blockState = overrideState;
            else {
                blockState = mc.level.getBlockState(blockPos);
                if (blockState.getBlock().getExplosionResistance() < 600) return null;
            }

            return blockState.getCollisionShape(mc.level, blockPos).clip(context.start(), context.end(), blockPos);
        };
    }
    // Fall Damage

    public static float fallDamage(LivingEntity entity) {
        if (entity instanceof Player player && player.getAbilities().flying) return 0f;
        if (entity.hasEffect(MobEffects.SLOW_FALLING) || entity.hasEffect(MobEffects.LEVITATION))
            return 0f;

        // Fast path - Above the surface
        int surface = mc.level.getChunkAt(entity.blockPosition()).getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING).getFirstAvailable(entity.getBlockX() & 15, entity.getBlockZ() & 15);
        if (entity.getBlockY() >= surface) return fallDamageReductions(entity, surface);

        // Under the surface
        BlockHitResult raycastResult = mc.level.clip(new ClipContext(new Vec3(entity.getX(), entity.getY(), entity.getZ()), new Vec3(entity.getX(), mc.level.getMinY(), entity.getZ()), ClipContext.Block.COLLIDER, ClipContext.Fluid.WATER, entity));
        if (raycastResult.getType() == HitResult.Type.MISS) return 0;

        return fallDamageReductions(entity, raycastResult.getBlockPos().getY());
    }

    private static float fallDamageReductions(LivingEntity entity, int surface) {
        int fallHeight = (int) (entity.getY() - surface + entity.fallDistance - 3d);
        @Nullable MobEffectInstance jumpBoostInstance = entity.getEffect(MobEffects.JUMP_BOOST);
        if (jumpBoostInstance != null) fallHeight -= jumpBoostInstance.getAmplifier() + 1;

        return calculateReductions(fallHeight, entity, mc.level.damageSources().fall());
    }

    // Utils

    public static float calculateReductionsExplosion(float damage, LivingEntity entity, DamageSource damageSource) {
        if (damageSource.scalesWithDifficulty()) {
            switch (mc.level.getDifficulty()) {
                case EASY -> damage = Math.min(damage / 2 + 1, damage);
                case HARD -> damage *= 1.5f;
            }
        }

        // Armor reduction
        damage = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(entity, damage, damageSource, getArmor(entity), (float) getARMOR_TOUGHNESS(entity));

        // Resistance reduction
        damage = resistanceReduction(entity, damage);

        // Protection reduction
        damage = net.minecraft.world.damagesource.CombatRules.getDamageAfterMagicAbsorb(damage, getProtectionAmount(getArmorItems(entity)));

        return Math.max(damage, 0);
    }

    public static float calculateReductions(float damage, LivingEntity entity, DamageSource damageSource) {
        if (damageSource.scalesWithDifficulty()) {
            switch (mc.level.getDifficulty()) {
                case EASY -> damage = Math.min(damage / 2 + 1, damage);
                case HARD -> damage *= 1.5f;
            }
        }

        // Armor reduction
        damage = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(entity, damage, damageSource, getArmor(entity), (float) getARMOR_TOUGHNESS(entity));

        // Resistance reduction
        damage = resistanceReduction(entity, damage);

        // Protection reduction
        damage = protectionReduction(entity, damage, damageSource);

        return Math.max(damage, 0);
    }

    public static double getARMOR_TOUGHNESS(LivingEntity entity) {
        return entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
    }

    private static float getArmor(LivingEntity entity) {
        return (float) Math.floor(entity.getAttributeValue(Attributes.ARMOR));
    }

    private static float protectionReduction(LivingEntity player, float damage, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return damage;

        int damageProtection = 0;

        for (ItemStack stack : getArmorItems(player)) {
            Object2IntMap<Holder<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
            getEnchantments(stack, enchantments);

            int protection = getEnchantmentLevel(enchantments, Enchantments.PROTECTION);
            if (protection > 0) {
                damageProtection += protection;
            }

            int fireProtection = getEnchantmentLevel(enchantments, Enchantments.FIRE_PROTECTION);
            if (fireProtection > 0 && source.is(DamageTypeTags.IS_FIRE)) {
                damageProtection += 2 * fireProtection;
            }

            int blastProtection = getEnchantmentLevel(enchantments, Enchantments.BLAST_PROTECTION);
            if (blastProtection > 0 && source.is(DamageTypeTags.IS_EXPLOSION)) {
                damageProtection += 2 * blastProtection;
            }

            int projectileProtection = getEnchantmentLevel(enchantments, Enchantments.PROJECTILE_PROTECTION);
            if (projectileProtection > 0 && source.is(DamageTypeTags.IS_PROJECTILE)) {
                damageProtection += 2 * projectileProtection;
            }

            int featherFalling = getEnchantmentLevel(enchantments, Enchantments.FEATHER_FALLING);
            if (featherFalling > 0 && source.is(DamageTypeTags.IS_FALL)) {
                damageProtection += 3 * featherFalling;
            }
        }

        return net.minecraft.world.damagesource.CombatRules.getDamageAfterMagicAbsorb(damage, damageProtection);
    }

    private static List<ItemStack> getArmorItems(LivingEntity entity) {
        return List.of(
            entity.getItemBySlot(EquipmentSlot.FEET),
            entity.getItemBySlot(EquipmentSlot.LEGS),
            entity.getItemBySlot(EquipmentSlot.CHEST),
            entity.getItemBySlot(EquipmentSlot.HEAD)
        );
    }

    public static int getProtectionAmount(Iterable<ItemStack> equipment) {
        MutableInt mutableInt = new MutableInt();
        equipment.forEach(i -> mutableInt.add(getProtectionAmount(i)));
        return mutableInt.intValue();
    }

    public static int getProtectionAmount(ItemStack stack) {
        int modifierBlast = getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION);
        int modifier = getEnchantmentLevel(stack, Enchantments.PROTECTION);
        return modifierBlast * 2 + modifier;
    }

    private static float resistanceReduction(LivingEntity player, float damage) {
        MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) {
            int lvl = resistance.getAmplifier() + 1;
            damage *= (1 - (lvl * 0.2f));
        }

        return Math.max(damage, 0);
    }

    private static float getExposure(Vec3 source, AABB box, RaycastFactory raycastFactory) {
        if (GlobalSetting.INSTANCE.optimizedCalc.get()) {
            int miss = 0;
            int hit = 0;

            for (int k = 0; k <= 1; k += 1) {
                for (int l = 0; l <= 1; l += 1) {
                    for (int m = 0; m <= 1; m += 1) {
                        double n = Mth.lerp(k, box.minX, box.maxX);
                        double o = Mth.lerp(l, box.minY, box.maxY);
                        double p = Mth.lerp(m, box.minZ, box.maxZ);
                        Vec3 vec3d = new Vec3(n, o, p);
                        if (raycast(vec3d, source, true) == HitResult.Type.MISS)
                            ++miss;
                        ++hit;
                    }
                }
            }
            return (float) miss / (float) hit;
        }
        double xDiff = box.maxX - box.minX;
        double yDiff = box.maxY - box.minY;
        double zDiff = box.maxZ - box.minZ;

        double xStep = 1 / (xDiff * 2 + 1);
        double yStep = 1 / (yDiff * 2 + 1);
        double zStep = 1 / (zDiff * 2 + 1);

        if (xStep > 0 && yStep > 0 && zStep > 0) {
            int misses = 0;
            int hits = 0;

            double xOffset = (1 - Math.floor(1 / xStep) * xStep) * 0.5;
            double zOffset = (1 - Math.floor(1 / zStep) * zStep) * 0.5;

            xStep = xStep * xDiff;
            yStep = yStep * yDiff;
            zStep = zStep * zDiff;

            double startX = box.minX + xOffset;
            double startY = box.minY;
            double startZ = box.minZ + zOffset;
            double endX = box.maxX + xOffset;
            double endY = box.maxY;
            double endZ = box.maxZ + zOffset;

            for (double x = startX; x <= endX; x += xStep) {
                for (double y = startY; y <= endY; y += yStep) {
                    for (double z = startZ; z <= endZ; z += zStep) {
                        Vec3 position = new Vec3(x, y, z);

                        if (raycast(new ExposureRaycastContext(position, source), raycastFactory) == null) misses++;

                        hits++;
                    }
                }
            }

            return (float) misses / hits;
        }

        return 0f;
    }

    /* Raycasts */

    public static HitResult.Type raycast(Vec3 start, Vec3 end, boolean ignoreTerrain) {
        return BlockGetter.traverseBlocks(start, end, null, (innerContext, blockPos) -> {
            BlockState blockState = mc.level.getBlockState(blockPos);
            if (blockState.getBlock().getExplosionResistance() < 600 && ignoreTerrain) return null;
            BlockHitResult hitResult = blockState.getCollisionShape(mc.level, blockPos).clip(start, end, blockPos);
            return hitResult == null ? null : hitResult.getType();
        }, (innerContext) -> HitResult.Type.MISS);
    }

    public static BlockHitResult raycast(ExposureRaycastContext context, RaycastFactory raycastFactory) {
        return BlockGetter.traverseBlocks(context.start(), context.end(), context, raycastFactory, ctx -> null);
    }

    public record ExposureRaycastContext(Vec3 start, Vec3 end) {
    }

    @FunctionalInterface
    public interface RaycastFactory extends BiFunction<ExposureRaycastContext, BlockPos, BlockHitResult> {
    }

    public static int getEnchantmentLevel(ItemStack itemStack, ResourceKey<Enchantment> enchantment) {
        if (itemStack.isEmpty()) return 0;
        Object2IntMap<Holder<Enchantment>> itemEnchantments = new Object2IntArrayMap<>();
        getEnchantments(itemStack, itemEnchantments);
        return getEnchantmentLevel(itemEnchantments, enchantment);
    }

    public static int getEnchantmentLevel(Object2IntMap<Holder<Enchantment>> itemEnchantments, ResourceKey<Enchantment> enchantment) {
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : Object2IntMaps.fastIterable(itemEnchantments)) {
            if (entry.getKey().is(enchantment)) return entry.getIntValue();
        }
        return 0;
    }

    public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.sqrt(squaredDistance(x1, y1, z1, x2, y2, z2));
    }

    public static GameType getGameMode(Player player) {
        if (player == null) return null;
        PlayerInfo playerListEntry = mc.getConnection().getPlayerInfo(player.getUUID());
        if (playerListEntry == null) return null;
        return playerListEntry.getGameMode();
    }

    public static double squaredDistanceTo(Entity entity) {
        return squaredDistanceTo(entity.getX(), entity.getY(), entity.getZ());
    }

    public static double squaredDistanceTo(BlockPos blockPos) {
        return squaredDistanceTo(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    public static double squaredDistanceTo(double x, double y, double z) {
        return squaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ(), x, y, z);
    }

    public static double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double f = x1 - x2;
        double g = y1 - y2;
        double h = z1 - z2;
        return org.joml.Math.fma(f, f, org.joml.Math.fma(g, g, h * h));
    }

    public static void getEnchantments(ItemStack itemStack, Object2IntMap<Holder<Enchantment>> enchantments) {
        enchantments.clear();

        if (!itemStack.isEmpty()) {
            Set<Object2IntMap.Entry<Holder<Enchantment>>> itemEnchantments = itemStack.getItem() == Items.ENCHANTED_BOOK
                    ? itemStack.get(DataComponents.STORED_ENCHANTMENTS).entrySet()
                    : itemStack.getEnchantments().entrySet();

            for (Object2IntMap.Entry<Holder<Enchantment>> entry : itemEnchantments) {
                enchantments.put(entry.getKey(), entry.getIntValue());
            }
        }
    }
}
