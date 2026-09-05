package com.codigohasta.addon.modules;

import net.minecraft.world.phys.Vec3;

import com.codigohasta.addon.AddonTemplate;
import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;

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
        super(AddonTemplate.CATEGORY, "假人", "能受击的假人。抄的原版meteor假人。效果很差。没用的功能没做完");
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
        fp.copyPosition(mc.player);
        mc.level.addEntity(fp);
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
                if (fp.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING) {
                    fp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                }
            }
        }
    }

    @EventHandler
    private void onAttack(PacketEvent.Send event) {
        if (!simulateDamage.get() || !(event.packet instanceof ServerboundAttackPacket packet)) return;

        // 26.1.2: 攻击包是 record，实体 id 直接可读
        int targetId = getPacketEntityId(packet);
        if (targetId == -1) return;

        Entity target = mc.level.getEntity(targetId);

        if (target instanceof CustomFakePlayer fp && fakePlayers.contains(fp)) {
            if (mc.player.isUsingItem()) return;

            float damage = DamageUtils.getAttackDamage(mc.player, fp);
            boolean isCrit = mc.player.fallDistance > 0.0F && !mc.player.onGround() && !mc.player.onClimbable() && !mc.player.isInWater();
            if (isCrit) damage *= 1.5f;

            fp.applyDamage(damage);

            // 只有造成了伤害才播放击打声音，否则可能有“咚咚”的无效攻击声
            // 这里为了反馈明显，我们总是播放声音，但伤害可能被免疫
            mc.level.playSound(mc.player, fp.getX(), fp.getY(), fp.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 1f);
            if (isCrit) mc.player.crit(fp);
        }
    }

    @EventHandler
    private void onExplosion(PacketEvent.Receive event) {
        if (!simulateDamage.get() || !(event.packet instanceof ClientboundExplodePacket packet)) return;

        Vec3 explosionPos = getExplosionPos(packet);
        if (explosionPos == null) return;

        for (CustomFakePlayer fp : fakePlayers) {
            float damage = calculateReflectedDamage(fp, explosionPos);

            if (damage > 0) {
                fp.applyDamage(damage);
            }
        }
    }

    // --- 反射工具区 (保持不变以兼容多版本) ---

    private Vec3 getExplosionPos(ClientboundExplodePacket packet) {
        try {
            List<Double> doubles = new ArrayList<>();
            for (Field f : ClientboundExplodePacket.class.getDeclaredFields()) {
                if (f.getType() == double.class) {
                    f.setAccessible(true);
                    doubles.add((Double) f.get(packet));
                }
            }
            if (doubles.size() >= 3) {
                return new Vec3(doubles.get(0), doubles.get(1), doubles.get(2));
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private float calculateReflectedDamage(LivingEntity entity, Vec3 pos) {
        try {
            for (Method method : DamageUtils.class.getMethods()) {
                if (method.getName().equals("crystalDamage")) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 4 && params[2] == AABB.class) {
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

    private int getPacketEntityId(ServerboundAttackPacket packet) {
        return packet.entityId();
    }

    // --- 假人实体类 ---

    private class CustomFakePlayer extends RemotePlayer {
        // 自定义冷却计时器
        private int combatCooldown = 0;

        public CustomFakePlayer(net.minecraft.world.entity.player.Player player, String name, float health, boolean copyInv) {
            super(mc.level, new GameProfile(UUID.randomUUID(), name));
            copyPosition(player);
            this.setYBodyRot(player.yBodyRot);
            this.setYHeadRot(player.yHeadRot);
            this.setHealth(health);
            if (copyInv) {
                this.getInventory().replaceWith(player.getInventory());
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
            this.hurtDuration = 10;
            // 播放受伤动画
            this.animateHurt(0);

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
            this.removeAllEffects();
            this.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.REGENERATION, 900, 1));
            this.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));

            mc.level.playSound(mc.player, this.getX(), this.getY(), this.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            this.handleEntityEvent(EntityEvent.PROTECTED_FROM_DEATH);

            // 触发图腾后，通常也会重置无敌时间
            this.combatCooldown = invulnerableTicks.get();
            this.hurtTime = 10;

            if (showDamage.get()) {
                info(Component.literal("§6假人触发了不死图腾！"));
            }
        }

        private void die() {
            this.setHealth(0);
            this.setRemoved(Entity.RemovalReason.KILLED);
            this.discard();
            fakePlayers.remove(this);
            mc.level.playSound(mc.player, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1.0F, 1.0F);
            info("假人已死亡。");
        }
    }
}