package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.translation.JsonEnglishTranslationManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class SchematicPro extends Module {
    private static final int GUI_OPEN_TIMEOUT_TICKS = 40;
    private static final int ITEM_ICON_SIZE = 16;
    private static final int ITEM_ICON_CELL = 20;
    private static final int PANEL_PADDING = 4;
    private static final int STATUS_TEXT_HEIGHT = 11;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染设置");
    private final SettingGroup sgMissingRender = settings.createGroup("缺失物品显示");

    public enum OperationMode {
        Automatic("自动扫描并打开"),
        Manual("手动右键触发");

        private final String title;

        OperationMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum FillMode {
        Creative("创造模式复制"),
        SurvivalInventory("生存背包填装");

        private final String title;

        FillMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    /**
     * 生存模式内部鼠标状态机。
     *
     * IDLE      : 没有由本模块拿在鼠标上的物品。
     * FILLING   : 从玩家背包拿起了目标材料，正在往目标槽补数量。
     * TRIMMING  : 从目标槽拿起了“数量过多”的同类物品，正在放回投影要求数量。
     * RETURNING : 目标槽已经处理完，正在把鼠标剩余物品放回玩家背包。
     */
    private enum SurvivalStage {
        IDLE,
        FILLING,
        TRIMMING,
        RETURNING
    }

    /**
     * GUI 中确认过的缺失物品快照。
     * displayStack 保留完整 Data Components，用于药水、附魔书、自定义模型等原版图标渲染。
     */
    private static final class MissingItemEntry {
        private final ItemStack displayStack;
        private int missingCount;

        private MissingItemEntry(ItemStack displayStack, int missingCount) {
            this.displayStack = displayStack;
            this.missingCount = missingCount;
        }
    }

    /**
     * 只有实际打开过容器 GUI 后才建立。关闭 GUI 后继续沿用最后一次 ScreenHandler 快照，
     * 不用世界 BlockEntity 库存覆盖，避免已完成容器被旧客户端库存误判。
     */
    private static final class ContainerRenderSnapshot {
        private final List<MissingItemEntry> missingItems = new ArrayList<>();
        private boolean scanned;
    }

    // ================= 功能设置 =================

    private final Setting<OperationMode> operationMode = sgGeneral.add(new EnumSetting.Builder<OperationMode>()
        .name("操作模式")
        .description("自动扫描附近容器，或手动右键容器后触发。")
        .defaultValue(OperationMode.Automatic)
        .build()
    );

    private final Setting<FillMode> fillMode = sgGeneral.add(new EnumSetting.Builder<FillMode>()
        .name("填装模式")
        .description("创造模式直接复制投影物品；生存模式只使用玩家背包中真实存在的物品。")
        .defaultValue(FillMode.Creative)
        .build()
    );

    private final Setting<Integer> interactRange = sgGeneral.add(new IntSetting.Builder()
        .name("交互范围")
        .description("只有在这个范围内的目标容器会被自动打开。")
        .defaultValue(5)
        .min(1)
        .sliderMax(8)
        .visible(() -> operationMode.get() == OperationMode.Automatic)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("填装延迟 (Tick)")
        .description("每次背包/槽位操作之间的等待 Tick")
        .defaultValue(1)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> autoClose = sgGeneral.add(new BoolSetting.Builder()
        .name("填完自动关闭")
        .description("容器完全符合投影后自动关闭 GUI。自动扫描模式遇到缺料/背包满时也会关闭，以便继续处理其他容器。")
        .defaultValue(true)
        .build()
    );

    // ================= 渲染设置 =================

    private final Setting<Integer> renderRange = sgRender.add(new IntSetting.Builder()
        .name("全局渲染范围")
        .description("可以看到待处理投影容器的范围。")
        .defaultValue(128)
        .min(8)
        .sliderMax(256)
        .build()
    );

    // 🔵 远处待处理
    private final Setting<SettingColor> previewSideColor = sgRender.add(new ColorSetting.Builder()
        .name("预览(远)-面颜色")
        .description("需要处理，但距离太远无法自动交互。")
        .defaultValue(new SettingColor(0, 100, 255, 25))
        .build()
    );

    private final Setting<SettingColor> previewLineColor = sgRender.add(new ColorSetting.Builder()
        .name("预览(远)-线颜色")
        .defaultValue(new SettingColor(0, 100, 255, 150))
        .build()
    );

    // 🔴 近处待处理
    private final Setting<SettingColor> targetSideColor = sgRender.add(new ColorSetting.Builder()
        .name("目标(近)-面颜色")
        .description("在交互范围内，且容器内容与投影不一致。")
        .defaultValue(new SettingColor(255, 0, 0, 40))
        .build()
    );

    private final Setting<SettingColor> targetLineColor = sgRender.add(new ColorSetting.Builder()
        .name("目标(近)-线颜色")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .build()
    );

    // 🟢 已完成
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

    // 🟡 背包满，无法把错误/多余物品安全取出
    private final Setting<SettingColor> blockedSideColor = sgRender.add(new ColorSetting.Builder()
        .name("背包满-面颜色")
        .description("生存填装时，玩家背包没有足够空间接收需要从容器取出的物品。")
        .defaultValue(new SettingColor(255, 200, 0, 40))
        .build()
    );

    private final Setting<SettingColor> blockedLineColor = sgRender.add(new ColorSetting.Builder()
        .name("背包满-线颜色")
        .defaultValue(new SettingColor(255, 200, 0, 220))
        .build()
    );

    // ================= 缺失物品世界标签 =================

    private final Setting<Boolean> showMissingItems = sgMissingRender.add(new BoolSetting.Builder()
        .name("显示容器缺失物品")
        .description("在未完成投影容器中心显示物品图标和数量；扫描前可显示投影总需求，扫描后显示实际缺失量。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> missingItemRenderRange = sgMissingRender.add(new IntSetting.Builder()
        .name("显示距离")
        .description("缺失物品图标的最大世界距离。")
        .defaultValue(32)
        .min(1)
        .sliderMax(128)
        .visible(showMissingItems::get)
        .build()
    );

    private final Setting<Double> missingItemScale = sgMissingRender.add(new DoubleSetting.Builder()
        .name("图标缩放")
        .description("容器中心物品面板的整体缩放。")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(2.0)
        .visible(showMissingItems::get)
        .build()
    );

    private final Setting<Integer> missingItemsPerRow = sgMissingRender.add(new IntSetting.Builder()
        .name("每行物品数")
        .description("图标网格每行最多显示多少种物品。")
        .defaultValue(4)
        .min(1)
        .sliderMax(16)
        .visible(showMissingItems::get)
        .build()
    );

    private final Setting<Integer> maxMissingItemsShown = sgMissingRender.add(new IntSetting.Builder()
        .name("最大显示种类")
        .description("避免大型投影容器的图标面板占满屏幕；超出部分显示为 +N。")
        .defaultValue(8)
        .min(1)
        .sliderMax(16)
        .visible(showMissingItems::get)
        .build()
    );

    private final Setting<Boolean> showMissingCounts = sgMissingRender.add(new BoolSetting.Builder()
        .name("显示数量")
        .description("使用原版物品槽位覆盖层显示精确缺失数量。")
        .defaultValue(true)
        .visible(showMissingItems::get)
        .build()
    );

    private final Setting<Boolean> showMissingNames = sgMissingRender.add(new BoolSetting.Builder()
        .name("显示物品名称")
        .description("开启后改用纵向列表显示物品图标、名称和数量；默认关闭以减少遮挡。")
        .defaultValue(false)
        .visible(showMissingItems::get)
        .build()
    );

    private final Setting<Boolean> showMissingBackground = sgMissingRender.add(new BoolSetting.Builder()
        .name("背景板")
        .description("在缺失物品图标后绘制半透明背景，增强复杂场景可读性。")
        .defaultValue(true)
        .visible(showMissingItems::get)
        .build()
    );

    private final Setting<SettingColor> missingBackgroundColor = sgMissingRender.add(new ColorSetting.Builder()
        .name("背景颜色")
        .defaultValue(new SettingColor(0, 0, 0, 110))
        .visible(() -> showMissingItems.get() && showMissingBackground.get())
        .build()
    );

    private final Setting<Boolean> showDistantMissingItems = sgMissingRender.add(new BoolSetting.Builder()
        .name("远处容器显示")
        .description("开启后蓝色远距离容器也显示最后一次扫描得到的缺失物品。")
        .defaultValue(true)
        .visible(showMissingItems::get)
        .build()
    );

    private final Setting<Boolean> showUnscannedHint = sgMissingRender.add(new BoolSetting.Builder()
        .name("未扫描提示")
        .description("开启：未扫描容器显示 ?；关闭：扫描前直接显示投影容器总需求，扫描后自动切换为实际缺失量。")
        .defaultValue(true)
        .visible(showMissingItems::get)
        .build()
    );

    // ================= 工作状态 =================

    private int timer = 0;
    private int guiWaitTicks = 0;

    private BlockPos currentTarget = null;
    private boolean isWorking = false;
    private final Set<BlockPos> finishedCache = new HashSet<>();
    private final Set<BlockPos> blockedCache = new HashSet<>();
    private final Set<BlockPos> waitingMaterialCache = new HashSet<>();
    private final Map<BlockPos, ContainerRenderSnapshot> renderSnapshotCache = new HashMap<>();

    // ================= 生存模式状态机 =================

    private SurvivalStage survivalStage = SurvivalStage.IDLE;

    /** 当前正在处理的投影 Inventory 槽位索引。 */
    private int actionSchematicSlot = -1;

    /** 当前正在处理的 GUI/ScreenHandler 槽位 ID。 */
    private int actionTargetHandlerSlot = -1;

    /** 填装时最初取料的玩家 GUI 槽位，鼠标剩余物优先放回这里。 */
    private int preferredReturnHandlerSlot = -1;

    /** 用于检测玩家背包变化，变化后自动重新尝试“缺料/背包满”的容器。 */
    private int lastPlayerInventoryFingerprint = 0;

    public SchematicPro() {
        super(
            AddonTemplate.CATEGORY,
            "SchematicPro",
            "投影容器自动填装：创造模式可生成投影所需物品填入容器，生存模式从背包取料并按投影槽位填装。很方便"
        );
    }

    @Override
    public void onActivate() {
        resetAllState();
        lastPlayerInventoryFingerprint = computePlayerInventoryFingerprint();
    }

    @Override
    public void onDeactivate() {
        // 不主动丢弃/删除鼠标上的物品。
        // 如果玩家在状态机中途关闭模块，原版 GUI 仍会保留当前 cursor stack，玩家可以手动放回。
        clearSurvivalActionState();
        isWorking = false;
        currentTarget = null;
        guiWaitTicks = 0;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton btn = theme.button(translatedText("reset-progress", "重置所有进度"));
        btn.action = () -> resetAllState();
        return btn;
    }

    private static String translatedText(String key, String fallback) {
        return JsonEnglishTranslationManager.moduleText("SchematicPro", key, fallback);
    }

    // ================= 主循环 =================

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (operationMode.get() != OperationMode.Manual) return;

        // 创造复制模式必须真的处于创造模式。
        if (fillMode.get() == FillMode.Creative && !mc.player.isCreative()) return;

        BlockPos pos = event.result.getBlockPos();
        if (!(mc.world.getBlockEntity(pos) instanceof LootableContainerBlockEntity)) return;
        if (shouldSkipContainer(pos)) return;

        // 手动触发意味着玩家明确想重试这个容器，所以清掉暂时状态。
        finishedCache.remove(pos);
        blockedCache.remove(pos);
        waitingMaterialCache.remove(pos);
        renderSnapshotCache.remove(pos);

        beginWorking(pos);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        refreshDeferredCachesWhenInventoryChanges();

        if (isWorking && currentTarget != null) {
            handleFillingInGui();
            return;
        }

        if (operationMode.get() != OperationMode.Automatic) return;

        // 创造复制模式在非创造状态下不自动开箱。
        if (fillMode.get() == FillMode.Creative && !mc.player.isCreative()) return;

        // 不要在玩家已经打开其他 GUI 时从背后自动交互容器。
        if (mc.currentScreen != null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        findAndOpenTarget();
    }

    // ================= 自动寻找目标 =================

    private void findAndOpenTarget() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double interactSq = interactRange.get() * interactRange.get();

        for (BlockEntity be : Utils.blockEntities()) {
            if (!(be instanceof LootableContainerBlockEntity)) continue;

            BlockPos pos = be.getPos();
            double distSq = playerPos.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );

            if (distSq > interactSq) continue;
            if (shouldSkipContainer(pos)) continue;
            if (finishedCache.contains(pos)) continue;
            if (blockedCache.contains(pos) || waitingMaterialCache.contains(pos)) continue;

            if (!needsFixing(pos)) {
                finishedCache.add(pos);
                continue;
            }

            beginWorking(pos);

            mc.interactionManager.interactBlock(
                mc.player,
                Hand.MAIN_HAND,
                new BlockHitResult(
                    new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                    Direction.UP,
                    pos,
                    false
                )
            );
            return;
        }
    }

    private void beginWorking(BlockPos pos) {
        currentTarget = pos;
        isWorking = true;
        timer = delay.get();
        guiWaitTicks = GUI_OPEN_TIMEOUT_TICKS;
        clearSurvivalActionState();
    }

    // ================= 投影/真实容器检查 =================

    /**
     * 整个投影容器完全为空时跳过。
     * 这是安全保护：避免把“投影本来没记录内容”的普通空容器当成清空任务。
     */
    private boolean shouldSkipContainer(BlockPos pos) {
        var schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) return true;

        BlockEntity schemBe = schematicWorld.getBlockEntity(pos);
        if (!(schemBe instanceof Inventory schemInv)) return true;

        for (int i = 0; i < schemInv.size(); i++) {
            if (!schemInv.getStack(i).isEmpty()) return false;
        }

        return true;
    }

    /**
     * 严格比较投影与真实容器：Item + Components + Count 都必须一致。
     */
    private boolean needsFixing(BlockPos pos) {
        if (mc.world == null) return false;

        var schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) return false;

        BlockEntity realBe = mc.world.getBlockEntity(pos);
        BlockEntity schemBe = schematicWorld.getBlockEntity(pos);

        if (!(realBe instanceof Inventory realInv)) return false;
        if (!(schemBe instanceof Inventory schemInv)) return false;

        int size = Math.min(realInv.size(), schemInv.size());

        for (int i = 0; i < size; i++) {
            if (!ItemStack.areEqual(schemInv.getStack(i), realInv.getStack(i))) {
                return true;
            }
        }

        // 理论上同种容器 size 应相同；不相同就保守判为需要处理。
        return realInv.size() != schemInv.size();
    }

    // ================= GUI 填装入口 =================

    private void handleFillingInGui() {
        if (mc.player == null || mc.world == null) {
            abortWorking();
            return;
        }

        // 自动 interactBlock 后 GUI 可能要等服务端响应若干 Tick。
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            if (guiWaitTicks > 0) {
                guiWaitTicks--;
                return;
            }

            abortWorking();
            return;
        }

        guiWaitTicks = 0;

        if (timer > 0) {
            timer--;
            return;
        }

        var schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null || currentTarget == null) {
            safeStopAndClose();
            return;
        }

        BlockEntity schemBe = schematicWorld.getBlockEntity(currentTarget);
        if (!(schemBe instanceof Inventory schemInv)) {
            safeStopAndClose();
            return;
        }

        // 这里是缺失物品显示唯一的权威数据入口：真实打开 GUI 后的 ScreenHandler。
        // 每次槽位操作等待结束后都会重新快照，因此图标数量会跟随填装实时减少。
        updateRenderSnapshotFromGui(currentTarget, screen.getScreenHandler(), schemInv);

        if (fillMode.get() == FillMode.Creative) {
            handleCreativeFill(screen, schemInv);
        } else {
            handleSurvivalFill(screen, schemInv);
        }
    }

    // ================= 创造模式：保留原核心逻辑 =================

    private void handleCreativeFill(HandledScreen<?> screen, Inventory schemInv) {
        if (mc.player == null || mc.interactionManager == null) return;

        // 防止玩家在填装过程中切出生存后继续发送创造包。
        if (!mc.player.isCreative()) {
            abortWorking();
            return;
        }

        ScreenHandler handler = screen.getScreenHandler();
        boolean allCorrect = true;

        for (int schematicSlot = 0; schematicSlot < schemInv.size(); schematicSlot++) {
            int handlerSlot = getContainerHandlerSlot(handler, schematicSlot);
            if (handlerSlot == -1) break;

            ItemStack targetStack = schemInv.getStack(schematicSlot);
            ItemStack realStack = handler.getSlot(handlerSlot).getStack();

            if (ItemStack.areEqual(targetStack, realStack)) continue;

            allCorrect = false;

            // 与原版模块一致：投影空槽不主动删除真实物品。
            // 生存模式会按用户要求把错误物品安全取回背包。
            if (targetStack.isEmpty()) continue;

            ItemStack stackToSend = targetStack.copy();

            // 创造模式原逻辑：把目标物品写到热键栏 0（creative inventory slot 36），
            // 再通过 SWAP(0) 放入目标容器槽。
            mc.player.networkHandler.sendPacket(
                new CreativeInventoryActionC2SPacket(36, stackToSend)
            );

            mc.interactionManager.clickSlot(
                handler.syncId,
                handlerSlot,
                0,
                SlotActionType.SWAP,
                mc.player
            );

            timer = delay.get();
            return;
        }

        if (allCorrect) finishCurrentContainer();
    }

    // ================= 生存模式 =================

    private void handleSurvivalFill(HandledScreen<?> screen, Inventory schemInv) {
        if (mc.player == null || mc.interactionManager == null) return;

        ScreenHandler handler = screen.getScreenHandler();

        // 状态机中途必须先把鼠标上的真实物品处理完，不能重新扫描其他槽。
        if (survivalStage != SurvivalStage.IDLE) {
            handleSurvivalAction(handler, schemInv);
            return;
        }

        // 玩家自己鼠标上正拿着物品时，不抢控制权。
        if (!handler.getCursorStack().isEmpty()) return;

        boolean hasMissingMaterial = false;
        boolean hasBlockedByInventory = false;

        for (int schematicSlot = 0; schematicSlot < schemInv.size(); schematicSlot++) {
            int handlerSlot = getContainerHandlerSlot(handler, schematicSlot);
            if (handlerSlot == -1) break;

            Slot targetSlot = handler.getSlot(handlerSlot);
            ItemStack targetStack = schemInv.getStack(schematicSlot);
            ItemStack realStack = targetSlot.getStack();

            if (ItemStack.areEqual(targetStack, realStack)) continue;

            // ---------------------------------------------------------
            // 1. 投影要求空槽，但真实槽里有物品 -> 全部退回玩家背包
            // ---------------------------------------------------------
            if (targetStack.isEmpty()) {
                if (!realStack.isEmpty()) {
                    if (!canPlayerAccept(handler, realStack, realStack.getCount())) {
                        // 先别立刻判黄色。继续找其他可填装槽位；
                        // 如果能先把背包材料塞进容器，就可能自动腾出空间。
                        hasBlockedByInventory = true;
                        continue;
                    }

                    quickMoveContainerToPlayer(handler, handlerSlot);
                    return;
                }

                continue;
            }

            // ---------------------------------------------------------
            // 2. 真实槽不是投影要求的同一 Item + Components
            //    -> 错误物品全部退回玩家背包
            // ---------------------------------------------------------
            if (!realStack.isEmpty()
                && !ItemStack.areItemsAndComponentsEqual(targetStack, realStack)) {

                if (!canPlayerAccept(handler, realStack, realStack.getCount())) {
                    hasBlockedByInventory = true;
                    continue;
                }

                quickMoveContainerToPlayer(handler, handlerSlot);
                return;
            }

            // 到这里：realStack 要么为空，要么与 targetStack Item + Components 相同。
            int currentCount = realStack.isEmpty() ? 0 : realStack.getCount();
            int targetCount = targetStack.getCount();

            // ---------------------------------------------------------
            // 3. 同类物品数量过多 -> 只取出多余部分
            // ---------------------------------------------------------
            if (currentCount > targetCount) {
                int surplus = currentCount - targetCount;

                // 只要求背包能接收“多余部分”，不会因为装不下整栈而误判黄色。
                if (!canPlayerAccept(handler, realStack, surplus)) {
                    hasBlockedByInventory = true;
                    continue;
                }

                startTrimAction(handler, schematicSlot, handlerSlot, currentCount, targetCount);
                return;
            }

            // ---------------------------------------------------------
            // 4. 数量不足 -> 从玩家背包找完全相同 Item + Components 的材料
            // ---------------------------------------------------------
            if (currentCount < targetCount) {
                int missing = targetCount - currentCount;
                int sourceSlot = findBestPlayerSourceSlot(handler, targetStack, missing);

                if (sourceSlot == -1) {
                    // 不变黄。只是暂时缺材料，继续检查其他槽，看是否还有可以处理的工作。
                    hasMissingMaterial = true;
                    continue;
                }

                startFillAction(handler, schematicSlot, handlerSlot, sourceSlot, missing);
                return;
            }
        }

        if (hasBlockedByInventory) {
            // 已经尝试过先填其他槽来腾空间；仍然没有任何可执行动作时才黄色。
            markBlockedAndStop();
        } else if (hasMissingMaterial) {
            markWaitingMaterialAndStop();
        } else {
            finishCurrentContainer();
        }
    }

    /**
     * 处理 FILLING / TRIMMING / RETURNING。
     * 每次只执行一个普通原版槽位点击，然后等待 delay Tick。
     */
    private void handleSurvivalAction(ScreenHandler handler, Inventory schemInv) {
        switch (survivalStage) {
            case IDLE -> {
                // 不会进入这里。
            }
            case FILLING -> handleFillingCursor(handler, schemInv);
            case TRIMMING -> handleTrimmingCursor(handler, schemInv);
            case RETURNING -> handleReturningCursor(handler);
        }
    }

    /**
     * 从玩家背包拿起目标材料。
     *
     * 优化：如果源堆数量 > 缺少数量，但“拿一半”不会超过缺少数量，
     * 使用右键拿半组，避免例如 64 -> 目标缺 32 时执行 32 次右键。
     */
    private void startFillAction(
        ScreenHandler handler,
        int schematicSlot,
        int targetHandlerSlot,
        int sourceHandlerSlot,
        int missing
    ) {
        if (mc.player == null || mc.interactionManager == null) return;

        ItemStack source = handler.getSlot(sourceHandlerSlot).getStack();
        if (source.isEmpty()) return;

        actionSchematicSlot = schematicSlot;
        actionTargetHandlerSlot = targetHandlerSlot;
        preferredReturnHandlerSlot = sourceHandlerSlot;
        survivalStage = SurvivalStage.FILLING;

        int half = (source.getCount() + 1) / 2;
        int button = (source.getCount() > missing && half <= missing) ? 1 : 0;

        mc.interactionManager.clickSlot(
            handler.syncId,
            sourceHandlerSlot,
            button,
            SlotActionType.PICKUP,
            mc.player
        );

        timer = delay.get();
    }

   
    private void startTrimAction(
        ScreenHandler handler,
        int schematicSlot,
        int targetHandlerSlot,
        int currentCount,
        int targetCount
    ) {
        if (mc.player == null || mc.interactionManager == null) return;

        actionSchematicSlot = schematicSlot;
        actionTargetHandlerSlot = targetHandlerSlot;
        preferredReturnHandlerSlot = -1;

        boolean halfSplitFinishes = currentCount / 2 == targetCount;
        survivalStage = halfSplitFinishes ? SurvivalStage.RETURNING : SurvivalStage.TRIMMING;

        mc.interactionManager.clickSlot(
            handler.syncId,
            targetHandlerSlot,
            halfSplitFinishes ? 1 : 0,
            SlotActionType.PICKUP,
            mc.player
        );

        timer = delay.get();
    }

    private void handleFillingCursor(ScreenHandler handler, Inventory schemInv) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (!isActionTargetValid(handler, schemInv)) {
            survivalStage = SurvivalStage.RETURNING;
            return;
        }

        ItemStack cursor = handler.getCursorStack();
        if (cursor.isEmpty()) {
            clearSurvivalActionState();
            return;
        }

        ItemStack targetStack = schemInv.getStack(actionSchematicSlot);
        Slot targetSlot = handler.getSlot(actionTargetHandlerSlot);
        ItemStack realStack = targetSlot.getStack();


        if (!ItemStack.areItemsAndComponentsEqual(cursor, targetStack)) {
            survivalStage = SurvivalStage.RETURNING;
            return;
        }


        if (!realStack.isEmpty()
            && !ItemStack.areItemsAndComponentsEqual(realStack, targetStack)) {
            survivalStage = SurvivalStage.RETURNING;
            return;
        }

        int currentCount = realStack.isEmpty() ? 0 : realStack.getCount();
        int missing = targetStack.getCount() - currentCount;

        if (missing <= 0) {
            survivalStage = SurvivalStage.RETURNING;
            return;
        }

        if (!targetSlot.canInsert(cursor)) {

            survivalStage = SurvivalStage.RETURNING;
            waitingMaterialCache.add(currentTarget);
            return;
        }

        if (cursor.getCount() <= missing) {
            mc.interactionManager.clickSlot(
                handler.syncId,
                actionTargetHandlerSlot,
                0,
                SlotActionType.PICKUP,
                mc.player
            );
        } else {

            mc.interactionManager.clickSlot(
                handler.syncId,
                actionTargetHandlerSlot,
                1,
                SlotActionType.PICKUP,
                mc.player
            );
        }

        timer = delay.get();
    }

    private void handleTrimmingCursor(ScreenHandler handler, Inventory schemInv) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (!isActionTargetValid(handler, schemInv)) {
            survivalStage = SurvivalStage.RETURNING;
            return;
        }

        ItemStack cursor = handler.getCursorStack();
        if (cursor.isEmpty()) {
            clearSurvivalActionState();
            return;
        }

        ItemStack targetStack = schemInv.getStack(actionSchematicSlot);
        Slot targetSlot = handler.getSlot(actionTargetHandlerSlot);
        ItemStack realStack = targetSlot.getStack();

        if (targetStack.isEmpty()
            || !ItemStack.areItemsAndComponentsEqual(cursor, targetStack)) {
            survivalStage = SurvivalStage.RETURNING;
            return;
        }

        if (!realStack.isEmpty()
            && !ItemStack.areItemsAndComponentsEqual(realStack, targetStack)) {
            survivalStage = SurvivalStage.RETURNING;
            return;
        }

        int currentCount = realStack.isEmpty() ? 0 : realStack.getCount();
        int needToRestore = targetStack.getCount() - currentCount;

        if (needToRestore <= 0) {
            survivalStage = SurvivalStage.RETURNING;
            return;
        }

        if (!targetSlot.canInsert(cursor)) {
            survivalStage = SurvivalStage.RETURNING;
            waitingMaterialCache.add(currentTarget);
            return;
        }

        if (cursor.getCount() <= needToRestore) {
            mc.interactionManager.clickSlot(
                handler.syncId,
                actionTargetHandlerSlot,
                0,
                SlotActionType.PICKUP,
                mc.player
            );
        } else {
            mc.interactionManager.clickSlot(
                handler.syncId,
                actionTargetHandlerSlot,
                1,
                SlotActionType.PICKUP,
                mc.player
            );
        }

        timer = delay.get();
    }


    private void handleReturningCursor(ScreenHandler handler) {
        if (mc.player == null || mc.interactionManager == null) return;

        ItemStack cursor = handler.getCursorStack();
        if (cursor.isEmpty()) {
            clearSurvivalActionState();
            return;
        }

        int returnSlot = findAcceptingPlayerSlot(
            handler,
            cursor,
            preferredReturnHandlerSlot
        );

        if (returnSlot == -1) {

            if (currentTarget != null) {
                blockedCache.add(currentTarget);
                waitingMaterialCache.remove(currentTarget);
                finishedCache.remove(currentTarget);
            }

            timer = delay.get();
            return;
        }

        mc.interactionManager.clickSlot(
            handler.syncId,
            returnSlot,
            0,
            SlotActionType.PICKUP,
            mc.player
        );

        timer = delay.get();
    }

    private boolean isActionTargetValid(ScreenHandler handler, Inventory schemInv) {
        return actionSchematicSlot >= 0
            && actionSchematicSlot < schemInv.size()
            && actionTargetHandlerSlot >= 0
            && actionTargetHandlerSlot < handler.slots.size();
    }

    // ================= 生存模式：背包槽工具 =================

    /**
     * 选择一个最适合补当前缺口的玩家槽：
     * 1. 优先选择 count <= missing 中最大的（可以整堆一次放入）；
     * 2. 如果没有，再选 count > missing 中最小的，减少精确右键次数。
     */
    private int findBestPlayerSourceSlot(
        ScreenHandler handler,
        ItemStack targetStack,
        int missing
    ) {
        if (mc.player == null) return -1;

        int bestUnderSlot = -1;
        int bestUnderCount = -1;

        int bestOverSlot = -1;
        int bestOverCount = Integer.MAX_VALUE;

        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot.inventory != mc.player.getInventory()) continue;
            if (!slot.canTakeItems(mc.player)) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            if (!ItemStack.areItemsAndComponentsEqual(stack, targetStack)) continue;

            int count = stack.getCount();

            if (count <= missing) {
                if (count > bestUnderCount) {
                    bestUnderCount = count;
                    bestUnderSlot = i;
                }
            } else if (count < bestOverCount) {
                bestOverCount = count;
                bestOverSlot = i;
            }
        }

        return bestUnderSlot != -1 ? bestUnderSlot : bestOverSlot;
    }

    /**
     * 判断玩家背包是否能完整接收指定数量。
     * 会计算：
     * - 已有相同 Item + Components 堆的剩余容量
     * - 空槽容量
     * - Slot 自身 getMaxItemCount 限制
     */
    private boolean canPlayerAccept(
        ScreenHandler handler,
        ItemStack stack,
        int amount
    ) {
        if (mc.player == null) return false;
        if (amount <= 0) return true;
        if (stack.isEmpty()) return true;

        int remaining = amount;

        // 第一遍：先计算已有同类堆可合并空间。
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot.inventory != mc.player.getInventory()) continue;
            if (!slot.canInsert(stack)) continue;

            ItemStack existing = slot.getStack();
            if (existing.isEmpty()) continue;
            if (!ItemStack.areItemsAndComponentsEqual(existing, stack)) continue;

            int max = Math.min(stack.getMaxCount(), slot.getMaxItemCount(stack));
            int free = Math.max(0, max - existing.getCount());

            remaining -= free;
            if (remaining <= 0) return true;
        }

        // 第二遍：空槽。
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot.inventory != mc.player.getInventory()) continue;
            if (!slot.canInsert(stack)) continue;
            if (!slot.getStack().isEmpty()) continue;

            int max = Math.min(stack.getMaxCount(), slot.getMaxItemCount(stack));
            remaining -= max;

            if (remaining <= 0) return true;
        }

        return false;
    }

    private int findAcceptingPlayerSlot(
        ScreenHandler handler,
        ItemStack stack,
        int preferredSlot
    ) {
        if (mc.player == null || stack.isEmpty()) return -1;

        if (isPlayerSlotAbleToAccept(handler, preferredSlot, stack)) {
            return preferredSlot;
        }

        // 优先合并已有同类堆。
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot.inventory != mc.player.getInventory()) continue;
            if (!slot.canInsert(stack)) continue;

            ItemStack existing = slot.getStack();
            if (existing.isEmpty()) continue;
            if (!ItemStack.areItemsAndComponentsEqual(existing, stack)) continue;

            int max = Math.min(stack.getMaxCount(), slot.getMaxItemCount(stack));
            if (existing.getCount() < max) return i;
        }

        // 再找空槽。
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot.inventory != mc.player.getInventory()) continue;
            if (!slot.canInsert(stack)) continue;
            if (slot.getStack().isEmpty()) return i;
        }

        return -1;
    }

    private boolean isPlayerSlotAbleToAccept(
        ScreenHandler handler,
        int slotId,
        ItemStack stack
    ) {
        if (mc.player == null) return false;
        if (slotId < 0 || slotId >= handler.slots.size()) return false;

        Slot slot = handler.getSlot(slotId);
        if (slot.inventory != mc.player.getInventory()) return false;
        if (!slot.canInsert(stack)) return false;

        ItemStack existing = slot.getStack();
        if (existing.isEmpty()) return true;
        if (!ItemStack.areItemsAndComponentsEqual(existing, stack)) return false;

        int max = Math.min(stack.getMaxCount(), slot.getMaxItemCount(stack));
        return existing.getCount() < max;
    }

    /**
     * 从容器槽 Shift+左键移动到玩家背包。
     * 调用前已经用 canPlayerAccept() 验证能完整接收。
     */
    private void quickMoveContainerToPlayer(ScreenHandler handler, int handlerSlot) {
        if (mc.player == null || mc.interactionManager == null) return;

        mc.interactionManager.clickSlot(
            handler.syncId,
            handlerSlot,
            0,
            SlotActionType.QUICK_MOVE,
            mc.player
        );

        timer = delay.get();
    }

    /**
     * 将“投影槽索引”映射到当前 ScreenHandler 对应的容器槽。
     * 不硬编码 36/54 等玩家背包偏移，而是跳过所有 player inventory 槽。
     */
    private int getContainerHandlerSlot(ScreenHandler handler, int schematicSlot) {
        if (mc.player == null || schematicSlot < 0) return -1;

        int containerIndex = 0;

        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot.inventory == mc.player.getInventory()) continue;

            if (containerIndex == schematicSlot) return i;
            containerIndex++;
        }

        return -1;
    }

    // ================= 缺失物品快照 =================

    /**
     * 使用已打开容器的 ScreenHandler 计算“现在还缺什么”。
     * 只统计正缺少的目标物品；错误物品、多余物品由生存填装逻辑单独处理。
     */
    private void updateRenderSnapshotFromGui(
        BlockPos pos,
        ScreenHandler handler,
        Inventory schemInv
    ) {
        if (pos == null || handler == null || schemInv == null) return;

        BlockPos key = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        ContainerRenderSnapshot snapshot = renderSnapshotCache.computeIfAbsent(
            key,
            ignored -> new ContainerRenderSnapshot()
        );

        snapshot.scanned = true;
        snapshot.missingItems.clear();

        for (int schematicSlot = 0; schematicSlot < schemInv.size(); schematicSlot++) {
            int handlerSlot = getContainerHandlerSlot(handler, schematicSlot);
            if (handlerSlot == -1) break;

            ItemStack targetStack = schemInv.getStack(schematicSlot);
            if (targetStack.isEmpty()) continue;

            ItemStack realStack = handler.getSlot(handlerSlot).getStack();
            int currentCount = 0;

            if (!realStack.isEmpty()
                && ItemStack.areItemsAndComponentsEqual(targetStack, realStack)) {
                currentCount = realStack.getCount();
            }

            int missing = targetStack.getCount() - currentCount;
            if (missing <= 0) continue;

            addMissingRenderEntry(snapshot.missingItems, targetStack, missing);
        }
    }

    /** 合并相同 Item + Components 的缺失量，但保留完整 ItemStack 用于原版图标。 */
    private void addMissingRenderEntry(
        List<MissingItemEntry> entries,
        ItemStack targetStack,
        int amount
    ) {
        if (targetStack.isEmpty() || amount <= 0) return;

        for (MissingItemEntry entry : entries) {
            if (ItemStack.areItemsAndComponentsEqual(entry.displayStack, targetStack)) {
                entry.missingCount += amount;
                return;
            }
        }

        ItemStack display = targetStack.copy();
        display.setCount(1);
        entries.add(new MissingItemEntry(display, amount));
    }

    /**
     * 未扫描容器的临时显示数据：直接读取 Litematica 投影容器的完整需求。
     * 这里只用于“未扫描提示”关闭时的预览；不会写入 renderSnapshotCache，
     * 因此一旦真实 GUI 扫描完成，渲染会立即切换为 ScreenHandler 的实际缺失量。
     */
    private ContainerRenderSnapshot buildSchematicRequirementSnapshot(BlockPos pos) {
        if (pos == null) return null;

        var schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) return null;

        BlockEntity schemBe = schematicWorld.getBlockEntity(pos);
        if (!(schemBe instanceof Inventory schemInv)) return null;

        ContainerRenderSnapshot snapshot = new ContainerRenderSnapshot();
        snapshot.scanned = false;

        for (int schematicSlot = 0; schematicSlot < schemInv.size(); schematicSlot++) {
            ItemStack targetStack = schemInv.getStack(schematicSlot);
            if (targetStack.isEmpty()) continue;

            addMissingRenderEntry(
                snapshot.missingItems,
                targetStack,
                targetStack.getCount()
            );
        }

        return snapshot;
    }

    private void markRenderSnapshotFinished(BlockPos pos) {
        if (pos == null) return;

        BlockPos key = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        ContainerRenderSnapshot snapshot = renderSnapshotCache.computeIfAbsent(
            key,
            ignored -> new ContainerRenderSnapshot()
        );
        snapshot.scanned = true;
        snapshot.missingItems.clear();
    }

    // ================= 完成 / 等待 / 阻塞 =================

    private void finishCurrentContainer() {
        if (currentTarget != null) {
            finishedCache.add(currentTarget);
            blockedCache.remove(currentTarget);
            waitingMaterialCache.remove(currentTarget);
            markRenderSnapshotFinished(currentTarget);
        }

        if (autoClose.get()) {
            safeStopAndClose();
        } else {
            stopWorkingKeepScreen();
        }
    }

    private void markWaitingMaterialAndStop() {
        if (currentTarget != null) {
            waitingMaterialCache.add(currentTarget);
            blockedCache.remove(currentTarget);
            finishedCache.remove(currentTarget);
        }

        // 自动扫描必须关闭这个缺料容器，否则无法继续扫描其他容器。
        if (operationMode.get() == OperationMode.Automatic || autoClose.get()) {
            safeStopAndClose();
        } else {
            stopWorkingKeepScreen();
        }
    }

    private void markBlockedAndStop() {
        if (currentTarget != null) {
            blockedCache.add(currentTarget);
            waitingMaterialCache.remove(currentTarget);
            finishedCache.remove(currentTarget);
        }

        // 此方法只会在 cursor 为空的 IDLE 阶段调用，因此可以安全关闭。
        if (operationMode.get() == OperationMode.Automatic || autoClose.get()) {
            safeStopAndClose();
        } else {
            stopWorkingKeepScreen();
        }
    }

    private void safeStopAndClose() {
        // 生存状态机鼠标上仍有物品时，绝不能主动关闭。
        if (mc.player != null
            && mc.player.currentScreenHandler != null
            && !mc.player.currentScreenHandler.getCursorStack().isEmpty()
            && survivalStage != SurvivalStage.IDLE) {
            survivalStage = SurvivalStage.RETURNING;
            return;
        }

        if (mc.player != null) mc.player.closeHandledScreen();

        isWorking = false;
        currentTarget = null;
        guiWaitTicks = 0;
        timer = delay.get();
        clearSurvivalActionState();
    }

    private void stopWorkingKeepScreen() {
        isWorking = false;
        currentTarget = null;
        guiWaitTicks = 0;
        timer = delay.get();
        clearSurvivalActionState();
    }

    private void abortWorking() {
        isWorking = false;
        currentTarget = null;
        guiWaitTicks = 0;
        timer = delay.get();
        clearSurvivalActionState();
    }

    private void clearSurvivalActionState() {
        survivalStage = SurvivalStage.IDLE;
        actionSchematicSlot = -1;
        actionTargetHandlerSlot = -1;
        preferredReturnHandlerSlot = -1;
    }

    private void resetAllState() {
        finishedCache.clear();
        blockedCache.clear();
        waitingMaterialCache.clear();
        renderSnapshotCache.clear();

        isWorking = false;
        currentTarget = null;
        timer = 0;
        guiWaitTicks = 0;
        clearSurvivalActionState();
    }

    // ================= 背包变化检测 =================

    private void refreshDeferredCachesWhenInventoryChanges() {
        int fingerprint = computePlayerInventoryFingerprint();

        if (fingerprint != lastPlayerInventoryFingerprint) {
            lastPlayerInventoryFingerprint = fingerprint;

            // 玩家拿到材料、腾出空间、移动堆叠后，自动允许重新尝试。
            waitingMaterialCache.clear();
            blockedCache.clear();
        }
    }

    private int computePlayerInventoryFingerprint() {
        if (mc.player == null) return 0;

        int hash = 1;
        Inventory inventory = mc.player.getInventory();

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);

            hash = 31 * hash + stack.getItem().hashCode();
            hash = 31 * hash + stack.getCount();
            hash = 31 * hash + stack.getComponents().hashCode();
        }

        return hash;
    }

    // ================= 渲染 =================

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        var schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) return;

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double renderSq = renderRange.get() * renderRange.get();
        double interactSq = interactRange.get() * interactRange.get();

        for (BlockEntity be : Utils.blockEntities()) {
            if (!(be instanceof LootableContainerBlockEntity)) continue;

            BlockPos pos = be.getPos();
            double distSq = playerPos.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );

            if (distSq > renderSq) continue;
            if (shouldSkipContainer(pos)) continue;

            // 已经在真实容器 GUI 中逐槽确认完成的容器，直接保持绿色。
            // 不再用关闭 GUI 后的 BlockEntity Inventory 重新判定；该数据对容器内容并不可靠，
            // 会把已经填好的空桶等物品误判成未完成并触发无限开关箱。
            if (finishedCache.contains(pos)) {
                blockedCache.remove(pos);
                waitingMaterialCache.remove(pos);

                event.renderer.box(
                    pos,
                    matchSideColor.get(),
                    matchLineColor.get(),
                    ShapeMode.Both,
                    0
                );
                continue;
            }

            boolean needsFix = needsFixing(pos);

            // 对尚未经过 GUI 验证的容器，世界侧状态只能作为“是否值得打开检查”的提示。
            // 如果它看起来已经正确，可以先标记完成，保持原模块对已有正确容器的兼容行为。
            if (!needsFix) {
                finishedCache.add(pos);
                blockedCache.remove(pos);
                waitingMaterialCache.remove(pos);

                event.renderer.box(
                    pos,
                    matchSideColor.get(),
                    matchLineColor.get(),
                    ShapeMode.Both,
                    0
                );
                continue;
            }

            // 只有“玩家背包没有足够接收空间”才黄色。
            if (fillMode.get() == FillMode.SurvivalInventory && blockedCache.contains(pos)) {
                event.renderer.box(
                    pos,
                    blockedSideColor.get(),
                    blockedLineColor.get(),
                    ShapeMode.Both,
                    0
                );
                continue;
            }

            // 缺材料仍然属于“未完成”，不黄色。
            if (distSq <= interactSq) {
                event.renderer.box(
                    pos,
                    targetSideColor.get(),
                    targetLineColor.get(),
                    ShapeMode.Both,
                    0
                );
            } else {
                event.renderer.box(
                    pos,
                    previewSideColor.get(),
                    previewLineColor.get(),
                    ShapeMode.Both,
                    0
                );
            }
        }
    }

    /**
     * 世界坐标物品面板。
     * 先用 NametagUtils 把容器中心世界坐标投影成屏幕坐标，再让原版 DrawContext
     * 直接渲染 ItemStack，因此药水颜色、附魔光效、自定义模型/Data Components 都沿用原版表现。
     */
    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!showMissingItems.get()) return;
        if (mc.player == null || mc.world == null) return;

        double maxDistSq = missingItemRenderRange.get() * missingItemRenderRange.get();
        double interactSq = interactRange.get() * interactRange.get();
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        for (BlockEntity be : Utils.blockEntities()) {
            if (!(be instanceof LootableContainerBlockEntity)) continue;

            BlockPos pos = be.getPos();
            if (shouldSkipContainer(pos)) continue;
            if (finishedCache.contains(pos)) continue;

            double distSq = playerPos.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );
            if (distSq > maxDistSq) continue;

            // 如果关闭“远处显示”，只在自动交互距离内画物品面板。
            if (!showDistantMissingItems.get() && distSq > interactSq) continue;

            ContainerRenderSnapshot snapshot = renderSnapshotCache.get(pos);

            // 未扫描时有两种显示方式：
            // 1) “未扫描提示”开启 -> 只显示 ?；
            // 2) “未扫描提示”关闭 -> 先显示投影容器的完整需求。
            // 一旦真实打开 GUI 扫描过，下面就自动改用 ScreenHandler 计算出的实际缺失快照。
            if (snapshot == null || !snapshot.scanned) {
                if (showUnscannedHint.get()) {
                    renderUnscannedHint(event, pos);
                } else {
                    ContainerRenderSnapshot projectedRequirements = buildSchematicRequirementSnapshot(pos);
                    if (projectedRequirements != null && !projectedRequirements.missingItems.isEmpty()) {
                        renderMissingItemPanel(event, pos, projectedRequirements);
                    }
                }
                continue;
            }

            // 已扫描且没有缺失物品时：如果还未完成，说明通常只剩错误/多余物品需要取出，
            // 此时不伪造缺失图标；黄色状态仍会显示文字警告。
            if (snapshot.missingItems.isEmpty()) {
                if (blockedCache.contains(pos)) {
                    renderStatusOnlyPanel(event, pos, translatedText("inventory-full", "背包空间不足"), 0xFFFFCC00);
                }
                continue;
            }

            renderMissingItemPanel(event, pos, snapshot);
        }
    }

    private void renderMissingItemPanel(
        Render2DEvent event,
        BlockPos pos,
        ContainerRenderSnapshot snapshot
    ) {
        Vector3d projected = projectContainerAnchor(pos);
        if (projected == null) return;

        boolean blocked = blockedCache.contains(pos);
        boolean waiting = waitingMaterialCache.contains(pos);

        NametagUtils.begin(projected, event.drawContext);
        if (showMissingNames.get()) {
            renderNamedMissingList(event, snapshot, blocked, waiting);
        } else {
            renderMissingIconGrid(event, snapshot, blocked, waiting);
        }
        NametagUtils.end(event.drawContext);
    }

    /** 默认紧凑图标网格。 */
    private void renderMissingIconGrid(
        Render2DEvent event,
        ContainerRenderSnapshot snapshot,
        boolean blocked,
        boolean waiting
    ) {
        int totalTypes = snapshot.missingItems.size();
        int maxShown = Math.max(1, maxMissingItemsShown.get());
        boolean overflow = totalTypes > maxShown;

        // 有溢出时预留最后一个格子显示 +N。
        int realItemSlots = overflow ? Math.max(0, maxShown - 1) : Math.min(totalTypes, maxShown);
        int visibleCells = realItemSlots + (overflow ? 1 : 0);
        if (visibleCells <= 0) return;

        int perRow = Math.max(1, Math.min(missingItemsPerRow.get(), visibleCells));
        int rows = (visibleCells + perRow - 1) / perRow;
        int widestRowCells = Math.min(perRow, visibleCells);

        int statusHeight = (blocked || waiting) ? STATUS_TEXT_HEIGHT : 0;
        int panelWidth = widestRowCells * ITEM_ICON_CELL + PANEL_PADDING * 2;
        int panelHeight = rows * ITEM_ICON_CELL + PANEL_PADDING * 2 + statusHeight;
        int panelX = -panelWidth / 2;
        int panelY = -panelHeight / 2;

        if (showMissingBackground.get()) {
            event.drawContext.fill(
                panelX, panelY,
                panelX + panelWidth, panelY + panelHeight,
                missingBackgroundColor.get().getPacked()
            );
        }

        int gridY = panelY + PANEL_PADDING + statusHeight;

        for (int index = 0; index < realItemSlots; index++) {
            MissingItemEntry entry = snapshot.missingItems.get(index);
            int col = index % perRow;
            int row = index / perRow;
            int x = panelX + PANEL_PADDING + col * ITEM_ICON_CELL + (ITEM_ICON_CELL - ITEM_ICON_SIZE) / 2;
            int y = gridY + row * ITEM_ICON_CELL + (ITEM_ICON_CELL - ITEM_ICON_SIZE) / 2;

            ItemStack display = entry.displayStack.copy();
            display.setCount(1);
            RenderUtils.drawItem(
                event.drawContext,
                display,
                x,
                y,
                1.0f,
                showMissingCounts.get(),
                showMissingCounts.get() ? Integer.toString(entry.missingCount) : null,
                false
            );
        }

        if (overflow) {
            int index = visibleCells - 1;
            int col = index % perRow;
            int row = index / perRow;
            int cellX = panelX + PANEL_PADDING + col * ITEM_ICON_CELL;
            int cellY = gridY + row * ITEM_ICON_CELL;
            String more = "+" + (totalTypes - realItemSlots);
            int textX = cellX + (ITEM_ICON_CELL - mc.textRenderer.getWidth(more)) / 2;
            int textY = cellY + (ITEM_ICON_CELL - 9) / 2;
            event.drawContext.drawText(mc.textRenderer, more, textX, textY, 0xFFFFFFFF, true);
        }

        if (blocked) {
            drawPanelStatus(event, translatedText("inventory-full", "背包空间不足"), panelY + 2, 0xFFFFCC00);
        } else if (waiting) {
            drawPanelStatus(event, translatedText("missing-materials", "背包材料不足"), panelY + 2, 0xFFFF6666);
        }
    }

    /** 开启名称时改为更易读的纵向列表。 */
    private void renderNamedMissingList(
        Render2DEvent event,
        ContainerRenderSnapshot snapshot,
        boolean blocked,
        boolean waiting
    ) {
        int totalTypes = snapshot.missingItems.size();
        int maxShown = Math.max(1, maxMissingItemsShown.get());
        int shown = Math.min(totalTypes, maxShown);
        boolean overflow = totalTypes > shown;
        int statusHeight = (blocked || waiting) ? STATUS_TEXT_HEIGHT : 0;

        int maxTextWidth = 0;
        for (int i = 0; i < shown; i++) {
            MissingItemEntry entry = snapshot.missingItems.get(i);
            String text = buildMissingName(entry);
            maxTextWidth = Math.max(maxTextWidth, mc.textRenderer.getWidth(text));
        }
        if (overflow) {
            maxTextWidth = Math.max(
                maxTextWidth,
                mc.textRenderer.getWidth(translatedText("more-types", "+%d 种").formatted(totalTypes - shown))
            );
        }

        int rows = shown + (overflow ? 1 : 0);
        int rowHeight = 18;
        int panelWidth = PANEL_PADDING * 2 + ITEM_ICON_CELL + maxTextWidth + 4;
        int panelHeight = PANEL_PADDING * 2 + statusHeight + rows * rowHeight;
        int panelX = -panelWidth / 2;
        int panelY = -panelHeight / 2;

        if (showMissingBackground.get()) {
            event.drawContext.fill(
                panelX, panelY,
                panelX + panelWidth, panelY + panelHeight,
                missingBackgroundColor.get().getPacked()
            );
        }

        int contentY = panelY + PANEL_PADDING + statusHeight;
        for (int i = 0; i < shown; i++) {
            MissingItemEntry entry = snapshot.missingItems.get(i);
            int y = contentY + i * rowHeight;
            int iconX = panelX + PANEL_PADDING + 1;
            int iconY = y + 1;

            ItemStack display = entry.displayStack.copy();
            display.setCount(1);
            RenderUtils.drawItem(
                event.drawContext,
                display,
                iconX,
                iconY,
                1.0f,
                false,
                null,
                false
            );

            event.drawContext.drawText(
                mc.textRenderer,
                buildMissingName(entry),
                iconX + ITEM_ICON_CELL + 2,
                y + 5,
                0xFFFFFFFF,
                true
            );
        }

        if (overflow) {
            String more = translatedText("more-types", "+%d 种").formatted(totalTypes - shown);
            int y = contentY + shown * rowHeight + 5;
            event.drawContext.drawText(
                mc.textRenderer,
                more,
                panelX + PANEL_PADDING + ITEM_ICON_CELL + 2,
                y,
                0xFFCCCCCC,
                true
            );
        }

        if (blocked) {
            drawPanelStatus(event, translatedText("inventory-full", "背包空间不足"), panelY + 2, 0xFFFFCC00);
        } else if (waiting) {
            drawPanelStatus(event, translatedText("missing-materials", "背包材料不足"), panelY + 2, 0xFFFF6666);
        }
    }

    private String buildMissingName(MissingItemEntry entry) {
        String name = Names.get(entry.displayStack);
        if (showMissingCounts.get()) {
            return name + " x" + entry.missingCount;
        }
        return name;
    }

    private void drawPanelStatus(Render2DEvent event, String text, int y, int color) {
        int x = -mc.textRenderer.getWidth(text) / 2;
        event.drawContext.drawText(mc.textRenderer, text, x, y, color, true);
    }

    private void renderUnscannedHint(Render2DEvent event, BlockPos pos) {
        Vector3d projected = projectContainerAnchor(pos);
        if (projected == null) return;

        NametagUtils.begin(projected, event.drawContext);

        int width = 18;
        int height = 16;
        int x = -width / 2;
        int y = -height / 2;
        if (showMissingBackground.get()) {
            event.drawContext.fill(
                x, y, x + width, y + height,
                missingBackgroundColor.get().getPacked()
            );
        }
        event.drawContext.drawCenteredTextWithShadow(
            mc.textRenderer,
            "?",
            0,
            y + 4,
            0xFFFFFFFF
        );

        NametagUtils.end(event.drawContext);
    }

    private void renderStatusOnlyPanel(
        Render2DEvent event,
        BlockPos pos,
        String text,
        int color
    ) {
        Vector3d projected = projectContainerAnchor(pos);
        if (projected == null) return;

        NametagUtils.begin(projected, event.drawContext);

        int width = mc.textRenderer.getWidth(text) + 8;
        int height = 15;
        int x = -width / 2;
        int y = -height / 2;
        if (showMissingBackground.get()) {
            event.drawContext.fill(
                x, y, x + width, y + height,
                missingBackgroundColor.get().getPacked()
            );
        }
        event.drawContext.drawCenteredTextWithShadow(mc.textRenderer, text, 0, y + 3, color);

        NametagUtils.end(event.drawContext);
    }

    private Vector3d projectContainerAnchor(BlockPos pos) {
        Vector3d projected = new Vector3d(
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5
        );

        if (!NametagUtils.to2D(projected, missingItemScale.get(), false)) return null;
        return projected;
    }

}
