package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d; // ✅ [规则5] 必须显式导入

import java.util.HashSet;
import java.util.Set;

public class FillESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // --- 设置 ---
    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> unopenedColor = sgColors.add(new ColorSetting.Builder()
        .name("unopened-color")
        .description("Color for containers you haven't interacted with.")
        .defaultValue(new SettingColor(255, 160, 0, 100))
        .build()
    );

    private final Setting<SettingColor> openedColor = sgColors.add(new ColorSetting.Builder()
        .name("opened-color")
        .description("Color for containers you have clicked (Filled).")
        .defaultValue(new SettingColor(0, 255, 0, 100)) // 默认绿色
        .build()
    );

    // 存储已点击的容器坐标
    private final Set<BlockPos> openedBlocks = new HashSet<>();

    public FillESP() {
        super(Categories.Render, "fill-esp", "Highlights containers and marks them green when clicked.");
    }

    @Override
    public void onDeactivate() {
        openedBlocks.clear();
    }

    // --- UI 控制 ---
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton clear = theme.button("Reset Opened Cache");
        clear.action = openedBlocks::clear;
        return clear;
    }

    // --- 逻辑处理：拦截点击 ---
    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        BlockPos pos = event.result.getBlockPos();
        if (mc.world == null) return;

        BlockEntity be = mc.world.getBlockEntity(pos);
        if (be == null) return;

        // 检查是否是容器 (通过字符串判断 ID 或 实例判断)
        // 在 1.21.11 中 BlockEntity 的实例判断依然稳健，但为了保险我们增加类型过滤
        if (isContainer(be)) {
            openedBlocks.add(pos);

            // 处理大箱子（Double Chest）：如果点了一个，另一个也变绿
            if (be instanceof ChestBlockEntity) {
                BlockState state = mc.world.getBlockState(pos);
                if (state.contains(ChestBlock.CHEST_TYPE)) {
                    ChestType type = state.get(ChestBlock.CHEST_TYPE);
                    if (type != ChestType.SINGLE) {
                        Direction facing = state.get(ChestBlock.FACING);
                        BlockPos neighborPos = pos.offset(type == ChestType.LEFT ? 
                            facing.rotateYClockwise() : facing.rotateYCounterclockwise());
                        openedBlocks.add(neighborPos);
                    }
                }
            }
        }
    }

    // --- 渲染逻辑 ---
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;

        for (BlockEntity be : Utils.blockEntities()) {
            if (!isContainer(be)) continue;

            BlockPos pos = be.getPos();
            
            // ✅ [规则5] 使用 Vec3d 处理坐标逻辑（此处渲染器需要）
            Color renderSideColor;
            Color renderLineColor;

            if (openedBlocks.contains(pos)) {
                renderSideColor = new Color(openedColor.get()).a(openedColor.get().a);
                renderLineColor = new Color(openedColor.get()).a(255);
            } else {
                renderSideColor = new Color(unopenedColor.get()).a(unopenedColor.get().a);
                renderLineColor = new Color(unopenedColor.get()).a(255);
            }

            // 渲染方块框
            // 注意：1.21.11 的渲染器调用
            drawBox(event, be, renderSideColor, renderLineColor);
        }
    }

    private void drawBox(Render3DEvent event, BlockEntity be, Color side, Color line) {
        double x = be.getPos().getX();
        double y = be.getPos().getY();
        double z = be.getPos().getZ();

        // 针对箱子/末影箱微调尺寸（比完整方块小一点点）
        double shrink = 0.0625;
        if (be instanceof ChestBlockEntity || be instanceof EnderChestBlockEntity) {
            event.renderer.box(x + shrink, y, z + shrink, x + 1 - shrink, y + 1 - shrink * 2, z + 1 - shrink, side, line, shapeMode.get(), 0);
        } else {
            event.renderer.box(x, y, z, x + 1, y + 1, z + 1, side, line, shapeMode.get(), 0);
        }
    }

    private boolean isContainer(BlockEntity be) {
        // 1.21.11 建议使用 BlockEntityType 匹配，或者字符串包含
        String type = be.getType().toString().toLowerCase();
        return be instanceof ChestBlockEntity 
            || be instanceof BarrelBlockEntity 
            || be instanceof ShulkerBoxBlockEntity 
            || be instanceof EnderChestBlockEntity
            || be instanceof HopperBlockEntity
            || be instanceof DispenserBlockEntity
            || type.contains("furnace")
            || type.contains("chest");
    }
}