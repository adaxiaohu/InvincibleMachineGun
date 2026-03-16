package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;





public class ScaffoldPlus extends Module {
    public ScaffoldPlus() {
        super(AddonTemplate.CATEGORY, "scaffold-plus", "抄袭leavehack的模块");
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("Rotate")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> usingPause = sgGeneral.add(new BoolSetting.Builder()
            .name("UsingPause")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
            new EnumSetting.Builder<ShapeMode>()
                    .name("Shape Mode")
                    .defaultValue(ShapeMode.Both)
                    .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(
            new ColorSetting.Builder()
                    .name("Line")
                    .defaultValue(new SettingColor(255, 255, 255, 255))
                    .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(
            new ColorSetting.Builder()
                    .name("Side")
                    .defaultValue(new SettingColor(255, 255, 255, 50))
                    .build()
    );
    // --- 反射修复 selectedSlot 访问权限问题 ---
    private static java.lang.reflect.Field selectedSlotField;

    static {
        try {
            // 获取 PlayerInventory 类的 selectedSlot 字段
            selectedSlotField = net.minecraft.entity.player.PlayerInventory.class.getDeclaredField("selectedSlot");
            selectedSlotField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    // 辅助方法：读取当前槽位
    private int getSelectedSlot() {
        try {
            if (mc.player == null) return 0;
            return selectedSlotField.getInt(mc.player.getInventory());
        } catch (Exception e) {
            return 0;
        }
    }

    // 辅助方法：写入当前槽位 (仅客户端变量，不发包)
    private void setSelectedSlot(int slot) {
        try {
            if (mc.player == null) return;
            selectedSlotField.setInt(mc.player.getInventory(), slot);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- 静态常量 (从 BlockUtil 移植) ---
    private static final List<Block> SHIFT_BLOCKS = Arrays.asList(
            Blocks.ENDER_CHEST, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.CRAFTING_TABLE,
            Blocks.BIRCH_TRAPDOOR, Blocks.BAMBOO_TRAPDOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.CHERRY_TRAPDOOR,
            Blocks.ANVIL, Blocks.BREWING_STAND, Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER,
            Blocks.ACACIA_TRAPDOOR, Blocks.ENCHANTING_TABLE, Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX,
            Blocks.MAGENTA_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX, Blocks.LIME_SHULKER_BOX,
            Blocks.PINK_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX,
            Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX, Blocks.RED_SHULKER_BOX, Blocks.BLACK_SHULKER_BOX
    );

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isUsingItem() && usingPause.get()) return;

        // 修复: 使用反射获取 selectedSlot
        int selectedSlot = getSelectedSlot(); 
        
        ItemStack stack = mc.player.getInventory().getStack(selectedSlot);
        BlockPos pos = mc.player.getBlockPos();

        boolean slabMode = mc.world.getBlockState(pos).getBlock() instanceof SlabBlock;
        if (!slabMode) {
            for (Direction i : Direction.values()) {
                if (i == Direction.UP || i == Direction.DOWN) continue;
                if (mc.world.getBlockState(pos.offset(i)).getBlock() instanceof SlabBlock) {
                    slabMode = true;
                    break;
                }
            }
        }

        int blockSlot = slabMode ? findSlabBlock() : findBlock();
        Block handBlock = Block.getBlockFromItem(stack.getItem());
        boolean isHandBlockValid = handBlock != Blocks.AIR && !SHIFT_BLOCKS.contains(handBlock) && handBlock != Blocks.COBWEB;

        if (slabMode) {
            if (isHandBlockValid && handBlock instanceof SlabBlock) {
                blockSlot = selectedSlot;
            }
        } else {
            if (isHandBlockValid) {
                blockSlot = selectedSlot;
            }
        }

        if (blockSlot == -1) return;

        BlockPos placePos = mc.player.getBlockPos().down();

        if (!slabMode) {
            if (clientCanPlace(placePos)) {
                // 修复: 获取旧槽位
                int oldSlot = getSelectedSlot();

                if (getPlaceSide(placePos, null) == null) {
                    double distance = 1000;
                    BlockPos bestPos = null;
                    for (Direction i : Direction.values()) {
                        if (i == Direction.UP) continue;
                        BlockPos offsetPos = placePos.offset(i);
                        if (canPlace(offsetPos, null)) {
                            double dist = mc.player.squaredDistanceTo(offsetPos.toCenterPos());
                            if (bestPos == null || dist < distance) {
                                bestPos = offsetPos;
                                distance = dist;
                            }
                        }
                    }
                    if (bestPos != null) {
                        placePos = bestPos;
                    } else {
                        return;
                    }
                }

                Direction side = getPlaceSide(placePos, null);
                Direction slabSide = null;
                
                ItemStack toPlaceStack = mc.player.getInventory().getStack(blockSlot);
                if (Block.getBlockFromItem(toPlaceStack.getItem()) instanceof SlabBlock) {
                    slabSide = Direction.UP;
                }

                if (side != null) {
                    renderBox(event, placePos);
                    switchToSlot(blockSlot);
                    placeSlabBlock(placePos, side, slabSide, rotate.get());
                    switchToSlot(oldSlot);
                }
            }
        } else {
            placePos = pos;
            if (mc.world.getBlockState(pos).getBlock() instanceof SlabBlock) {
                Direction face = mc.player.getHorizontalFacing();
                placePos = pos.offset(face);
            } else {
                if (clientCanPlace(placePos)) {
                    if (getPlaceSide(placePos, null) == null) {
                        double distance = 1000;
                        BlockPos bestPos = null;
                        for (Direction i : Direction.values()) {
                            if (i == Direction.UP) continue;
                            BlockPos offsetPos = placePos.offset(i);
                            if (canPlace(offsetPos, null)) {
                                double dist = mc.player.squaredDistanceTo(offsetPos.toCenterPos());
                                if (bestPos == null || dist < distance) {
                                    bestPos = offsetPos;
                                    distance = dist;
                                }
                            }
                        }
                        if (bestPos != null) {
                            placePos = bestPos;
                        } else {
                            return;
                        }
                    }
                }
            }

            // 修复: 获取旧槽位
            int oldSlot = getSelectedSlot();
            Direction side = getPlaceSide(placePos, null);
            
            if (side != null && !(mc.world.getBlockState(placePos).getBlock() instanceof SlabBlock)) {
                renderBox(event, placePos);
                switchToSlot(blockSlot);
                placeSlabBlock(placePos, side, Direction.DOWN, rotate.get());
                switchToSlot(oldSlot);
            }
        }
    }

    private void renderBox(Render3DEvent event, BlockPos pos) {
        event.renderer.box(new Box(pos), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    // ==========================================
    //           Inventory Util (移植版)
    // ==========================================

    private int findBlock() {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            // 1.21.11 安全检查: 不使用 instanceof BlockItem
            Block block = Block.getBlockFromItem(stack.getItem());
            if (block != Blocks.AIR && !SHIFT_BLOCKS.contains(block) && block != Blocks.COBWEB) {
                return i;
            }
        }
        return -1;
    }

    private int findSlabBlock() {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            Block block = Block.getBlockFromItem(stack.getItem());
            if (block instanceof SlabBlock) {
                return i;
            }
        }
        return -1;
    }

   private void switchToSlot(int slot) {
        // 修复: 使用辅助方法读取和写入
        if (getSelectedSlot() == slot) return;
        
        setSelectedSlot(slot); // 更新客户端变量
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot)); // 发送切包
    }

    // ==========================================
    //             Block Util (移植版)
    // ==========================================

    private boolean clientCanPlace(BlockPos pos) {
        if (!canReplace(pos)) return false;
        return !hasEntity(pos);
    }

    private boolean canReplace(BlockPos pos) {
        if (pos.getY() >= 320) return false;
        return mc.world.getBlockState(pos).isReplaceable();
    }

    private boolean canPlace(BlockPos pos, Predicate<Direction> directionPredicate) {
        if (getPlaceSide(pos, directionPredicate) == null) return false;
        if (!canReplace(pos)) return false;
        return !hasEntity(pos);
    }

    private boolean hasEntity(BlockPos pos) {
        for (Entity entity : mc.world.getOtherEntities(null, new Box(pos))) {
            if (!entity.isAlive() || entity instanceof ItemEntity || entity instanceof ExperienceBottleEntity || entity instanceof ArrowEntity)
                continue;
            return true;
        }
        return false;
    }

    private boolean canClick(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        // --- 核心修复 ---
        // 1. 不要用 state.isSolidBlock(...)，因为它会排除玻璃和半砖。
        // 2. 改为判断: 不是空气 且 不是液体 且 不是可替换方块(如草/雪)。
        // 这样玻璃、树叶、半砖都会被视为"可点击"，Scaffold 就能吸附在它们上面了。
        boolean isClickableSurface = !state.isAir() && state.getFluidState().isEmpty() && !state.isReplaceable();

        return isClickableSurface && 
               (!(SHIFT_BLOCKS.contains(block) || block instanceof BedBlock) || mc.player.isSneaking());
    }

    // --- 核心逻辑: 获取放置面 (包含 Grim 绕过) ---
    private Direction getPlaceSide(BlockPos pos, Predicate<Direction> directionPredicate) {
        if (pos == null) return null;
        double dis = 114514;
        Direction side = null;
        
        for (Direction i : Direction.values()) {
            if (directionPredicate != null && !directionPredicate.test(i)) continue;
            
            BlockPos neighbor = pos.offset(i);
            if (canClick(neighbor) && !mc.world.getBlockState(neighbor).isReplaceable()) {
                // Grim 核心检测逻辑
                if (!isGrimDirection(neighbor, i.getOpposite())) continue;
                
                double vecDis = mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos().add(i.getVector().getX() * 0.5, i.getVector().getY() * 0.5, i.getVector().getZ() * 0.5));
                if (side == null || vecDis < dis) {
                    side = i;
                    dis = vecDis;
                }
            }
        }
        return side;
    }

    // --- Grim Anti-Cheat Bypass Logic ---
    private static final double MIN_EYE_HEIGHT = 0.4;
    private static final double MAX_EYE_HEIGHT = 1.62;
    private static final double MOVEMENT_THRESHOLD = 0.0002;

    private boolean isGrimDirection(BlockPos pos, Direction direction) {
        Box combined = getCombinedBox(pos, mc.world);
        ClientPlayerEntity player = mc.player;
        Box eyePositions = new Box(player.getX(), player.getY() + MIN_EYE_HEIGHT, player.getZ(), player.getX(), player.getY() + MAX_EYE_HEIGHT, player.getZ()).expand(MOVEMENT_THRESHOLD);
        
        if (isIntersected(eyePositions, combined)) {
            return true;
        }
        return !switch (direction) {
            case NORTH -> eyePositions.minZ > combined.minZ;
            case SOUTH -> eyePositions.maxZ < combined.maxZ;
            case EAST -> eyePositions.maxX < combined.maxX;
            case WEST -> eyePositions.minX > combined.minX;
            case UP -> eyePositions.maxY < combined.maxY;
            case DOWN -> eyePositions.minY > combined.minY;
        };
    }

    private Box getCombinedBox(BlockPos pos, World level) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos).offset(pos.getX(), pos.getY(), pos.getZ());
        Box combined = new Box(pos);
        for (Box box : shape.getBoundingBoxes()) {
            double minX = Math.max(box.minX, combined.minX);
            double minY = Math.max(box.minY, combined.minY);
            double minZ = Math.max(box.minZ, combined.minZ);
            double maxX = Math.min(box.maxX, combined.maxX);
            double maxY = Math.min(box.maxY, combined.maxY);
            double maxZ = Math.min(box.maxZ, combined.maxZ);
            combined = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        }
        return combined;
    }

