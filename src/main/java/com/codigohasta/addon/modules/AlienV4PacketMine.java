package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import com.codigohasta.addon.utils.alien.*;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AlienV4PacketMine extends Module {

    public static AlienV4PacketMine INSTANCE;
    public static BlockPos secondPos;
    public static double progress = 0.0;
    public static boolean ghost = false;
    public static boolean complete = false;

    private final AlienFadeUtils animationTime = new AlienFadeUtils(1000L);
    private final AlienFadeUtils secondAnim = new AlienFadeUtils(1000L);
    private final DecimalFormat df = new DecimalFormat("0.0");
    private final AlienTimer mineTimer = new AlienTimer();
    private final AlienTimer sync = new AlienTimer();
    private final AlienTimer secondTimer = new AlienTimer();
    private final AlienTimer delayTimer = new AlienTimer();
    private final AlienTimer placeTimer = new AlienTimer();
    private final AlienTimer startTime = new AlienTimer();

    int lastSlot = -1;
    Vec3d directionVec = null;
    Runnable switchBack;
    BlockPos breakPos;
    boolean startPacket = false;
    int breakNumber = 0;
    double breakFinalTime;
    double secondFinalTime;
    boolean sendGroundPacket = false;
    boolean swapped = false;
    int mainSlot = 0;

    // ─── Settings Pages ───
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCheck = settings.createGroup("Check");
    private final SettingGroup sgRotation = settings.createGroup("Rotation");
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Page> page = sgGeneral.add(new EnumSetting.Builder<Page>()
        .name("Page").description("Settings page").defaultValue(Page.General).build());

    // ─── General Settings ───
    private final Setting<Double> stopDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("StopDelay").description("Delay before stopping").defaultValue(50.0).min(0.0).max(500.0).sliderRange(0, 500)
        .visible(() -> page.get() == Page.General).build());
    private final Setting<Double> startDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("StartDelay").description("Delay before starting").defaultValue(200.0).min(0.0).max(500.0).sliderRange(0, 500)
        .visible(() -> page.get() == Page.General).build());
    private final Setting<Double> damage = sgGeneral.add(new DoubleSetting.Builder()
        .name("Damage").description("Mine speed multiplier").defaultValue(0.7).min(0.0).max(2.0).sliderRange(0, 2)
        .visible(() -> page.get() == Page.General).build());
    private final Setting<Integer> maxBreak = sgGeneral.add(new IntSetting.Builder()
        .name("MaxBreak").description("Max break count before stopping").defaultValue(3).min(0).max(20).sliderRange(0, 20)
        .visible(() -> page.get() == Page.General).build());
    public final Setting<Boolean> noGhostHand = sgGeneral.add(new BoolSetting.Builder()
        .name("1.21").description("1.21 mode (no ghost hand)").defaultValue(false)
        .visible(() -> page.get() == Page.General).build());
    public final Setting<Boolean> noCollide = sgGeneral.add(new BoolSetting.Builder()
        .name("NoCollide").description("No collision when ghost").defaultValue(true)
        .visible(() -> page.get() == Page.General).build());
    private final Setting<TimingMode> timing = sgGeneral.add(new EnumSetting.Builder<TimingMode>()
        .name("Timing").description("Tick timing").defaultValue(TimingMode.All)
        .visible(() -> page.get() == Page.General).build());
    private final Setting<Boolean> grimDisabler = sgGeneral.add(new BoolSetting.Builder()
        .name("GrimDisabler").description("Grim anticheat bypass").defaultValue(false)
        .visible(() -> page.get() == Page.General).build());
    private final Setting<Boolean> instant = sgGeneral.add(new BoolSetting.Builder()
        .name("Instant").description("Instant break mode").defaultValue(false)
        .visible(() -> page.get() == Page.General).build());
    private final Setting<Boolean> wait = sgGeneral.add(new BoolSetting.Builder()
        .name("Wait").description("Wait for block break confirmation").defaultValue(true)
        .visible(() -> !instant.get() && page.get() == Page.General).build());
    private final Setting<Boolean> mineAir = sgGeneral.add(new BoolSetting.Builder()
        .name("MineAir").description("Allow mining air blocks").defaultValue(true)
        .visible(() -> wait.get() && !instant.get() && page.get() == Page.General).build());
    private final Setting<Boolean> hotBar = sgGeneral.add(new BoolSetting.Builder()
        .name("HotbarSwap").description("Only swap in hotbar").defaultValue(false)
        .visible(() -> page.get() == Page.General).build());
    private final Setting<Boolean> doubleBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("DoubleBreak").description("Break two blocks at once").defaultValue(true)
        .visible(() -> page.get() == Page.General).build());
    public final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("AutoSwitch").description("Auto switch tools for double break").defaultValue(true)
        .visible(() -> page.get() == Page.General && doubleBreak.get()).build());
    private final Setting<Double> start = sgGeneral.add(new DoubleSetting.Builder()
        .name("Start").description("Double break start threshold").defaultValue(0.9).min(0.0).max(2.0).sliderRange(0, 2)
        .visible(() -> page.get() == Page.General && doubleBreak.get()).build());
    private final Setting<Double> timeOut = sgGeneral.add(new DoubleSetting.Builder()
        .name("TimeOut").description("Double break timeout multiplier").defaultValue(1.2).min(0.0).max(2.0).sliderRange(0, 2)
        .visible(() -> page.get() == Page.General && doubleBreak.get()).build());
    private final Setting<Boolean> setAir = sgGeneral.add(new BoolSetting.Builder()
        .name("SetAir").description("Set air client-side after break").defaultValue(false)
        .visible(() -> page.get() == Page.General).build());
    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("Swing").description("Swing when starting mining").defaultValue(true)
        .visible(() -> page.get() == Page.General).build());
    private final Setting<Boolean> endSwing = sgGeneral.add(new BoolSetting.Builder()
        .name("EndSwing").description("Swing when finishing mining").defaultValue(false)
        .visible(() -> page.get() == Page.General).build());
    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("Range").description("Mine reach distance").defaultValue(6.0).min(3.0).max(10.0).sliderRange(3, 10)
        .visible(() -> page.get() == Page.General).build());
    private final Setting<SwingHandMode> swingMode = sgGeneral.add(new EnumSetting.Builder<SwingHandMode>()
        .name("SwingMode").description("Swing hand mode").defaultValue(SwingHandMode.All)
        .visible(() -> page.get() == Page.General).build());

    // ─── Check Settings ───
    private final Setting<Boolean> unbreakableCancel = sgCheck.add(new BoolSetting.Builder()
        .name("UnbreakableCancel").description("Cancel mining unbreakable blocks").defaultValue(true)
        .visible(() -> page.get() == Page.Check).build());
    private final Setting<Boolean> switchReset = sgCheck.add(new BoolSetting.Builder()
        .name("SwitchReset").description("Reset on tool switch").defaultValue(false)
        .visible(() -> page.get() == Page.Check).build());
    private final Setting<Boolean> preferWeb = sgCheck.add(new BoolSetting.Builder()
        .name("PreferWeb").description("Prefer mining webs").defaultValue(true)
        .visible(() -> page.get() == Page.Check).build());
    private final Setting<Boolean> preferHead = sgCheck.add(new BoolSetting.Builder()
        .name("PreferHead").description("Prefer head-level blocks").defaultValue(true)
        .visible(() -> page.get() == Page.Check).build());
    private final Setting<Boolean> farCancel = sgCheck.add(new BoolSetting.Builder()
        .name("FarCancel").description("Cancel if too far").defaultValue(false)
        .visible(() -> page.get() == Page.Check).build());
    private final Setting<Boolean> onlyGround = sgCheck.add(new BoolSetting.Builder()
        .name("OnlyGround").description("Only mine when on ground").defaultValue(true)
        .visible(() -> page.get() == Page.Check).build());
    private final Setting<Boolean> checkWeb = sgCheck.add(new BoolSetting.Builder()
        .name("CheckWeb").description("Check web slowdown").defaultValue(true)
        .visible(() -> page.get() == Page.Check).build());
    private final Setting<Boolean> checkGround = sgCheck.add(new BoolSetting.Builder()
        .name("CheckGround").description("Check ground slowdown").defaultValue(true)
        .visible(() -> page.get() == Page.Check).build());
    private final Setting<Boolean> smart = sgCheck.add(new BoolSetting.Builder()
        .name("Smart").description("Smart ground check").defaultValue(true)
        .visible(() -> page.get() == Page.Check && checkGround.get()).build());
    private final Setting<Boolean> usingPause = sgCheck.add(new BoolSetting.Builder()
        .name("UsingPause").description("Pause when using items").defaultValue(false)
        .visible(() -> page.get() == Page.Check).build());
    private final Setting<Boolean> allowOffhand = sgCheck.add(new BoolSetting.Builder()
        .name("AllowOffhand").description("Allow offhand use").defaultValue(true)
        .visible(() -> page.get() == Page.Check && usingPause.get()).build());
    private final Setting<Boolean> bypassGround = sgCheck.add(new BoolSetting.Builder()
        .name("BypassGround").description("Bypass ground slowdown").defaultValue(true)
        .visible(() -> page.get() == Page.Check).build());
    private final Setting<Integer> bypassTime = sgCheck.add(new IntSetting.Builder()
        .name("BypassTime").description("Bypass time in ms").defaultValue(400).min(0).max(2000).sliderRange(0, 2000)
        .visible(() -> bypassGround.get() && page.get() == Page.Check).build());
    private final Setting<Boolean> pauseBind = sgCheck.add(new BoolSetting.Builder()
        .name("Pause").description("Pause mining").defaultValue(false)
        .visible(() -> page.get() == Page.Check).build());

    // ─── Rotation Settings ───
    private final Setting<Boolean> rotate = sgRotation.add(new BoolSetting.Builder()
        .name("StartRotate").description("Rotate when starting").defaultValue(true)
        .visible(() -> page.get() == Page.Rotation).build());
    private final Setting<Boolean> endRotate = sgRotation.add(new BoolSetting.Builder()
        .name("EndRotate").description("Rotate when ending").defaultValue(false)
        .visible(() -> page.get() == Page.Rotation).build());
    private final Setting<Integer> syncTime = sgRotation.add(new IntSetting.Builder()
        .name("Sync").description("Rotation sync time").defaultValue(300).min(0).max(1000).sliderRange(0, 1000)
        .visible(() -> page.get() == Page.Rotation).build());
    private final Setting<Boolean> yawStep = sgRotation.add(new BoolSetting.Builder()
        .name("YawStep").description("Step rotation gradually").defaultValue(false)
        .visible(() -> page.get() == Page.Rotation).build());
    private final Setting<Boolean> whenElytra = sgRotation.add(new BoolSetting.Builder()
        .name("FallFlying").description("Yaw step while flying").defaultValue(true)
        .visible(() -> page.get() == Page.Rotation && yawStep.get()).build());
    private final Setting<Double> steps = sgRotation.add(new DoubleSetting.Builder()
        .name("Steps").description("Yaw step size").defaultValue(0.05).min(0.0).max(1.0).sliderRange(0, 1)
        .visible(() -> page.get() == Page.Rotation && yawStep.get()).build());
    private final Setting<Boolean> checkFov = sgRotation.add(new BoolSetting.Builder()
        .name("OnlyLooking").description("Only rotate if not looking").defaultValue(true)
        .visible(() -> page.get() == Page.Rotation && yawStep.get()).build());
    private final Setting<Double> fov = sgRotation.add(new DoubleSetting.Builder()
        .name("Fov").description("Field of view check").defaultValue(20.0).min(0.0).max(360.0).sliderRange(0, 360)
        .visible(() -> page.get() == Page.Rotation && yawStep.get()).build());
    private final Setting<Integer> priority = sgRotation.add(new IntSetting.Builder()
        .name("Priority").description("Rotation priority").defaultValue(10).min(0).max(100).sliderRange(0, 100)
        .visible(() -> page.get() == Page.Rotation && yawStep.get()).build());

    // ─── Place Settings ───
    private final Setting<Boolean> crystal = sgPlace.add(new BoolSetting.Builder()
        .name("Crystal").description("Place crystal after break").defaultValue(false)
        .visible(() -> page.get() == Page.Place).build());
    private final Setting<Boolean> onlyHeadBomber = sgPlace.add(new BoolSetting.Builder()
        .name("OnlyCev").description("Only place for cev breaker").defaultValue(true)
        .visible(() -> page.get() == Page.Place && crystal.get()).build());
    private final Setting<Boolean> waitPlace = sgPlace.add(new BoolSetting.Builder()
        .name("WaitPlace").description("Wait for place confirmation").defaultValue(true)
        .visible(() -> page.get() == Page.Place && crystal.get()).build());
    private final Setting<Boolean> spamPlace = sgPlace.add(new BoolSetting.Builder()
        .name("SpamPlace").description("Spam place crystals").defaultValue(false)
        .visible(() -> page.get() == Page.Place && crystal.get()).build());
    private final Setting<Boolean> afterBreak = sgPlace.add(new BoolSetting.Builder()
        .name("AfterBreak").description("Place crystal after break").defaultValue(true)
        .visible(() -> page.get() == Page.Place && crystal.get()).build());
    private final Setting<Boolean> checkDamage = sgPlace.add(new BoolSetting.Builder()
        .name("DetectProgress").description("Check progress before placing").defaultValue(true)
        .visible(() -> page.get() == Page.Place && crystal.get()).build());
    private final Setting<Double> crystalDamage = sgPlace.add(new DoubleSetting.Builder()
        .name("Progress").description("Progress threshold for crystal").defaultValue(0.9).min(0.0).max(1.0).sliderRange(0, 1)
        .visible(() -> page.get() == Page.Place && crystal.get() && checkDamage.get()).build());
    private final Setting<Boolean> obsidian = sgPlace.add(new BoolSetting.Builder()
        .name("Obsidian").description("Place obsidian after break").defaultValue(false)
        .visible(() -> page.get() == Page.Place).build());
    private final Setting<Boolean> enderChest = sgPlace.add(new BoolSetting.Builder()
        .name("EnderChest").description("Place ender chest after break").defaultValue(false)
        .visible(() -> page.get() == Page.Place).build());
    private final Setting<Boolean> placeRotate = sgPlace.add(new BoolSetting.Builder()
        .name("PlaceRotate").description("Rotate when placing").defaultValue(true)
        .visible(() -> page.get() == Page.Place).build());
    private final Setting<Boolean> inventory = sgPlace.add(new BoolSetting.Builder()
        .name("InventorySwap").description("Use inventory swap").defaultValue(true)
        .visible(() -> page.get() == Page.Place).build());
    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("PlaceDelay").description("Delay between placements").defaultValue(100).min(0).max(1000).sliderRange(0, 1000)
        .visible(() -> page.get() == Page.Place).build());

    // ─── Render Settings ───
    private final Setting<Boolean> checkDouble = sgRender.add(new BoolSetting.Builder()
        .name("CheckDouble").description("Check double break render").defaultValue(false)
        .visible(() -> page.get() == Page.Render).build());
    private final Setting<AnimMode> animation = sgRender.add(new EnumSetting.Builder<AnimMode>()
        .name("Animation").description("Animation type").defaultValue(AnimMode.Up)
        .visible(() -> page.get() == Page.Render).build());
    private final Setting<AlienEasing> ease = sgRender.add(new EnumSetting.Builder<AlienEasing>()
        .name("Ease").description("Easing function").defaultValue(AlienEasing.CubicInOut)
        .visible(() -> page.get() == Page.Render).build());
    private final Setting<AlienEasing> fadeEase = sgRender.add(new EnumSetting.Builder<AlienEasing>()
        .name("FadeEase").description("Fade easing function").defaultValue(AlienEasing.CubicInOut)
        .visible(() -> page.get() == Page.Render).build());
    private final Setting<Double> expandLine = sgRender.add(new DoubleSetting.Builder()
        .name("ExpandLine").description("Expand outline").defaultValue(0.0).min(0.0).max(1.0).sliderRange(0, 1)
        .visible(() -> page.get() == Page.Render).build());
    private final Setting<SettingColor> startColor = sgRender.add(new ColorSetting.Builder()
        .name("StartFill").description("Start fill color").defaultValue(new SettingColor(255, 255, 255, 100))
        .visible(() -> page.get() == Page.Render).build());
    private final Setting<SettingColor> startOutlineColor = sgRender.add(new ColorSetting.Builder()
        .name("StartOutline").description("Start outline color").defaultValue(new SettingColor(255, 255, 255, 100))
        .visible(() -> page.get() == Page.Render).build());
    private final Setting<SettingColor> endColor = sgRender.add(new ColorSetting.Builder()
        .name("EndFill").description("End fill color").defaultValue(new SettingColor(255, 255, 255, 100))
        .visible(() -> page.get() == Page.Render).build());
    private final Setting<SettingColor> endOutlineColor = sgRender.add(new ColorSetting.Builder()
        .name("EndOutline").description("End outline color").defaultValue(new SettingColor(255, 255, 255, 100))
        .visible(() -> page.get() == Page.Render).build());
    private final Setting<SettingColor> doubleColor = sgRender.add(new ColorSetting.Builder()
        .name("DoubleFill").description("Double break fill color").defaultValue(new SettingColor(88, 94, 255, 100))
        .visible(() -> doubleBreak.get() && page.get() == Page.Render).build());
    private final Setting<SettingColor> doubleOutlineColor = sgRender.add(new ColorSetting.Builder()
        .name("DoubleOutline").description("Double break outline color").defaultValue(new SettingColor(88, 94, 255, 100))
        .visible(() -> doubleBreak.get() && page.get() == Page.Render).build());
    private final Setting<Boolean> text = sgRender.add(new BoolSetting.Builder()
        .name("Text").description("Show progress text").defaultValue(true)
        .visible(() -> page.get() == Page.Render).build());
    private final Setting<Boolean> box = sgRender.add(new BoolSetting.Builder()
        .name("Box").description("Show fill box").defaultValue(true)
        .visible(() -> page.get() == Page.Render).build());
    private final Setting<Boolean> outline = sgRender.add(new BoolSetting.Builder()
        .name("Outline").description("Show outline").defaultValue(true)
        .visible(() -> page.get() == Page.Render).build());
    private final Setting<Double> maxTextScale = sgRender.add(new DoubleSetting.Builder()
        .name("TextMaxScale").description("最大文字缩放上限。").defaultValue(15.0).min(1.0).max(100.0).sliderRange(1, 50)
        .visible(() -> page.get() == Page.Render && text.get()).build());
    private final Setting<Double> textScaleBase = sgRender.add(new DoubleSetting.Builder()
        .name("TextScaleBase").description("文字基础缩放（距离为0时的大小）。默认5.0").defaultValue(5.0).min(0.5).max(30.0).sliderRange(0.5, 20)
        .visible(() -> page.get() == Page.Render && text.get()).build());
    private final Setting<Double> textScaleFactor = sgRender.add(new DoubleSetting.Builder()
        .name("TextScaleFactor").description("每格距离增加的缩放值。默认0.1").defaultValue(0.1).min(0.0).max(2.0).sliderRange(0, 1)
        .visible(() -> page.get() == Page.Render && text.get()).build());

    // ─── Constructor ───
    public AlienV4PacketMine() {
        super(AddonTemplate.CATEGORY, "发包挖掘V4", "AlienV4的PacketMine。移植过来的，可以玩");
        INSTANCE = this;
    }

    public static BlockPos getBreakPos() {
        return INSTANCE.isActive() ? INSTANCE.breakPos : null;
    }

    @Override
    public String getInfoString() {
        return progress >= 1.0 ? "Done" : df.format(progress * 100.0) + "%";
    }

    @Override
    public void onActivate() {
        startPacket = false;
        ghost = false;
        complete = false;
        breakPos = null;
        secondPos = null;
    }

    @Override
    public void onDeactivate() {
        startPacket = false;
        ghost = false;
        complete = false;
        breakPos = null;
        secondPos = null;
        directionVec = null;
        switchBack = null;
    }

    // ─── Rotation helpers ───

    private float[] getRotationTo(Vec3d vec) {
        double diffX = vec.x - mc.player.getX();
        double diffY = vec.y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double diffZ = vec.z - mc.player.getZ();
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));
        return new float[]{yaw, pitch};
    }

    private void lookAt(Vec3d vec) {
        float[] rot = getRotationTo(vec);
        mc.player.setYaw(rot[0]);
        mc.player.setPitch(rot[1]);
    }

    private boolean inFov(Vec3d vec, float fovDeg) {
        float[] rot = getRotationTo(vec);
        float yawDiff = MathHelper.wrapDegrees(mc.player.getYaw() - rot[0]);
        float pitchDiff = MathHelper.wrapDegrees(mc.player.getPitch() - rot[1]);
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff) <= fovDeg;
    }

    private boolean shouldYawStep() {
        if (!yawStep.get()) return false;
        if (whenElytra.get()) return true;
        return !mc.player.getPose().name().equals("GLIDING");
    }

    boolean faceVector(Vec3d directionVec) {
        if (!shouldYawStep()) {
            lookAt(directionVec);
            return true;
        } else {
            sync.reset();
            this.directionVec = directionVec;
            return inFov(directionVec, fov.get().floatValue()) || !checkFov.get();
        }
    }

    // ─── Web check ───

    private boolean isInWeb(PlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        if (mc.world.getBlockState(pos).getBlock() == Blocks.COBWEB) return true;
        if (mc.world.getBlockState(pos.up()).getBlock() == Blocks.COBWEB) return true;
        if (mc.world.getBlockState(pos.down()).getBlock() == Blocks.COBWEB) return true;
        return false;
    }

    // ─── Auto switch tool ───

    private void autoSwitch() {
        if (autoSwitch.get() && doubleBreak.get()) {
            int index = -1;
            if (secondPos != null) {
                float currentFastest = 1.0F;
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (stack != ItemStack.EMPTY) {
                        int eff = getEfficiencyLevel(stack);
                        float digSpeed = eff;
                        float destroySpeed = stack.getMiningSpeedMultiplier(mc.world.getBlockState(secondPos));
                        if (digSpeed + destroySpeed > currentFastest) {
                            currentFastest = digSpeed + destroySpeed;
                            index = i;
                        }
                    }
                }
            }
            if (index != -1
                && !mc.options.useKey.isPressed()
                && !mc.options.attackKey.isPressed()
                && !mc.player.isUsingItem()
                && secondTimer.passedMs(getBreakTime(secondPos, index, start.get()))) {
                if (index != ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot()) {
                    mainSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                    AlienInventoryUtil.switchToSlot(index);
                    swapped = true;
                }
            } else if (swapped) {
                AlienInventoryUtil.switchToSlot(mainSlot);
                swapped = false;
            }
        }
    }

    // ─── Tick Event ───

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // ---- rotation (yaw step) ----
        if (rotate.get() && shouldYawStep() && directionVec != null && !sync.passedMs(syncTime.get())) {
            lookAt(directionVec);
        }

        // ---- main tick logic ----
        if (breakPos != null && mc.world.isAir(breakPos)) {
            complete = true;
        }

        if (secondPos != null) {
            int secondSlot = getTool(secondPos);
            if (secondSlot == -1) secondSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
            secondFinalTime = getBreakTime(secondPos, secondSlot, 1.0);
            if (!isAir(secondPos) && !unbreakable(secondPos)) {
                if (secondTimer.passedMs(getBreakTime(secondPos, ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot(), 1.0) * timeOut.get())) {
                    secondPos = null;
                }
            } else {
                secondPos = null;
            }
        }

        if (switchBack != null) {
            if (AlienEntityUtil.inInventory() || hotBar.get()) {
                switchBack.run();
            } else {
                // Screen changed (e.g. merchant UI opened) — can't SWAP, just send STOP_DESTROY
                if (breakPos != null) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, breakPos, AlienBlockUtil.getClickSide(breakPos)));
                }
                breakNumber++;
                delayTimer.reset();
                startTime.reset();
            }
            switchBack = null;
        }

        if (mc.player.isDead()) {
            secondPos = null;
        }

        autoSwitch();

        if (mc.player.isCreative()) {
            startPacket = false;
            ghost = false;
            complete = false;
            breakNumber = 0;
            breakPos = null;
            progress = 0.0;
        } else if (breakPos == null) {
            breakNumber = 0;
            startPacket = false;
            ghost = false;
            complete = false;
            progress = 0.0;
        } else {
            int slot = getTool(breakPos);
            if (slot == -1) slot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
            breakFinalTime = getBreakTime(breakPos, slot);
            progress = mineTimer.getMs() / breakFinalTime;

            if (isAir(breakPos)) {
                breakNumber = 0;
            }

            boolean maxBreakReached = breakNumber > maxBreak.get() - 1 && maxBreak.get() > 0 && !complete;
            if (!maxBreakReached
                && (wait.get() || !isAir(breakPos) || instant.get())) {

                if (unbreakable(breakPos)) {
                    if (unbreakableCancel.get()) {
                        breakPos = null;
                        startPacket = false;
                        ghost = false;
                        complete = false;
                    }
                    breakNumber = 0;
                } else if (MathHelper.sqrt((float) mc.player.getEyePos().squaredDistanceTo(breakPos.toCenterPos())) > range.get()) {
                    if (farCancel.get()) {
                        startPacket = false;
                        ghost = false;
                        complete = false;
                        breakNumber = 0;
                        breakPos = null;
                    }
                } else if (!usingPause.get() || !mc.player.isUsingItem() || (allowOffhand.get() && mc.player.getActiveHand() != Hand.MAIN_HAND)) {
                    if (!pauseBind.get()) {
                        if (hotBar.get() || AlienEntityUtil.inInventory()) {
                            if (isAir(breakPos)) {
                                // ── Air block: place blocks / attack crystals ──
                                if (shouldCrystal()) {
                                    for (Direction facing : Direction.values()) {
                                        AlienCombatUtil.attackCrystal(breakPos.offset(facing), placeRotate.get(), true);
                                    }
                                }
                                if (placeTimer.passedMs(placeDelay.get()) && AlienBlockUtil.canPlace(breakPos) && mc.currentScreen == null) {
                                    if (enderChest.get()) {
                                        int eChest = AlienInventoryUtil.findBlock(Blocks.ENDER_CHEST);
                                        if (eChest != -1) {
                                            int oldSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                                            doSwap(eChest, eChest);
                                            AlienBlockUtil.placeBlock(breakPos, placeRotate.get(), true);
                                            doSwap(oldSlot, eChest);
                                            placeTimer.reset();
                                        }
                                    } else if (obsidian.get()) {
                                        int obby = AlienInventoryUtil.findBlock(Blocks.OBSIDIAN);
                                        if (obby != -1) {
                                            boolean hasCrystal = false;
                                            if (shouldCrystal()) {
                                                for (Entity entity : AlienBlockUtil.getEntities(new Box(breakPos.up()))) {
                                                    if (entity instanceof EndCrystalEntity) { hasCrystal = true; break; }
                                                }
                                            }
                                            if (!hasCrystal || spamPlace.get()) {
                                                int oldSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                                                doSwap(obby, obby);
                                                AlienBlockUtil.placeBlock(breakPos, placeRotate.get(), true);
                                                doSwap(oldSlot, obby);
                                                placeTimer.reset();
                                            }
                                        }
                                    }
                                }
                                breakNumber = 0;

                            } else if (canPlaceCrystal(breakPos.up()) && shouldCrystal()) {
                                // ── Place crystal near block ──
                                if (placeTimer.passedMs(placeDelay.get())) {
                                    if (checkDamage.get()) {
                                        if (mineTimer.getMs() / breakFinalTime >= crystalDamage.get()) {
                                            if (!placeCrystal()) return;
                                        }
                                    } else {
                                        if (!placeCrystal()) return;
                                    }
                                } else if (startPacket) {
                                    return;
                                }
                            }

                            // ── Wait for AutoCrystal in original ──
                            // (simplified: AutoCrystal check removed)

                            if (delayTimer.passed(stopDelay.get().longValue())) {
                                if (startPacket) {
                                    if (isAir(breakPos)) return;
                                    if (onlyGround.get() && !mc.player.isOnGround()) return;

                                    if (mineTimer.passed((long) breakFinalTime)) {
                                        if (endRotate.get() && shouldYawStep()
                                            && !faceVector(breakPos.toCenterPos().offset(AlienBlockUtil.getClickSide(breakPos), 0.5))) {
                                            return;
                                        }

                                        // ── Tool switch for final break ──
                                        int old = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                                        boolean shouldSwitch;
                                        if (hotBar.get()) {
                                            shouldSwitch = slot != old;
                                        } else {
                                            int invSlot = slot < 9 ? slot + 36 : slot;
                                            shouldSwitch = (old + 36) != invSlot;
                                        }
                                        if (shouldSwitch) {
                                            if (hotBar.get()) {
                                                AlienInventoryUtil.switchToSlot(slot);
                                            } else {
                                                int invSlot = slot < 9 ? slot + 36 : slot;
                                                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, invSlot, old, SlotActionType.SWAP, mc.player);
                                            }
                                        }

                                        int finalSlot = slot;
                                        switchBack = () -> {
                                            if (endRotate.get()
                                                && !faceVector(breakPos.toCenterPos().offset(AlienBlockUtil.getClickSide(breakPos), 0.5))) {
                                                // snap back original slot
                                                if (shouldSwitch) {
                                                    if (hotBar.get()) {
                                                        AlienInventoryUtil.switchToSlot(old);
                                                    } else if (AlienEntityUtil.inInventory()) {
                                                        int fs = finalSlot < 9 ? finalSlot + 36 : finalSlot;
                                                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fs, old, SlotActionType.SWAP, mc.player);
                                                        AlienEntityUtil.syncInventory();
                                                    } else if (old >= 0 && old <= 8) {
                                                        AlienInventoryUtil.switchToSlot(old);
                                                    }
                                                }
                                            } else {
                                                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, breakPos, AlienBlockUtil.getClickSide(breakPos)));
                                                if (endSwing.get()) {
                                                    swingHand(Hand.MAIN_HAND, swingMode.get());
                                                }
                                                if (shouldSwitch) {
                                                    if (hotBar.get()) {
                                                        AlienInventoryUtil.switchToSlot(old);
                                                    } else if (AlienEntityUtil.inInventory()) {
                                                        int fs = finalSlot < 9 ? finalSlot + 36 : finalSlot;
                                                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fs, old, SlotActionType.SWAP, mc.player);
                                                        AlienEntityUtil.syncInventory();
                                                    } else if (old >= 0 && old <= 8) {
                                                        AlienInventoryUtil.switchToSlot(old);
                                                    }
                                                }
                                                breakNumber++;
                                                delayTimer.reset();
                                                startTime.reset();
                                                if (afterBreak.get() && shouldCrystal()) {
                                                    for (Direction facing : Direction.values()) {
                                                        AlienCombatUtil.attackCrystal(breakPos.offset(facing), placeRotate.get(), true);
                                                    }
                                                }
                                                if (setAir.get()) {
                                                    mc.world.setBlockState(breakPos, Blocks.AIR.getDefaultState());
                                                }
                                                ghost = true;
                                            }
                                        };
                                        if (!noGhostHand.get()) {
                                            switchBack.run();
                                            switchBack = null;
                                        }
                                    }
                                } else {
                                    if (!startTime.passed(startDelay.get().intValue())) return;
                                    if (!mineAir.get() && isAir(breakPos)) return;

                                    Direction side = AlienBlockUtil.getClickSide(breakPos);
                                    if (rotate.get()) {
                                        Vec3i vec3i = side.getVector();
                                        if (!faceVector(breakPos.toCenterPos().add(new Vec3d(vec3i.getX() * 0.5, vec3i.getY() * 0.5, vec3i.getZ() * 0.5)))) {
                                            return;
                                        }
                                    }
                                    mineTimer.reset();
                                    animationTime.reset();
                                    if (swing.get()) {
                                        swingHand(Hand.MAIN_HAND, swingMode.get());
                                    }
                                    if (doubleBreak.get()) {
                                        if (secondPos == null || isAir(secondPos)) {
                                            double breakTime = getBreakTime(breakPos, slot, 1.0);
                                            secondAnim.reset();
                                            secondAnim.setLength((long) breakTime);
                                            secondTimer.reset();
                                            secondPos = breakPos;
                                        }
                                        doDoubleBreak(side);
                                    }
                                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, breakPos, side));
                                    startTime.reset();
                                }
                            }
                        }
                    }
                }
            } else {
                if (breakPos.equals(secondPos)) secondPos = null;
                startPacket = false;
                ghost = false;
                complete = false;
                breakNumber = 0;
                breakPos = null;
            }
        }
    }

    // ─── Attack block event ───

    @EventHandler
    public void onStartBreakingBlock(meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isCreative()) return;

        event.cancel();
        BlockPos pos = event.blockPos;

        if (pos.equals(breakPos)) return;
        if (unbreakable(pos)) return;

        if (breakPos == null || !preferWeb.get() || AlienBlockUtil.getBlock(breakPos) != Blocks.COBWEB) {
            if (breakPos == null || !preferHead.get() || !mc.player.isCrawling()
                || !AlienEntityUtil.getPlayerPos(true).up().equals(breakPos)) {
                if (AlienBlockUtil.getClickSideStrict(pos) == null) return;
                if (MathHelper.sqrt((float) mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos())) > range.get()) return;

                breakPos = pos;
                breakNumber = 0;
                startPacket = false;
                ghost = false;
                complete = false;
                mineTimer.reset();
                animationTime.reset();

                Direction side = AlienBlockUtil.getClickSide(breakPos);
                if (rotate.get()) {
                    Vec3i vec3i = side.getVector();
                    if (!faceVector(breakPos.toCenterPos().add(new Vec3d(vec3i.getX() * 0.5, vec3i.getY() * 0.5, vec3i.getZ() * 0.5)))) {
                        return;
                    }
                }
                if (startTime.passed(startDelay.get().intValue())) {
                    if (swing.get()) swingHand(Hand.MAIN_HAND, swingMode.get());
                    if (doubleBreak.get()) {
                        if (secondPos == null || isAir(secondPos)) {
                            int s = getTool(breakPos);
                            if (s == -1) s = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                            secondFinalTime = getBreakTime(breakPos, s, 1.0);
                            secondAnim.reset();
                            secondAnim.setLength((long) secondFinalTime);
                            secondTimer.reset();
                            secondPos = breakPos;
                        }
                        doDoubleBreak(side);
                    }
                    int s = getTool(breakPos);
                    if (s == -1) s = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                    breakFinalTime = getBreakTime(breakPos, s);
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, breakPos, side));
                    startTime.reset();
                }
            }
        }
    }

    // ─── Packet Event ───

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isCreative()) return;

        if (event.packet instanceof PlayerMoveC2SPacket) {
            if (bypassGround.get()
                && !mc.player.getPose().name().equals("GLIDING")
                && breakPos != null
                && !isAir(breakPos)
                && bypassTime.get() > 0
                && MathHelper.sqrt((float) breakPos.toCenterPos().squaredDistanceTo(mc.player.getEyePos())) <= range.get().floatValue() + 2.0F) {
                double breakTime = breakFinalTime - bypassTime.get();
                if (breakTime <= 0 || mineTimer.passed((long) breakTime)) {
                    sendGroundPacket = true;
                    // We cannot modify the packet easily, so send a ground=true packet after
                }
            } else {
                sendGroundPacket = false;
            }
        } else if (event.packet instanceof UpdateSelectedSlotC2SPacket packet) {
            if (packet.getSelectedSlot() != lastSlot) {
                lastSlot = packet.getSelectedSlot();
                if (switchReset.get()) {
                    startPacket = false;
                    ghost = false;
                    complete = false;
                    mineTimer.reset();
                    animationTime.reset();
                }
            }
        } else if (event.packet instanceof PlayerActionC2SPacket packet) {
            if (packet.getAction() == Action.START_DESTROY_BLOCK) {
                if (breakPos == null || !packet.getPos().equals(breakPos)) return;
                if (grimDisabler.get()) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, packet.getPos(), packet.getDirection()));
                }
                startPacket = true;
            } else if (packet.getAction() == Action.STOP_DESTROY_BLOCK) {
                if (breakPos == null || !packet.getPos().equals(breakPos)) return;
                if (!instant.get()) {
                    startPacket = false;
                    ghost = false;
                    complete = false;
                }
            }
        }
    }

    // ─── Render Event ───

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (breakPos != null && mc.world.isAir(breakPos)) {
            complete = true;
        }
        if (mc.player.isCreative()) {
            progress = 0.0;
            return;
        }

        if (secondPos != null) {
            if (isAir(secondPos)) {
                secondPos = null;
                return;
            }
            if (!checkDouble.get() || !secondPos.equals(breakPos)) {
                secondAnim.setLength((long) secondFinalTime);
                double easeVal = secondAnim.ease(ease.get());
                if (box.get()) {
                    AlienRender3DUtil.drawFill(event, getFillBox(secondPos, easeVal), toAwt(doubleColor.get()));
                }
                if (outline.get()) {
                    AlienRender3DUtil.drawBox(event, getOutlineBox(secondPos, easeVal), toAwt(doubleOutlineColor.get()));
                }
            }
        }

        if (breakPos != null) {
            progress = mineTimer.getMs() / breakFinalTime;
            animationTime.setLength((long) breakFinalTime);
            double easeVal = animationTime.ease(ease.get());

            if (unbreakable(breakPos)) {
                if (box.get()) {
                    AlienRender3DUtil.drawFill(event, new Box(breakPos), toAwt(startColor.get()));
                }
                if (outline.get()) {
                    AlienRender3DUtil.drawBox(event, new Box(breakPos), toAwt(startOutlineColor.get()));
                }
                return;
            }

            double fadeVal = animationTime.ease(fadeEase.get());
            if (box.get()) {
                AlienRender3DUtil.drawFill(event, getFillBox(breakPos, easeVal), toAwt(getColor(fadeVal)));
            }
            if (outline.get()) {
                AlienRender3DUtil.drawBox(event, getOutlineBox(breakPos, easeVal), toAwt(getOutlineColor(fadeVal)));
            }
            if (text.get()) {
                Vec3d textPos = breakPos.toCenterPos();
                String progressText;
                if (isAir(breakPos)) {
                    progressText = "Waiting";
                } else if (mineTimer.getMs() < breakFinalTime) {
                    progressText = df.format(progress * 100.0) + "%";
                } else {
                    progressText = "100.0%";
                }
                AlienRender3DUtil.drawText3D(progressText, textPos, textScaleBase.get(), textScaleFactor.get(), maxTextScale.get(), -1);
            }
        } else {
            progress = 0.0;
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        AlienRender3DUtil.renderDeferred();
    }

    // ─── Helper methods ───

    private void swingHand(Hand hand, SwingHandMode mode) {
        switch (mode) {
            case All -> mc.player.swingHand(hand);
            case Client -> mc.player.swingHand(hand, false);
            case Server -> mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.HandSwingC2SPacket(hand));
        }
    }

    private Box getFillBox(BlockPos pos, double easeVal) {
        return switch ((AnimMode) animation.get()) {
            case Center -> {
                easeVal = (1.0 - easeVal) / 2.0;
                yield new Box(pos).shrink(easeVal, easeVal, easeVal).shrink(-easeVal, -easeVal, -easeVal);
            }
            case Grow -> {
                easeVal = (1.0 - easeVal) / 2.0;
                yield new Box(pos).shrink(easeVal, 0.0, easeVal).shrink(-easeVal, 0.0, -easeVal);
            }
            case Up -> new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + easeVal, pos.getZ() + 1);
            case Down -> new Box(pos.getX(), pos.getY() + 1 - easeVal, pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            case Oscillation -> new Box(pos).shrink(easeVal, easeVal, easeVal).shrink(-easeVal, -easeVal, -easeVal);
            case None -> new Box(pos);
        };
    }

    private Box getOutlineBox(BlockPos pos, double easeVal) {
        easeVal = Math.min(easeVal + expandLine.get(), 1.0);
        return switch ((AnimMode) animation.get()) {
            case Center -> {
                easeVal = (1.0 - easeVal) / 2.0;
                yield new Box(pos).shrink(easeVal, easeVal, easeVal).shrink(-easeVal, -easeVal, -easeVal);
            }
            case Grow -> {
                easeVal = (1.0 - easeVal) / 2.0;
                yield new Box(pos).shrink(easeVal, 0.0, easeVal).shrink(-easeVal, 0.0, -easeVal);
            }
            case Up -> new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + easeVal, pos.getZ() + 1);
            case Down -> new Box(pos.getX(), pos.getY() + 1 - easeVal, pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            case Oscillation -> new Box(pos).shrink(easeVal, easeVal, easeVal).shrink(-easeVal, -easeVal, -easeVal);
            case None -> new Box(pos);
        };
    }

    boolean canPlaceCrystal(BlockPos pos) {
        BlockPos obsPos = pos.down();
        BlockPos boost = obsPos.up();
        return (AlienBlockUtil.getBlock(obsPos) == Blocks.BEDROCK || AlienBlockUtil.getBlock(obsPos) == Blocks.OBSIDIAN)
            && AlienBlockUtil.getClickSideStrict(obsPos) != null
            && noEntity(boost) && noEntity(boost.up());
    }

    boolean noEntity(BlockPos pos) {
        for (Entity entity : AlienBlockUtil.getEntities(new Box(pos))) {
            if (!(entity instanceof ItemEntity) && !(entity instanceof ArmorStandEntity)) {
                return false;
            }
        }
        return true;
    }

    boolean shouldCrystal() {
        return crystal.get() && (!onlyHeadBomber.get() || obsidian.get());
    }

    boolean placeCrystal() {
        int crystalSlot = inventory.get() ? AlienInventoryUtil.findItemInventorySlot(Items.END_CRYSTAL) : AlienInventoryUtil.findItem(Items.END_CRYSTAL);
        if (crystalSlot != -1) {
            int oldSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
            doSwap(crystalSlot, crystalSlot);
            AlienBlockUtil.placeCrystal(breakPos.up(), placeRotate.get());
            doSwap(oldSlot, crystalSlot);
            placeTimer.reset();
            return !waitPlace.get();
        }
        return true;
    }

    void doSwap(int slot, int inv) {
        if (!inventory.get()) {
            if (slot < 0 || slot > 8) return;
            AlienInventoryUtil.switchToSlot(slot);
        } else {
            if (inv == -1) return;
            AlienInventoryUtil.inventorySwap(inv, ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot());
        }
    }

    void doDoubleBreak(Direction side) {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, breakPos, side));
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, breakPos, side));
    }

    public static double getBreakTime(BlockPos pos) {
        int slot = INSTANCE.getTool(pos);
        if (slot == -1) slot = ((InventoryAccessor) INSTANCE.mc.player.getInventory()).getSelectedSlot();
        return INSTANCE.getBreakTime(pos, slot);
    }

    double getBreakTime(BlockPos pos, int slot) {
        return getBreakTime(pos, slot, damage.get());
    }

    double getBreakTime(BlockPos pos, int slot, double damageMul) {
        return 1.0F / getBlockStrength(pos, mc.player.getInventory().getStack(slot)) / 20.0F * 1000.0F * damageMul;
    }

    float getBlockStrength(BlockPos position, ItemStack itemStack) {
        BlockState state = mc.world.getBlockState(position);
        float hardness = state.getHardness(mc.world, position);
        if (hardness < 0.0F) return 0.0F;
        float i = state.isToolRequired() && !itemStack.isSuitableFor(state) ? 100.0F : 30.0F;
        return getDigSpeed(state, itemStack) / hardness / i;
    }

    float getDigSpeed(BlockState state, ItemStack itemStack) {
        float digSpeed = getDestroySpeed(state, itemStack);
        if (digSpeed > 1.0F) {
            int efficiencyModifier = getEfficiencyLevel(itemStack);
            if (efficiencyModifier > 0 && !itemStack.isEmpty()) {
                digSpeed += (float) (StrictMath.pow(efficiencyModifier, 2.0) + 1.0);
            }
        }
        if (mc.player.hasStatusEffect(StatusEffects.HASTE)) {
            digSpeed *= 1.0F + (mc.player.getStatusEffect(StatusEffects.HASTE).getAmplifier() + 1) * 0.2F;
        }
        if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            digSpeed *= switch (mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
        }
        if (mc.player.isSubmergedInWater()) {
            digSpeed *= (float) mc.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.SUBMERGED_MINING_SPEED);
        }
        boolean inWeb = checkWeb.get() && isInWeb(mc.player) && mc.world.getBlockState(breakPos).getBlock() == Blocks.COBWEB;
        if ((!mc.player.isOnGround() || inWeb)
            && checkGround.get()
            && (!smart.get() || mc.player.getPose().name().equals("GLIDING") || inWeb)) {
            digSpeed /= 5.0F;
        }
        return digSpeed < 0.0F ? 0.0F : digSpeed;
    }

    float getDestroySpeed(BlockState state, ItemStack itemStack) {
        float destroySpeed = 1.0F;
        if (itemStack != null && !itemStack.isEmpty()) {
            destroySpeed *= itemStack.getMiningSpeedMultiplier(state);
        }
        return destroySpeed;
    }

    int getEfficiencyLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        try {
            var enchantments = stack.getEnchantments();
            if (enchantments == null) return 0;
            for (var entry : enchantments.getEnchantmentEntries()) {
                if (entry == null || entry.getKey() == null) continue;
                String idStr = entry.getKey().getKey().map(k -> k.getValue().toString()).orElse(entry.getKey().toString().toLowerCase());
                if (idStr.contains("efficiency")) return entry.getIntValue();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    int getTool(BlockPos pos) {
        if (hotBar.get()) {
            int index = -1;
            float currentFastest = 1.0F;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack != ItemStack.EMPTY) {
                    int eff = getEfficiencyLevel(stack);
                    float destroySpeed = stack.getMiningSpeedMultiplier(mc.world.getBlockState(pos));
                    if (eff + destroySpeed > currentFastest) {
                        currentFastest = eff + destroySpeed;
                        index = i;
                    }
                }
            }
            return index;
        } else {
            AtomicInteger slot = new AtomicInteger(-1);
            float currentFastest = 1.0F;
            for (Map.Entry<Integer, ItemStack> entry : AlienInventoryUtil.getInventoryAndHotbarSlots().entrySet()) {
                if (!(entry.getValue().getItem() instanceof AirBlockItem)) {
                    int eff = getEfficiencyLevel(entry.getValue());
                    float destroySpeed = entry.getValue().getMiningSpeedMultiplier(mc.world.getBlockState(pos));
                    if (eff + destroySpeed > currentFastest) {
                        currentFastest = eff + destroySpeed;
                        slot.set(entry.getKey());
                    }
                }
            }
            return slot.get();
        }
    }

    boolean isAir(BlockPos breakPos) {
        return mc.world.isAir(breakPos) || (AlienBlockUtil.getBlock(breakPos) == Blocks.FIRE && AlienBlockUtil.hasCrystal(breakPos));
    }

    public static boolean unbreakable(BlockPos blockPos) {
        if (INSTANCE == null || INSTANCE.mc.world == null) return true;
        Block block = INSTANCE.mc.world.getBlockState(blockPos).getBlock();
        return !(block instanceof AirBlock) && (block.getHardness() == -1.0F || block.getHardness() == 100.0F);
    }

    SettingColor getColor(double quad) {
        SettingColor sc = startColor.get();
        SettingColor ec = endColor.get();
        return new SettingColor(
            (int)(sc.r + (ec.r - sc.r) * quad),
            (int)(sc.g + (ec.g - sc.g) * quad),
            (int)(sc.b + (ec.b - sc.b) * quad),
            (int)(sc.a + (ec.a - sc.a) * quad)
        );
    }

    SettingColor getOutlineColor(double quad) {
        SettingColor sc = startOutlineColor.get();
        SettingColor ec = endOutlineColor.get();
        return new SettingColor(
            (int)(sc.r + (ec.r - sc.r) * quad),
            (int)(sc.g + (ec.g - sc.g) * quad),
            (int)(sc.b + (ec.b - sc.b) * quad),
            (int)(sc.a + (ec.a - sc.a) * quad)
        );
    }

    private Color toAwt(SettingColor c) {
        return new Color(c.r, c.g, c.b, c.a);
    }

    // ─── Enums ───

    public enum Page { General, Check, Rotation, Place, Render }
    public enum TimingMode { Pre, Post, All }
    public enum SwingHandMode { All, Client, Server }
    public enum AnimMode { Center, Grow, Up, Down, Oscillation, None }
}
