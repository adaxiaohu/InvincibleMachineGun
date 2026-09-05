package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.leaveshack.CombatUtil;
import com.codigohasta.addon.utils.leaveshack.InventoryUtil;
import com.codigohasta.addon.utils.Timer;
import com.codigohasta.addon.utils.leaveshack.Rotation;
import com.codigohasta.addon.utils.leaveshack.BlockUtil;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.PoweredBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import com.codigohasta.addon.mixin.InventoryAccessor;

public class PistonCrystal extends Module {
    public static PistonCrystal INSTANCE;
    public PistonCrystal() {
        super(AddonTemplate.CATEGORY, "L活塞水晶", "来自leaveshack的活塞水晶");
        INSTANCE = this;
    }
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("TargetRange")
            .description("目标距离")
            .defaultValue(6.0)
            .sliderRange(1, 6)
            .build()
    );
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("放置距离")
            .defaultValue(4.5)
            .sliderRange(1, 6)
            .build()
    );
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay-ms")
            .description("放置延迟")
            .defaultValue(50)
            .sliderRange(0, 500)
            .build()
    );
    private final Setting<Integer> breakDelay = sgGeneral.add(new IntSetting.Builder()
            .name("breakDelay-ms")
            .description("破坏延迟")
            .defaultValue(300)
            .sliderRange(0, 500)
            .build()
    );
    private final Setting<Double> minDamage = sgGeneral.add(new DoubleSetting.Builder()
            .name("MinDamage")
            .description("最小对敌伤害")
            .defaultValue(4.0)
            .sliderRange(1, 36)
            .build()
    );
    private final Setting<Double> maxSelfDmg = sgGeneral.add(new DoubleSetting.Builder()
            .name("MaxSelfDmg")
            .description("最大自伤")
            .defaultValue(12)
            .sliderRange(1, 36)
            .build()
    );
    private final Setting<Boolean> usingPause = sgGeneral.add(new BoolSetting.Builder()
            .name("UsingPause")
            .description("使用暂停")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> onlyMain = sgGeneral.add(new BoolSetting.Builder()
            .name("OnlyMain")
            .description("仅检查主手")
            .defaultValue(true)
            .visible(usingPause::get)
            .build()
    );
    private final Setting<Boolean> noSuicide = sgGeneral.add(new BoolSetting.Builder()
            .name("NoSuicide")
            .description("防自杀")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("转头")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> inventory = sgGeneral.add(new BoolSetting.Builder()
            .name("InventorySwap")
            .description("背包鬼手")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> mine = sgGeneral.add(new BoolSetting.Builder()
            .name("Mine")
            .description("自动挖掘")
            .defaultValue(true)
            .build()
    );
//    private final Setting<Integer> checkMineTime = sgGeneral.add(new IntSetting.Builder()
//            .name("CheckMineTime")
//            .defaultValue(300)
//            .sliderRange(0, 1000)
//            .build()
//    );
    private final Setting<Boolean> yawDeceive = sgGeneral.add(new BoolSetting.Builder()
            .name("YawDeceive")
            .description("朝向欺骗")
            .defaultValue(true)
            .build()
    );
    private final Setting<RedstoneMode> redStoneMode = sgGeneral.add(new EnumSetting.Builder<RedstoneMode>()
            .name("redStone")
            .description("红石模式")
            .defaultValue(RedstoneMode.Block)
            .build()
    );
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("渲染")
            .defaultValue(true)
            .build()
    );
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("形状模式")
            .defaultValue(ShapeMode.Both)
            .build()
    );
    private final Setting<SettingColor> crystalColor = sgRender.add(new ColorSetting.Builder()
            .name("crystal")
            .description("水晶颜色")
            .defaultValue(new SettingColor(255, 0, 0, 80))
            .build()
    );
    private final Setting<SettingColor> pistonColor = sgRender.add(new ColorSetting.Builder()
            .name("piston")
            .description("活塞颜色")
            .defaultValue(new SettingColor(255, 255, 255, 80))
            .build()
    );
    private final Setting<SettingColor> redstoneColor = sgRender.add(new ColorSetting.Builder()
            .name("redstone")
            .description("红石颜色")
            .defaultValue(new SettingColor(255, 100, 0, 80))
            .build()
    );
    private long lastAction = 0;
    private BlockPos crystalPos;
    private BlockPos pistonPos;
    private BlockPos redstonePos;
    private BlockPos lastPiston;
    private BlockPos lastRedstone;
    private BlockPos lastCrystal;
    private Direction face;
    private final Timer breakTimer = new Timer();
    private Player target;
//    private int checkMine = 9999999;
    @Override
    public void onActivate() {
//        checkMine = 999999999;
        breakTimer.setMs(99999999);
    }
    @Override
    public String getInfoString() {
        return target == null ? null : "§f[" + target.getName().getString() + "]";
    }
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        if (System.currentTimeMillis() - lastAction < delay.get()) return;
        target = CombatUtil.getClosestEnemy(targetRange.get());
        int redStone = findRedstone();
        int crystal = inventory.get() ? InventoryUtil.findItemInventorySlot(Items.END_CRYSTAL) : InventoryUtil.findItem(Items.END_CRYSTAL);
        int piston = inventory.get() ? InventoryUtil.findClassInventory(PistonBaseBlock.class) : InventoryUtil.findClass(PistonBaseBlock.class);
        if (shouldPause()) {
            return;
        }
        if (redStone == -1 || crystal == -1 || piston == -1) {
            pistonPos = null;
            crystalPos = null;
            redstonePos = null;
            return;
        }
        if (crystalPos != null && redstonePos != null && pistonPos != null) {
            if (!BlockUtil.canPlaceCrystal(crystalPos) || !BlockUtil.canPlace(redstonePos) || !BlockUtil.canPlace(pistonPos)) {
                pistonPos = null;
                crystalPos = null;
                redstonePos = null;
            }
        }
        if (target == null) return;
        if (pistonPos == null && crystalPos == null && redstonePos == null) doPistonCrystal(target);
        if (lastPiston != null) {
            if (BlockUtil.getBlock(lastPiston.relative(face.getOpposite())) instanceof PistonHeadBlock && BlockUtil.getBlock(lastPiston) instanceof PistonBaseBlock) {
                if (mine.get()) {
                    Direction side = BlockUtil.getClickSide(lastPiston);
                    mc.gameMode.startDestroyBlock(lastPiston, side);
                    lastPiston = null;
                    lastCrystal = null;
                    lastRedstone = null;
                    return;
                }
            }
        }
        if (pistonPos != null && BlockUtil.canPlace(pistonPos)) {
            place(Items.PISTON, piston, pistonPos, face);
            lastPiston = pistonPos;
        }
        if (redstonePos != null && BlockUtil.canPlace(redstonePos)) {
            if (redStoneMode.get() == RedstoneMode.Torch) {
                placeTorch(redstonePos, redStone);
            } else {
                place(Items.REDSTONE_BLOCK, redStone, redstonePos, null);
            }
            lastRedstone = redstonePos;
        }
        if (crystalPos != null && BlockUtil.canPlaceCrystal(crystalPos)) {
            placeCrystal(crystalPos, crystal);
            lastCrystal = crystalPos;
            lastAction = System.currentTimeMillis();
            return;
        }
        if (breakTimer.passedMs(breakDelay.get())) {
            if (BlockUtil.hasCrystal(target.blockPosition().above())) {
                CombatUtil.attackCrystal(target.blockPosition().above(), true, false);
                lastAction = System.currentTimeMillis();
                pistonPos = null;
                crystalPos = null;
                redstonePos = null;
                breakTimer.reset();
            }
            if (BlockUtil.hasCrystal(target.blockPosition().above(2))) {
                CombatUtil.attackCrystal(target.blockPosition().above(2), true, false);
                lastAction = System.currentTimeMillis();
                pistonPos = null;
                crystalPos = null;
                redstonePos = null;
                breakTimer.reset();
            }
        }
    }

    private int findRedstone() {
        if (redStoneMode.get() == RedstoneMode.Torch) {
            return inventory.get() ? InventoryUtil.findItemInventorySlot(Items.REDSTONE_TORCH) : InventoryUtil.findItem(Items.REDSTONE_TORCH);
        } else {
            return inventory.get() ? InventoryUtil.findItemInventorySlot(Items.REDSTONE_BLOCK) : InventoryUtil.findItem(Items.REDSTONE_BLOCK);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent e) {
        if (!render.get()) return;

        if (crystalPos != null) {
            e.renderer.box(crystalPos, crystalColor.get(), crystalColor.get(), shapeMode.get(), 0);
        }

        if (pistonPos != null) {
            e.renderer.box(pistonPos, pistonColor.get(), pistonColor.get(), shapeMode.get(), 0);
        }

        if (redstonePos != null) {
            e.renderer.box(redstonePos, redstoneColor.get(), redstoneColor.get(), shapeMode.get(), 0);
        }
    }

    private void doPistonCrystal(Player target) {
        BlockPos base = target.blockPosition();
        BlockPos tempCrystalPos = null;
        BlockPos tempPistonPos = null;
        BlockPos tempRedstonePos = null;
        Vec3 vec = new Vec3(base.above().getX() + 0.5, base.above().getY(), base.above().getZ() + 0.5);
        float damage1 = DamageUtils.crystalDamage(target, vec);
        float selfDmg1 = DamageUtils.crystalDamage(mc.player, vec);
        if (damage1 > minDamage.get() && selfDmg1 <= maxSelfDmg.get()) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (!yawDeceive.get() && dir != mc.player.getDirection()) continue;
                BlockPos temp1 = base.relative(dir).above();
                if (!BlockUtil.canPlaceCrystal(temp1)) continue;
                if (mc.player.getEyePosition().distanceTo(temp1.getCenter()) > range.get()) {
                    continue;
                }
                tempCrystalPos = temp1;
                for (Direction dir2 : Direction.Plane.HORIZONTAL) {
                    if (dir2 == dir.getOpposite()) continue;
                    BlockPos temp2 = temp1.relative(dir).relative(dir2);
                    if (!mc.level.isEmptyBlock(temp2.relative(dir.getOpposite())) && !mc.level.getBlockState(temp2.relative(dir.getOpposite())).canBeReplaced()) {
                        continue;
                    }
                    if (!BlockUtil.canPlace(temp2) && !(BlockUtil.getBlock(temp2) instanceof PistonBaseBlock)) {
                        for (Direction help : Direction.values()) {
                            if (help == dir.getOpposite()) continue;
                            if (!BlockUtil.isGrimDirection(temp2.relative(help), help.getOpposite())) continue;
                            if (!BlockUtil.canPlace(temp2.relative(help)) || mc.player.getEyePosition().distanceTo(temp2.relative(help).getCenter()) > range.get())
                                continue;
                            BlockPos helpPos = temp2.relative(help);
                            int old = ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot();
                            Direction side = BlockUtil.getPlaceSide(helpPos, null);
                            doSwap(findRedstone());
                            BlockUtil.placeBlock(helpPos, side, rotate.get());
                            if (inventory.get()) {
                                doSwap(findRedstone());
                            } else {
                                doSwap(old);
                            }
                            return;
                        }
                        continue;
                    }
                    if (mc.player.getEyePosition().distanceTo(temp2.getCenter()) > range.get()) {
                        continue;
                    }
                    tempPistonPos = temp1.relative(dir).relative(dir2);
                    for (Direction dir3 : Direction.values()) {
                        if (dir3 == dir.getOpposite()) continue;
                        BlockPos temp3 = temp2.relative(dir3);
                        if ((BlockUtil.getBlock(temp3) instanceof PoweredBlock && redStoneMode.get() == RedstoneMode.Block) || (BlockUtil.getBlock(temp3) instanceof RedstoneTorchBlock && redStoneMode.get() == RedstoneMode.Torch)) {
                            tempRedstonePos = tempPistonPos.relative(dir3);
                            break;
                        }
                        if (!BlockUtil.canPlace(temp3)) {
                            continue;
                        }
                        if (mc.player.getEyePosition().distanceTo(temp3.getCenter()) > range.get()) {
                            continue;
                        }
                        tempRedstonePos = tempPistonPos.relative(dir3);
                        break;
                    }
                    break;
                }
                if (tempPistonPos == null) {
                    tempCrystalPos = null;
                    tempRedstonePos = null;
                    continue;
                }
                if (tempRedstonePos == null) {
                    tempCrystalPos = null;
                    tempPistonPos = null;
                    continue;
                }
                if (tempCrystalPos == null) {
                    tempPistonPos = null;
                    tempRedstonePos = null;
                    continue;
                }
                if (selfDmg1 > EntityUtils.getTotalHealth(mc.player) && noSuicide.get()) {
                    return;
                }
                face = dir;
                crystalPos = tempCrystalPos;
                pistonPos = tempPistonPos;
                redstonePos = tempRedstonePos;
                return;
            }
        }
        Vec3 vec2 = new Vec3(base.above(2).getX() + 0.5, base.above(2).getY(), base.above(2).getZ() + 0.5);
        float damage2 = DamageUtils.crystalDamage(target, vec2);
        float selfDmg2 = DamageUtils.crystalDamage(mc.player, vec2);
        if (selfDmg2 > EntityUtils.getTotalHealth(mc.player) && noSuicide.get()) return;
        if (damage2 > minDamage.get() && selfDmg2 <= maxSelfDmg.get()) {
            if (crystalPos == null && pistonPos == null && redstonePos == null) {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    if (!yawDeceive.get() && dir != mc.player.getDirection()) continue;
                    BlockPos temp1 = base.relative(dir).above(2);
                    if (!BlockUtil.canPlaceCrystal(temp1)) continue;
                    if (mc.player.getEyePosition().distanceTo(temp1.getCenter()) > range.get()) {
                        continue;
                    }
                    tempCrystalPos = temp1;
                    for (Direction dir2 : Direction.Plane.HORIZONTAL) {
                        if (dir2 == dir.getOpposite()) continue;
                        BlockPos temp2 = temp1.relative(dir).relative(dir2);
                        if (!mc.level.isEmptyBlock(temp2.relative(dir.getOpposite())) && !mc.level.getBlockState(temp2.relative(dir.getOpposite())).canBeReplaced()) {
                            continue;
                        }
                        if (!BlockUtil.canPlace(temp2) && !(BlockUtil.getBlock(temp2) instanceof PistonBaseBlock)) {
                            for (Direction help : Direction.values()) {
                                if (help == dir.getOpposite()) continue;
                                if (!BlockUtil.isGrimDirection(temp2.relative(help), help.getOpposite())) continue;
                                if (!BlockUtil.canPlace(temp2.relative(help)) || mc.player.getEyePosition().distanceTo(temp2.relative(help).getCenter()) > range.get())
                                    continue;
                                BlockPos helpPos = temp2.relative(help);
                                int old = ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot();
                                Direction side = BlockUtil.getPlaceSide(helpPos, null);
                                doSwap(findRedstone());
                                BlockUtil.placeBlock(helpPos, side, rotate.get());
                                if (inventory.get()) {
                                    doSwap(findRedstone());
                                } else {
                                    doSwap(old);
                                }
                                return;
                            }
                            continue;
                        }
                        if (mc.player.getEyePosition().distanceTo(temp2.getCenter()) > range.get()) {
                            continue;
                        }
                        tempPistonPos = temp1.relative(dir).relative(dir2);
                        for (Direction dir3 : Direction.values()) {
                            if (dir3 == dir.getOpposite()) continue;
                            BlockPos temp3 = temp2.relative(dir3);
                            if ((BlockUtil.getBlock(temp3) instanceof PoweredBlock && redStoneMode.get() == RedstoneMode.Block) || (BlockUtil.getBlock(temp3) instanceof RedstoneTorchBlock && redStoneMode.get() == RedstoneMode.Torch)) {
                                tempRedstonePos = tempPistonPos.relative(dir3);
                                break;
                            }
                            if (!BlockUtil.canPlace(temp3)) {
                                continue;
                            }
                            if (mc.player.getEyePosition().distanceTo(temp3.getCenter()) > range.get()) {
                                continue;
                            }
                            tempRedstonePos = tempPistonPos.relative(dir3);
                            break;
                        }
                        break;
                    }
                    if (tempPistonPos == null) {
                        tempCrystalPos = null;
                        tempRedstonePos = null;
                        continue;
                    }
                    if (tempRedstonePos == null) {
                        tempCrystalPos = null;
                        tempPistonPos = null;
                        continue;
                    }
                    if (tempCrystalPos == null) {
                        tempPistonPos = null;
                        tempRedstonePos = null;
                        continue;
                    }
                    if (selfDmg1 > EntityUtils.getTotalHealth(mc.player) && noSuicide.get()) {
                        return;
                    }
                    face = dir;
                    crystalPos = tempCrystalPos;
                    pistonPos = tempPistonPos;
                    redstonePos = tempRedstonePos;
                    return;
                }
            }
        }
    }

    private void place(net.minecraft.world.item.Item item, int slot, BlockPos pos, Direction dir) {
        Direction side = BlockUtil.getPlaceSide(pos, d -> true);
        if (side == null) return;

        if (slot == -1) return;

        int old = ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot();
        doSwap(slot);
        if (rotate.get()) {
            Rotation.snapAt(pos.getCenter().add(new Vec3(side.getUnitVec3i().getX() * 0.5, side.getUnitVec3i().getY() * 0.5, side.getUnitVec3i().getZ() * 0.5)));
        }
        if (item == Items.PISTON) {
            if (yawDeceive.get() && rotate.get()) {
                pistonFacing(dir);
            }
        }
        BlockUtil.placeBlock(pos, side, false);
        if (rotate.get()) {
            Rotation.snapBack();
        }
        if (inventory.get()) {
            doSwap(slot);
        } else {
            doSwap(old);
        }
    }
    private boolean shouldPause() {
        return !usingPause.get() || checkPause(onlyMain.get());
    }
    public boolean checkPause(boolean onlyMain) {
        return (mc.options.keyUse.isDown() || mc.player.isUsingItem()) && (!onlyMain || mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND);
    }
    public static void pistonFacing(Direction i) {
        if (i == Direction.EAST) {
            Rotation.snapAt(-90.0f, 5.0f);
        } else if (i == Direction.WEST) {
            Rotation.snapAt(90.0f, 5.0f);
        } else if (i == Direction.NORTH) {
            Rotation.snapAt(180.0f, 5.0f);
        } else if (i == Direction.SOUTH) {
            Rotation.snapAt(0.0f, 5.0f);
        }
    }
    private void placeCrystal(BlockPos pos, int slot) {
        BlockPos base = pos.below();

        Direction side = BlockUtil.getClickSide(base);
        if (side == null) return;
        if (slot == -1) return;

        int old = ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot();
        doSwap(slot);
        BlockUtil.clickBlock(base, side, rotate.get());
        if (inventory.get()) {
            doSwap(slot);
        } else {
            doSwap(old);
        }
    }

    private void placeTorch(BlockPos pos, int slot) {
        if (!BlockUtil.canPlace(pos)) return;
        ArrayList<Direction> sides = BlockUtil.getPlaceSides(pos, null);
        if (sides.isEmpty()) return;
        for (Direction side : sides) {
            if (BlockUtil.getBlock(pos.relative(side)) instanceof PistonBaseBlock) continue;
            if (side == Direction.UP) continue;
            if (slot == -1) return;
            int old = ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot();
            doSwap(slot);
            BlockUtil.placeBlock(pos, side, rotate.get());
            if (inventory.get()) {
                doSwap(slot);
            } else {
                doSwap(old);
            }
            return;
        }
    }
    private void doSwap(int slot) {
        if (slot == -1) return;
        if (!inventory.get()) {
            InventoryUtil.switchToSlot(slot);
        } else {
            InventoryUtil.inventorySwap(slot, ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot());
        }
    }

    public enum RedstoneMode {
        Torch,
        Block
    }
}