    private boolean isIntersected(Box bb, Box other) {
        return other.maxX - VoxelShapes.MIN_SIZE > bb.minX
                && other.minX + VoxelShapes.MIN_SIZE < bb.maxX
                && other.maxY - VoxelShapes.MIN_SIZE > bb.minY
                && other.minY + VoxelShapes.MIN_SIZE < bb.maxY
                && other.maxZ - VoxelShapes.MIN_SIZE > bb.minZ
                && other.minZ + VoxelShapes.MIN_SIZE < bb.maxZ;
    }

    // --- 交互与放置逻辑 ---
    private void placeSlabBlock(BlockPos pos, Direction side, Direction slabSide, boolean shouldRotate) {
        double yOffset = 0.5;
        if (slabSide == Direction.UP) yOffset += 0.1;
        if (slabSide == Direction.DOWN) yOffset -= 0.1;
        
        Vec3d directionVec = new Vec3d(
            pos.getX() + 0.5 + side.getVector().getX() * 0.5, 
            pos.getY() + yOffset + side.getVector().getY() * 0.5, 
            pos.getZ() + 0.5 + side.getVector().getZ() * 0.5
        );

        if (shouldRotate) {
            // 使用 Meteor 内置旋转系统替代原有的 Rotation.snapAt
            Rotations.rotate(Rotations.getYaw(directionVec), Rotations.getPitch(directionVec));
        }

        mc.player.swingHand(Hand.MAIN_HAND);
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, result);
        
        // Meteor 的 Rotations 会在 tick 结束自动复位，通常不需要手动 snapBack，但为了保持原逻辑意图：
        // 原版 snapBack 通常意味着立即发包，这里 Meteor 自动处理
    }
}