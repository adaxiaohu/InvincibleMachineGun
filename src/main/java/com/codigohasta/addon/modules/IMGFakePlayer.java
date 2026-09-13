package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.LivingEntityAccessor;
import com.codigohasta.addon.utils.alien.AlienBlockUtil;
import com.codigohasta.addon.utils.alien.AlienDamageUtils;
import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        mc.world.addEntity(fakePlayer);
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
            if (fakePlayer.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                fakePlayer.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
            if (fakePlayer.getMainHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                fakePlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
        }

        if (record.get() != lastRecordValue && record.get()) {
            positions.clear();
        }
        lastRecordValue = record.get();

        if (record.get()) {
            positions.add(new PlayerState(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.getYaw(), mc.player.getPitch()
            ));
        }

        if (play.get() && !positions.isEmpty()) {
            movementTick++;
            if (movementTick >= positions.size()) {
                movementTick = 0;
            }
            PlayerState p = positions.get(movementTick);
            fakePlayer.setYaw(p.yaw);
            fakePlayer.setPitch(p.pitch);
            fakePlayer.setHeadYaw(p.yaw);
            fakePlayer.updateTrackedPosition(p.x, p.y, p.z);
            fakePlayer.updateTrackedPositionAndAngles(new Vec3d(p.x, p.y, p.z), p.yaw, p.pitch);
        }

        // Process pending visual effects (set in packet handlers on Netty thread)
        if (pendingHurtSound) {
            mc.world.playSound(null, fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0F, 1.0F);
            pendingHurtSound = false;
        }
        if (pendingCritSound) {
            mc.world.playSound(null, fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.0F);
            mc.player.addCritParticles(fakePlayer);
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
        if (!(event.packet instanceof PlayerInteractEntityC2SPacket packet)) return;

        IPlayerInteractEntityC2SPacket accessor = (IPlayerInteractEntityC2SPacket) packet;
        if (!String.valueOf(accessor.meteor$getType()).equals("ATTACK")) return;
        if (accessor.meteor$getEntity() != fakePlayer) return;

        float dmg = AlienDamageUtils.getAttackDamage(mc.player, fakePlayer);

        boolean isCrit = mc.player.fallDistance > 0.0F
            && !mc.player.isOnGround()
            && !mc.player.isClimbing()
            && !mc.player.isTouchingWater()
            && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
            && !mc.player.hasVehicle();

        if (fakePlayer.hurtTime <= 0) {
            fakePlayer.onDamaged(mc.world.getDamageSources().generic());
            if (fakePlayer.getAbsorptionAmount() >= dmg) {
                fakePlayer.setAbsorptionAmount(fakePlayer.getAbsorptionAmount() - dmg);
            } else {
                float remaining = dmg - fakePlayer.getAbsorptionAmount();
                fakePlayer.setAbsorptionAmount(0.0F);
                fakePlayer.setHealth(fakePlayer.getHealth() - remaining);
            }

            if (fakePlayer.isDead()) {
                tryTotemPop(fakePlayer);
            }

            fakePlayer.hurtTime = 10;
            fakePlayer.maxHurtTime = 10;
            fakePlayer.animateDamage(0);
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
        if (!(event.packet instanceof ExplosionS2CPacket explosion)) return;

        Vec3d explosionPos = explosion.center();
        if (explosionPos.squaredDistanceTo(new Vec3d(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ())) > 100.0) return;

        float dmg;
        if (AlienBlockUtil.getBlock(BlockPos.ofFloored(explosionPos)) == Blocks.RESPAWN_ANCHOR) {
            dmg = AlienDamageUtils.explosionDamage(fakePlayer, explosionPos, 10.0F);
        } else {
            dmg = AlienDamageUtils.explosionDamage(fakePlayer, explosionPos, 12.0F);
        }

        fakePlayer.onDamaged(mc.world.getDamageSources().generic());
        if (fakePlayer.getAbsorptionAmount() >= dmg) {
            fakePlayer.setAbsorptionAmount(fakePlayer.getAbsorptionAmount() - dmg);
        } else {
            float remaining = dmg - fakePlayer.getAbsorptionAmount();
            fakePlayer.setAbsorptionAmount(0.0F);
            fakePlayer.setHealth(fakePlayer.getHealth() - remaining);
        }

        if (fakePlayer.isDead()) {
            tryTotemPop(fakePlayer);
        }
    }

    private void tryTotemPop(FakePlayerEntity fp) {
        boolean hasTotem = fp.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING
            || fp.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING;

        if (hasTotem) {
            fp.setHealth(10.0F);
            fp.clearStatusEffects();
            fp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
            fp.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));
            fp.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));

            if (fp.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
                fp.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            } else {
                fp.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            }

            // Schedule visual effects for main thread (packet handlers run on Netty thread)
            pendingTotemPopVisuals = true;
        }
    }

    private void spawnTotemPopVisuals(FakePlayerEntity fp) {
        if (mc.world == null) return;
        for (int i = 0; i < 30; i++) {
            double vx = (mc.world.random.nextDouble() - 0.5) * 0.5;
            double vy = mc.world.random.nextDouble() * 0.5;
            double vz = (mc.world.random.nextDouble() - 0.5) * 0.5;
            mc.particleManager.addParticle(
                ParticleTypes.TOTEM_OF_UNDYING,
                fp.getX() + vx * 2, fp.getY() + 1.0 + vy * 2, fp.getZ() + vz * 2,
                vx, vy + 0.5, vz
            );
        }
        mc.world.playSound(null, fp.getX(), fp.getY(), fp.getZ(),
            SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0F, 1.0F);

        // Notify IMGTips if active
        IMGTips.onFakePlayerTotemPop(fp.getName().getString(), fp);

        // Notify IMGPopChams to render totem pop wireframe on the fake player
        IMGPopChams.onFakePlayerTotemPop(fp);
    }

    public class FakePlayerEntity extends OtherClientPlayerEntity {
        private final boolean ground;

        public FakePlayerEntity(PlayerEntity player, String name) {
            super(mc.world, new GameProfile(UUID.fromString("66666666-6666-6666-6666-666666666666"), name));
            copyPositionAndRotation(player);
            this.lastRenderX = player.lastRenderX;
            this.lastRenderZ = player.lastRenderZ;
            this.lastRenderY = player.lastRenderY;
            this.bodyYaw = player.bodyYaw;
            this.headYaw = player.headYaw;
            this.handSwingProgress = player.handSwingProgress;
            this.handSwingTicks = player.handSwingTicks;
            this.limbAnimator.setSpeed(player.limbAnimator.getSpeed());
            ((LivingEntityAccessor) this).setLeaningPitch(((LivingEntityAccessor) player).getLeaningPitch());
            ((LivingEntityAccessor) this).setLastLeaningPitch(((LivingEntityAccessor) player).getLeaningPitch());
            this.touchingWater = player.isTouchingWater();
            this.setSneaking(player.isSneaking());
            this.setPose(player.getPose());
            this.ground = player.isOnGround();
            this.setOnGround(this.ground);
            this.getInventory().clone(player.getInventory());
            this.setAbsorptionAmount(player.getAbsorptionAmount());
            this.setHealth(player.getHealth());
            this.setBoundingBox(player.getBoundingBox());
        }

        @Override
        public boolean isOnGround() {
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
