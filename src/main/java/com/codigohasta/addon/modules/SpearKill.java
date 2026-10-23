//由etianl（Lunge模式）和Kimtaeho（Blink模式）编写
//Kimtaeho: https://github.com/needitem
//etianl: https://github.com/etianl
package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoFall;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import com.codigohasta.addon.AddonTemplate;

import java.util.*;

public class SpearKill extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlink = settings.createGroup("Blink选项");
    private final SettingGroup sgLunge = settings.createGroup("Lunge选项");
    private final SettingGroup sgBlinkLunge = settings.createGroup("BlinkLunge选项");

    private final Setting<Boolean> nonofall = sgGeneral.add(new BoolSetting.Builder()
            .name("充能时禁用nofall")
            .description("在快速向下Lunge时防止摔伤。")
            .defaultValue(true)
            .build()
    );
    public enum Mode {
        Lunge,
        Blink
    }
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("模式")
            .description("Lunge=速度提升, Blink=数据包延迟。")
            .defaultValue(Mode.Lunge)
            .build()
    );
    public final Setting<Double> maxrange = sgGeneral.add(new DoubleSetting.Builder()
            .name("最大瞄准距离")
            .description("实体仍可被瞄准的距离（方块）。")
            .defaultValue(256)
            .min(0)
            .sliderRange(0, 512)
            .build()
    );
    public enum TargetListMode {
        Whitelist,
        Blacklist
    }
    private final Setting<TargetListMode> targetListMode = sgGeneral.add(new EnumSetting.Builder<TargetListMode>()
            .name("目标列表模式")
            .description("白名单 = 只瞄准这些实体, 黑名单 = 不瞄准这些实体")
            .defaultValue(TargetListMode.Blacklist)
            .build()
    );
    private final Setting<Set<EntityType<?>>> targetEntities = sgGeneral.add(new EntityTypeListSetting.Builder()
            .name("目标实体")
            .description("要列入白名单/黑名单的实体")
            .onlyAttackable()
            .build()
    );
    private final Setting<Boolean> ignorefriends = sgGeneral.add(new BoolSetting.Builder()
            .name("忽略好友")
            .description("不对好友发动打击。")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> blinkLunge = sgBlinkLunge.add(new BoolSetting.Builder()
            .name("Blink+Lunge")
            .description("将Blink模式与Lunge相结合。")
            .defaultValue(false)
            .visible(() -> mode.get() == Mode.Blink)
            .build()
    );
    private final Setting<Double> blinkLungeStrength = sgBlinkLunge.add(new DoubleSetting.Builder()
            .name("Lunge强度")
            .description("应用于目标的速度")
            .defaultValue(1.0)
            .min(0.1)
            .sliderRange(0.1, 2.0)
            .visible(() -> mode.get() == Mode.Blink && blinkLunge.get())
            .build()
    );
    private final Setting<Integer> blinkLungeTicks = sgBlinkLunge.add(new IntSetting.Builder()
            .name("Lunge延迟")
            .description("Lunge前充能的刻度数")
            .defaultValue(15)
            .min(1)
            .sliderRange(1, 30)
            .visible(() -> mode.get() == Mode.Blink && blinkLunge.get())
            .build()
    );

    private final Setting<Double> flushRange = sgBlink.add(new DoubleSetting.Builder()
            .name("刷新距离")
            .description("触发刷新时到目标的距离（方块）")
            .defaultValue(3.0)
            .min(1.0)
            .sliderRange(1.0, 10.0)
            .visible(() -> mode.get() == Mode.Blink)
            .build());
    private final Setting<Double> maxflushRange = sgBlink.add(new DoubleSetting.Builder()
            .name("强制刷新距离")
            .description("强制刷新的距离")
            .defaultValue(9.5)
            .min(1.0)
            .sliderRange(1.0, 20.0)
            .visible(() -> mode.get() == Mode.Blink && !blinkLunge.get())
            .build());
    private final Setting<Boolean> blinkAimbot = sgBlink.add(new BoolSetting.Builder()
            .name("自动瞄准")
            .description("锁定目标")
            .defaultValue(true)
            .visible(() -> mode.get() == Mode.Blink)
            .build()
    );
    private final Setting<Double> blinkDistanceBoost = sgBlink.add(new DoubleSetting.Builder()
            .name("距离提升")
            .description("添加到起始位置的额外方块（延长移动距离）")
            .defaultValue(0.0)
            .min(0.0)
            .sliderRange(0.0, 10.0)
            .visible(() -> mode.get() == Mode.Blink)
            .build()
    );

    public enum lungeMode {
        DirectionBased,
        FromAbove,
        Auto_FromAboveFirst
    }
    private final Setting<lungeMode> LungeDirectionMode = sgLunge.add(new EnumSetting.Builder<lungeMode>()
            .name("Lunge方向")
            .description("DirectionBased = 朝向视角方向, FromAbove = FromAbove刺击, 自动FromAbove优先 = FromAbove刺击, 除非上方位置或FromAbove位置到目标的路径无效。")
            .defaultValue(lungeMode.DirectionBased)
            .build()
    );
    private final Setting<Double> aboveHeight = sgLunge.add(new DoubleSetting.Builder()
            .name("上方高度")
            .description("目标中心上方的方块数。")
            .defaultValue(10.0)
            .min(5)
            .sliderRange(5, 50)
            .visible(() -> mode.get() == Mode.Lunge && (LungeDirectionMode.get() == lungeMode.FromAbove || LungeDirectionMode.get() == lungeMode.Auto_FromAboveFirst))
            .build()
    );
    private final Setting<Double> aboveHeightdistance = sgLunge.add(new DoubleSetting.Builder()
            .name("上方高度触发距离")
            .description("如果在此距离内到达上方目标位置，则开始向目标Lunge。")
            .defaultValue(3.0)
            .min(1)
            .sliderRange(1, 10)
            .visible(() -> mode.get() == Mode.Lunge && (LungeDirectionMode.get() == lungeMode.FromAbove || LungeDirectionMode.get() == lungeMode.Auto_FromAboveFirst))
            .build()
    );
    private final Setting<Boolean> checkdistanceinvalid = sgLunge.add(new BoolSetting.Builder()
            .name("触发距离验证")
            .description("检查上方位置周围的区域是否有效，使用上方高度触发距离作为半径。")
            .defaultValue(true)
            .visible(() -> mode.get() == Mode.Lunge && LungeDirectionMode.get() == lungeMode.Auto_FromAboveFirst)
            .build()
    );
    public final Setting<Double> getLungeStrength = sgLunge.add(new DoubleSetting.Builder()
            .name("长矛速度")
            .description("应用于玩家的速度量，朝向目标方向。")
            .defaultValue(5)
            .min(0)
            .sliderRange(1, 10)
            .visible(() -> mode.get() == Mode.Lunge)
            .build()
    );
    private final Setting<Boolean> stop = sgLunge.add(new BoolSetting.Builder()
            .name("到达目标时停止")
            .description("到达目标时停止Lunge。")
            .defaultValue(true)
            .visible(() -> mode.get() == Mode.Lunge)
            .build()
    );
    public final Setting<Double> stopDistance = sgLunge.add(new DoubleSetting.Builder()
            .name("停止距离")
            .description("尝试停止时您碰撞箱和实体碰撞箱之间的距离。")
            .defaultValue(2)
            .min(0)
            .sliderRange(0, 10)
            .visible(() -> stop.get() && mode.get() == Mode.Lunge)
            .build()
    );
    private final Setting<Integer> getreadyticksModifier = sgLunge.add(new IntSetting.Builder()
            .name("Lunge延迟调节器")
            .description("在Lunge前等待长矛就绪的时间百分比。")
            .defaultValue(100)
            .min(0)
            .sliderRange(0, 100)
            .visible(() -> mode.get() == Mode.Lunge)
            .build());

    private final List<PlayerMoveC2SPacket> packets = new ArrayList<>();
    private boolean isBlinking = false;
    private boolean isFlushing = false;
    private Vec3d startPos = null;
    private boolean wasCharging = false;
    private double lastTargetDistance = Double.MAX_VALUE;
    private boolean wasApproaching = false;
    private Entity killtarget;
    private int blinkChargeTicks = 0;
    private int flushCooldown = 0;
    private boolean firstPhase = false;
    private Vec3d aboveTargetPos = null;
    private boolean wasNoFallEnabled = false;
    private boolean noFallToggled = false;

    public SpearKill() {
        super(AddonTemplate.CATEGORY, "鹰击长空·矛", "增加长矛伤害,Lunge模式，Blink模式。");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        flushPackets();
        resetState();
    }

    private void resetState() {
        synchronized (packets) {
            packets.clear();
        }
        isBlinking = false;
        isFlushing = false;
        startPos = null;
        wasCharging = false;
        lastTargetDistance = Double.MAX_VALUE;
        wasApproaching = false;
        killtarget = null;
        blinkChargeTicks = 0;
        flushCooldown = 0;
        firstPhase = false;
        aboveTargetPos = null;
        if (noFallToggled && wasNoFallEnabled) {
            Modules.get().get(NoFall.class).toggle();
        }
        noFallToggled = false;
        wasNoFallEnabled = false;
    }

    private boolean isUsingSpear() {
        if (mc.player == null) return false;
        String itemName = mc.player.getActiveItem().getItem().toString().toLowerCase();
        return itemName.contains("spear");
    }

    private boolean currentlyCharging = false;
    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        currentlyCharging = isUsingSpear();

        if (nonofall.get()){
            if (currentlyCharging && !noFallToggled) {
                wasNoFallEnabled = Modules.get().get(NoFall.class).isActive();
                if (wasNoFallEnabled) {
                    Modules.get().get(NoFall.class).toggle();
                    noFallToggled = true;
                }
            } else if (!currentlyCharging && noFallToggled) {
                if (wasNoFallEnabled) {
                    Modules.get().get(NoFall.class).toggle();
                }
                noFallToggled = false;
            }
        }

        if (mode.get() == Mode.Lunge) return;

        if (currentlyCharging) {
            blinkChargeTicks++;
            if (killtarget == null || !killtarget.isAlive() || !canSeeTarget(killtarget)) {
                killtarget = target();
                lastTargetDistance = killtarget != null ? mc.player.distanceTo(killtarget) : Double.MAX_VALUE;
                wasApproaching = false;
            }
        } else {
            blinkChargeTicks = 0;
            killtarget = null;
            lastTargetDistance = Double.MAX_VALUE;
            wasApproaching = false;
        }

        if (flushCooldown > 0) {
            flushCooldown--;
        }

        if (currentlyCharging && !wasCharging) {
            startBlink();
        }

        if (!currentlyCharging && wasCharging) {
            if (isBlinking) {
                if (killtarget != null) {
                    rotateToTarget(killtarget);
                }
                flushPackets();
                isBlinking = false;
                startPos = null;
            }
        }

        wasCharging = currentlyCharging;

        if (isBlinking && killtarget != null && currentlyCharging) {
            double currentDistance = mc.player.distanceTo(killtarget);
            boolean isApproaching = currentDistance < lastTargetDistance;
            boolean shouldFlush = false;

            if (currentDistance <= flushRange.get()) {
                shouldFlush = true;
            } else if (wasApproaching && !isApproaching && currentDistance < 8.0) {
                shouldFlush = true;
            } else if (!blinkLunge.get() && startPos != null && new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).distanceTo(startPos) >= maxflushRange.get()) {
                // Only force flush when NOT using blink lunge
                flushPackets();
                startBlink();
            }

            if (shouldFlush) {
                rotateToTarget(killtarget);
                flushPackets();

                // Set cooldown to stop lunge temporarily after flush
                if (blinkLunge.get()) {
                    flushCooldown = blinkLungeTicks.get();  // Wait same amount as lunge delay before lunging again
                }

                // Restart blink for continuous damage
                isBlinking = true;
                startPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                synchronized (packets) {
                    packets.clear();
                }
                lastTargetDistance = mc.player.distanceTo(killtarget);
                wasApproaching = false;
            } else {
                lastTargetDistance = currentDistance;
                wasApproaching = isApproaching;
            }
        }
    }
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mode.get() == Mode.Lunge) {
            if (currentlyCharging) {
                if (killtarget == null) killtarget = target();
                if (killtarget != null && !killtarget.isAlive()) {
                    if (stop.get()) {
                        mc.player.setVelocity(0, 0, 0);
                        mc.player.setSprinting(false);
                    }
                    killtarget = null;
                    firstPhase = false;
                    aboveTargetPos = null;
                }
                if (killtarget == null || !(killtarget instanceof LivingEntity)) return;
                if (!isValidTarget(killtarget)) return;
                LUNGE();
            } else {
                killtarget = null;
                firstPhase = false;
                aboveTargetPos = null;
            }
            return;
        }

        if (currentlyCharging && blinkAimbot.get() && killtarget != null) {
            rotateToTarget(killtarget);
        }

        // Blink Lunge: Launch towards target after charge delay
        // Stop lunging after flush to allow re-approach for next hit
        if (currentlyCharging && blinkLunge.get() && killtarget != null && flushCooldown == 0) {
            if (blinkChargeTicks >= blinkLungeTicks.get()) {
                rotateToTarget(killtarget);
                Vec3d viewDir = Vec3d.fromPolar(mc.player.getPitch(), mc.player.getYaw());
                mc.player.setSprinting(true);
                mc.player.setVelocity(viewDir.multiply(blinkLungeStrength.get()));
            }
        }
    }

     private void LUNGE() {
        int readyTicks = mc.player.getActiveHand() == Hand.MAIN_HAND
                ? getReadyTicks(mc.player.getMainHandStack().getItem())
                : getReadyTicks(mc.player.getOffHandStack().getItem());

        rotateToTarget(killtarget);

        if (mc.player.getItemUseTime() > readyTicks) {
            Box playerBox = mc.player.getBoundingBox().expand(stopDistance.get());
            Box targetBox = killtarget.getBoundingBox();
            boolean atTarget = playerBox.intersects(targetBox);

            if (atTarget) {
                if (stop.get()) {
                    killtarget = null;
                    mc.player.setVelocity(0, 0, 0);
                    mc.player.setSprinting(false);
                }
                firstPhase = false;
                aboveTargetPos = null;
                return;
            }

            double lungeSpeed = getLungeStrength.get();
            Vec3d viewDir;

            if (killtarget == null) return;
            switch (LungeDirectionMode.get()) {
                case DirectionBased -> {
                    Vec3d targetDir = killtarget.getBoundingBox().getCenter().subtract(mc.player.getEntityPos()).normalize();
                    viewDir = targetDir;

                    mc.player.setSprinting(true);
                    mc.player.setVelocity(viewDir.multiply(lungeSpeed));
                }
                case FromAbove -> {
                    if (!firstPhase || aboveTargetPos == null) {
                        Vec3d targetCenter = killtarget.getBoundingBox().getCenter();
                        aboveTargetPos = new Vec3d(targetCenter.x, targetCenter.y + aboveHeight.get(), targetCenter.z);
                        firstPhase = true;
                    }

                    Vec3d playerPos = mc.player.getEntityPos();
                    double distToAbove = playerPos.distanceTo(aboveTargetPos);

                    if (distToAbove < aboveHeightdistance.get()) {
                        Vec3d targetDir = killtarget.getBoundingBox().getCenter().subtract(playerPos).normalize();
                        viewDir = targetDir;
                        firstPhase = false;
                    } else {
                        Vec3d aboveDir = aboveTargetPos.subtract(playerPos).normalize();
                        viewDir = aboveDir;
                    }

                    mc.player.setSprinting(true);
                    mc.player.setVelocity(viewDir.multiply(lungeSpeed));
                }
                case Auto_FromAboveFirst -> {
                    if (!firstPhase || aboveTargetPos == null) {
                        Vec3d targetCenter = killtarget.getBoundingBox().getCenter();
                        aboveTargetPos = new Vec3d(targetCenter.x, targetCenter.y + aboveHeight.get(), targetCenter.z);
                        firstPhase = true;
                    }

                    Vec3d playerPos = mc.player.getEntityPos();
                    double distToAbove = playerPos.distanceTo(aboveTargetPos);

                    boolean FromAbovePathValid = isFromAbovePathValid(aboveTargetPos, killtarget);

                    if (!FromAbovePathValid) {
                        Vec3d targetDir = killtarget.getBoundingBox().getCenter().subtract(playerPos).normalize();
                        viewDir = targetDir;
                        firstPhase = false;
                    } else if (distToAbove < aboveHeightdistance.get()) {
                        Vec3d targetDir = killtarget.getBoundingBox().getCenter().subtract(playerPos).normalize();
                        viewDir = targetDir;
                        firstPhase = false;
                    } else {
                        Vec3d aboveDir = aboveTargetPos.subtract(playerPos).normalize();
                        viewDir = aboveDir;
                    }

                    mc.player.setSprinting(true);
                    mc.player.setVelocity(viewDir.multiply(lungeSpeed));
                }
            }
        }
    }
    private boolean isFromAbovePathValid(Vec3d abovePos, Entity target) {
        if (mc.world == null || abovePos == null) return false;

        Vec3d targetCenter = target.getBoundingBox().getCenter();

        if (invalid(abovePos)) {
            return false;
        }

        if (checkdistanceinvalid.get()) {
            double checkDistance = aboveHeightdistance.get();
            int radius = (int)checkDistance;
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Vec3d testPos = abovePos.add(x, y, z);
                        if (testPos.distanceTo(abovePos) <= checkDistance && invalid(testPos)) {
                            return false;
                        }
                    }
                }
            }
        }

        int pathSteps = Math.max(10, (int)(abovePos.distanceTo(targetCenter) * 2.5));
        for (int i = 1; i < pathSteps; i++) {
            double t = i / (double)pathSteps;
            Vec3d sample = abovePos.lerp(targetCenter, t);
            if (invalid(sample)) {
                return false;
            }
        }

        return true;
    }

    private final BlockPos.Mutable mutablePos = new BlockPos.Mutable();
    private final Map<Vec3d, Boolean> positionCache = new HashMap<>();
    private boolean invalid(Vec3d pos) {
        if (mc.world == null) return true;

        double clampedY = MathHelper.clamp(pos.y, mc.world.getBottomY(), mc.world.getTopYInclusive() - 1);
        if (clampedY != pos.y) return true;

        BlockPos floored = BlockPos.ofFloored(pos);
        int chunkX = floored.getX() >> 4;
        int chunkZ = floored.getZ() >> 4;
        if (mc.world.getChunkManager().getWorldChunk(chunkX, chunkZ) == null) return true;

        if (positionCache.containsKey(pos)) return positionCache.get(pos);

        Entity entity = mc.player;
        Vec3d delta = pos.subtract(entity.getEntityPos());
        Box box = entity.getBoundingBox().offset(delta);

        mutablePos.set(floored);
        for (int x = -1; x <= 1; x++) {
            mutablePos.setX(floored.getX() + x);
            for (int y = -1; y <= 1; y++) {
                mutablePos.setY(floored.getY() + y);
                for (int z = -1; z <= 1; z++) {
                    mutablePos.setZ(floored.getZ() + z);
                    BlockState state = mc.world.getBlockState(mutablePos);
                    if (state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)
                            || state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.CAMPFIRE)
                            || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.POWDER_SNOW)) {
                        positionCache.put(pos, true);
                        return true;
                    }
                }
            }
        }

        for (Entity e : mc.world.getOtherEntities(entity, box)) {
            if (e.isCollidable(entity)) {
                positionCache.put(pos, true);
                return true;
            }
        }

        boolean collides = mc.world.getBlockCollisions(entity, box).iterator().hasNext();
        positionCache.put(pos, collides);
        return collides;
    }
    private void rotateToTarget(Entity target) {
        if (mc.player == null || target == null) return;
        Vec3d playerPos = mc.player.getEyePos();
        Box box = target.getBoundingBox();

        // Calculate height difference
        double targetCenterY = box.getCenter().y;
        double heightDiff = targetCenterY - playerPos.y;

        // Adjust aim point based on height difference
        // If target is above: aim lower (towards their feet)
        // If target is below: aim higher (towards their head)
        // Scale: more height diff = more offset
        double targetY;
        double boxHeight = box.maxY - box.minY;

        if (Math.abs(heightDiff) < 1.0) {
            // Same level - aim at center
            targetY = targetCenterY;
        } else if (heightDiff > 0) {
            // Target above - aim at lower part
            // More height diff = aim lower
            double offset = Math.min(heightDiff / 5.0, 0.4); // max 40% down from center
            targetY = targetCenterY - (boxHeight * offset);
        } else {
            // Target below - aim at upper part
            double offset = Math.min(-heightDiff / 5.0, 0.4); // max 40% up from center
            targetY = targetCenterY + (boxHeight * offset);
        }

        Vec3d targetPos = new Vec3d(box.getCenter().x, targetY, box.getCenter().z);
        Vec3d toTarget = targetPos.subtract(playerPos).normalize();
        float yaw = (float) (Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.asin(toTarget.y));
        mc.player.setYaw(yaw);
        mc.player.setHeadYaw(yaw);
        mc.player.setPitch(pitch);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!Utils.canUpdate()) return;
        if (!(event.packet instanceof PlayerMoveC2SPacket p)) return;
        if (mode.get() == Mode.Blink && isBlinking && !isFlushing) {
            event.cancel();
            synchronized (packets) {
                if (!packets.isEmpty()) {
                    PlayerMoveC2SPacket last = packets.get(packets.size() - 1);
                    if (isSamePacket(p, last)) return;
                }
                packets.add(p);
            }
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (mc.world == null) return;
        if (!(event.packet instanceof PlayerPositionLookS2CPacket)) return;
        if (mode.get() == Mode.Blink && isBlinking) {
            synchronized (packets) {
                packets.clear();
            }
            startPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            lastTargetDistance = killtarget != null ? mc.player.distanceTo(killtarget) : Double.MAX_VALUE;
            wasApproaching = false;
        }
    }

    private int getReadyTicks(Item item) {
        String name = item.toString().toLowerCase();
        int value = 14;
        if (name.contains("wooden")) value = 14;
        else if (name.contains("stone") || name.contains("golden")) value = 13;
        else if (name.contains("copper")) value = 12;
        else if (name.contains("iron")) value = 11;
        else if (name.contains("diamond")) value = 9;
        else if (name.contains("netherite")) value = 7;
        return Math.round(value * (getreadyticksModifier.get() / 100.0f));
    }

    private void startBlink() {
        isBlinking = true;
        startPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        synchronized (packets) {
            packets.clear();
        }
        lastTargetDistance = killtarget != null ? mc.player.distanceTo(killtarget) : Double.MAX_VALUE;
        wasApproaching = false;
    }

    private boolean isSamePacket(PlayerMoveC2SPacket a, PlayerMoveC2SPacket b) {
        return a.isOnGround() == b.isOnGround()
                && a.getYaw(-1) == b.getYaw(-1)
                && a.getPitch(-1) == b.getPitch(-1)
                && a.getX(-1) == b.getX(-1)
                && a.getY(-1) == b.getY(-1)
                && a.getZ(-1) == b.getZ(-1);
    }

    private void flushPackets() {
        if (mc.player == null || mc.player.networkHandler == null) return;
        synchronized (packets) {
            if (packets.isEmpty()) return;
            isFlushing = true;
            Vec3d currentPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            double distance = startPos != null ? startPos.distanceTo(currentPos) : 0;
            if (distance < flushRange.get()) {
                packets.clear();
                isFlushing = false;
                return;
            }

            // Calculate boosted start position if boost is enabled
            // Raycast to find max valid position (avoid walls)
            Vec3d sendStartPos = startPos;
            double boost = blinkDistanceBoost.get();
            if (boost > 0 && startPos != null) {
                Vec3d direction = currentPos.subtract(startPos);
                Vec3d horizontalDir = new Vec3d(direction.x, 0, direction.z).normalize();
                if (horizontalDir.length() > 0.01) {
                    // Target position with full boost
                    Vec3d targetPos = startPos.subtract(horizontalDir.multiply(boost));

                    // Raycast from startPos backwards to check for walls
                    HitResult hit = mc.world.raycast(new RaycastContext(
                            startPos, targetPos,
                            RaycastContext.ShapeType.COLLIDER,
                            RaycastContext.FluidHandling.NONE,
                            mc.player
                    ));

                    if (hit.getType() == HitResult.Type.MISS) {
                        // No wall, use full boost
                        sendStartPos = targetPos;
                    } else {
                        // Wall found, use position just before wall
                        sendStartPos = hit.getPos().add(horizontalDir.multiply(0.5));
                    }
                }
            }

            if (sendStartPos != null) {
                PlayerMoveC2SPacket startPacket = new PlayerMoveC2SPacket.Full(
                        sendStartPos.x, sendStartPos.y, sendStartPos.z,
                        mc.player.getYaw(), mc.player.getPitch(), false, false
                );
                mc.player.networkHandler.sendPacket(startPacket);
            }
            if (chatFeedback) {
                double totalDist = sendStartPos != null ? sendStartPos.distanceTo(currentPos) : distance;
                info("Flush: %.1f blocks (actual=%.1f, boost=%.1f)", totalDist, distance, boost);
            }
            PlayerMoveC2SPacket endPacket = new PlayerMoveC2SPacket.Full(
                    currentPos.x, currentPos.y, currentPos.z,
                    mc.player.getYaw(), mc.player.getPitch(),
                    mc.player.isOnGround(), mc.player.horizontalCollision
            );
            mc.player.networkHandler.sendPacket(endPacket);
            packets.clear();
            isFlushing = false;
        }
    }

    private Entity target() {
        if (mc.player == null || mc.world == null) return null;
        if (mc.crosshairTarget instanceof EntityHitResult hit) {
            if (isValidTarget(hit.getEntity())) return hit.getEntity();
        }
        double maxRange = maxrange.get();
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        HitResult blockHit = mc.world.raycast(new RaycastContext(eyePos,
                eyePos.add(lookVec.multiply(maxRange)), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player));
        double rayLength = blockHit.getType() == HitResult.Type.MISS ? maxRange :
                eyePos.distanceTo(blockHit.getPos());
        List<Entity> candidates = mc.world.getOtherEntities(mc.player,
                mc.player.getBoundingBox().stretch(lookVec.multiply(rayLength)),
                e -> e instanceof LivingEntity && e.isAlive() && e != mc.player);
        candidates.sort(Comparator.comparingDouble(e ->
                eyePos.squaredDistanceTo(e.getBoundingBox().getCenter())));
        double coneAngle = 0.999;
        for (Entity e : candidates) {
            double dist = eyePos.distanceTo(e.getBoundingBox().getCenter());
            if (dist > maxRange) break;
            if (!isValidTarget(e)) continue;
            if (!canSeeTarget(e)) continue;
            Vec3d toEntity = e.getBoundingBox().getCenter().subtract(eyePos).normalize();
            if (lookVec.dotProduct(toEntity) > coneAngle) {
                return e;
            }
        }
        return null;
    }

    private boolean canSeeTarget(Entity target) {
        if (mc.player == null || mc.world == null) return false;
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetCenter = target.getBoundingBox().getCenter();
        HitResult result = mc.world.raycast(new RaycastContext(
                eyePos, targetCenter,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player
        ));
        if (result.getType() == HitResult.Type.MISS) return true;
        return eyePos.distanceTo(result.getPos()) >= eyePos.distanceTo(targetCenter) - 0.5;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null) return false;

        if (entity instanceof PlayerEntity player && ignorefriends.get() && Friends.get().isFriend(player)) {
            return false;
        }

        EntityType<?> type = entity.getType();
        boolean inList = targetEntities.get().contains(type);
        if (targetListMode.get() == TargetListMode.Whitelist) {
            return inList || targetEntities.get().isEmpty();
        } else {
            return !inList;
        }
    }
}