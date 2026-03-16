package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;

public class AutoObsidian extends Module {
    public AutoObsidian() {
        super(AddonTemplate.CATEGORY, "Auto Obsidian", "Automatically places obsidian for crystals (Support).");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Place range.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> minDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-damage")
        .description("Minimum damage required to place obsidian.")
        .defaultValue(6.0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between placements.")
        .defaultValue(0) // 推荐设为 0 或 1
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the block when placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings hand when placing.")
        .defaultValue(true)
        .build()
    );

    private int timer;

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0) {
            timer--;
            return;
        }

        // 1. 基础检查
        if (!InvUtils.testInHotbar(Items.OBSIDIAN)) return;
        PlayerEntity target = getNearestTarget();
        if (target == null) return;

        // 2. 关键修复：先检查是否已经有现成的、能造成足够伤害的水晶位
        // 如果有，就不放黑曜石了，让 AutoCrystal 直接去炸
        if (hasExistingCrystalSpot(target)) {
            return; 
        }

        // 3. 如果没有好位置，才寻找并放置黑曜石
        BlockPos bestPos = findBestPos(target);

        if (bestPos != null) {
            BlockUtils.place(bestPos, InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 50, swing.get(), true);
            timer = delay.get();
        }
    }

    // 检查是否已经存在有效的放置点（黑曜石/基岩 + 空气）
    private boolean hasExistingCrystalSpot(PlayerEntity target) {
        BlockPos pPos = mc.player.getBlockPos();
        int r = (int) Math.ceil(range.get());

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = pPos.add(x, y, z);

                    // 检查距离
                    if (Math.sqrt(pos.getSquaredDistance(mc.player.getPos())) > range.get()) continue;

                    // 检查基座是否是黑曜石或基岩
                    BlockState state = mc.world.getBlockState(pos);
                    if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) continue;

                    // 检查上方是否有空间放水晶 (需要是空气)
                    if (!mc.world.getBlockState(pos.up()).isAir()) continue;

                    // 计算如果这里放水晶，伤害是否达标
                    double damage = calculateDamage(target, pos.up());
                    
                    // 只要找到一个现成的、伤害够的位置，就返回 true，阻止放置新的黑曜石
                    if (damage >= minDamage.get()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private PlayerEntity getNearestTarget() {
        if (mc.world == null) return null;
        return mc.world.getPlayers().stream()
            .filter(p -> !p.isDead() && p != mc.player && !Friends.get().isFriend(p))
            .filter(p -> p.distanceTo(mc.player) <= 10)
            .min(Comparator.comparingDouble(p -> p.distanceTo(mc.player)))
            .orElse(null);
    }

   // 修复后的寻找最佳位置逻辑：伤害优先，距离其次
    private BlockPos findBestPos(PlayerEntity target) {
        if (mc.player == null || mc.world == null) return null;

        BlockPos bestPos = null;
        double maxDmg = minDamage.get();
        double bestDist = Double.MAX_VALUE; // 记录最佳位置离敌人的距离

        BlockPos pPos = mc.player.getBlockPos();
        int r = (int) Math.ceil(range.get());

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = pPos.add(x, y, z);

                    // 1. 基础检查
                    if (Math.sqrt(pos.getSquaredDistance(mc.player.getPos())) > range.get()) continue;
                    if (!BlockUtils.canPlace(pos)) continue;
                    if (!mc.world.getBlockState(pos.up()).isAir()) continue;
                    if (intersectsWithEntity(pos)) continue;

                    // 2. 计算伤害
                    double damage = calculateDamage(target, pos.up());

                    if (damage < minDamage.get()) continue;

                    // 3. 智能比较逻辑
                    // 计算这个位置离敌人的距离
                    double distToTarget = pos.getSquaredDistance(target.getPos());

                    // 情况A: 发现了伤害更高的地方 -> 直接更新
                    if (damage > maxDmg) {
                        maxDmg = damage;
                        bestPos = pos;
                        bestDist = distToTarget;
                    } 
                    // 情况B: 伤害差不多(相差0.5以内)，但这个位置离敌人更近 -> 更新
                    else if (Math.abs(damage - maxDmg) <= 0.5 && distToTarget < bestDist) {
                        maxDmg = damage;
                        bestPos = pos;
                        bestDist = distToTarget;
                    }
                }
            }
        }
        return bestPos;
    }

    private double calculateDamage(PlayerEntity target, BlockPos crystalPos) {
        if (target == null || crystalPos == null) return 0;

        Vec3d explosionPos = crystalPos.toCenterPos().add(0, -0.5, 0);

        // 使用安全的伤害计算，防止崩溃
        return DamageUtils.crystalDamage(target, explosionPos, target.getBoundingBox(), target.getPos(), 
            (context, block) -> {
                Vec3d blockCenter = block.toCenterPos();
                BlockState state = mc.world.getBlockState(block);
                if (!state.getCollisionShape(mc.world, block).isEmpty()) {
                    return new BlockHitResult(blockCenter, Direction.UP, block, false);
                }
                return BlockHitResult.createMissed(blockCenter, Direction.UP, block);
            });
    }

    private boolean intersectsWithEntity(BlockPos pos) {
        Box box = new Box(pos);
        for (Entity entity : mc.world.getEntities()) {
            if (!entity.isSpectator() && entity.isAlive() && entity.getBoundingBox().intersects(box)) {
                return true;
            }
        }
        return false;
    }
}