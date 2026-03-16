package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d; // ✅ 1.21.11 必须导入

import java.util.HashSet;
import java.util.Set;

public class SchematicPro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染设置");

    public enum OperationMode {
        Automatic("自动扫描并打开"),
        Manual("手动右键触发");
        private final String title;
        OperationMode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    // ================= 功能设置 =================

    private final Setting<OperationMode> operationMode = sgGeneral.add(new EnumSetting.Builder<OperationMode>()
        .name("操作模式")
        .defaultValue(OperationMode.Automatic)
        .build()
    );

    private final Setting<Integer> interactRange = sgGeneral.add(new IntSetting.Builder()
        .name("交互范围")
        .description("只有在这个范围内的红色容器会被自动打开。")
        .defaultValue(5)
        .min(1)
        .sliderMax(8)
        .visible(() -> operationMode.get() == OperationMode.Automatic)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("填装延迟 (Tick)")
        .description("填装速度，建议 4-5 以保证精准。")
        .defaultValue(4)
        .min(2)
        .build()
    );

    private final Setting<Boolean> autoClose = sgGeneral.add(new BoolSetting.Builder()
        .name("填完自动关闭")
        .defaultValue(true)
        .build()
    );

    // ================= 渲染设置 =================

    private final Setting<Integer> renderRange = sgRender.add(new IntSetting.Builder()
        .name("全局渲染范围")
        .description("可以看到远处容器的范围 (蓝色预览)。")
        .defaultValue(128)
        .min(8)
        .sliderMax(256)
        .build()
    );
    
    // 🔵 预览颜色 (远处的、待处理的)
    private final Setting<SettingColor> previewSideColor = sgRender.add(new ColorSetting.Builder()
        .name("预览(远)-面颜色")
        .description("需要去填装，但距离太远无法交互。")
        .defaultValue(new SettingColor(0, 100, 255, 25))
        .build()
    );

    private final Setting<SettingColor> previewLineColor = sgRender.add(new ColorSetting.Builder()
        .name("预览(远)-线颜色")
        .defaultValue(new SettingColor(0, 100, 255, 150))
        .build()
    );

    // 🔴 交互颜色 (近处的、即将打开的)
    private final Setting<SettingColor> targetSideColor = sgRender.add(new ColorSetting.Builder()
        .name("目标(近)-面颜色")
        .description("在交互范围内，且内容错误（即将被自动打开）。")
        .defaultValue(new SettingColor(255, 0, 0, 40))
        .build()
    );

    private final Setting<SettingColor> targetLineColor = sgRender.add(new ColorSetting.Builder()
        .name("目标(近)-线颜色")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .build()
    );

    // 🟢 完成颜色
    private final Setting<SettingColor> matchSideColor = sgRender.add(new ColorSetting.Builder()
        .name("已完成-面颜色")
        .defaultValue(new SettingColor(0, 255, 0, 25))
        .build()
    );

    private final Setting<SettingColor> matchLineColor = sgRender.add(new ColorSetting.Builder()
        .name("已完成-线颜色")
        .defaultValue(new SettingColor(0, 255, 0, 150))
        .build()
    );

    // ================= 内部状态 =================
    
    private int timer = 0;
    private BlockPos currentTarget = null;
    private boolean isWorking = false;
    private final Set<BlockPos> finishedCache = new HashSet<>();

    public SchematicPro() {
        super(AddonTemplate.CATEGORY, "SchematicPro", "投影容器自动填装，创造模式可以自动拿容器的物品然后填进去。很方便 ");
    }

    @Override
    public void onActivate() {
        finishedCache.clear();
        isWorking = false;
        currentTarget = null;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton btn = theme.button("重置所有进度");
        btn.action = () -> finishedCache.clear();
        return btn;
    }

    // ================= 逻辑循环 =================

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (operationMode.get() != OperationMode.Manual) return;
        if (!mc.player.isCreative()) return;

        BlockPos pos = event.result.getBlockPos();
        if (mc.world.getBlockEntity(pos) instanceof LootableContainerBlockEntity) {
            if (shouldSkipContainer(pos)) return; 

            currentTarget = pos;
            isWorking = true;
            timer = delay.get();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (isWorking && currentTarget != null) {
            handleFillingInGui();
            return;
        }

        if (operationMode.get() == OperationMode.Automatic) {
            if (timer > 0) {
                timer--;
                return;
            }
            findAndOpenTarget();
        }
    }

    private void findAndOpenTarget() {
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double interactSq = Math.pow(interactRange.get(), 2);

        // 仅遍历交互范围内的方块，用于打开容器
        // 为了性能，我们这里还是遍历 loaded entities，但在判断距离时卡死
        for (BlockEntity be : Utils.blockEntities()) {
            BlockPos pos = be.getPos();
            
            // 距离检查：只处理交互范围内的
            if (playerPos.squaredDistanceTo(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5) > interactSq) continue;
            
            if (!(be instanceof LootableContainerBlockEntity)) continue;

            if (finishedCache.contains(pos)) continue;
            if (shouldSkipContainer(pos)) continue;

            if (needsFixing(pos)) {
                currentTarget = pos;
                isWorking = true;
                timer = delay.get();

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, 
                    new BlockHitResult(new Vec3d(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5), 
                    Direction.UP, pos, false));
                return; 
            } else {
                finishedCache.add(pos);
            }
        }
    }

    private boolean shouldSkipContainer(BlockPos pos) {
        var schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) return true;

        BlockEntity schemBe = schematicWorld.getBlockEntity(pos);
        if (!(schemBe instanceof Inventory schemInv)) return true;

        boolean isEmpty = true;
        for (int i = 0; i < schemInv.size(); i++) {
            if (!schemInv.getStack(i).isEmpty()) {
                isEmpty = false;
                break;
            }
        }
        return isEmpty; 
    }

    private boolean needsFixing(BlockPos pos) {
        var schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) return false;

        BlockEntity realBe = mc.world.getBlockEntity(pos);
        BlockEntity schemBe = schematicWorld.getBlockEntity(pos);

        if (realBe instanceof Inventory realInv && schemBe instanceof Inventory schemInv) {
            for (int i = 0; i < schemInv.size(); i++) {
                ItemStack need = schemInv.getStack(i);
                ItemStack have = realInv.getStack(i);
                
                if (need.isEmpty() && have.isEmpty()) continue;
                if (!ItemStack.areEqual(need, have)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleFillingInGui() {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            isWorking = false;
            currentTarget = null;
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        var schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null || currentTarget == null) {
            closeContainer();
            return;
        }

        BlockEntity schemBe = schematicWorld.getBlockEntity(currentTarget);
        if (!(schemBe instanceof Inventory schemInv)) {
            closeContainer();
            return;
        }

        ScreenHandler handler = screen.getScreenHandler();
        boolean allCorrect = true;

        for (int i = 0; i < schemInv.size(); i++) {
            if (i >= handler.slots.size()) break;

            ItemStack targetStack = schemInv.getStack(i);
            ItemStack realStack = handler.getSlot(i).getStack();

            if (ItemStack.areEqual(targetStack, realStack)) {
                continue;
            }

            allCorrect = false;
            if (targetStack.isEmpty()) continue; 

            ItemStack stackToSend = targetStack.copy();
            mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(36, stackToSend));
            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.SWAP, mc.player);

            timer = delay.get(); 
            return; 
        }

        if (allCorrect) {
            finishedCache.add(currentTarget);
            if (autoClose.get()) {
                closeContainer();
            } else {
                isWorking = false;
                currentTarget = null;
            }
        }
    }

    private void closeContainer() {
        if (mc.player != null) mc.player.closeHandledScreen();
        isWorking = false;
        currentTarget = null;
        timer = delay.get();
    }

    // ================= 渲染逻辑 (全局预览) =================

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        var schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) return;

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        
        // 范围平方计算
        double renderSq = Math.pow(renderRange.get(), 2);
        double interactSq = Math.pow(interactRange.get(), 2);

        // 遍历所有加载的 BlockEntity
        for (BlockEntity be : Utils.blockEntities()) {
            BlockPos pos = be.getPos();
            double distSq = playerPos.squaredDistanceTo(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5);

            // 1. 如果超出了全局渲染范围，直接跳过
            if (distSq > renderSq) continue;
            
            if (!(be instanceof LootableContainerBlockEntity)) continue;

            // 2. 投影空箱子过滤
            if (shouldSkipContainer(pos)) continue;

            // 3. 颜色判定逻辑
            if (finishedCache.contains(pos)) {
                // 🟢 情况一：已完成 (缓存中)
                event.renderer.box(pos, matchSideColor.get(), matchLineColor.get(), ShapeMode.Both, 0);
            } else {
                // 尚未完成，再次检查内容是否一致（防止手动修改后没变绿）
                if (!needsFixing(pos)) {
                    // 内容其实已经对了，只是没进缓存 -> 变绿并加入缓存
                    finishedCache.add(pos);
                    event.renderer.box(pos, matchSideColor.get(), matchLineColor.get(), ShapeMode.Both, 0);
                } else {
                    // 内容确实不对，需要填装
                    if (distSq <= interactSq) {
                        // 🔴 情况二：在交互范围内 (准备动手)
                        event.renderer.box(pos, targetSideColor.get(), targetLineColor.get(), ShapeMode.Both, 0);
                    } else {
                        // 🔵 情况三：在交互范围外 (等待靠近) -> 全局预览
                        event.renderer.box(pos, previewSideColor.get(), previewLineColor.get(), ShapeMode.Both, 0);
                    }
                }
            }
        }
    }
}