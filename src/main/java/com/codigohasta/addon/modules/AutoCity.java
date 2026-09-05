package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.BlockPosX;
import com.codigohasta.addon.utils.Timer;
import com.codigohasta.addon.utils.leaveshack.BlockUtil;
import com.codigohasta.addon.utils.leaveshack.CombatUtil;
import com.codigohasta.addon.utils.leaveshack.InventoryUtil;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class AutoCity extends Module {
    public static AutoCity INSTANCE;
    public AutoCity() {
        super(AddonTemplate.CATEGORY, "L自动挖角", "来自leaveshack的自动挖角");
        INSTANCE = this;
    }
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Integer> targetRange = sgGeneral.add(new IntSetting.Builder()
            .name("TargetRange")
            .description("目标距离")
            .defaultValue(6)
            .min(0)
            .sliderMax(8)
            .build()
    );
    public final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
            .name("Range")
            .description("操作距离")
            .defaultValue(6)
            .min(0)
            .sliderMax(8)
            .build()
    );
    private final Setting<Boolean> doubleBreak = sgGeneral.add(new BoolSetting.Builder()
            .name("DoubleBreak")
            .description("双挖")
            .defaultValue(true)
            .build()
    );
    public final Setting<Boolean> delay = sgGeneral.add(new BoolSetting.Builder()
            .name("CityDelay")
            .description("挖角延迟")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> antiCrawl = sgGeneral.add(new BoolSetting.Builder()
            .name("AntiCrawl")
            .description("自动反趴下")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> preferSelfClick = sgGeneral.add(new BoolSetting.Builder()
            .name("PreferSelfClick")
            .description("优先处理手动点击的挖掘")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> head = sgGeneral.add(new BoolSetting.Builder()
            .name("Head")
            .description("挖头")
            .defaultValue(false)
            .build()
    );
    private final Setting<Boolean> burrow = sgGeneral.add(new BoolSetting.Builder()
            .name("Burrow")
            .description("挖黑曜石卡身")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> face = sgGeneral.add(new BoolSetting.Builder()
            .name("Face")
            .description("挖脸")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> down = sgGeneral.add(new BoolSetting.Builder()
            .name("Down")
            .description("挖脚底")
            .defaultValue(false)
            .build()
    );
    private final Setting<Boolean> surround = sgGeneral.add(new BoolSetting.Builder()
            .name("Surround")
            .description("挖包围")
            .defaultValue(true)
            .build()
    );
    private final Timer cityTimer = new Timer();
    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundPlayerActionPacket packet) {
            if (packet.getAction() == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                cityTimer.reset();
            }
        }
    }
    @EventHandler
    public void onTick(TickEvent.Pre event) {
        Player player = CombatUtil.getClosestEnemy(targetRange.get());
        if (preferSelfClick.get() && PacketMinePlus.selfClickPos != null) return;
        if (delay.get() && !cityTimer.passedMs(PacketMinePlus.INSTANCE.mineDelay.get())) return;
        if (antiCrawl.get() && mc.player.isVisuallyCrawling()) {
            if (canBreak(mc.player.blockPosition().above()) && !mc.player.blockPosition().above().equals(PacketMinePlus.targetPos) && !mc.player.blockPosition().above().equals(PacketMinePlus.secondPos)) {
                PacketMinePlus.selfClickPos = mc.player.blockPosition().above();
                PacketMinePlus.INSTANCE.mine(mc.player.blockPosition().above());
                return;
            }
        }
        if (player == null) return;
        doBreak(player);
    }

    private void doBreak(Player player) {
        BlockPos pos = player.blockPosition();
        double[] yOffset = new double[]{-0.8, 0.3, 2.3, 1.1};
        double[] xzOffset = new double[]{0.3, -0.3};
        if (!doubleBreak.get()) {
            for (Player entity : CombatUtil.getEnemies(targetRange.get())) {
                for (double y : yOffset) {
                    for (double x : xzOffset) {
                        for (double z : xzOffset) {
                            BlockPos offsetPos = new BlockPosX(entity.getX() + x, entity.getY() + y, entity.getZ() + z);
                            if (isObsidian(offsetPos) && BlockUtil.getClickSideStrict(offsetPos) != null && offsetPos.equals(PacketMinePlus.targetPos)) {
                                return;
                            }
                        }
                    }
                }
            }
        } else {
            int count = 0;
            for (Player entity : CombatUtil.getEnemies(targetRange.get())) {
                for (double y : yOffset) {
                    for (double x : xzOffset) {
                        for (double z : xzOffset) {
                            BlockPos offsetPos = new BlockPosX(entity.getX() + x, entity.getY() + y, entity.getZ() + z);
                            if (isObsidian(offsetPos) && BlockUtil.getClickSideStrict(offsetPos) != null && (offsetPos.equals(PacketMinePlus.targetPos) || offsetPos.equals(PacketMinePlus.secondPos))) {
                                count++;
                            }
                        }
                    }
                }
            }
            if (count == 2) {
                return;
            }
        }
        List<Float> yList = new ArrayList<>();
        if (down.get()) {
            yList.add(-0.8f);
        }
        if (head.get()) {
            yList.add(2.3f);
        }
        if (burrow.get()) {
            yList.add(0.3f);
        }
        if (face.get()) {
            yList.add(1.1f);
        }
        for (double y : yList) {
            for (double offset : xzOffset) {
                BlockPos offsetPos = new BlockPosX(player.getX() + offset, player.getY() + y, player.getZ() + offset);
                if (canBreak(offsetPos)) {
                    PacketMinePlus.INSTANCE.mine(offsetPos);
                    return;
                }
            }
        }
        for (double y : yList) {
            for (double offset : xzOffset) {
                for (double offset2 : xzOffset) {
                    BlockPos offsetPos = new BlockPosX(player.getX() + offset2, player.getY() + y, player.getZ() + offset);
                    if (canBreak(offsetPos)) {
                        PacketMinePlus.INSTANCE.mine(offsetPos);
                        return;
                    }
                }
            }
        }
        if (surround.get()) {
            for (Direction i : Direction.values()) {
                if (i == Direction.UP || i == Direction.DOWN) continue;
                if (Math.sqrt(mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter())) > range.get()) {
                    continue;
                }
                if ((mc.level.isEmptyBlock(pos.relative(i)) || pos.relative(i).equals(PacketMinePlus.targetPos)) && canPlaceCrystal(pos.relative(i), false)) {
                    if (!doubleBreak.get()) return;
                    if (PacketMinePlus.targetPos != null && PacketMinePlus.completed) return;
                }
            }
            ArrayList<BlockPos> list = new ArrayList<>();
            for (Direction i : Direction.values()) {
                if (i == Direction.UP || i == Direction.DOWN) continue;
                if (Math.sqrt(mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter())) > range.get()) {
                    continue;
                }
                if (canBreak(pos.relative(i)) && canPlaceCrystal(pos.relative(i), true) && !isSurroundPos(pos.relative(i))) {
                    list.add(pos.relative(i));
                }
            }
            if (!list.isEmpty()) {
                PacketMinePlus.INSTANCE.mine(list.stream().min(Comparator.comparingDouble((E) -> E.distToCenterSqr(mc.player.getEyePosition()))).get());
            } else {
                for (Direction i : Direction.values()) {
                    if (i == Direction.UP || i == Direction.DOWN) continue;
                    if (Math.sqrt(mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter())) > range.get()) {
                        continue;
                    }
                    if (canBreak(pos.relative(i)) && canPlaceCrystal(pos.relative(i), false)) {
                        list.add(pos.relative(i));
                    }
                }
                if (!list.isEmpty()) {
                    PacketMinePlus.INSTANCE.mine(list.stream().min(Comparator.comparingDouble((E) -> E.distToCenterSqr(mc.player.getEyePosition()))).get());
                }
            }
        }
    }
    private boolean isSurroundPos(BlockPos pos) {
        for (Direction i : Direction.values()) {
            if (i == Direction.UP || i == Direction.DOWN) {
                continue;
            }
            BlockPos self = getPlayerPos(true);
            if (self.relative(i).equals(pos)) {
                return true;
            }
        }
        return false;
    }
    public BlockPos getPlayerPos(boolean fix) {
        return new BlockPosX(new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()), fix);
    }
    public Block getBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock();
    }
    public boolean canPlaceCrystal(BlockPos pos, boolean block) {
        BlockPos obsPos = pos.below();
        BlockPos boost = obsPos.above();
        return (getBlock(obsPos) == Blocks.BEDROCK || getBlock(obsPos) == Blocks.OBSIDIAN || !block)
                && BlockUtil.noEntityBlockCrystal(boost, true, true)
                && BlockUtil.noEntityBlockCrystal(boost.above(), true, true)
                ;
    }
    public static final List<Block> hard = Arrays.asList(
            Blocks.OBSIDIAN, Blocks.ENDER_CHEST, Blocks.NETHERITE_BLOCK, Blocks.CRYING_OBSIDIAN, Blocks.RESPAWN_ANCHOR, Blocks.ANCIENT_DEBRIS, Blocks.ANVIL
    );

    private boolean isObsidian(BlockPos pos) {
        return mc.player.getEyePosition().distanceTo(pos.getCenter()) <= PacketMinePlus.INSTANCE.range.get() && hard.contains(mc.level.getBlockState(pos).getBlock()) && BlockUtil.getClickSideStrict(pos) != null;
    }

    private boolean canBreak(BlockPos pos) {
        return isObsidian(pos) && BlockUtil.getClickSideStrict(pos) != null && !pos.equals(PacketMinePlus.targetPos) && !pos.equals(PacketMinePlus.secondPos) && (((PacketMinePlus.targetPos == null || PacketMinePlus.secondPos == null) && doubleBreak.get()) || (PacketMinePlus.targetPos == null));
    }
}
