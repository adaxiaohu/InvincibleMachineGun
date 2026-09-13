package com.codigohasta.addon.utils.alien;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.GameMode;

public class AlienDamageUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static float getAttackDamage(LivingEntity attacker, LivingEntity target) {
        float itemDamage = (float) attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        DamageSource damageSource = attacker instanceof PlayerEntity player
            ? mc.world.getDamageSources().playerAttack(player)
            : mc.world.getDamageSources().mobAttack(attacker);

        StatusEffectInstance strength = attacker.getStatusEffect(StatusEffects.STRENGTH);
        if (strength != null) {
            itemDamage += 3.0F * (strength.getAmplifier() + 1);
        }

        float damage = modifyAttackDamage(attacker, target, attacker.getWeaponStack(), damageSource, itemDamage);
        return calculateReductions(damage, target, damageSource);
    }

    public static float explosionDamage(LivingEntity target, Vec3d explosionPos, float power) {
        if (target instanceof PlayerEntity player && getGameMode(player) == GameMode.CREATIVE) return 0;

        Vec3d targetPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        Box targetBox = target.getBoundingBox();

        double distance = Math.sqrt(targetPos.squaredDistanceTo(explosionPos));
        if (distance > power) return 0;

        double exposure = getExposure(explosionPos, targetBox);
        double impact = (1.0 - distance / power) * exposure;
        float damage = (int) ((impact * impact + impact) / 2.0 * 7.0 * power + 1.0);

        return calculateReductionsExplosion(damage, target, mc.world.getDamageSources().explosion(null));
    }

    private static float modifyAttackDamage(LivingEntity attacker, LivingEntity target, ItemStack weapon, DamageSource damageSource, float damage) {
        Object2IntMap<RegistryEntry<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
        getEnchantments(weapon, enchantments);

        float enchantDamage = 0.0F;
        int sharpness = getEnchantmentLevel(enchantments, Enchantments.SHARPNESS);
        if (sharpness > 0) {
            enchantDamage += 1.0F + 0.5F * (sharpness - 1);
        }

        int baneOfArthropods = getEnchantmentLevel(enchantments, Enchantments.BANE_OF_ARTHROPODS);
        if (baneOfArthropods > 0 && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
            enchantDamage += 2.5F * baneOfArthropods;
        }

        int impaling = getEnchantmentLevel(enchantments, Enchantments.IMPALING);
        if (impaling > 0 && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_IMPALING)) {
            enchantDamage += 2.5F * impaling;
        }

        int smite = getEnchantmentLevel(enchantments, Enchantments.SMITE);
        if (smite > 0 && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_SMITE)) {
            enchantDamage += 2.5F * smite;
        }

        if (attacker instanceof PlayerEntity playerEntity) {
            float charge = playerEntity.getAttackCooldownProgress(0.5F);
            damage *= 0.2F + charge * charge * 0.8F;
            enchantDamage *= charge;

            if (weapon.getItem() instanceof MaceItem) {
                float bonus = ((MaceItem) weapon.getItem()).getBonusAttackDamage(target, damage, damageSource);
                if (bonus > 0.0F) {
                    int density = getEnchantmentLevel(enchantments, Enchantments.DENSITY);
                    if (density > 0) {
                        bonus += 0.5F * attacker.fallDistance;
                    }
                    damage += bonus;
                }
            }

            if (charge > 0.9F
                && (attacker.fallDistance > 0.0F
                    || (attacker == mc.player && isCriticalsOn()))
                && (!attacker.isOnGround() || (attacker == mc.player && isCriticalsOn()))
                && !attacker.isClimbing()
                && !attacker.isTouchingWater()
                && !attacker.hasStatusEffect(StatusEffects.BLINDNESS)
                && !attacker.hasVehicle()) {
                damage *= 1.5F;
            }
        }

        return damage + enchantDamage;
    }

    private static boolean isCriticalsOn() {
        return mc.player != null && mc.player.fallDistance > 0.0F && !mc.player.isOnGround();
    }

    private static float calculateReductionsExplosion(float damage, LivingEntity entity, DamageSource damageSource) {
        if (damageSource.isScaledWithDifficulty()) {
            switch (mc.world.getDifficulty()) {
                case EASY -> damage = Math.min(damage / 2.0F + 1.0F, damage);
                case HARD -> damage *= 1.5F;
            }
        }
        damage = DamageUtil.getDamageLeft(entity, damage, damageSource, getArmor(entity), (float) getArmorToughness(entity));
        damage = resistanceReduction(entity, damage);
        damage = DamageUtil.getInflictedDamage(damage, protectionAmount(entity));
        return Math.max(damage, 0.0F);
    }

    private static float calculateReductions(float damage, LivingEntity entity, DamageSource damageSource) {
        if (damageSource.isScaledWithDifficulty()) {
            switch (mc.world.getDifficulty()) {
                case EASY -> damage = Math.min(damage / 2.0F + 1.0F, damage);
                case HARD -> damage *= 1.5F;
            }
        }
        damage = DamageUtil.getDamageLeft(entity, damage, damageSource, getArmor(entity), (float) getArmorToughness(entity));
        damage = resistanceReduction(entity, damage);
        damage = protectionReduction(entity, damage, damageSource);
        return Math.max(damage, 0.0F);
    }

    private static double getArmorToughness(LivingEntity entity) {
        return entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
    }

    private static float getArmor(LivingEntity entity) {
        return (float) Math.floor(entity.getAttributeValue(EntityAttributes.ARMOR));
    }

    private static int protectionAmount(LivingEntity entity) {
        int total = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                total += getProtectionAmount(entity.getEquippedStack(slot));
            }
        }
        return total;
    }

    private static float protectionReduction(LivingEntity entity, float damage, DamageSource source) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return damage;

        int damageProtection = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack stack = entity.getEquippedStack(slot);
                Object2IntMap<RegistryEntry<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
                getEnchantments(stack, enchantments);

                int protection = getEnchantmentLevel(enchantments, Enchantments.PROTECTION);
                if (protection > 0) damageProtection += protection;

                int fireProtection = getEnchantmentLevel(enchantments, Enchantments.FIRE_PROTECTION);
                if (fireProtection > 0 && source.isIn(DamageTypeTags.IS_FIRE)) damageProtection += 2 * fireProtection;

                int blastProtection = getEnchantmentLevel(enchantments, Enchantments.BLAST_PROTECTION);
                if (blastProtection > 0 && source.isIn(DamageTypeTags.IS_EXPLOSION)) damageProtection += 2 * blastProtection;

                int projectileProtection = getEnchantmentLevel(enchantments, Enchantments.PROJECTILE_PROTECTION);
                if (projectileProtection > 0 && source.isIn(DamageTypeTags.IS_PROJECTILE)) damageProtection += 2 * projectileProtection;

                int featherFalling = getEnchantmentLevel(enchantments, Enchantments.FEATHER_FALLING);
                if (featherFalling > 0 && source.isIn(DamageTypeTags.IS_FALL)) damageProtection += 3 * featherFalling;
            }
        }

        return DamageUtil.getInflictedDamage(damage, damageProtection);
    }

    private static int getProtectionAmount(ItemStack stack) {
        Object2IntMap<RegistryEntry<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
        getEnchantments(stack, enchantments);
        int blast = getEnchantmentLevel(enchantments, Enchantments.BLAST_PROTECTION);
        int prot = getEnchantmentLevel(enchantments, Enchantments.PROTECTION);
        return blast * 2 + prot;
    }

    private static float resistanceReduction(LivingEntity entity, float damage) {
        StatusEffectInstance resistance = entity.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance != null) {
            int lvl = resistance.getAmplifier() + 1;
            damage *= 1.0F - lvl * 0.2F;
        }
        return Math.max(damage, 0.0F);
    }

    private static float getExposure(Vec3d source, Box box) {
        double xDiff = box.maxX - box.minX;
        double yDiff = box.maxY - box.minY;
        double zDiff = box.maxZ - box.minZ;
        double xStep = 1.0 / (xDiff * 2.0 + 1.0);
        double yStep = 1.0 / (yDiff * 2.0 + 1.0);
        double zStep = 1.0 / (zDiff * 2.0 + 1.0);

        if (xStep > 0.0 && yStep > 0.0 && zStep > 0.0) {
            int misses = 0;
            int hits = 0;
            double xOffset = (1.0 - Math.floor(1.0 / xStep) * xStep) * 0.5;
            double zOffset = (1.0 - Math.floor(1.0 / zStep) * zStep) * 0.5;
            xStep *= xDiff;
            yStep *= yDiff;
            zStep *= zDiff;

            double startX = box.minX + xOffset;
            double startY = box.minY;
            double startZ = box.minZ + zOffset;
            double endX = box.maxX + xOffset;
            double endY = box.maxY;
            double endZ = box.maxZ + zOffset;

            for (double x = startX; x <= endX; x += xStep) {
                for (double y = startY; y <= endY; y += yStep) {
                    for (double z = startZ; z <= endZ; z += zStep) {
                        Vec3d position = new Vec3d(x, y, z);
                        if (raycast(position, source) == net.minecraft.util.hit.HitResult.Type.MISS) {
                            misses++;
                        }
                        hits++;
                    }
                }
            }
            return (float) misses / hits;
        }
        return 0.0F;
    }

    private static net.minecraft.util.hit.HitResult.Type raycast(Vec3d start, Vec3d end) {
        return BlockView.raycast(start, end, null, (ctx, blockPos) -> {
            BlockState blockState = mc.world.getBlockState(blockPos);
            if (blockState.getBlock().getBlastResistance() < 600.0F) {
                return null;
            }
            BlockHitResult hitResult = blockState.getCollisionShape(mc.world, blockPos).raycast(start, end, blockPos);
            return hitResult == null ? null : hitResult.getType();
        }, ctx -> net.minecraft.util.hit.HitResult.Type.MISS);
    }

    private static int getEnchantmentLevel(Object2IntMap<RegistryEntry<Enchantment>> enchantments, RegistryKey<Enchantment> key) {
        for (var entry : enchantments.object2IntEntrySet()) {
            if (entry.getKey().matchesKey(key)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    private static void getEnchantments(ItemStack stack, Object2IntMap<RegistryEntry<Enchantment>> map) {
        map.clear();
        if (stack.isEmpty()) return;

        ItemEnchantmentsComponent ench;
        if (stack.getItem() == Items.ENCHANTED_BOOK) {
            ench = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        } else {
            ench = stack.getEnchantments();
        }
        if (ench == null) return;

        for (var entry : ench.getEnchantmentEntries()) {
            map.put(entry.getKey(), entry.getIntValue());
        }
    }

    private static GameMode getGameMode(PlayerEntity player) {
        if (player == null) return null;
        var entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        return entry == null ? null : entry.getGameMode();
    }
}
