package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.LivingEntityAccessor;
import com.codigohasta.addon.utils.alien.AlienBlockUtil;
import com.codigohasta.addon.utils.alien.AlienDamageUtils;
import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;

public class IMGFakePlayer extends Module {
    public static IMGFakePlayer INSTANCE;
    public FakePlayerEntity fakePlayer;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> name = sgGeneral.add(new StringSetting.Builder()
        .name("name")
        .description("The name of the fake player.")
        .defaultValue("FakePlayer")
        .build()
    );

    private final Setting<Boolean> damage = sgGeneral.add(new BoolSetting.Builder()
        .name("damage")
        .description("Simulate damage from attacks and explosions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-totem")
        .description("Automatically give totems to the fake player.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> record = sgGeneral.add(new BoolSetting.Builder()
        .name("record")
        .description("Record the real player's movement.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> play = sgGeneral.add(new BoolSetting.Builder()
        .name("play")
        .description("Play back recorded movement on the fake player.")
        .defaultValue(false)
        .build()
    );

    private final List<PlayerState> positions = new ArrayList<>();
    private int movementTick;
    private boolean lastRecordValue;

    // Pending visual effect flags — set in packet handlers (Netty thread), processed in onTick (Render thread)
    private boolean pendingHurtSound;
    private boolean pendingCritSound;
    private boolean pendingTotemPopVisuals;

    public IMGFakePlayer() {
        super(AddonTemplate.CATEGORY, "假人2", "生成一个客户端的假人用于测试. 来自AlienV4的FakePlayer模块。");
        INSTANCE = this;
    }

    @Override
    public String getInfoString() {
        return name.get();
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }
        fakePlayer = new FakePlayerEntity(mc.player, name.get());
        mc.level.addEntity(fakePlayer);
    }

    @Override
    public void onDeactivate() {
        if (fakePlayer != null) {
            fakePlayer.discard();
            fakePlayer = null;
        }
        positions.clear();
        movementTick = 0;
        lastRecordValue = false;
        pendingHurtSound = false;
        pendingCritSound = false;
        pendingTotemPopVisuals = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (fakePlayer == null || fakePlayer.isRemoved()) {
            toggle();
            return;
        }

        if (autoTotem.get()) {
            if (fakePlayer.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING) {
                fakePlayer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
            if (fakePlayer.getMainHandItem().getItem() != Items.TOTEM_OF_UNDYING) {
                fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
        }

        if (record.get() != lastRecordValue && record.get()) {
            positions.clear();
        }
        lastRecordValue = record.get();

        if (record.get()) {
            positions.add(new PlayerState(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.getYRot(), mc.player.getXRot()
            ));
        }

        if (play.get() && !positions.isEmpty()) {
            movementTick++;
            if (movementTick >= positions.size()) {
                movementTick = 0;
            }
            PlayerState p = positions.get(movementTick);
            fakePlayer.setYRot(p.yaw);
            fakePlayer.setXRot(p.pitch);
            fakePlayer.setYHeadRot(p.yaw);
            fakePlayer.moveOrInterpolateTo(new Vec3(p.x, p.y, p.z), p.yaw, p.pitch);
        }

        // Process pending visual effects (set in packet handlers on Netty thread)
        if (pendingHurtSound) {
            mc.level.playSound(null, fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 1.0F);
            pendingHurtSound = false;
        }
        if (pendingCritSound) {
            mc.level.playSound(null, fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
            mc.player.crit(fakePlayer);
            pendingCritSound = false;
        }
        if (pendingTotemPopVisuals) {
            spawnTotemPopVisuals(fakePlayer);
            pendingTotemPopVisuals = false;
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!damage.get() || fakePlayer == null) return;
        // 26.1.2: 攻击是独立的 ServerboundAttackPacket，包里只留实体 id
        if (!(event.packet instanceof ServerboundAttackPacket packet)) return;
        if (mc.level == null || mc.level.getEntity(packet.entityId()) != fakePlayer) return;

        float dmg = AlienDamageUtils.getAttackDamage(mc.player, fakePlayer);

        boolean isCrit = mc.player.fallDistance > 0.0F
            && !mc.player.onGround()
            && !mc.player.onClimbable()
            && !mc.player.isInWater()
            && !mc.player.hasEffect(MobEffects.BLINDNESS)
            && !mc.player.isPassenger();

        if (fakePlayer.hurtTime <= 0) {
            fakePlayer.handleDamageEvent(mc.level.damageSources().generic());
            if (fakePlayer.getAbsorptionAmount() >= dmg) {
                fakePlayer.setAbsorptionAmount(fakePlayer.getAbsorptionAmount() - dmg);
            } else {
                float remaining = dmg - fakePlayer.getAbsorptionAmount();
                fakePlayer.setAbsorptionAmount(0.0F);
                fakePlayer.setHealth(fakePlayer.getHealth() - remaining);
            }

            if (fakePlayer.isDeadOrDying()) {
                tryTotemPop(fakePlayer);
            }

            fakePlayer.hurtTime = 10;
            fakePlayer.hurtDuration = 10;
            fakePlayer.animateHurt(0);
        }

        // Schedule visual effects for main thread
        if (isCrit) {
            pendingCritSound = true;
        } else {
            pendingHurtSound = true;
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!damage.get() || fakePlayer == null || fakePlayer.hurtTime > 0) return;
        if (!(event.packet instanceof ClientboundExplodePacket explosion)) return;

        Vec3 explosionPos = explosion.center();
        if (explosionPos.distanceToSqr(new Vec3(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ())) > 100.0) return;

        float dmg;
        if (AlienBlockUtil.getBlock(BlockPos.containing(explosionPos)) == Blocks.RESPAWN_ANCHOR) {
            dmg = AlienDamageUtils.explosionDamage(fakePlayer, explosionPos, 10.0F);
        } else {
            dmg = AlienDamageUtils.explosionDamage(fakePlayer, explosionPos, 12.0F);
        }

        fakePlayer.handleDamageEvent(mc.level.damageSources().generic());
        if (fakePlayer.getAbsorptionAmount() >= dmg) {
            fakePlayer.setAbsorptionAmount(fakePlayer.getAbsorptionAmount() - dmg);
        } else {
            float remaining = dmg - fakePlayer.getAbsorptionAmount();
            fakePlayer.setAbsorptionAmount(0.0F);
            fakePlayer.setHealth(fakePlayer.getHealth() - remaining);
        }

        if (fakePlayer.isDeadOrDying()) {
            tryTotemPop(fakePlayer);
        }
    }

    private void tryTotemPop(FakePlayerEntity fp) {
        boolean hasTotem = fp.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING
            || fp.getMainHandItem().getItem() == Items.TOTEM_OF_UNDYING;

        if (hasTotem) {
            fp.setHealth(10.0F);
            fp.removeAllEffects();
            fp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
            fp.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
            fp.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));

            if (fp.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
                fp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            } else {
                fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }

            // Schedule visual effects for main thread (packet handlers run on Netty thread)
            pendingTotemPopVisuals = true;
        }
    }

    private void spawnTotemPopVisuals(FakePlayerEntity fp) {
        if (mc.level == null) return;
        for (int i = 0; i < 30; i++) {
            double vx = (mc.level.getRandom().nextDouble() - 0.5) * 0.5;
            double vy = mc.level.getRandom().nextDouble() * 0.5;
            double vz = (mc.level.getRandom().nextDouble() - 0.5) * 0.5;
            mc.particleEngine.createParticle(
                ParticleTypes.TOTEM_OF_UNDYING,
                fp.getX() + vx * 2, fp.getY() + 1.0 + vy * 2, fp.getZ() + vz * 2,
                vx, vy + 0.5, vz
            );
        }
        mc.level.playSound(null, fp.getX(), fp.getY(), fp.getZ(),
            SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);

        // Notify IMGTips if active
        IMGTips.onFakePlayerTotemPop(fp.getName().getString(), fp);

        // Notify IMGPopChams to render totem pop wireframe on the fake player
        IMGPopChams.onFakePlayerTotemPop(fp);
    }

    public class FakePlayerEntity extends RemotePlayer {
        private final boolean ground;

        public FakePlayerEntity(Player player, String name) {
            super(mc.level, new GameProfile(UUID.fromString("66666666-6666-6666-6666-666666666666"), name));
            copyPosition(player);
            this.xOld = player.xOld;
            this.zOld = player.zOld;
            this.yOld = player.yOld;
            this.yBodyRot = player.yBodyRot;
            this.yHeadRot = player.yHeadRot;
            this.attackAnim = player.attackAnim;
            this.swingTime = player.swingTime;
            this.walkAnimation.setSpeed(player.walkAnimation.speed());
            ((LivingEntityAccessor) this).setLeaningPitch(((LivingEntityAccessor) player).getLeaningPitch());
            ((LivingEntityAccessor) this).setLastLeaningPitch(((LivingEntityAccessor) player).getLeaningPitch());
            this.wasTouchingWater = player.isInWater();
            this.setShiftKeyDown(player.isShiftKeyDown());
            this.setPose(player.getPose());
            this.ground = player.onGround();
            this.setOnGround(this.ground);
            this.getInventory().replaceWith(player.getInventory());
            this.setAbsorptionAmount(player.getAbsorptionAmount());
            this.setHealth(player.getHealth());
            this.setBoundingBox(player.getBoundingBox());
        }

        @Override
        public boolean onGround() {
            return ground;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }
    }

    private record PlayerState(double x, double y, double z, float yaw, float pitch) {}
}
