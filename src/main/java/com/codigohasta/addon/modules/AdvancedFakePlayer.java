package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdvancedFakePlayer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCombat = settings.createGroup("Combat");

    // --- 通用设置 ---
    private final Setting<String> name = sgGeneral.add(new StringSetting.Builder()
        .name("名")
        .description("假人的名字")
        .defaultValue("CodigoHasta")
        .build()
    );

    private final Setting<Integer> health = sgGeneral.add(new IntSetting.Builder()
        .name("血量")
        .description("假人的初始血量")
        .defaultValue(20)
        .min(1)
        .sliderMax(36)
        .build()
    );

    private final Setting<Boolean> copyInv = sgGeneral.add(new BoolSetting.Builder()
        .name("复制背包")
        .description("复制你的背包物品给假人")
        .defaultValue(true)
        .build()
    );

    // --- 战斗设置 ---
    private final Setting<Boolean> simulateDamage = sgCombat.add(new BoolSetting.Builder()
        .name("受伤")
        .description("是否模拟伤害（攻击/爆炸）")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> invulnerableTicks = sgCombat.add(new IntSetting.Builder()
        .name("无敌时间")
        .description("受到伤害后的无敌时间(Ticks)。原版默认是20。设为0可以测试极限DPS。")
        .defaultValue(20)
        .min(0)
        .max(20)
        .visible(simulateDamage::get)
        .build()
    );

    private final Setting<Boolean> autoTotem = sgCombat.add(new BoolSetting.Builder()
        .name("手持图腾")
        .description("是否自动在副手拿图腾并在死亡时触发")
        .defaultValue(true)
        .visible(simulateDamage::get)
        .build()
    );

    private final Setting<Boolean> showDamage = sgCombat.add(new BoolSetting.Builder()
        .name("伤害回馈")
        .description("在聊天栏显示受到的伤害数值")
        .defaultValue(true)
        .visible(simulateDamage::get)
        .build()
    );

    private final List<CustomFakePlayer> fakePlayers = new ArrayList<>();

    public AdvancedFakePlayer() {
        super(AddonTemplate.CATEGORY, "假人", "能受击的假人。");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        spawnFakePlayer();
    }

    @Override
    public void onDeactivate() {
        removeAll();
    }

    @Override
    public String getInfoString() {
        return String.valueOf(fakePlayers.size());
    }

    private void spawnFakePlayer() {
        CustomFakePlayer fp = new CustomFakePlayer(mc.player, name.get(), health.get(), copyInv.get());
        fp.copyPositionAndRotation(mc.player);
        mc.world.addEntity(fp);
        fakePlayers.add(fp);
        info("已生成假人: " + name.get());
    }

    private void removeAll() {
        for (CustomFakePlayer fp : fakePlayers) {
            fp.discard();
        }
        fakePlayers.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!simulateDamage.get()) return;

        for (CustomFakePlayer fp : fakePlayers) {
            // 更新无敌时间计时器
            fp.tickCombat();

            // 补图腾逻辑
            if (autoTotem.get()) {
                if (fp.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                    fp.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                }
            }
        }
    }

    @EventHandler
    private void onAttack(PacketEvent.Send event) {
        if (!simulateDamage.get() || !(event.packet instanceof PlayerInteractEntityC2SPacket packet)) return;

        // 反射获取 Entity ID
        int targetId = getPacketEntityId(packet);
        if (targetId == -1) return;

        Entity target = mc.world.getEntityById(targetId);

        if (target instanceof CustomFakePlayer fp && fakePlayers.contains(fp)) {
            if (mc.player.isUsingItem()) return;

            float damage = DamageUtils.getAttackDamage(mc.player, fp);
            boolean isCrit = mc.player.fallDistance > 0.0F && !mc.player.isOnGround() && !mc.player.isClimbing() && !mc.player.isTouchingWater();
            if (isCrit) damage *= 1.5f;

            fp.applyDamage(damage);

            // 只有造成了伤害才播放击打声音，否则可能有“咚咚”的无效攻击声
            // 这里为了反馈明显，我们总是播放声音，但伤害可能被免疫
            mc.world.playSound(mc.player, fp.getX(), fp.getY(), fp.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1f, 1f);
            if (isCrit) mc.player.addCritParticles(fp);
        }
    }

    @EventHandler
    private void onExplosion(PacketEvent.Receive event) {
        if (!simulateDamage.get() || !(event.packet instanceof ExplosionS2CPacket packet)) return;

        Vec3d explosionPos = getExplosionPos(packet);
        if (explosionPos == null) return;

        for (CustomFakePlayer fp : fakePlayers) {
            float damage = calculateReflectedDamage(fp, explosionPos);

            if (damage > 0) {
                fp.applyDamage(damage);
            }
        }
    }

    // --- 反射工具区 (保持不变以兼容多版本) ---

    private Vec3d getExplosionPos(ExplosionS2CPacket packet) {
        try {
            List<Double> doubles = new ArrayList<>();
            for (Field f : ExplosionS2CPacket.class.getDeclaredFields()) {
                if (f.getType() == double.class) {
                    f.setAccessible(true);
                    doubles.add((Double) f.get(packet));
                }
            }
            if (doubles.size() >= 3) {
                return new Vec3d(doubles.get(0), doubles.get(1), doubles.get(2));
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private float calculateReflectedDamage(LivingEntity entity, Vec3d pos) {
        try {
            for (Method method : DamageUtils.class.getMethods()) {
                if (method.getName().equals("crystalDamage")) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 4 && params[2] == Box.class) {
                        return (float) method.invoke(null, entity, pos, entity.getBoundingBox(), false);
                    }
                    if (params.length == 5 && params[2] == boolean.class) {
                        return (float) method.invoke(null, entity, pos, false, entity.getBoundingBox(), false);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0f;
    }

    private int getPacketEntityId(PlayerInteractEntityC2SPacket packet) {
        try {
            for (Field field : PlayerInteractEntityC2SPacket.class.getDeclaredFields()) {
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    return field.getInt(packet);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }

    // --- 假人实体类 ---

    private class CustomFakePlayer extends OtherClientPlayerEntity {
        // 自定义冷却计时器
        private int combatCooldown = 0;

        public CustomFakePlayer(net.minecraft.entity.player.PlayerEntity player, String name, float health, boolean copyInv) {
            super(mc.world, new GameProfile(UUID.randomUUID(), name));
            copyPositionAndRotation(player);
            this.setBodyYaw(player.bodyYaw);
            this.setHeadYaw(player.headYaw);
            this.setHealth(health);
            if (copyInv) {
                this.getInventory().clone(player.getInventory());
            }
        }

        // 每tick更新
        public void tickCombat() {
            if (combatCooldown > 0) {
                combatCooldown--;
            }
            // 同步原版的 hurtTime 动画效果
            if (this.hurtTime > 0) {
                this.hurtTime--;
            }
        }

        public void applyDamage(float damage) {
            // 如果还在无敌时间内，直接跳过伤害
            if (combatCooldown > 0) {
                return;
            }

            float oldHealth = this.getHealth();
            float newHealth = oldHealth - damage;

            if (showDamage.get()) {
                info(String.format("假人受到伤害: %.1f (剩余: %.1f)", damage, Math.max(0, newHealth)));
            }

            // 设置无敌时间
            this.combatCooldown = invulnerableTicks.get();
            // 设置视觉上的变红时间 (固定为10或者跟随设置)
            this.hurtTime = 10; 
            this.maxHurtTime = 10;
            // 播放受伤动画
            this.animateDamage(0);

            if (newHealth <= 0) {
                if (autoTotem.get()) {
                    popTotem();
                } else {
                    die();
                }
            } else {
                this.setHealth(newHealth);
            }
        }

        private void popTotem() {
            this.setHealth(1.0f);
            this.setAbsorptionAmount(4.0f);
            this.clearStatusEffects();
            this.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
            this.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));

            mc.world.playSound(mc.player, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0F, 1.0F);
            this.handleStatus(EntityStatuses.USE_TOTEM_OF_UNDYING);

            // 触发图腾后，通常也会重置无敌时间
            this.combatCooldown = invulnerableTicks.get();
            this.hurtTime = 10;

            if (showDamage.get()) {
                info(Text.of("§6假人触发了不死图腾！"));
            }
        }

        private void die() {
            this.setHealth(0);
            this.setRemoved(Entity.RemovalReason.KILLED);
            this.discard();
            fakePlayers.remove(this);
            mc.world.playSound(mc.player, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_PLAYER_DEATH, SoundCategory.PLAYERS, 1.0F, 1.0F);
            info("假人已死亡。");
        }
    }
}