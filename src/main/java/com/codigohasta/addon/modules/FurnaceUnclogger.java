package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.HandledScreenInvoker;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Meteor 26.1.2 - 自动清堵熔炉（TweakerMore 点击路径版）。
 *
 * 客户端无法在远距离、未打开容器时读取熔炉真实库存，所以远处只能标记
 * “熄灭且待检查”的候选熔炉。自动清理开启时，玩家靠近后本模块会自动打开检查；
 * 自动清理关闭时，只处理玩家手动打开的熔炉。两种模式下 ESP 都持续工作。
 */
public class FurnaceUnclogger extends Module {
    private static final int INPUT_SLOT = 0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTypes = settings.createGroup("熔炉类型");
    private final SettingGroup sgTiming = settings.createGroup("操作延迟");
    private final SettingGroup sgRender = settings.createGroup("堵塞标记");

    // 常规
    private final Setting<Boolean> autoClean = sgGeneral.add(new BoolSetting.Builder()
        .name("自动清理")
        .description("开启：主动扫描并自动打开附近熔炉清堵；关闭：不再主动打开，只在你手动打开熔炉时清堵。渲染标记不受此开关影响。")
        .defaultValue(true)
        .build()
    );

    private final Setting<CleanMode> cleanMode = sgGeneral.add(new EnumSetting.Builder<CleanMode>()
        .name("清理方式")
        .description("堵塞物取出后放入背包，或者直接丢到地上。")
        .defaultValue(CleanMode.Inventory)
        .build()
    );

    private final Setting<Boolean> probeOnlyUnlit = sgGeneral.add(new BoolSetting.Builder()
        .name("只检查熄灭熔炉")
        .description("只自动打开没有燃烧的熔炉，可明显减少大型熔炉组中的界面闪烁。")
        .defaultValue(true)
        .visible(autoClean::get)
        .build()
    );

