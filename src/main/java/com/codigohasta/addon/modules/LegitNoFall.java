package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.util.function.Predicate;

public class LegitNoFall extends Module {

    private static Field selectedSlotField;
    static {
        try {
            selectedSlotField = net.minecraft.entity.player.PlayerInventory.class.getDeclaredField("selectedSlot");
            selectedSlotField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }
    private int getSelectedSlot() {
        try {
            if (mc.player == null) return 0;
            return selectedSlotField.getInt(mc.player.getInventory());
        } catch (Exception e) {
            return 0;
        }
    }
    private void setSelectedSlot(int slot) {
        try {
            if (mc.player == null) return;
            selectedSlotField.setInt(mc.player.getInventory(), slot);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        if (mc.player == null || mc.world == null) return;
        if (mc.world.getRegistryKey() == World.NETHER) return;

        rotationYaw = mc.player.getYaw();
        rotationPitch = mc.player.getPitch();

        int old = getSelectedSlot();
        int water = hasPlacedWater ? findItem(Items.BUCKET) : findItem(Items.WATER_BUCKET);

        if (water != -1) {
            if (hasPlacedWater && lastPos != null) {
                doSwap(water);
                Color color = new Color(70, 177, 229, 80);
                event.renderer.box(lastPos, color, color, ShapeMode.Both, 0);
                
                // 【修复】：收水时，强制看向地板上方块的顶部中心，而不是空气的中心
                Vec3d targetAim = new Vec3d(lastPos.getX() + 0.5, lastPos.getY(), lastPos.getZ() + 0.5);
                snapAt(targetAim);
                mc.player.swingHand(Hand.MAIN_HAND);
                
                float[] rot = getRotation(targetAim);
                mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 1, rot[0], rot[1]));
                
                if (inventorySwap.get()) {
                    doSwap(water);
                } else {
                    doSwap(old);
                }
                snapBack();
                hasPlacedWater = false;

            } else if (!hasPlacedWater) {
                BlockPos pos = mc.player.getBlockPos().down(checkDown.get());
                double[] xzOffset = new double[]{offSet.get(), -offSet.get()};
                
                for (double x : xzOffset) {
                    for (double z : xzOffset) {
                        // 还原原作者 BlockPosX 取整逻辑，但基于真实的浮点坐标，防止方块盲区
                        BlockPos offSetPos = new BlockPos(
                            MathHelper.floor(mc.player.getX() + x), 
                            pos.getY(), 
                            MathHelper.floor(mc.player.getZ() + z)
                        );

                        if (checkFalling() && !mc.world.isAir(offSetPos) && !mc.world.getBlockState(offSetPos).isReplaceable()) {
                            Direction side = getPlaceSide(pos.up(), null);
                            
                            if (side != null && !behindWall(offSetPos.up())) {
                                Color color = new Color(70, 177, 229, 80);
                                event.renderer.box(offSetPos.up(), color, color, ShapeMode.Both, 0);
                                
                                doSwap(water);
                                
                                // 【致命漏洞修复核心】：
                                // 不要 snapAt(offSetPos.up().toCenterPos()) (会瞄准空气)
                                // 必须瞄准脚下方块(offSetPos)的【顶部中心】！强制玩家低头90度放水！
                                Vec3d targetAim = new Vec3d(offSetPos.getX() + 0.5, offSetPos.getY() + 1.0, offSetPos.getZ() + 0.5);
                                snapAt(targetAim);
                                lastPos = offSetPos.up();
                                
                                mc.player.swingHand(Hand.MAIN_HAND);

                                float[] rot = getRotation(targetAim);
                                mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 1, rot[0], rot[1]));
                                
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
        Vec3d testVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 2 * 0.85, pos.getZ() + 0.5);
        HitResult result = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), testVec, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (result == null || result.getType() == HitResult.Type.MISS) return false;
        return false;
    }

    private Direction getPlaceSide(BlockPos pos, Predicate<Direction> directionPredicate) {
        if (pos == null) return null;
        for (Direction i : Direction.values()) {
            if (directionPredicate != null && !directionPredicate.test(i)) continue;
            BlockPos neighbor = pos.offset(i);
            if (!mc.world.getBlockState(neighbor).isAir() && !mc.world.getBlockState(neighbor).isReplaceable()) {
                return i;
            }
        }
        return null;
    }

    private boolean checkFalling() {
        return mc.player.fallDistance > mc.player.getSafeFallDistance() && !mc.player.isOnGround() && !mc.player.isGliding();
    }

    private int findItem(Item item) {
        if (inventorySwap.get()) {
            for (int i = 0; i < 45; ++i) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == item) return i < 9 ? i + 36 : i;
            }
            return -1;
        } else {
            for (int i = 0; i < 9; ++i) {
                ItemStack stack = mc.player.getInventory().getStack(i);
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
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
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
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, selectedSlot, SlotActionType.SWAP, mc.player);
    }

    private void snapAt(float yaw, float pitch) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
            mc.player.getX(), mc.player.getY(), mc.player.getZ(), yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision
        ));
    }

    private void snapBack() {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
            mc.player.getX(), mc.player.getY(), mc.player.getZ(), rotationYaw, rotationPitch, mc.player.isOnGround(), mc.player.horizontalCollision
        ));
    }

    private void snapAt(Vec3d directionVec) {
        float[] angle = getRotation(directionVec);
        snapAt(angle[0], angle[1]);
    }

    private float[] getRotation(Vec3d vec) {
        Vec3d eyesPos = mc.player.getEyePos();
        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));
        
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch)};
    }
}