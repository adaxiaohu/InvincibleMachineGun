package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.BlockPosX;
import com.codigohasta.addon.utils.leaveshack.InventoryUtil;
import com.codigohasta.addon.utils.leaveshack.Rotation;
import com.codigohasta.addon.utils.leaveshack.BlockUtil;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import com.codigohasta.addon.mixin.InventoryAccessor;

public class LegitNoFallLeaves extends Module {
    public static LegitNoFallLeaves INSTANCE;
    public LegitNoFallLeaves() {
        super(AddonTemplate.CATEGORY, "L落地水", "来自leaveshack的合法防摔伤");
        INSTANCE = this;
    }
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Integer> checkDown = sgGeneral.add(new IntSetting.Builder()
            .name("checkDown")
            .description("检查距离")
            .defaultValue(1)
            .min(0)
            .sliderMax(3)
            .build()
    );
    private final Setting<Boolean> inventorySwap = sgGeneral.add(new BoolSetting.Builder()
            .name("inventorySwap")
            .description("背包鬼手")
            .defaultValue(true)
            .build()
    );
    private final Setting<Double> offSet = sgGeneral.add(new DoubleSetting.Builder()
            .name("offSet")
            .description("偏移位移")
            .defaultValue(0.3)
            .min(0)
            .sliderMax(1)
            .build()
    );
    private boolean hasPlacedWater = false;
    private BlockPos lastPos = null;
    @Override
    public void onActivate() {
        hasPlacedWater = false;
    }
    @EventHandler
    private void onRender3d(Render3DEvent event) {
        if (mc.level.dimension() == Level.NETHER) return;
        int old = ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot();
        int water = hasPlacedWater ? findItem(Items.BUCKET) : findItem(Items.WATER_BUCKET);
        if (water != -1) {
            if (hasPlacedWater && lastPos != null) {
                Direction clickSide = BlockUtil.getClickSide(lastPos);
                if (clickSide != null) {
                    Vec3 directionVec = new Vec3(lastPos.getX() + 0.5 + clickSide.getUnitVec3i().getX() * 0.5, lastPos.getY() + 0.5 + clickSide.getUnitVec3i().getY() * 0.5, lastPos.getZ() + 0.5 + clickSide.getUnitVec3i().getZ() * 0.5);
                    doSwap(water);
                    Color color = new Color(70, 177, 229, 80);
                    event.renderer.box(lastPos, color, color, ShapeMode.Both, 0);
                    Rotation.snapAt(directionVec);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, Rotation.getRotation(directionVec)[0], Rotation.getRotation(directionVec)[1]));
                    if (inventorySwap.get()) {
                        doSwap(water);
                    } else {
                        doSwap(old);
                    }
                    Rotation.snapBack();
                    hasPlacedWater = false;
                }
            } else if (!hasPlacedWater) {
                BlockPos pos = mc.player.blockPosition().below(checkDown.get());
                double[] xzOffset = new double[]{offSet.get(), -offSet.get()};
                for (double x : xzOffset){
                    for (double z : xzOffset){
                        BlockPos offSetPos = new BlockPosX(pos.getX() + x, pos.getY(), pos.getZ() + z);
                        if (checkFalling() && !mc.level.isEmptyBlock(offSetPos) && !mc.level.getBlockState(offSetPos).canBeReplaced()) {
                            Direction side = BlockUtil.getPlaceSide(pos.above(), null);
                            if (side != null && !behindWall(offSetPos.above())) {
                                Color color = new Color(70, 177, 229, 80);
                                event.renderer.box(offSetPos.above(), color, color, ShapeMode.Both, 0);
                                doSwap(water);
                                Rotation.snapAt(offSetPos.above().getCenter());
                                lastPos = offSetPos.above();
                                mc.player.swing(InteractionHand.MAIN_HAND);
                                mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, Rotation.getRotation(offSetPos.above().getCenter())[0], Rotation.getRotation(offSetPos.above().getCenter())[1]));
                                if (inventorySwap.get()) {
                                    doSwap(water);
                                } else {
                                    doSwap(old);
                                }
                                Rotation.snapBack();
                                hasPlacedWater = true;
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
    public boolean behindWall(BlockPos pos) {
        Vec3 testVec = new Vec3(pos.getX() + 0.5, pos.getY() + 2 * 0.85, pos.getZ() + 0.5);
        HitResult result = mc.level.clip(new ClipContext(mc.player.getEyePosition(), testVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return result != null && result.getType() != HitResult.Type.MISS;
    }
    private boolean checkFalling() {
        return mc.player.fallDistance > 3.0f && !mc.player.onGround() && !mc.player.isFallFlying();
    }
    private int findItem(Item item) {
        if (inventorySwap.get()) {
            return InventoryUtil.findItemInventorySlot(item);
        } else {
            return InventoryUtil.findItem(item);
        }
    }
    private void doSwap(int slot) {
        if (slot == -1) return;
        if (!inventorySwap.get()) {
            InventoryUtil.switchToSlot(slot);
        } else {
            InventoryUtil.inventorySwap(slot, ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot());
        }
    }
}
