package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;

import java.util.function.Predicate;

public class LegitNoFall extends Module {

    private int getSelectedSlot() {
        if (mc.player == null) return 0;
        return mc.player.getInventory().getSelectedSlot();
    }
    private void setSelectedSlot(int slot) {
        if (mc.player == null) return;
        mc.player.getInventory().setSelectedSlot(slot);
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> checkDown = sgGeneral.add(new IntSetting.Builder()
            .name("checkDown")
            .defaultValue(1)
            .min(0)
            .sliderMax(3)
            .build()
    );

    private final Setting<Boolean> inventorySwap = sgGeneral.add(new BoolSetting.Builder()
            .name("inventorySwap")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> offSet = sgGeneral.add(new DoubleSetting.Builder()
            .name("offSet")
            .defaultValue(0.3)
            .min(0)
            .sliderMax(1)
            .build()
    );

    private boolean hasPlacedWater = false;
    private BlockPos lastPos = null;

    private float rotationYaw = 0;
    private float rotationPitch = 0;
    
    private int lastSlot = -1;
    private int lastSelect = -1;

    public LegitNoFall() {
        super(AddonTemplate.CATEGORY, "LegitNoFall", "抄袭自leavehack，不一定好用");
    }

    @Override
    public void onActivate() {
        hasPlacedWater = false;
        lastPos = null;
        lastSlot = -1;
        lastSelect = -1;
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.level.dimension() == Level.NETHER) return;

        rotationYaw = mc.player.getYRot();
        rotationPitch = mc.player.getXRot();

        int old = getSelectedSlot();
        int water = hasPlacedWater ? findItem(Items.BUCKET) : findItem(Items.WATER_BUCKET);

        if (water != -1) {
            if (hasPlacedWater && lastPos != null) {
                doSwap(water);
                Color color = new Color(70, 177, 229, 80);
                event.renderer.box(lastPos, color, color, ShapeMode.Both, 0);
                
                // 【修复】：收水时，强制看向地板上方块的顶部中心，而不是空气的中心
                Vec3 targetAim = new Vec3(lastPos.getX() + 0.5, lastPos.getY(), lastPos.getZ() + 0.5);
                snapAt(targetAim);
                mc.player.swing(InteractionHand.MAIN_HAND);
                
                float[] rot = getRotation(targetAim);
                mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, rot[0], rot[1]));
                
                if (inventorySwap.get()) {
                    doSwap(water);
                } else {
                    doSwap(old);
                }
                snapBack();
                hasPlacedWater = false;

            } else if (!hasPlacedWater) {
                BlockPos pos = mc.player.blockPosition().below(checkDown.get());
                double[] xzOffset = new double[]{offSet.get(), -offSet.get()};
                
                for (double x : xzOffset) {
                    for (double z : xzOffset) {
                        // 还原原作者 BlockPosX 取整逻辑，但基于真实的浮点坐标，防止方块盲区
                        BlockPos offSetPos = new BlockPos(
                            Mth.floor(mc.player.getX() + x), 
                            pos.getY(), 
                            Mth.floor(mc.player.getZ() + z)
                        );

                        if (checkFalling() && !mc.level.isEmptyBlock(offSetPos) && !mc.level.getBlockState(offSetPos).canBeReplaced()) {
                            Direction side = getPlaceSide(pos.above(), null);
                            
                            if (side != null && !behindWall(offSetPos.above())) {
                                Color color = new Color(70, 177, 229, 80);
                                event.renderer.box(offSetPos.above(), color, color, ShapeMode.Both, 0);
                                
                                doSwap(water);
                                
                                // 【致命漏洞修复核心】：
                                // 不要 snapAt(offSetPos.up().toCenterPos()) (会瞄准空气)
                                // 必须瞄准脚下方块(offSetPos)的【顶部中心】！强制玩家低头90度放水！
                                Vec3 targetAim = new Vec3(offSetPos.getX() + 0.5, offSetPos.getY() + 1.0, offSetPos.getZ() + 0.5);
                                snapAt(targetAim);
                                lastPos = offSetPos.above();
                                
                                mc.player.swing(InteractionHand.MAIN_HAND);

                                float[] rot = getRotation(targetAim);
                                mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, rot[0], rot[1]));
                                
                                if (inventorySwap.get()) {
                                    doSwap(water);
                                } else {
                                    doSwap(old);
                                }
                                snapBack();
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
        if (result == null || result.getType() == HitResult.Type.MISS) return false;
        return false;
    }

    private Direction getPlaceSide(BlockPos pos, Predicate<Direction> directionPredicate) {
        if (pos == null) return null;
        for (Direction i : Direction.values()) {
            if (directionPredicate != null && !directionPredicate.test(i)) continue;
            BlockPos neighbor = pos.relative(i);
            if (!mc.level.getBlockState(neighbor).isAir() && !mc.level.getBlockState(neighbor).canBeReplaced()) {
                return i;
            }
        }
        return null;
    }

    private boolean checkFalling() {
        return mc.player.fallDistance > mc.player.getMaxFallDistance() && !mc.player.onGround() && !mc.player.isFallFlying();
    }

    private int findItem(Item item) {
        if (inventorySwap.get()) {
            for (int i = 0; i < 45; ++i) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.getItem() == item) return i < 9 ? i + 36 : i;
            }
            return -1;
        } else {
            for (int i = 0; i < 9; ++i) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.getItem() == item) return i;
            }
            return -1;
        }
    }

    private void doSwap(int slot) {
        if (!inventorySwap.get()) {
            switchToSlot(slot);
        } else {
            inventorySwap(slot, getSelectedSlot());
        }
    }

    private void switchToSlot(int slot) {
        setSelectedSlot(slot); // 使用我们之前写好的反射方法修改变量，避免 private 报错
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private void inventorySwap(int slot, int selectedSlot) {
        if (slot == lastSlot) {
            switchToSlot(lastSelect);
            lastSlot = -1;
            lastSelect = -1;
            return;
        }
        if (slot - 36 == selectedSlot) return;
        if (slot - 36 >= 0) {
            lastSlot = slot;
            lastSelect = selectedSlot;
            switchToSlot(slot - 36);
            return;
        }
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, selectedSlot, ContainerInput.SWAP, mc.player);
    }

    private void snapAt(float yaw, float pitch) {
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
            mc.player.getX(), mc.player.getY(), mc.player.getZ(), yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision
        ));
    }

    private void snapBack() {
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
            mc.player.getX(), mc.player.getY(), mc.player.getZ(), rotationYaw, rotationPitch, mc.player.onGround(), mc.player.horizontalCollision
        ));
    }

    private void snapAt(Vec3 directionVec) {
        float[] angle = getRotation(directionVec);
        snapAt(angle[0], angle[1]);
    }

    private float[] getRotation(Vec3 vec) {
        Vec3 eyesPos = mc.player.getEyePosition();
        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));
        
        return new float[]{Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch)};
    }
}