    private final Setting<Double> interactRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("自动交互距离")
        .description("走到这个距离内时自动打开待检查熔炉。")
        .defaultValue(4.5)
        .range(1.0, 6.0)
        .sliderRange(1.0, 6.0)
        .visible(autoClean::get)
        .build()
    );

    private final Setting<Double> scanRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("扫描距离")
        .description("扫描客户端已经加载的熔炉，用于自动检查和远距离标记。")
        .defaultValue(128.0)
        .range(16.0, 256.0)
        .sliderRange(16.0, 256.0)
        .build()
    );

    private final Setting<Boolean> operationMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("操作提示")
        .description("清理成功或失败时在聊天栏显示简短提示。")
        .defaultValue(false)
        .build()
    );

    // 熔炉类型
    private final Setting<Boolean> normalFurnace = sgTypes.add(new BoolSetting.Builder()
        .name("普通熔炉")
        .description("检查普通熔炉。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blastFurnace = sgTypes.add(new BoolSetting.Builder()
        .name("高炉")
        .description("检查高炉。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smoker = sgTypes.add(new BoolSetting.Builder()
        .name("烟熏炉")
        .description("检查烟熏炉。")
        .defaultValue(true)
        .build()
    );

    // 操作延迟
    private final Setting<Integer> scanInterval = sgTiming.add(new IntSetting.Builder()
        .name("扫描间隔")
        .description("每隔多少 Tick 更新一次已经加载的熔炉列表。")
        .defaultValue(10)
        .range(1, 100)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> recheckDelay = sgTiming.add(new IntSetting.Builder()
        .name("重复检查间隔")
        .description("一个熔炉检查完成后，至少等待多少 Tick 才再次检查。")
        .defaultValue(600)
        .range(20, 7200)
        .sliderRange(20, 2400)
        .build()
    );

    private final Setting<Integer> openTimeout = sgTiming.add(new IntSetting.Builder()
        .name("打开超时")
        .description("与熔炉交互后，最多等待多少 Tick 等待服务端打开界面。")
        .defaultValue(20)
        .range(5, 100)
        .sliderRange(5, 60)
        .build()
    );

    private final Setting<Integer> inspectDelay = sgTiming.add(new IntSetting.Builder()
        .name("读取等待")
        .description("熔炉界面打开后等待多少 Tick 再读取输入槽，避免库存尚未同步。")
        .defaultValue(3)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> actionDelay = sgTiming.add(new IntSetting.Builder()
        .name("点击间隔")
        .description("自动拿起、放入背包或丢弃之间等待的 Tick。延迟较高的服务器建议 2~4。")
        .defaultValue(2)
        .range(1, 20)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> maxRetries = sgTiming.add(new IntSetting.Builder()
        .name("失败重试次数")
        .description("服务端没有接受取物操作时最多重试几次。")
        .defaultValue(4)
        .range(1, 10)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> closeDelay = sgTiming.add(new IntSetting.Builder()
        .name("关闭等待")
        .description("确认操作完成后等待多少 Tick 再关闭熔炉界面。")
        .defaultValue(2)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    // 渲染
    private final Setting<Boolean> renderConfirmed = sgRender.add(new BoolSetting.Builder()
        .name("标记确认堵塞")
        .description("用红框标记已经打开并确认仍然堵塞的熔炉。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderSuspected = sgRender.add(new BoolSetting.Builder()
        .name("标记待检查熔炉")
        .description("用橙框标记熄灭且等待检查的熔炉。橙框只是候选，不代表一定堵塞。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> renderDistance = sgRender.add(new DoubleSetting.Builder()
        .name("标记距离")
        .description("最远显示多少格内已经加载的熔炉标记。")
        .defaultValue(128.0)
        .range(8.0, 256.0)
        .sliderRange(8.0, 256.0)
        .build()
    );

    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
        .name("渲染模式")
        .description("标记框显示填充、边框或两者。")
        .defaultValue(RenderMode.Both)
        .build()
    );

    private final Setting<SettingColor> confirmedSideColor = sgRender.add(new ColorSetting.Builder()
        .name("堵塞填充颜色")
        .description("确认堵塞熔炉的填充颜色。")
        .defaultValue(new SettingColor(255, 40, 40, 45))
        .visible(renderConfirmed::get)
        .build()
    );

    private final Setting<SettingColor> confirmedLineColor = sgRender.add(new ColorSetting.Builder()
        .name("堵塞边框颜色")
        .description("确认堵塞熔炉的边框颜色。")
        .defaultValue(new SettingColor(255, 40, 40, 255))
        .visible(renderConfirmed::get)
        .build()
    );

    private final Setting<SettingColor> suspectedSideColor = sgRender.add(new ColorSetting.Builder()
        .name("待检查填充颜色")
        .description("熄灭待检查熔炉的填充颜色。")
        .defaultValue(new SettingColor(255, 150, 30, 25))
        .visible(renderSuspected::get)
        .build()
    );

    private final Setting<SettingColor> suspectedLineColor = sgRender.add(new ColorSetting.Builder()
        .name("待检查边框颜色")
        .description("熄灭待检查熔炉的边框颜色。")
        .defaultValue(new SettingColor(255, 150, 30, 210))
        .visible(renderSuspected::get)
        .build()
    );

    private final Set<BlockPos> loadedFurnaces = new HashSet<>();
    private final Set<BlockPos> confirmedClogged = new HashSet<>();
    private final Map<BlockPos, Long> nextProbeTick = new HashMap<>();

    private ClientLevel trackedWorld;
    private BlockPos target;
    private BlockPos manualOpenCandidate;
    private long manualOpenCandidateUntil;
    private boolean targetOpenedManually;
    private boolean openingAutomatically;
    private Stage stage = Stage.Idle;
    private long ticks;
    private int timer;
    private int cleanAttempts;

    public FurnaceUnclogger() {
        super(
            AddonTemplate.CATEGORY,
            "熔炉清道夫",
            "自动检查附近熔炉，把堵在输入槽中的不可烧炼物品取出，并穿墙标记异常熔炉。多用于熔炉组正在冶炼物品时候使用，掏炉子",
            "furnace-unclogger",
            "furnaceunclogger"
        );
    }

    @Override
    public void onActivate() {
        resetAll();
        trackedWorld = mc.level;
    }

    @Override
    public void onDeactivate() {
        closeOwnedFurnaceScreen();
        resetAll();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (trackedWorld != mc.level) {
            resetAll();
            trackedWorld = mc.level;
        }

        ticks++;
        if (ticks % scanInterval.get() == 0) scanLoadedFurnaces();

        switch (stage) {
            case Idle -> tickIdle();
            case WaitingForScreen -> tickWaitingForScreen();
            case Inspecting -> tickInspecting();
            case PickingUp -> tickPickingUp();
            case Verifying -> tickVerifying();
            case Closing -> tickClosing();
        }
    }


    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (mc.player == null || mc.level == null || openingAutomatically) return;

        BlockPos pos = event.result.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!isSupported(state)) return;

        // 记录玩家真正右键过的熔炉。GUI 通常在随后 1~数 Tick 内打开。
        manualOpenCandidate = pos.immutable();
        manualOpenCandidateUntil = ticks + Math.max(20, openTimeout.get());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        double maxDistanceSq = renderDistance.get() * renderDistance.get();

        if (renderSuspected.get()) {
            for (BlockPos pos : loadedFurnaces) {
                if (confirmedClogged.contains(pos)) continue;
                if (distanceSq(pos) > maxDistanceSq) continue;
                if (ticks < nextProbeTick.getOrDefault(pos, 0L)) continue;

                BlockState state = mc.level.getBlockState(pos);
                if (!isSupported(state) || isLit(state)) continue;

                event.renderer.box(pos, suspectedSideColor.get(), suspectedLineColor.get(), renderMode.get().shapeMode, 0);
            }
        }

        if (renderConfirmed.get()) {
            for (BlockPos pos : confirmedClogged) {
                if (distanceSq(pos) > maxDistanceSq) continue;
                event.renderer.box(pos, confirmedSideColor.get(), confirmedLineColor.get(), renderMode.get().shapeMode, 0);
            }
        }
    }

    @Override
    public String getInfoString() {
        return Integer.toString(confirmedClogged.size());
    }

    private void tickIdle() {
        // 优先接管“玩家手动打开”的熔炉。这个逻辑无论自动清理开关是否开启都有效。
        AbstractFurnaceMenu openedHandler = currentAnyFurnaceHandler();
        if (openedHandler != null && manualOpenCandidate != null && ticks <= manualOpenCandidateUntil) {
            BlockState state = mc.level.getBlockState(manualOpenCandidate);
            if (handlerMatchesState(openedHandler, state)) {
                target = manualOpenCandidate;
                manualOpenCandidate = null;
                manualOpenCandidateUntil = 0;
                targetOpenedManually = true;
                cleanAttempts = 0;
                stage = Stage.Inspecting;
                timer = inspectDelay.get();
                return;
            }
        }

        // 手动候选超时后清理，避免之后误绑定到别的熔炉 GUI。
        if (manualOpenCandidate != null && ticks > manualOpenCandidateUntil) {
            manualOpenCandidate = null;
            manualOpenCandidateUntil = 0;
        }

        // 玩家正在使用其它 GUI 时绝不抢占。
        if (mc.screen != null) return;

        // 关闭“自动清理”后，到这里就停止；ESP 扫描/渲染仍由其它逻辑继续运行。
        if (!autoClean.get()) return;

        BlockPos best = findBestTarget();
        if (best == null) return;

        target = best;
        targetOpenedManually = false;
        cleanAttempts = 0;

        if (!openTarget()) {
            nextProbeTick.put(target, ticks + 40);
            resetTarget();
            return;
        }

        stage = Stage.WaitingForScreen;
        timer = openTimeout.get();
    }

    private void tickWaitingForScreen() {
        if (currentFurnaceHandler() != null) {
            stage = Stage.Inspecting;
            timer = inspectDelay.get();
            return;
        }

        // 出现了不是本模块打开的 GUI，就立刻放弃，不碰玩家界面。
        if (mc.screen != null) {
            nextProbeTick.put(target, ticks + 40);
            resetTarget();
            return;
        }

        if (--timer <= 0) {
            nextProbeTick.put(target, ticks + 40);
            resetTarget();
        }
    }

    private void tickInspecting() {
        AbstractFurnaceMenu handler = currentFurnaceHandler();
        if (handler == null) {
            abortTarget();
            return;
        }

        if (timer-- > 0) return;

        // 正常情况下打开容器时光标应为空。若不为空，为安全起见不自动操作。
        if (!handler.getCarried().isEmpty()) {
            if (operationMessages.get()) warning("光标上有物品，本次跳过自动清理。位置：%s", formatPos(target));
            nextProbeTick.put(target, ticks + 40);
            startClosing();
            return;
        }

        ItemStack input = handler.getSlot(INPUT_SLOT).getItem();
        if (input.isEmpty() || isSmeltable(input)) {
            confirmedClogged.remove(target);
            nextProbeTick.put(target, ticks + recheckDelay.get());

            // 玩家手动打开正常熔炉时，不替玩家关 GUI。
            if (targetOpenedManually) {
                resetTargetWithoutClosing();
            } else {
                startClosing();
            }
            return;
        }

        confirmedClogged.add(target);

        // autoClean 只控制“是否主动开炉”。既然熔炉已经打开，无论自动/手动模式都执行清堵。
        cleanAttempts = 0;
        stage = Stage.PickingUp;
        timer = actionDelay.get();
    }

    /**
     * 按 TweakerMore + Item Scroller 的实现路径处理：
     * 直接对“容器原槽位”调用 AbstractContainerScreen#slotClicked(...)。
     *
     * 丢弃整组：button = 1, ContainerInput.THROW
     * 放入背包：button = 0, ContainerInput.QUICK_MOVE
     *
     * 不再先把物品拿到鼠标光标，也不点击 GUI 外部。
     */
    private void tickPickingUp() {
        AbstractFurnaceMenu handler = currentFurnaceHandler();
        if (handler == null) {
            abortTarget();
            return;
        }

        if (timer-- > 0) return;

        ItemStack input = handler.getSlot(INPUT_SLOT).getItem();

        // 已经清掉或已经变成可烧炼物品。
        if (input.isEmpty() || isSmeltable(input)) {
            finishSuccessfulClean();
            return;
        }

        if (cleanAttempts >= maxRetries.get()) {
            failClean("多次尝试后服务端仍未接受清理操作");
            return;
        }

        if (!clickInputSlotLikeTweakerMore(handler)) {
            failClean("无法调用熔炉界面的原生槽位点击");
            return;
        }

        cleanAttempts++;
        stage = Stage.Verifying;
        timer = actionDelay.get();
    }

    /**
     * TweakerMore 的 ContainerCleaner 最终调用 Item Scroller 的 dropStack：
     * 对目标 Slot 调用容器 GUI 的 slotClicked，
     * THROW 时 mouseButton=1 代表整组丢弃。
     *
     * 这里通过 Mixin Invoker 调用 26.1.2 的
     * AbstractContainerScreen#slotClicked(Slot, int, int, ContainerInput)，
     * 与它的 GUI 原生路径一致。
     */
    private boolean clickInputSlotLikeTweakerMore(AbstractFurnaceMenu handler) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return false;

        Slot slot = handler.getSlot(INPUT_SLOT);
        if (slot == null || slot.getItem().isEmpty()) return false;

        ContainerInput actionType;
        int button;

        if (cleanMode.get() == CleanMode.Drop) {
            actionType = ContainerInput.THROW;
            button = 1; // 整组丢弃，与 Item Scroller dropStack 完全一致
        } else {
            actionType = ContainerInput.QUICK_MOVE;
            button = 0; // Shift+左键，将物品移入玩家背包
        }

        ((HandledScreenInvoker) (Object) screen).codigohasta$invokeOnMouseClick(
            slot,
            slot.index,
            button,
            actionType
        );

        return true;
    }

    /**
     * 等待 GUI / 服务端同步后检查输入槽。
     * 只有确认输入槽已改变/清空才关闭，不再基于鼠标光标判断。
     */
    private void tickVerifying() {
        AbstractFurnaceMenu handler = currentFurnaceHandler();
        if (handler == null) {
            abortTarget();
            return;
        }

        if (timer-- > 0) return;

        ItemStack input = handler.getSlot(INPUT_SLOT).getItem();

        if (input.isEmpty() || isSmeltable(input)) {
            finishSuccessfulClean();
            return;
        }

        // QUICK_MOVE 在背包空间不足时可能只移动一部分。
        // THROW 正常情况下会一次清空；如果服务器/反作弊拒绝，则重试。
        if (cleanAttempts < maxRetries.get()) {
            stage = Stage.PickingUp;
            timer = actionDelay.get();
            return;
        }

        failClean(
            cleanMode.get() == CleanMode.Drop
                ? "整组丢弃未被服务端接受"
                : "无法把堵塞物完整移入背包，可能背包已满或服务端拒绝操作"
        );
    }

    private void tickClosing() {
        if (timer-- > 0) return;

        AbstractFurnaceMenu handler = currentFurnaceHandler();
        if (handler != null && !handler.getCarried().isEmpty()) {
            // 上一步放回/放入背包还没同步，继续给服务端时间。
            timer = actionDelay.get();
            return;
        }

        closeOwnedFurnaceScreen();
        resetTarget();
    }

    private void finishSuccessfulClean() {
        confirmedClogged.remove(target);
        nextProbeTick.put(target, ticks + recheckDelay.get());
        if (operationMessages.get()) info("已清理堵塞熔炉：%s", formatPos(target));
        startClosing();
    }

    private void failClean(String reason) {
        confirmedClogged.add(target);
        nextProbeTick.put(target, ticks + recheckDelay.get());
        if (operationMessages.get()) warning("%s。位置：%s", reason, formatPos(target));
        startClosing();
    }

    private void startClosing() {
        stage = Stage.Closing;
        timer = closeDelay.get();
    }

    private void abortTarget() {
        if (target != null) nextProbeTick.put(target, ticks + 40);
        resetTarget();
    }

    private AbstractFurnaceMenu currentAnyFurnaceHandler() {
        if (mc.player == null) return null;
        if (mc.player.containerMenu instanceof AbstractFurnaceMenu handler) return handler;
        return null;
    }

    private boolean handlerMatchesState(AbstractFurnaceMenu handler, BlockState state) {
        if (state.is(Blocks.FURNACE)) return handler instanceof FurnaceMenu;
        if (state.is(Blocks.BLAST_FURNACE)) return handler instanceof BlastFurnaceMenu;
        if (state.is(Blocks.SMOKER)) return handler instanceof SmokerMenu;
        return false;
    }

    private AbstractFurnaceMenu currentFurnaceHandler() {
        if (mc.player == null) return null;
        if (!(mc.player.containerMenu instanceof AbstractFurnaceMenu handler)) return null;
        if (target == null || mc.level == null) return null;

        BlockState state = mc.level.getBlockState(target);
        return handlerMatchesState(handler, state) ? handler : null;
    }

    private void closeOwnedFurnaceScreen() {
        if (mc.player == null) return;
        if (currentFurnaceHandler() != null) mc.player.closeContainer();
    }

    private boolean openTarget() {
        if (target == null || mc.level == null || mc.player == null) return false;

        BlockState state = mc.level.getBlockState(target);
        if (!isSupported(state)) return false;

        Direction side = nearestFace(target);
        Vec3 center = Vec3.atCenterOf(target);
        Vec3 hitPos = center.add(
            side.getStepX() * 0.5,
            side.getStepY() * 0.5,
            side.getStepZ() * 0.5
        );

        BlockHitResult hitResult = new BlockHitResult(hitPos, side, target, false);

        boolean wasSneaking = mc.player.isShiftKeyDown();
        mc.player.setShiftKeyDown(false);
        InteractionResult result;
        openingAutomatically = true;
        try {
            result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        } finally {
            openingAutomatically = false;
            mc.player.setShiftKeyDown(wasSneaking);
        }

        if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);
        return result.consumesAction();
    }

    private BlockPos findBestTarget() {
        double maxDistanceSq = interactRange.get() * interactRange.get();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockPos pos : loadedFurnaces) {
            if (ticks < nextProbeTick.getOrDefault(pos, 0L)) continue;

            double distanceSq = eyeDistanceSq(pos);
            if (distanceSq > maxDistanceSq) continue;

            BlockState state = mc.level.getBlockState(pos);
            if (!isSupported(state)) continue;

            boolean confirmed = confirmedClogged.contains(pos);
            if (!confirmed && probeOnlyUnlit.get() && isLit(state)) continue;

            // 已确认仍堵塞的熔炉优先处理。
            double score = confirmed ? distanceSq - 10_000.0 : distanceSq;
            if (score < bestScore) {
                bestScore = score;
                best = pos;
            }
        }

        return best;
    }

    private void scanLoadedFurnaces() {
        loadedFurnaces.clear();
        double maxDistanceSq = scanRange.get() * scanRange.get();

        for (BlockEntity blockEntity : Utils.blockEntities()) {
            if (!(blockEntity instanceof AbstractFurnaceBlockEntity)) continue;

            BlockPos pos = blockEntity.getBlockPos();
            if (distanceSq(pos) > maxDistanceSq) continue;

            BlockState state = blockEntity.getBlockState();
            if (!isSupported(state)) continue;

            loadedFurnaces.add(pos.immutable());
        }

        confirmedClogged.removeIf(pos ->
            mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && !isSupported(mc.level.getBlockState(pos))
        );
    }

    private boolean isSupported(BlockState state) {
        if (state.is(Blocks.FURNACE)) return normalFurnace.get();
        if (state.is(Blocks.BLAST_FURNACE)) return blastFurnace.get();
        if (state.is(Blocks.SMOKER)) return smoker.get();
        return false;
    }

    private boolean isLit(BlockState state) {
        return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
    }

    private boolean isSmeltable(ItemStack stack) {
        if (stack.isEmpty() || target == null || mc.level == null) return false;

        ResourceKey<RecipePropertySet> key = recipePropertySetKey(mc.level.getBlockState(target));
        if (key == null) return false;

        return mc.level.recipeAccess().propertySet(key).test(stack);
    }

    private ResourceKey<RecipePropertySet> recipePropertySetKey(BlockState state) {
        if (state.is(Blocks.FURNACE)) return RecipePropertySet.FURNACE_INPUT;
        if (state.is(Blocks.BLAST_FURNACE)) return RecipePropertySet.BLAST_FURNACE_INPUT;
        if (state.is(Blocks.SMOKER)) return RecipePropertySet.SMOKER_INPUT;
        return null;
    }

    private Direction nearestFace(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 eye = mc.player.getEyePosition();

        double dx = eye.x - center.x;
        double dy = eye.y - center.y;
        double dz = eye.z - center.z;
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);

        if (ax >= ay && ax >= az) return dx >= 0 ? Direction.EAST : Direction.WEST;
        if (ay >= ax && ay >= az) return dy >= 0 ? Direction.UP : Direction.DOWN;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private double distanceSq(BlockPos pos) {
        double dx = (pos.getX() + 0.5) - mc.player.getX();
        double dy = (pos.getY() + 0.5) - mc.player.getY();
        double dz = (pos.getZ() + 0.5) - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private double eyeDistanceSq(BlockPos pos) {
        Vec3 eye = mc.player.getEyePosition();
        double dx = (pos.getX() + 0.5) - eye.x;
        double dy = (pos.getY() + 0.5) - eye.y;
        double dz = (pos.getZ() + 0.5) - eye.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private String formatPos(BlockPos pos) {
        if (pos == null) return "未知";
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private void resetTarget() {
        target = null;
        targetOpenedManually = false;
        stage = Stage.Idle;
        timer = 0;
        cleanAttempts = 0;
    }

    private void resetTargetWithoutClosing() {
        target = null;
        targetOpenedManually = false;
        stage = Stage.Idle;
        timer = 0;
        cleanAttempts = 0;
    }

    private void resetAll() {
        loadedFurnaces.clear();
        confirmedClogged.clear();
        nextProbeTick.clear();
        target = null;
        manualOpenCandidate = null;
        manualOpenCandidateUntil = 0;
        targetOpenedManually = false;
        openingAutomatically = false;
        stage = Stage.Idle;
        ticks = 0;
        timer = 0;
        cleanAttempts = 0;
    }

    public enum CleanMode {
        Inventory("放入背包"),
        Drop("丢到地上");

        private final String title;

        CleanMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }


    public enum RenderMode {
        Both("填充和边框", ShapeMode.Both),
        Lines("仅边框", ShapeMode.Lines),
        Sides("仅填充", ShapeMode.Sides);

        private final String title;
        private final ShapeMode shapeMode;

        RenderMode(String title, ShapeMode shapeMode) {
            this.title = title;
            this.shapeMode = shapeMode;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private enum Stage {
        Idle,
        WaitingForScreen,
        Inspecting,
        PickingUp,
        Verifying,
        Closing
    }
}
