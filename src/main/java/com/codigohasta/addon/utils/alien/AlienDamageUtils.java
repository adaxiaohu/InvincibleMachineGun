package com.codigohasta.addon.utils.alien;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;

public class AlienDamageUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    public static float getAttackDamage(LivingEntity attacker, LivingEntity target) {
        float itemDamage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource damageSource = attacker instanceof Player player
            ? mc.level.damageSources().playerAttack(player)
            : mc.level.damageSources().mobAttack(attacker);

        MobEffectInstance strength = attacker.getEffect(MobEffects.STRENGTH);
        if (strength != null) {
            itemDamage += 3.0F * (strength.getAmplifier() + 1);
        }

        float damage = modifyAttackDamage(attacker, target, attacker.getWeaponItem(), damageSource, itemDamage);
        return calculateReductions(damage, target, damageSource);
    }

    public static float explosionDamage(LivingEntity target, Vec3 explosionPos, float power) {
        if (target instanceof Player player && getGameMode(player) == GameType.CREATIVE) return 0;

        Vec3 targetPos = new Vec3(target.getX(), target.getY(), target.getZ());
        AABB targetBox = target.getBoundingBox();

        double distance = Math.sqrt(targetPos.distanceToSqr(explosionPos));
        if (distance > power) return 0;

        double exposure = getExposure(explosionPos, targetBox);
        double impact = (1.0 - distance / power) * exposure;
        float damage = (int) ((impact * impact + impact) / 2.0 * 7.0 * power + 1.0);

        return calculateReductionsExplosion(damage, target, mc.level.damageSources().explosion(null));
    }

    private static float modifyAttackDamage(LivingEntity attacker, LivingEntity target, ItemStack weapon, DamageSource damageSource, float damage) {
        Object2IntMap<Holder<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
        getEnchantments(weapon, enchantments);

        float enchantDamage = 0.0F;
        int sharpness = getEnchantmentLevel(enchantments, Enchantments.SHARPNESS);
        if (sharpness > 0) {
            enchantDamage += 1.0F + 0.5F * (sharpness - 1);
        }

        int baneOfArthropods = getEnchantmentLevel(enchantments, Enchantments.BANE_OF_ARTHROPODS);
        if (baneOfArthropods > 0 && target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
            enchantDamage += 2.5F * baneOfArthropods;
        }

        int impaling = getEnchantmentLevel(enchantments, Enchantments.IMPALING);
        if (impaling > 0 && target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_IMPALING)) {
            enchantDamage += 2.5F * impaling;
        }

        int smite = getEnchantmentLevel(enchantments, Enchantments.SMITE);
        if (smite > 0 && target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_SMITE)) {
            enchantDamage += 2.5F * smite;
        }

        if (attacker instanceof Player playerEntity) {
            float charge = playerEntity.getAttackStrengthScale(0.5F);
            damage *= 0.2F + charge * charge * 0.8F;
            enchantDamage *= charge;

            if (weapon.getItem() instanceof MaceItem) {
                float bonus = ((MaceItem) weapon.getItem()).getAttackDamageBonus(target, damage, damageSource);
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
                && (!attacker.onGround() || (attacker == mc.player && isCriticalsOn()))
                && !attacker.onClimbable()
                && !attacker.isInWater()
                && !attacker.hasEffect(MobEffects.BLINDNESS)
                && !attacker.isPassenger()) {
                damage *= 1.5F;
            }
        }

        return damage + enchantDamage;
    }

    private static boolean isCriticalsOn() {
        return mc.player != null && mc.player.fallDistance > 0.0F && !mc.player.onGround();
    }

    private static float calculateReductionsExplosion(float damage, LivingEntity entity, DamageSource damageSource) {
        if (damageSource.scalesWithDifficulty()) {
            switch (mc.level.getDifficulty()) {
                case EASY -> damage = Math.min(damage / 2.0F + 1.0F, damage);
                case HARD -> damage *= 1.5F;
            }
        }
        damage = CombatRules.getDamageAfterAbsorb(entity, damage, damageSource, getArmor(entity), (float) getArmorToughness(entity));
        damage = resistanceReduction(entity, damage);
        damage = CombatRules.getDamageAfterMagicAbsorb(damage, protectionAmount(entity));
        return Math.max(damage, 0.0F);
    }

    private static float calculateReductions(float damage, LivingEntity entity, DamageSource damageSource) {
        if (damageSource.scalesWithDifficulty()) {
            switch (mc.level.getDifficulty()) {
                case EASY -> damage = Math.min(damage / 2.0F + 1.0F, damage);
                case HARD -> damage *= 1.5F;
            }
        }
        damage = CombatRules.getDamageAfterAbsorb(entity, damage, damageSource, getArmor(entity), (float) getArmorToughness(entity));
        damage = resistanceReduction(entity, damage);
        damage = protectionReduction(entity, damage, damageSource);
        return Math.max(damage, 0.0F);
    }

    private static double getArmorToughness(LivingEntity entity) {
        return entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
    }

    private static float getArmor(LivingEntity entity) {
        return (float) Math.floor(entity.getAttributeValue(Attributes.ARMOR));
    }

    private static int protectionAmount(LivingEntity entity) {
        int total = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                total += getProtectionAmount(entity.getItemBySlot(slot));
            }
        }
        return total;
    }

    private static float protectionReduction(LivingEntity entity, float damage, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return damage;

        int damageProtection = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack stack = entity.getItemBySlot(slot);
                Object2IntMap<Holder<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
                getEnchantments(stack, enchantments);

                int protection = getEnchantmentLevel(enchantments, Enchantments.PROTECTION);
                if (protection > 0) damageProtection += protection;

                int fireProtection = getEnchantmentLevel(enchantments, Enchantments.FIRE_PROTECTION);
                if (fireProtection > 0 && source.is(DamageTypeTags.IS_FIRE)) damageProtection += 2 * fireProtection;

                int blastProtection = getEnchantmentLevel(enchantments, Enchantments.BLAST_PROTECTION);
                if (blastProtection > 0 && source.is(DamageTypeTags.IS_EXPLOSION)) damageProtection += 2 * blastProtection;

                int projectileProtection = getEnchantmentLevel(enchantments, Enchantments.PROJECTILE_PROTECTION);
                if (projectileProtection > 0 && source.is(DamageTypeTags.IS_PROJECTILE)) damageProtection += 2 * projectileProtection;

                int featherFalling = getEnchantmentLevel(enchantments, Enchantments.FEATHER_FALLING);
                if (featherFalling > 0 && source.is(DamageTypeTags.IS_FALL)) damageProtection += 3 * featherFalling;
            }
        }

        return CombatRules.getDamageAfterMagicAbsorb(damage, damageProtection);
    }

    private static int getProtectionAmount(ItemStack stack) {
        Object2IntMap<Holder<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
        getEnchantments(stack, enchantments);
        int blast = getEnchantmentLevel(enchantments, Enchantments.BLAST_PROTECTION);
        int prot = getEnchantmentLevel(enchantments, Enchantments.PROTECTION);
        return blast * 2 + prot;
    }

    private static float resistanceReduction(LivingEntity entity, float damage) {
        MobEffectInstance resistance = entity.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) {
            int lvl = resistance.getAmplifier() + 1;
            damage *= 1.0F - lvl * 0.2F;
        }
        return Math.max(damage, 0.0F);
    }

    private static float getExposure(Vec3 source, AABB box) {
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
                        Vec3 position = new Vec3(x, y, z);
                        if (raycast(position, source) == net.minecraft.world.phys.HitResult.Type.MISS) {
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

    private static net.minecraft.world.phys.HitResult.Type raycast(Vec3 start, Vec3 end) {
        return BlockGetter.traverseBlocks(start, end, null, (ctx, blockPos) -> {
            BlockState blockState = mc.level.getBlockState(blockPos);
            if (blockState.getBlock().getExplosionResistance() < 600.0F) {
                return null;
            }
            BlockHitResult hitResult = blockState.getCollisionShape(mc.level, blockPos).clip(start, end, blockPos);
            return hitResult == null ? null : hitResult.getType();
        }, ctx -> net.minecraft.world.phys.HitResult.Type.MISS);
    }

    private static int getEnchantmentLevel(Object2IntMap<Holder<Enchantment>> enchantments, ResourceKey<Enchantment> key) {
        for (var entry : enchantments.object2IntEntrySet()) {
            if (entry.getKey().is(key)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    private static void getEnchantments(ItemStack stack, Object2IntMap<Holder<Enchantment>> map) {
        map.clear();
        if (stack.isEmpty()) return;

        ItemEnchantments ench;
        if (stack.getItem() == Items.ENCHANTED_BOOK) {
            ench = stack.get(DataComponents.STORED_ENCHANTMENTS);
        } else {
            ench = stack.getEnchantments();
        }
        if (ench == null) return;

        for (var entry : ench.entrySet()) {
            map.put(entry.getKey(), entry.getIntValue());
        }
    }

    private static GameType getGameMode(Player player) {
        if (player == null) return null;
        var entry = mc.getConnection().getPlayerInfo(player.getUUID());
        return entry == null ? null : entry.getGameMode();
    }
}
