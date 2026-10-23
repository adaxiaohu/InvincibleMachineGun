package com.codigohasta.addon.modules.slimefunhelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Predicate;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientPlayerAccess;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.PlayerInteractItemC2SPacketAccess;
import com.codigohasta.addon.utils.slimefunhelper.accessors.hacks.EntityInternalAccess;
import com.codigohasta.addon.utils.slimefunhelper.accessors.hacks.PlayerInteractionAccess;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.events.PacketManager;
import com.codigohasta.addon.utils.slimefunhelper.events.impl.MetadataUpdate;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.enums.GhostHandMode;

import com.codigohasta.addon.utils.slimefunhelper.events.impl.EventContainer;
import com.codigohasta.addon.utils.slimefunhelper.hacks.ACTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.CombatTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePreset;
import com.codigohasta.addon.modules.slimefunhelper.PacketOrderManager;
import com.codigohasta.addon.modules.slimefunhelper.InvExtra;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.HotKeyUtils;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config.*;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.move.ElytraOptimizeUtils;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.tasks.CounterExecutor;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.tasks.StateExecutor;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.tasks.TimerExecutor;
import com.codigohasta.addon.utils.slimefunhelper.hooks.BaritoneHooks;
import com.codigohasta.addon.utils.slimefunhelper.hooks.ViaFabricPlusHooks;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.Tasks;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind;
import com.codigohasta.addon.utils.slimefunhelper.utils.*;
import com.codigohasta.addon.utils.slimefunhelper.utils.collections.IndexEntry;
import com.codigohasta.addon.utils.slimefunhelper.utils.entity.PlayerInputUtils;
import com.codigohasta.addon.utils.slimefunhelper.versioned.api.VDataFlag;
import com.codigohasta.addon.utils.slimefunhelper.versioned.api.VItem;
import com.codigohasta.addon.utils.slimefunhelper.versioned.api.VPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.ApiStatus;

public class ElytraExtra extends BaseModule implements LegalMovementManager.MovementModifier {
    public static ElytraExtra INSTANCE;
    private static LegalMovementManager.DelegateMovementModifier instance;

    public final ModulePath elytra = makePath(Configs.MOV_CONFIG, "elytra");
    public final ModulePath elytraTweaks = elytra.add("elytra-tweaks");
    public final ModulePath unbreakableElytra = elytra.add("unbreakable-elytra");
    public final ModulePath armorFlyPath = elytra.add("armor-fly");
    public final ModulePath customFireworksPath = elytra.add("custom-fireworks");

    public ElytraExtra() {
        super("ElytraExtra");
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        INSTANCE = this;
    }

    public final FlagRef fuckGrimAC = MovTasks.getMovExtra().fuckGrimAC;

    public final NBTRef<OptionalPrimitive<WrapEnum<Configs.CompatMode>>> noKineticMode = builder(
                    elytraTweaks.add("no-kinetic-mode"), OptionalPrimitive.configEnum(Configs.CompatMode.class))
            .defaultValue(new OptionalPrimitive<>(
                    false, NBTTypes.CONFIG_ENUM_TYPE.cast(), new WrapEnum<>(Configs.CompatMode.NO_COMPAT)))
            .show(() -> !this.armorFly.get())
            .build();

    // todo: remove this shit,
    public final NBTRef<OptionalPrimitive<WrapEnum<Configs.CompatMode>>> maceFixMode = builder(
                    elytraTweaks.add("mace-hit-fix-mode"), OptionalPrimitive.configEnum(Configs.CompatMode.class))
            .defaultValue(new OptionalPrimitive<>(
                    false, NBTTypes.CONFIG_ENUM_TYPE.cast(), new WrapEnum<>(Configs.CompatMode.NO_COMPAT)))
            .show(() -> !(this.armorFly.get() && this.armorMode.get().isIn(ArmorFlyMode.TICK)))
            .build();

    public final FlagRef noFallLanding =
            flagBuilder(elytraTweaks.add("no-fall-when-landing")).build();
    public final FlagRef noFallResetFallDistance =
            flagBuilder(elytraTweaks.add("no-fall-disable-no-fall-module")).build();

    public final FlagRef autoSwitch =
            flagBuilder(elytraTweaks.add("auto-switch")).build();

    public final KeyBindRef autoTakeOff = hotkey(elytraTweaks.add("auto-take-off"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::autoTakeoff))
            .build();

    public final FlagRef autoTakeOffFireworksFromGround =
            flagBuilder(elytraTweaks.add("auto-take-off-fireworks-from-ground")).build();

    public final IntRef autoTakeOffRetryGt = intBuilder(elytraTweaks.add("auto-take-off-retry-ticks"))
            .defaultValue(3)
            .build();

    public final FlagRef autoTakeOffWhenJoinServer =
            flagBuilder(elytraTweaks.add("auto-start-fly-join-server")).build();

    public final FlagRef fireworkSpeedLimitWeb =
            flagBuilder(elytraTweaks.add("firwork-speed-limit-web")).build();

    public final FlagRef fireworksLiquidFly =
            flagBuilder(elytraTweaks.add("firework-liquid-fly")).hideConfig().build();

    //    public final FlagRef elytraResyncBadPacketFix =
    //            flagBuilder(elytraTweaks.add("fix-duplicate-elytra-state-sync")).build();

    // public final FlagRef elytraAntiKB = flagBuilder(Configs.MOV_CONFIG, ELYTRA_ANTI_KB).build();

    public final FlagRef enableUnbreakableElytra =
            flagBuilder(unbreakableElytra.add("enable")).build();

    public final KeyBindRef unbreakableHotkey = toggleHotkey(
                    unbreakableElytra.addHotkey(), new MultiKeyBind(), unbreakableElytra.addEnable())
            .build();

    public final IntRef period = intBuilder(unbreakableElytra.add("period"))
            .defaultValue(16)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef resetVanilla =
            flagBuilder(unbreakableElytra.add("reset-vanilla")).build();

    public final FlagRef armorFly = flagBuilder(armorFlyPath.add("enable"))
            .updateListener(this::onToggleArmorFly)
            .build();

    public final KeyBindRef keyBind = moduleEntry(
                    armorFlyPath.add("enable-hotkey"), new MultiKeyBind(), armorFlyPath.add("enable"))
            .build();

    public final EnumRef<ArmorFlyMode> armorMode = builder(armorFlyPath.add("armor-mode"), ArmorFlyMode.class)
            .defaultValue(ArmorFlyMode.TICK)
            .build();

    public final FlagRef forceArmor =
            flagBuilder(armorFlyPath.add("force-no-elytra")).build();

    public final FlagRef enableLiquidFly =
            flagBuilder(armorFlyPath.add("enable-liquid-fly")).build();

    public final FlagRef enableLiquidFlyOnlyFireworks =
            flagBuilder(armorFlyPath.add("liquid-fly-only-fireworks")).build();

    public final FlagRef enableOnGroundFly =
            flagBuilder(armorFlyPath.add("on-ground-fly-only-fireworks")).build();

    public final FlagRef landAutoClose =
            flagBuilder(armorFlyPath.add("land-auto-close")).build();

    public final FlagRef landAutoSneak =
            flagBuilder(armorFlyPath.add("land-auto-sneak")).build();

    public final NBTRef<OptionalPrimitive<Integer>> closeContinueFly = builder(
                    armorFlyPath.add("close-continue-fly"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(true, NBTTypes.INT_TYPE, 20))
            .validator((v) -> v.getValue() >= 5)
            .build();

    public final FlagRef armorFlyBadPacketFix = flagBuilder(armorFlyPath.add("armor-fly-fix-grim-bad-packets-1"))
            .show(() -> this.armorMode.get().isIn(ArmorFlyMode.TICK))
            .build();

    public final FlagRef poseFix = flagBuilder(armorFlyPath.add("pose-fix")).build();

    public final FlagRef renderFix = flagBuilder(armorFlyPath.add("render-fix")).build();

    public final FlagRef handControl = builder(armorFlyPath.add("enable-manually-swap"), Boolean.class)
            .defaultValue(true)
            .build();

    /** Ported from upstream 1.1.7.2 ElytraExtra.antiKick. */
    public final FlagRef antiKick = builder(armorFlyPath.add("anti-kick"), Boolean.class)
            .defaultValue(true)
            .build();

    public final NBTRef<Regex> customFireworks = builder(customFireworksPath.add("firework-item-id"), Regex.class)
            .defaultValue(new Regex("^()$"))
            .build();

    public final IntRef fireworkTicks = intBuilder(customFireworksPath.add("firework-delay-multiply-vanilla"))
            .defaultValue(10)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    /** Ported from upstream 1.1.7.2 ElytraExtra: the vanilla firework delay multiplier. */
    public final DoubleRef fireworkMaxTicks = doubleBuilder(
                    customFireworksPath.add("firework-max-delay-multiply-vanilla"))
            .defaultValue(20.0D)
            .validator(Configs.doubleRange(0.0D, 100.0D))
            .build();

    public final FireworkTimer timerVanilla = new FireworkTimer(fireworkMaxTicks);

    public final FlagRef autoRocket =
            flagBuilder(customFireworksPath.add("firework-auto-use-vanilla")).build();

    public final IntRef rocketExtraEffectiveTicks = intBuilder(customFireworksPath.add("rocket-extra-effect-ticks"))
            .defaultValue(3)
            .build();

    public final FlagRef autoRescale =
            flagBuilder(customFireworksPath.add("auto-rescale-firework-box")).build();

    public final DoubleRef autoRescaleAmount = doubleBuilder(customFireworksPath.add("auto-rescale-firework-amount"))
            .defaultValue(1.65D)
            .show(this.autoRescale::get)
            .build();

    public final EnumRef<Al> autoRescaleAl = builder(customFireworksPath.add("auto-rescale-firework-al"), Al.class)
            .defaultValue(Al.V1)
            .show(this.autoRescale::get)
            .build();

    @ApiStatus.Experimental
    public final DoubleRef autoRescaleThreshold = doubleBuilder(
                    customFireworksPath.add("auto-rescale-firework-anti-lag-threshold"))
            .show(() -> autoRescaleAl.get().isIn(Al.V3))
            .defaultValue(0.002)
            .experimental()
            .build();

    @ApiStatus.Experimental
    public final KeyBindRef switchAlKey = hotkey(
                    customFireworksPath.add("switch-auto-rescale-firework-al-key"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(ElytraOptimizeUtils::toggleElytraAl))
            .experimental()
            .build();

    public final DoubleRef autoRescaleZeroPointThreeY = builder(
                    customFireworksPath.add("auto-rescale-axis-zero-point-three"), Double.class)
            .defaultValue(0.03)
            .show(this.autoRescale::get)
            .build();

    /**
     * Optional speed clamps applied after the axis limit shrinks the motion.
     *
     * <p>Ported from upstream 1.1.7.2, where {@code applyAxisLimit2/3_0} end with
     * {@code clampedMotion = applySpeedLimit(clampedMotion)}. Both default to absent, so
     * {@link #applySpeedLimit} is a no-op until the user enables them.
     */
    public final NBTRef<OptionalPrimitive<Double>> fireworkSpeedLimitUp = builder(
                    customFireworksPath.add("firework-speed-limit-up"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 5.0D))
            .show(this.autoRescale::get)
            .build();

    public final NBTRef<OptionalPrimitive<Double>> fireworkSpeedLimitDown = builder(
                    customFireworksPath.add("firework-speed-limit-down"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 5.0D))
            .show(this.autoRescale::get)
            .build();

    /**
     * Clamps the motion to the configured up/down speed limit.
     *
     * <p>Ported from upstream 1.1.7.2 {@code ElytraExtra.applySpeedLimit}. Returns the vector
     * unchanged while both limits are absent (the default).
     */
    public Vec3d applySpeedLimit(Vec3d vec3d) {
        OptionalPrimitive<Double> speedLimit = vec3d.y > 0 ? fireworkSpeedLimitUp.get() : fireworkSpeedLimitDown.get();
        if (speedLimit.isPresent() && vec3d.length() > speedLimit.getValue()) {
            if (vec3d.y > 0) {
                double horizontal = vec3d.horizontalLengthSquared();
                double newY = Math.sqrt(Math.max(0, MathUtils.s2(speedLimit.getValue()) - horizontal));
                return vec3d.withAxis(Direction.Axis.Y, newY);
            } else {
                double vertical = Math.abs(vec3d.y);
                double newXZ = Math.sqrt(Math.max(0, MathUtils.s2(speedLimit.getValue()) - MathUtils.s2(vertical)));
                double horizontal = vec3d.horizontalLength();
                double percentage = newXZ / horizontal;
                return vec3d.multiply(percentage, 1, percentage);
            }
        }
        return vec3d;
    }

    public final FlagRef rocketBoost =
            flagBuilder(customFireworksPath.add("firework-boost-enable")).build();

    /** Ported from upstream 1.1.7.2 ElytraExtra: gates baritone-driven rocket boost. */
    public final FlagRef baritoneUseRocketBoost =
            flagBuilder(customFireworksPath.add("baritone-use-rocket-boost")).build();

    public final KeyBindRef rocketBoostHotkey = toggleHotkey(
                    customFireworksPath.add("firework-boost-hotkey"),
                    new MultiKeyBind(),
                    customFireworksPath.add("firework-boost-enable"))
            .build();

    public final DoubleRef rocketBoostSpeed = doubleBuilder(customFireworksPath.add("firework-boost-speed"))
            .defaultValue(1.7D)
            .validator(Configs.doubleRange(0.0d, 10000.0D))
            .build();

    public final FlagRef rocketBoostUseRescale = flagBuilder(customFireworksPath.add("firework-boost-use-rescale"))
            .show(this.autoRescale::get)
            .build();

    public final FlagRef flyRocketOnFirstOff =
            flagBuilder(customFireworksPath.add("fly-rocket-on-first-off")).build();

    public final KeyBindRef flyRocketOnFirstOffHotkey = toggleHotkey(
                    customFireworksPath.add("fly-rocket-on-first-off-hotkey"),
                    new MultiKeyBind(),
                    customFireworksPath.add("fly-rocket-on-first-off"))
            .build();

    public final KeyBindRef clickRocket = hotkey(customFireworksPath.add("click-firework-use"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::clickRocket))
            .build();

    public final FlagRef noEnoughFireworksNotify =
            flagBuilder(customFireworksPath.add("log-no-fireworks")).build();

    // Ported from upstream 1.1.7.2 ElytraExtra: the fireworks-lag-delay feature driven by
    // onFireworkRemoval. Both default to inactive, so applyFireworkDelay is a no-op until used.
    public final FlagRef fireworksLagDelay =
            flagBuilder(customFireworksPath.add("fireworks-lag-delay")).build();

    public final KeyBindRef fireworkLagHotkey = toggleHotkey(
                    customFireworksPath.add("firework-lag-delay-hotkey"),
                    new MultiKeyBind(),
                    customFireworksPath.add("fireworks-lag-delay"))
            .build();

    public final IntRef fireworksDelayMS = builder(customFireworksPath.add("fireworks-delay-ms"), Integer.class)
            .defaultValue(500)
            .build();

    public final NBTRef<OptionalPrimitive<Integer>> armorGlideMaxDelayTicks = builder(
                    armorFlyPath.add("max-delay-ticks"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.INT_TYPE, 5))
            .validator(value -> value.getValue() >= 0)
            .build();

    // Ported from upstream 1.1.7.2: the firework-removal delay lock state.
    Object currentDelayingLock = null;
    Long lastStartDelayOrReleaseMs = 0L;

    // public final FlagRef useFireworks =
    //        flagBuilder(Configs.MOV_CONFIG, ELYTRA_FLIGHT_CONTROL_FIREWORKS).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerFallFlyingTick(), this::runElytraUnbreakable);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.PLAYER), this::handleEntityDataUpdate);
        registerListener(Listener.getPlayerSwitchFallFlying(), this::onStartFallFlying);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(PlayerPositionLookS2CPacket.class), this::onSetBack);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInteractItemC2SPacket.class), this::onUseFireworks);
        registerListener(
                Listener.getEntityTrackDataUpdate().getChannel(EntityType.FIREWORK_ROCKET), this::onFireworkOwner);
        registerListener(
                Listener.getPacketPoint().getChannel(EntitiesDestroyS2CPacket.class), this::onFireworkRemovePacketPeek);
        registerListener(
                Listener.getEntityRemoveListener().getChannel(EntityType.FIREWORK_ROCKET), this::onFireworkRemove);
        registerListener(Listener.getPlayerRespawnPoint(), this::onWorldSwitch);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class), this::handleMaceAttack);
        registerListener(Listener.getGameJoinPoint(), this::onGameJoinAutoStartFallFlying);
        registerListener(Listener.getPlayerSwitchFallFlying(), this::onMonitorFallFlying, Integer.MAX_VALUE - 1);
        registerListener(Listener.getPlayerSwitchFallFlying(), this::onElytraFlightStart, Integer.MAX_VALUE - 1);
        registerListener(
                Listener.getPacketPoint().getChannel(ClientCommandC2SPacket.class), this::onElytraFlightRocket);
        registerListener(
                Listener.getPacketPoint().getChannel(TeleportConfirmC2SPacket.class), this::onAcceptTeleportation);
        registerListener(Listener.getPostGameTick(), this::onArmorStateTick);
        // Ported from upstream 1.1.7.2 ElytraExtra.registerAll.
        registerListener(PacketManager.getPacketQueueInEvent(), this::onFireworkRemoval);
        registerListener(PacketManager.getPacketQueueInEvent(), this::onArmorGlideDelay);
        registerListener(PacketManager.getQueueShutdownEvent(), this::onPacketQueueFlush);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(CommonPingS2CPacket.class),
                this::onPacketPing,
                Integer.MIN_VALUE);
        registerListener(
                Listener.getPacketPoint().getChannel(CommonPongC2SPacket.class), this::onPacketPong, Integer.MIN_VALUE);
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvents);
    }

    //    public void onHit(Event<WorldEventS2CPacket> event){
    //        if(event.context().getData())
    //    }

    boolean autoTakeOffFlag = false;

    public boolean autoTakeoff() {
        if (checkNull()) return false;
        if (hasGlidingItem()) {
            autoTakeOffFlag = true;
            return true;
        }
        return false;
    }

    public void onGameJoinAutoStartFallFlying(Event<ClientPlayerEntity> joinServer) {
        if (autoTakeOffWhenJoinServer.get()) {
            MutableInt loadTicks = new MutableInt(0);
            Tasks.scheduleRepeated(
                    () -> {
                        if (mc.player == joinServer.context) {
                            if (mc.player.networkHandler.isLoaded()
                                    && WorldUtils.isChunkLoaded(mc.player.getBlockPos())
                                    && loadTicks.incrementAndGet() > 2) {
                                if (!mc.player.isOnGround() && !mc.player.isGliding()) {
                                    autoTakeoff();
                                }
                                return true;
                            } else {
                                return false;
                            }
                        } else {
                            return true;
                        }
                    },
                    20,
                    1);
        }
    }

    public boolean clickRocket() {
        if (mc.player.isGliding()) {
            sendCustomUseFireworkPacket(mc.player.getPitch(), mc.player.getYaw());
            return true;
        }
        return false;
    }

    public Vec3d clampFireworkSpeedInWeb(Vec3d oldVelocity) {
        if (fireworkSpeedLimitWeb.get() && canFireworkControlMotion() && PlayerStateManager.INSTANCE.lastInWeb) {
            Vec3d simulationVelocity = oldVelocity;
            Vec3d stuckSpeedMultiplier = mc.player.movementMultiplier;
            if (stuckSpeedMultiplier != null && stuckSpeedMultiplier.horizontalLengthSquared() > 1E-7) {
                simulationVelocity = simulationVelocity.multiply(stuckSpeedMultiplier);
            }
            Vec3d slowMovement = new Vec3d(0.25, 0.05F, 0.25);
            if (mc.player.hasStatusEffect(StatusEffects.WEAVING)) {
                slowMovement = new Vec3d(0.5, 0.25, 0.5);
            }
            double multiplier = slowMovement.horizontalLength() / (new Vec3d(1, 0, 1).horizontalLength());
            double delta = 1 - multiplier;
            double threshold = 0.245;
            double maxLength = threshold / delta;
            double scaling = maxLength / simulationVelocity.horizontalLength();
            if (scaling < 1.0D) {
                return oldVelocity.multiply(scaling);
            }
        }
        return oldVelocity;
    }

    // elytra unbreakable?

    // Ported from upstream 1.1.7.2 ElytraExtra: the boolean pending model that replaced
    // IMG's counter (`nextTimeDelaySwitchElytraUnbreakable`).
    private boolean pendingElytraUnbreakableTransaction = false;
    private boolean pendingHandleElytraUnbreakableTransaction = false;
    public int elytraUnbreakableTransactionSlot = -1;

    /** Ported from upstream 1.1.7.2 ElytraExtra.isCurrentArmorGlidingEnable. */
    public boolean isCurrentArmorGlidingEnable() {
        return armorFly.get() && armorGlideTransactionSlot != -1;
    }

    public boolean isCurrentArmorGliding() {
        return armorFly.get() && armorGlideTransactionSlot != -1 && !armorGlideTransactionAbort;
    }

    public boolean isCurrentArmorGlidingAbortState() {
        return armorFly.get() && armorGlideTransactionSlot != -1 && armorGlideTransactionAbort;
    }

    public boolean shouldElytraUnbreakable() {
        return enableUnbreakableElytra.get()
                && mc.player != null
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).get(DataComponentTypes.UNBREAKABLE) == null;
    }

    public int findElytraUnbreakableSwitchSlot() {
        if (this.armorGlideTransactionSlot != -1
                && this.armorGlideTransactionSlot < mc.player.playerScreenHandler.slots.size()) {
            ItemStack stackArmorFly = mc.player
                    .playerScreenHandler
                    .slots
                    .get(this.armorGlideTransactionSlot)
                    .getStack();
            if (canStopFallFlying(stackArmorFly)) {
                return this.armorGlideTransactionSlot;
            }
        }
        int idx = findEmptySlotForElytra();
        if (idx != -1) {
            broadcastSwapBackSlot(idx);
        }
        return idx;
    }

    private final CounterExecutor elytraUnbreakableDurabilityCounter = new CounterExecutor();
    private final CounterExecutor armorGlideAbortCounter = new CounterExecutor();

    public void onArmorStateTick(Event<ClientPlayerEntity> event) {
        if (armorFly.get() && mc.player.isGliding() && armorGlideTransactionSlot != -1) {
            if (armorMode.get() == ArmorFlyMode.TICK_LEGACY) {
                armorGlideAbortCounter.reset();
                armorGlideTransactionAbort = false;
                return;
            }
            ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (isValidElytra(stack)) {
                armorGlideAbortCounter.count();
                if (!armorGlideTransactionAbort && armorGlideAbortCounter.executeIf(5)) {
                    if (handControl.get()) {
                        armorGlideTransactionAbort = true;
                    } else {
                        // force armor
                        int oldSlot = armorGlideTransactionSlot;
                        armorGlideTransactionSlot = -1;
                        startArmorFlyTransaction(oldSlot);
                        armorGlideAbortCounter.reset();
                    }
                }
            } else {
                armorGlideAbortCounter.reset();
                armorGlideTransactionAbort = false;
            }
        } else {
            armorGlideAbortCounter.reset();
            armorGlideTransactionAbort = false;
        }
    }

    public void runElytraUnbreakable(Event<Integer> tickEvent) {
        if (mc.player.isGliding()) {
            if (isCurrentArmorGliding()) {
                elytraUnbreakableDurabilityCounter.reset();
            } else {
                elytraUnbreakableDurabilityCounter.count();
            }
        } else {
            elytraUnbreakableDurabilityCounter.reset();
        }

        if (shouldElytraUnbreakable() && canContinueGliding() && mc.player != null && mc.player.isGliding()) {
            if (elytraUnbreakableDurabilityCounter.executeIf(period.get())) {
                // fix bug: do not override current waiting unbreakable transaction if lagggggg
                if (pendingElytraUnbreakableTransaction
                        && !isValidElytra(mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
                    // what is wrong with the wifi
                    elytraUnbreakableDurabilityCounter.reset();
                } else {
                    elytraUnbreakableTransactionSlot = findElytraUnbreakableSwitchSlot();
                    if (elytraUnbreakableTransactionSlot == -1) {
                        mc.getNetworkHandler()
                                .sendPacket(new ClientCommandC2SPacket(
                                        mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                        //                Debug.info("send stop glide");
                        pendingElytraUnbreakableTransaction = true;
                        elytraUnbreakableDurabilityCounter.reset();
                        if (resetVanilla.get()) {
                            tickEvent.context(0);
                        }
                    } else {
                        switchSlotToArmor(elytraUnbreakableTransactionSlot);
                        pendingElytraUnbreakableTransaction = true;
                        elytraUnbreakableDurabilityCounter.reset();
                        if (resetVanilla.get()) {
                            tickEvent.context(0);
                        }
                    }
                }
            }
        } else {
            pendingElytraUnbreakableTransaction = false;
            elytraUnbreakableTransactionSlot = -1;
        }
    }

    public static boolean isUsable(ItemStack stack) {
        return stack.getDamage() < stack.getMaxDamage() - 1;
    }

    /** Ported from upstream 1.1.7.2 ElytraExtra: gates the reset_fallfly block. */
    private boolean delayResetFallFlyingFlag = false;

    // may cause fake gliding !!! must be careful
    // may cause fake gliding !!! must be careful
    public void handleEntityDataUpdate(Event<MetadataUpdate> event) {
        if (event.isCancelled()) return;
        // only when elytra unbreakable do
        var serializedEntryUpdateEvent = event.context;
        if (serializedEntryUpdateEvent.entity() instanceof ClientPlayerEntity player
                && player == mc.player
                && serializedEntryUpdateEvent.metadata().id() == VDataFlag.ID_FLAGS
                && player.isGliding()) {
            var entry = serializedEntryUpdateEvent.metadata();
            byte data = (byte) entry.value();
            boolean canRunElytraUnbreakable = false;
            boolean canRunArmorGlide = false;
            if ((data & (1 << VDataFlag.FALL_FLYING_FLAG_INDEX)) == 0) {
                elytraUnbreakableDurabilityCounter.reset();
                if (pendingElytraUnbreakableTransaction) {
                    canRunElytraUnbreakable = true;
                }
                canRunArmorGlide = true;
            } else {
                // reset delay mode
                pendingArmorGlideTransaction = false;
            }
            if ((data & (1 << VDataFlag.FALL_FLYING_FLAG_INDEX)) == 0) {
                // try start
                if (hasPendingFallFlyingReset()) {
                    data = (byte) (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX));
                    serializedEntryUpdateEvent.metadata(
                            new DataTracker.SerializedEntry(entry.id(), entry.handler(), data));
                    delayResetFallFlyingFlag = true;
                    canRunArmorGlide = false;
                    canRunElytraUnbreakable = false;
                }
            } else {
                delayResetFallFlyingFlag = false;
            }

            // reset important flag even if module is off or can not work
            // fix bugs like:
            // 1. elytraUnbreakable
            // 2. open armorGlide
            // 3. the pendingElytraUnbreakableTransaction flag was block from setting to false because armorGlide is on

            if (shouldElytraUnbreakable() && canRunElytraUnbreakable) {
                var val = serializedEntryUpdateEvent.metadata();
                data = (byte) val.value();
                serializedEntryUpdateEvent.metadata(new DataTracker.SerializedEntry(
                        val.id(), val.handler(), (byte) (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX))));
                pendingHandleElytraUnbreakableTransaction = true;
            } else if (armorFly.get() && this.armorGlideTransactionSlot != -1 && canRunArmorGlide) {
                var val = serializedEntryUpdateEvent.metadata();
                data = (byte) val.value();
                // try start
                if (armorMode.get() == ArmorFlyMode.LAZY) {
                    if (onSwitchItemArmorFallFlying()) {
                        serializedEntryUpdateEvent.metadata(new DataTracker.SerializedEntry(
                                val.id(), val.handler(), (byte) (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX))));
                        mc.getNetworkHandler()
                                .sendPacket(new ClientCommandC2SPacket(
                                        mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                        currentTickStartArmorGlideTransaction = true;
                        // we delayed the packets here to ensure that rockets are usable
                        // these rockets may not work,
                        flushRockets();
                    } else {
                        clearRockets();
                    }

                    if (
                    // armorMode.get() == Configs.AutoInvMode.LAZY &&
                    this.postTickSwapWithChestSlot != -1) {
                        switchSlotToArmor(this.postTickSwapWithChestSlot);
                        this.postTickSwapWithChestSlot = -1;
                    }
                    MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();

                } else if (armorMode.get() == ArmorFlyMode.TICK_LEGACY) {
                    if (onSwitchItemArmorFallFlying()) {
                        serializedEntryUpdateEvent.metadata(new DataTracker.SerializedEntry(
                                val.id(), val.handler(), (byte) (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX))));
                        mc.getNetworkHandler()
                                .sendPacket(new ClientCommandC2SPacket(
                                        mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                        currentTickStartArmorGlideTransaction = true;
                        flushRockets();
                    } else {
                        clearRockets();
                    }
                    // we delayed the packets here to ensure that rockets are usable

                    MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
                } else {
                    pendingArmorGlideTransaction = true;
                    serializedEntryUpdateEvent.metadata(new DataTracker.SerializedEntry(
                            val.id(), val.handler(), (byte) (data | (1 << VDataFlag.FALL_FLYING_FLAG_INDEX))));
                }
            }
        }
    }

    public boolean shouldUseMaceFix() {
        if (maceFixMode.get().isPresent() && mc.player.isGliding()) {
            if (this.isCurrentArmorGliding()) {
                return armorMode.get() == ArmorFlyMode.LAZY;
            } else return true;
        }
        return false;
    }

    public boolean shouldUseDelayMovementAttackMaceFix() {
        return maceFixMode.get().getValue().get() == Configs.CompatMode.COMPAT_GRIM;
    }

    /**
     * Whether a server-side fall-flying reset is currently outstanding.
     *
     * <p>Extracted in upstream 1.1.7.2 from the inline {@code !nextPacketResetFallFlying.isEmpty()}
     * checks. It prunes entries whose tick has already passed, so it reports only resets that are
     * still in the future.
     */
    public boolean hasPendingFallFlyingReset() {
        while (!nextPacketResetFallFlying.isEmpty() && nextPacketResetFallFlying.peekFirst() <= Tasks.getTick()) {
            nextPacketResetFallFlying.pollFirst();
        }
        return !nextPacketResetFallFlying.isEmpty()
                && nextPacketResetFallFlying.stream().anyMatch(s -> s > Tasks.getTick());
    }

    /**
     * Queues a reset of the client's fall-flying flag {@code effectiveTicks} from now.
     *
     * <p>Ported from upstream 1.1.7.2 {@code ElytraExtra.requestManualResetFallFlying}. The
     * queued tick makes {@link #hasPendingFallFlyingReset()} true, which is what keeps the
     * server from seeing us as grounded after an armour swap.
     */
    public boolean requestManualResetFallFlying(int effectiveTicks) {
        nextPacketResetFallFlying.add(Tasks.getTick() + effectiveTicks);
        return true;
    }

    /**
     * Swaps one chest-slot item in and arms a fall-flying reset, so a following mace attack still
     * counts as a smash.
     *
     * <p>Ported from upstream 1.1.7.2 {@code ElytraExtra.requestManualArmorSwapAndResetFallFlying}
     * (the "鞘翅追人: 重锤切甲修复" / mace-fix feature). A mace smash requires the server to
     * consider us gliding at the moment of impact, but swapping armour makes the server reset the
     * fall-flying flag. Doing the swap EARLY and arming the reset here means the attack lands
     * while we are still gliding, instead of being repaired after the packet has already gone out
     * (which is what {@link #handleMaceAttack} does for the older path).
     *
     * @return true when a swap was performed and a reset queued
     */
    public boolean requestManualArmorSwapAndResetFallFlying(int effectiveTicks) {
        if (checkNull() || !mc.player.isGliding() || isCurrentArmorGliding() || hasPendingFallFlyingReset()) {
            return false;
        }
        int elytraSlot = findEmptySlotForElytra();
        if (elytraSlot == -1) {
            return false;
        }
        switchSlotToArmor(elytraSlot);
        return requestManualResetFallFlying(effectiveTicks);
    }

    /** Whether a chest item can be displaced to make room for the elytra. */
    private boolean canStopFallFlying(ItemStack item) {
        return item.isEmpty() || (!isValidElytra(item) && mc.player.canEquip(item, EquipmentSlot.CHEST));
    }

    /** Ported from upstream 1.1.7.2 {@code ElytraExtra.findEmptySlotForElytra}. */
    public int findEmptySlotForElytra() {
        if (ClientPlayerAccess.of(mc.player).getServerScreenHandler() == mc.player.playerScreenHandler) {
            Predicate<ItemStack> canUnbreakablePredicate = this::canStopFallFlying;
            // use cached autoSwitch slot
            if (armorGlideTransactionSlot != -1
                    && mc.player.playerScreenHandler.slots.size() > armorGlideTransactionSlot
                    && canUnbreakablePredicate.test(mc.player
                            .playerScreenHandler
                            .slots
                            .get(armorGlideTransactionSlot)
                            .getStack())) {
                return armorGlideTransactionSlot;
            }
            if (autoSwitchTransactionSlot != -1
                    && mc.player.playerScreenHandler.slots.size() > autoSwitchTransactionSlot
                    && canUnbreakablePredicate.test(mc.player
                            .playerScreenHandler
                            .slots
                            .get(autoSwitchTransactionSlot)
                            .getStack())) {
                return autoSwitchTransactionSlot;
            }

            var re = InventoryUtils.findBestPlayerInventory(
                    (entry) -> {
                        if (entry.index() >= 36 && entry.index() != 40) return null;
                        // do not use chest item
                        var item = entry.val();
                        if (item.isEmpty()) return -3.0D;
                        if (canStopFallFlying(item)) {
                            return DamageUtils.getArmorValue(mc.player, item, EquipmentSlot.CHEST)
                                    * DamageUtils.getArmorToughnessValue(mc.player, item, EquipmentSlot.CHEST);
                        }
                        return null;
                    },
                    InventoryUtils.getPlayerInvSize(),
                    true,
                    true,
                    false);
            if (re != null) {
                return mc.player
                        .playerScreenHandler
                        .getSlotIndex(mc.player.getInventory(), re.index())
                        .orElse(-1);
            }
        }
        return -1;
    }

    public void handleMaceAttack(Event<PlayerInteractEntityC2SPacket> interactPacket) {
        if (interactPacket.isCancelled()) return;
        PlayerInteractEntityC2SPacket packet = interactPacket.context();
        if (maceFixMode.get().isPresent()
                && ((Enum) packet.type.getType()).name().equals("ATTACK")
                && mc.player.getMainHandStack().getItem() instanceof MaceItem
                && mc.player.isGliding()
                && shouldUseMaceFix()
                && !shouldUseDelayMovementAttackMaceFix()) {
            mc.getNetworkHandler()
                    .sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            PacketManager.schedulePostScheduleCallback(interactPacket.context, () -> {
                requestManualResetFallFlying(20);
            });
        }
    }

    Deque<Integer> nextPacketResetFallFlying = new ArrayDeque<>();

    //    public void attackPost(Event<PlayerInteractEntityC2SPacket> packet) {
    //        if (packet.context() == lastHandledPacket) {
    //
    //        }
    //        lastHandledPacket = null;
    //    }

    @Override
    public int priority() {
        return PRIORITY_COMMON;
    }

    /** Ported from upstream 1.1.7.2 ElytraExtra. */
    public static boolean isValidElytra(ItemStack item) {
        return VItem.getInstance().canGlide(item)
                && mc.player.canEquip(item, EquipmentSlot.CHEST)
                && !item.willBreakNextUse();
    }

    public static int findElytra() {
        // only backpack can operate
        if (ClientPlayerAccess.of(mc.player).getServerScreenHandler() == mc.player.playerScreenHandler) {
            // check hotbar first
            var re = InventoryUtils.findPlayerItem(ElytraExtra::isValidElytra, true, false, false);
            if (re != null) {
                return mc.player
                        .playerScreenHandler
                        .getSlotIndex(mc.player.getInventory(), re.index())
                        .orElse(-1);
            }
        }
        return -1;
    }

    public Deque<ItemStack> delayQueue = new ConcurrentLinkedDeque<>();

    public void sumitDelay(ItemStack stack) {
        timerVanilla.fire();
        if (stack.isEmpty() || stack.isOf(Items.FIREWORK_ROCKET)) {
            delayQueue.removeIf(s -> s.isEmpty() || s.isOf(Items.FIREWORK_ROCKET));
            delayQueue.add(stack);
        } else {
            delayQueue.add(stack);
        }
    }

    public boolean canBeUsedAsFireworks(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else if (stack.isOf(Items.FIREWORK_ROCKET)) {
            return true;
        } else {
            return false;
        }
    }
    // do not catch flushing packets
    boolean flushing = false;

    /** Ported from upstream 1.1.7.2 ElytraExtra.shouldBlockFireworkAction. */
    private boolean shouldBlockFireworkAction() {
        return isCurrentArmorGliding()
                || (pendingElytraUnbreakableTransaction && elytraUnbreakableTransactionSlot != -1)
                || hasPendingFallFlyingReset();
    }
    // todo rewrite this shit
    public void onUseFireworks(Event<PlayerInteractItemC2SPacket> packet) {
        if (!flushing && shouldBlockFireworkAction()) {
            ItemStack stack =
                    PlayerInteractItemC2SPacketAccess.of(packet.context).getItemStack();
            if (!stack.isEmpty()) {
                // make a stackCopy of origin item with 1 count
                if (canBeUsedAsFireworks(stack)) {
                    // 40-> offhand
                    sumitDelay(stack.copyWithCount(1));
                    packet.cancel();
                    NetworkUtils.restoreSequence(packet.context.getSequence());
                }
            }
        }
    }

    public void sendCustomUseFireworkPacket() {
        sendCustomUseFireworkPacket(PlayerStateManager.INSTANCE.lastPitch, PlayerStateManager.INSTANCE.lastYaw);
    }

    public void sendCustomUseFireworkPacket(float pitch, float yaw) {
        if (shouldBlockFireworkAction()) {
            sumitDelay(ItemStack.EMPTY);
        } else {
            sendUsePacket(pitch, yaw);
        }
        CombatTasks.getBlink().onFireworkUse();
    }

    public ItemStack findRocket() {
        var re = InventoryUtils.findPlayerBackpackItem(this::canBeUsedAsFireworks, false, true);
        return re != null ? re.val().getStack() : null;
    }

    public int getRocketLevel(ItemStack stack) {
        if (stack.isOf(Items.FIREWORK_ROCKET)) {
            FireworksComponent component = stack.get(DataComponentTypes.FIREWORKS);
            if (component != null) {
                return 1 + component.flightDuration();
            }
        }
        return 1;
    }

    private void sendUsePacket(float pitch, float yaw) {
        timerVanilla.fire();
        ItemStack stack = mc.player.getStackInHand(Hand.MAIN_HAND);
        if (canBeUsedAsFireworks(stack)) {
            sendFirework(Hand.MAIN_HAND, yaw, pitch);
            onHasFirework();
        } else {
            stack = mc.player.getStackInHand(Hand.OFF_HAND);
            if (canBeUsedAsFireworks(stack)) {
                sendFirework(Hand.OFF_HAND, yaw, pitch);
                onHasFirework();
            } else {
                // check hotbars
                var findResult = InventoryUtils.findPlayerBackpackItem(this::canBeUsedAsFireworks, false, true);
                if (findResult != null) {
                    Runnable callback = InvExtra.INSTANCE.swapInventorySlotToOffhand(findResult.index());
                    if (callback != null) {
                        sendFirework(Hand.OFF_HAND, yaw, pitch);
                        callback.run();
                    }
                    onHasFirework();
                } else {
                    onNoFireworks();
                }
            }
        }
    }

    public void switchSlotToArmor(int idx) {
        int armorSlot = 6;
        int targetSlot = idx;
        if (mc.player.playerScreenHandler == ClientPlayerAccess.of(mc.player).getServerScreenHandler()) {
            InvExtra.INSTANCE.swapScreenSlots(armorSlot, targetSlot);
        }
    }

    public static boolean canContinueGliding() {
        return !mc.player.isTouchingWater()
                && !mc.player.getAbilities().flying
                && !mc.player.isOnGround()
                && !mc.player.hasVehicle()
                && !mc.player.hasStatusEffect(StatusEffects.LEVITATION);
    }

    public boolean canContinueArmorGliding() {
        boolean liquidFlyFlag;
        if (enableLiquidFly.get()) {
            liquidFlyFlag =
                    !mc.player.isTouchingWater() || !enableLiquidFlyOnlyFireworks.get() || canFireworkControlMotion(0);
        } else {
            liquidFlyFlag = !mc.player.isTouchingWater();
        }
        boolean onGroundFlag;
        if (enableOnGroundFly.get()) {
            onGroundFlag = !PlayerStateManager.INSTANCE.lastHasGroundSupport
                    || canFireworkControlMotion(0)
                    || !mc.player.isOnGround();
        } else {
            onGroundFlag = !mc.player.isOnGround();
        }
        return liquidFlyFlag
                && !mc.player.getAbilities().flying
                && onGroundFlag
                && !mc.player.hasVehicle()
                && !mc.player.hasStatusEffect(StatusEffects.LEVITATION);
    }

    public static boolean hasGlidingEquipments() {
        EquipmentSlot equipmentSlot = EquipmentSlot.CHEST;
        return mc.player.canGlideWith(mc.player.getEquippedStack(equipmentSlot), equipmentSlot);
    }

    public static boolean hasGlidingItem() {
        return findElytra() != -1;
        // InventoryUtils.findPlayerItem((vv) -> mc.player.canGlideWith(vv, EquipmentSlot.CHEST), false, false)
        // != null;
    }

    public static boolean hasFireworks() {
        return InventoryUtils.findPlayerBackpackItem(ElytraExtra.INSTANCE::canBeUsedAsFireworks, false, true) != null;
    }

    int manuallySwitchTick = 0;

    public void onStartFallFlying(Event<Boolean> booleanEvent) {
        // not fallFlying, and not suitable for gliding
        // check armor fly
        // reset fly transaction
        // this is a check during the flying
        if ((Boolean) booleanEvent.getArgs(0) || booleanEvent.isCancelled()) {
            return;
        }
        this.armorGlideTransactionSlot = -1;

        // reset armor fly status
        if (!booleanEvent.context()) {
            if (armorFly.get()) {
                if (canContinueGliding()) {
                    // check equipments
                    if (onSwitchItemArmorFallFlying()) {
                        booleanEvent.context(Boolean.TRUE);
                        rocketFlush.state(true);
                    }
                }
            } else if (autoSwitch.get()) {
                // auto switch if not armorFly;
                if (canContinueGliding()) {
                    if (onAutoSwitchItemFallFlying(true)) {
                        booleanEvent.context(Boolean.TRUE);
                    }
                }
            }
        }
    }

    public void onMonitorFallFlying(Event<Boolean> booleanEvent) {
        if (booleanEvent.isCancelled()) return;
        if (booleanEvent.context) {
            manuallySwitchTick = Tasks.getTick();
        }
    }

    public boolean onSwitchItemArmorFallFlying() {
        if (!canContinueArmorGliding()) return false;
        ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!isValidElytra(stack)) {
            // switch one
            int elytraIndex = findElytra();
            if (elytraIndex != -1) {
                // ARMOR FLIGHT
                switchSlotToArmor(elytraIndex);
                postTickSwapWithChestSlot = elytraIndex;
                armorGlideTransactionSlot = postTickSwapWithChestSlot;
                currentTickStartArmorGlideTransaction = true;
                // mc.player.input.playerInput =
                // PlayerInputUtils.of(mc.player.input.playerInput).sprint(false).sneak(false).jump(true).forward(false).backward(false).right(false).left(false).toPlayerInput();
                return true;
            }
            return false;
        } else {
            postTickSwapWithChestSlot = -1;
            return true;
        }
    }

    public void endArmorFlyTransaction(boolean continueFly) {
        if (armorGlideTransactionSlot != -1) {
            boolean willContinueFly = continueFly && canContinueGliding();
            // current ArmoFly
            // switch Elytra on
            // 注意到我们的傻逼延迟任务没有处理干净
            // 我们需要在这里处理干净我们的傻逼延迟业务】
            // 不管是结束飞行还是继续飞行都要
            boolean hasDelayedTakeOffShit = false;
            if (this.postTickSwapWithChestSlot == -1
                    && this.pendingArmorGlideTransaction
                    && armorMode.get().isNotIn(ArmorFlyMode.LAZY)) {
                pendingArmorGlideTransaction = false;
                hasDelayedTakeOffShit = true;
            }
            if (willContinueFly) {
                switchSlotToArmor(armorGlideTransactionSlot);
                if (hasDelayedTakeOffShit) {
                    mc.getNetworkHandler()
                            .sendPacket(new ClientCommandC2SPacket(
                                    mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    flushRockets();
                    MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
                    if (armorFlyBadPacketFix.get()) {
                        mc.getNetworkHandler().sendPacket(new CommonPongC2SPacket(Integer.MIN_VALUE));
                    }
                } else {
                    // wait next delay takeoff shit
                    nextPacketResetFallFlying.add(
                            Tasks.getTick() + closeContinueFly.get().getValue());
                    autoTakeOffFlag = true;
                }
                //                nextPacketResetFallFlying.add(Tasks.getTick() + closeContinueFly.get().getValue());
                //                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player,
                // ClientCommandC2SPacket.Mode.START_FALL_FLYING));

            } else {
                if (hasDelayedTakeOffShit) {
                    clearRockets();
                    EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                }
            }
            onEndArmorFlyTransaction(willContinueFly);
        }
    }

    public void onEndArmorFlyTransaction(boolean willContinueGliding) {
        if (armorGlideTransactionSlot != -1) {
            if (mc.player.isOnGround() && !mc.player.isGliding() && armorFly.get() && landAutoClose.get()) {
                HotKeyUtils.wrapFlagAsToggle(armorFlyPath.add("enable").toPath(), armorFly)
                        .run();
            }
            if (landAutoSneak.get() && !mc.player.isSneaking()) {
                nextLandingSneak = 2;
            }
            // use autoSwitch for
            if (willContinueGliding) {
                broadcastSwapBackSlot(armorGlideTransactionSlot);
            }
        }
        armorGlideTransactionSlot = -1;
        armorGlideTransactionAbort = false;
    }

    // Ported from upstream 1.1.7.2 ElytraExtra (onArmorGlideDelay state).
    boolean currentDelayingArmorGlide;
    Integer lastTransactionRecv = Integer.MIN_VALUE;
    volatile Integer currentDelayingLastTransaction = null;
    boolean acceptFireworkRemovalDuringDelay = false;

    /**
     * Ported from upstream 1.1.7.2 ElytraExtra. Records which slot must be swapped back to on
     * the next transaction, and (while a transaction is open) makes that the armour-glide slot.
     * IMG previously assigned {@code autoSwitchTransactionSlot} directly and never updated
     * {@code armorGlideTransactionSlot}, so the chest piece was never put back.
     */
    private void broadcastSwapBackSlot(int swapBack) {
        if (autoSwitch.get() && mc.player.isGliding()) {
            autoSwitchTransactionSlot = swapBack;
        }
        if (this.armorGlideTransactionSlot != -1) {
            this.armorGlideTransactionSlot = swapBack;
        }
    }

    private void clearSwapBackSlot() {
        this.autoSwitchTransactionSlot = -1;
        this.elytraUnbreakableTransactionSlot = -1;
    }

    public void startArmorFlyTransaction(int value) {
        if (checkNull()) return;
        if (armorGlideTransactionSlot == -1) {
            if (value != -1
                    && value < mc.player.playerScreenHandler.slots.size()
                    && canStopFallFlying(
                            mc.player.playerScreenHandler.slots.get(value).getStack())) {
                armorGlideTransactionSlot = value;
                clearSwapBackSlot();
                switchSlotToArmor(value);

            } else {
                if (this.elytraUnbreakableTransactionSlot != -1) {
                    // during a elytraUnbreakableSwitch
                    this.armorGlideTransactionSlot = this.elytraUnbreakableTransactionSlot;
                    clearSwapBackSlot();
                    this.pendingElytraUnbreakableTransaction = false;
                    // use their cache
                } else if (hasGlidingEquipments()) {
                    ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
                    if (isValidElytra(stack)) {
                        int slot = findEmptySlotForElytra();
                        if (slot != -1) {
                            armorGlideTransactionSlot = slot;
                            clearSwapBackSlot();
                            switchSlotToArmor(slot);
                        }
                    }
                }
            }
        }
    }

    public void onToggleArmorFly(boolean armorFly) {
        if (mc.player != null && mc.player.isGliding()) {
            if (!armorFly) {
                endArmorFlyTransaction(closeContinueFly.get().isPresent());
            } else {
                startArmorFlyTransaction(-1);
            }
        }
    }

    public boolean onAutoSwitchItemFallFlying(boolean stopSprint) {
        ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!isValidElytra(stack)) {
            // switch one
            int elytraIndex = findElytra();
            if (elytraIndex != -1) {
                // ARMOR FLIGHT
                autoSwitchTransactionSlot = elytraIndex;
                switchSlotToArmor(elytraIndex);
                currentTickStartArmorGlideTransaction = true;
                return true;
            }
            return false;
        } else {
            autoSwitchTransactionSlot = -1;
            return true;
        }
    }

    public int postTickSwapWithChestSlot = -1;
    public int armorGlideTransactionSlot = -1;
    public boolean armorGlideTransactionAbort = false;
    public int autoSwitchTransactionSlot = -1;
    public boolean pendingArmorGlideTransaction = false;

    boolean currentTickStartArmorGlideTransaction = false;

    public boolean isThisTickArmoGlideMovementServerSideGlide() {
        return pendingArmorGlideTransaction || currentTickStartArmorGlideTransaction;
    }

    StateExecutor rocketFlush = new StateExecutor();

    boolean setback = false;

    public void onSetBack(Event<PlayerPositionLookS2CPacket> setbackPacket) {
        setback = true;
    }

    public void flushRockets() {
        flushing = true;
        int selected = InventoryUtils.getSelectedSlot();
        Runnable callback = null;
        try {
            float lastYaw = PlayerStateManager.INSTANCE.lastYaw;
            float lastPitch = PlayerStateManager.INSTANCE.lastPitch;
            while (!delayQueue.isEmpty()) {
                var packetEntry = delayQueue.poll();
                if (!packetEntry.isEmpty()) {
                    var findResult = InventoryUtils.findPlayerItem(
                            (it) -> ItemStack.areItemsAndComponentsEqual(packetEntry, it), false, false);
                    if (findResult != null) {
                        if (findResult.index() == InventoryUtils.getSelectedSlot()) {
                            timerVanilla.fire();
                            sendFirework(Hand.MAIN_HAND, lastYaw, lastPitch);
                        } else {
                            callback =
                                    InvExtra.INSTANCE.swapItemToHand(findResult.index(), true, GhostHandMode.INV_SWAP);
                            if (callback != null) {
                                timerVanilla.fire();
                                sendFirework(Hand.OFF_HAND, lastYaw, lastPitch);
                                callback.run();
                            }
                        }
                    }
                } else {
                    sendUsePacket(lastYaw, lastPitch);
                }
            }
        } finally {
            flushing = false;
            PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(selected);
        }
    }

    public void clearRockets() {
        delayQueue.clear();
    }

    // ArmorFly works
    // tested in 3c3u.uno, 20260311
    // tested in mc.loyisa.cn 1.21.1 20260311
    // could not pass GrimAC > 1.21.2 in loyisa due to inventory packets disorders and player input packet check

    int nextFlyFireworksTicks = 0;

    public void onElytraFlightStart(Event<Boolean> takeOff) {
        if (takeOff.context
                && (flyRocketOnFirstOff.get()
                        ||
                        // faster takeoff
                        (MovTasks.getElytraFlight().shouldFlyRocketOnFirstOff())
                        || (autoTakeOffFireworksFromGround.get() && lastOnGroundOrInWaterAutoTakeOff))) {
            nextFlyFireworksTicks = Tasks.getTick();
        }
    }

    public void onElytraFlightRocket(Event<ClientCommandC2SPacket> event) {
        if (checkNull()) return;
        if (nextFlyFireworksTicks == Tasks.getTick()
                && mc.player.isGliding()
                && event.context.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING
                && checkFireworkUseCondition(false)) {
            rocketFlush.state(true);
            sumitDelay(ItemStack.EMPTY);
            // fix sprint issues
            mc.player.setSprinting(false);
            PlayerInputUtils.of(mc.player).sprint(false).applyInput(mc.player);
        }
    }

    public boolean shouldApplyOnGroundFly() {
        return mc.player.isGliding() && armorFly.get() && armorGlideTransactionSlot != -1 && enableOnGroundFly.get();
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        tickElytraSimulation();
        // resetFallFly
        reset_fallfly:
        if (delayResetFallFlyingFlag) {
            delayResetFallFlyingFlag = false;
            while (!nextPacketResetFallFlying.isEmpty()) {
                var it = nextPacketResetFallFlying.poll();
                if (it > Tasks.getTick()) {
                    if (canContinueGliding()) {
                        if (hasGlidingEquipments()) {

                            MovTasks.getMovExtra().sendPacketsForPreStartFallFlying();
                            mc.getNetworkHandler()
                                    .sendPacket(new ClientCommandC2SPacket(
                                            mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                            flushRockets();
                            MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();

                            break reset_fallfly;
                        } else {
                            var entry = findElytra();
                            if (entry != -1) {
                                switchSlotToArmor(entry);
                                MovTasks.getMovExtra().sendPacketsForPreStartFallFlying();
                                mc.getNetworkHandler()
                                        .sendPacket(new ClientCommandC2SPacket(
                                                mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                                flushRockets();
                                MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
                                break reset_fallfly;
                            }
                        }
                    } else {
                        nextPacketResetFallFlying.clear();
                    }
                    EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                    clearRockets();
                    break reset_fallfly;
                }
            }
            clearRockets();
            EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
        }
        armor_fly:
        if (armorFly.get() && player.isGliding()) {
            if (isCurrentArmorGlidingAbortState()) {
                // postTickSwapWithChestSlot = -1;
                // trigger flush rockets
                rocketFlush.state(true);
            }
            boolean canGlide = canContinueGliding();
            if (armorMode.get() != ArmorFlyMode.LAZY && this.postTickSwapWithChestSlot == -1) {
                if (pendingArmorGlideTransaction) {
                    pendingArmorGlideTransaction = false;
                    if (onSwitchItemArmorFallFlying()) {

                        mc.getNetworkHandler()
                                .sendPacket(new ClientCommandC2SPacket(
                                        mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                        currentTickStartArmorGlideTransaction = true;
                        flushRockets();
                        MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
                        if (armorFlyBadPacketFix.get()) {
                            mc.getNetworkHandler().sendPacket(new CommonPongC2SPacket(Integer.MIN_VALUE));
                        }
                    } else {
                        clearRockets();
                        EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
                        onEndArmorFlyTransaction(false);
                    }
                } else if (armorMode.get() == ArmorFlyMode.TICK_LEGACY) {
                    if (canGlide) {
                        if (!isValidElytra(player.getEquippedStack(EquipmentSlot.CHEST))) {
                            int idx = findElytra();
                            if (idx != -1) {
                                switchSlotToArmor(idx);
                                this.postTickSwapWithChestSlot = idx;
                                this.armorGlideTransactionSlot = this.postTickSwapWithChestSlot;
                            }
                        } else {
                            // switch to origin armor
                            this.postTickSwapWithChestSlot = armorGlideTransactionSlot;
                        }
                    } else {
                        onEndArmorFlyTransaction(false);
                    }
                }
            }

        } else {
            onEndArmorFlyTransaction(false);
        }
        // todo handle this
        if (pendingHandleElytraUnbreakableTransaction) {
            pendingHandleElytraUnbreakableTransaction = false;

            pendingElytraUnbreakableTransaction = false;
            elytraUnbreakableTransactionSlot = findElytra();
            if (elytraUnbreakableTransactionSlot != -1) {
                switchSlotToArmor(elytraUnbreakableTransactionSlot);
            }
            // cancel stop fallflying only when can continue
            if (canContinueGliding()) {
                mc.getNetworkHandler()
                        .sendPacket(
                                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                if (!mc.player.isGliding()) {
                    EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, true);
                }

                currentTickStartArmorGlideTransaction = true;
                if (elytraUnbreakableTransactionSlot != -1) {
                    flushRockets();
                }
                MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
            } else {
                clearRockets();
                EntityInternalAccess.of(mc.player).setDataFlag(VDataFlag.FALL_FLYING_FLAG_INDEX, false);
            }

            elytraUnbreakableTransactionSlot = -1;
        }
        if (!mc.player.isGliding() && pendingElytraUnbreakableTransaction) {
            pendingElytraUnbreakableTransaction = false;
            // unexpected behaviour:
            if (shouldElytraUnbreakable()) {
                if (elytraUnbreakableTransactionSlot != -1) {
                    switchSlotToArmor(elytraUnbreakableTransactionSlot);
                }
                // cancel stop fallflying only when can continue
                if (canContinueGliding()) {
                    logI18N("message.module.elytra-extra.unexpected-unbreakable-state");
                    mc.player.startGliding();
                    mc.getNetworkHandler()
                            .sendPacket(new ClientCommandC2SPacket(
                                    mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    currentTickStartArmorGlideTransaction = true;
                    if (elytraUnbreakableTransactionSlot != -1) {
                        flushRockets();
                    }
                    MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
                } else {
                    clearRockets();
                }
            }

            elytraUnbreakableTransactionSlot = -1;
        }
        if (autoSwitchTransactionSlot != -1) {
            if (!player.isGliding()) {
                switchSlotToArmor(autoSwitchTransactionSlot);
                autoSwitchTransactionSlot = -1;
            }
        }
        if (shouldApplyOnGroundFly()) {
            // on Ground Fly
            if (PlayerStateManager.INSTANCE.lastHasGroundSupport && mc.player.getPitch() > 0) {
                mc.player.setPitch(0);
                movementManagerEvent.context.pushImportantRotation(true, false);
                movementManagerEvent.context.markForResetRot();
            }
        }
        rocket_boost:
        if (player.isGliding() && rocketBoost.get() && canFireworkControlMotion()) {
            boolean baritoneElytraProcessing = BaritoneHooks.getInstance().isBaritoneElytraProcessing()
                    && !BaritoneHooks.getInstance()
                            .getBaritoneCurrentMoveRot(mc.player)
                            .equals(new Vec2f(mc.player.getPitch(), mc.player.getYaw()));
            Vec3d currentTarget;
            if (baritoneElytraProcessing) {
                if (!baritoneUseRocketBoost.get()) {
                    break rocket_boost;
                }
                //                if(player.getVelocity().length() < rocketBoostSpeed.get()){
                //
                // player.setVelocity(player.getVelocity().normalize().multiply(rocketBoostSpeed.get()));
                //                }
                var pitchYaw = BaritoneHooks.getInstance().getBaritoneCurrentMoveRot(mc.player);
                currentTarget = player.getVelocity();
                if (rocketBoostUseRescale.get()) {
                    if (pitchYaw.x == PlayerStateManager.INSTANCE.lastPitch
                            && pitchYaw.y == PlayerStateManager.INSTANCE.lastYaw) {
                        BaritoneHooks.getInstance().updateBaritoneLookTarget(pitchYaw.x, pitchYaw.y + 0.01F);
                        pitchYaw = BaritoneHooks.getInstance().getBaritoneCurrentMoveRot(mc.player);
                    }
                    mc.player.setVelocity(applyAxisLimit(
                            currentTarget.normalize().multiply(rocketBoostSpeed.get()),
                            pitchYaw.x,
                            pitchYaw.y,
                            mc.player.hasNoGravity()));
                    Vec3d simulation = mc.player.getVelocity();
                    Vec3d move = MovTasks.simulateMovement(mc.player, mc.player.getEntityPos(), simulation, false);
                    if (move.squaredDistanceTo(simulation) < 0.01) {
                        break rocket_boost;
                    }
                    setOverridingFireworkVelocity(null);
                }
                if (player.getVelocity().length() < rocketBoostSpeed.get()) {
                    player.setVelocity(currentTarget.normalize().multiply(rocketBoostSpeed.get()));
                }
            } else {
                currentTarget = EntityUtils.pitchYawToRotation(player.getPitch(), player.getYaw());
                if (rocketBoostUseRescale.get()) {
                    float newYaw = mc.player.getYaw() + ((Tasks.getTick() % 2 == 0) ? 0.01F : -0.01F);
                    mc.player.setYaw(newYaw);
                    mc.player.setVelocity(applyAxisLimit(
                            currentTarget.normalize().multiply(rocketBoostSpeed.get()),
                            mc.player.getPitch(),
                            newYaw,
                            mc.player.hasNoGravity()));
                } else {
                    player.setVelocity(currentTarget.normalize().multiply(rocketBoostSpeed.get()));
                }
            }
        }
        // slow falling with no crash
        if (false && player.isGliding()) {
            if (!movementManagerEvent.context().hasImportantRotation()) {
                movementManagerEvent.context.markForResetRot();
                player.setPitch(0.0F);
                if (Tasks.getTick() % 2 == 0) {
                    PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                }
                movementManagerEvent.context.pushImportantRotation(true, true);
            }
        }
    }

    EntityDimensions pose = null;
    int triggerKinetic = 0;
    boolean executeNoKineticAfterTravel = false;

    @Override
    public void applyBeforeTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
        if (movementManagerEvent.isCancelled()) {
            return;
        }

        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;

        if (isCurrentArmorGliding()) {
            // fix boundingbox error
            if (poseFix.get()) {
                player.setPose(EntityPose.STANDING);
            } else {
                pose = player.dimensions;
                player.dimensions = player.getDimensions(EntityPose.STANDING);
                player.setBoundingBox(player.dimensions.getBoxAt(player.getEntityPos()));
            }
        } else if (hasPendingFallFlyingReset()) {
            pose = player.dimensions;
            player.dimensions = player.getDimensions(EntityPose.STANDING);
            player.setBoundingBox(player.dimensions.getBoxAt(player.getEntityPos()));
        }

        executeNoKineticAfterTravel = false;
        if (noKineticMode.get().isPresent() && armorGlideTransactionSlot == -1 && player.isGliding()) {
            boolean canControl = anyAliveRocket();
            if (noKineticMode.get().getValue().get().hasAc()) {
                // control by rotation and velocity
                // simulation
                Vec3d vec3d = mc.player.getVelocity().multiply(4);
                Vec3d simu2 = MovTasks.simulateMovement(player, mc.player.getEntityPos(), vec3d, false);
                if (vec3d.horizontalLength() > 0.3 && !MathHelper.approximatelyEquals(simu2.x, vec3d.x)
                        || !MathHelper.approximatelyEquals(simu2.z, vec3d.z)) {

                    // player.setVelocity(vec3d.multiply(0.3 / speed));
                    movementManagerEvent.context.markForResetRot();
                    triggerKinetic = 2;
                    // it can work, don't move it
                    if (canControl) {
                        EntityUtils.setEntityPitchSafe(player, (-90f + 1e-3f));
                        PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                    } else {
                        PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                    }
                } else if (triggerKinetic > 0) {
                    triggerKinetic--;
                    movementManagerEvent.context.markForResetRot();
                    if (canControl) {
                        EntityUtils.setEntityPitchSafe(player, (-90f + 1e-3f));
                        PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                    } else {
                        PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                    }
                }
            } else {
                executeNoKineticAfterTravel = true;
            }
        }
        // movementManagerEvent.cancel();
    }

    @Override
    public void applyAfterTravelTick(Event<LegalMovementManager> movementManagerEvent, Event<Vec3d> moveEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (pose != null) {
            player.dimensions = pose;
        }
        pose = null;
        if (executeNoKineticAfterTravel) {

            //                Vec3d simu = MovTasks.simulateMovement(player, mc.player.getEntityPos(), vec3d, false);
            //                Vec3d predictedPos = mc.player.getEntityPos().add(simu);
            boolean controlled = false;
            boolean canControl = anyAliveRocket();
            if (true) {
                // use firework to control server motion
                Vec3d vec3d = mc.player.getRotationVector().multiply(0.85 * 6);
                Vec3d simu2 = MovTasks.simulateMovement(player, mc.player.getEntityPos(), vec3d, false);
                if (!MathHelper.approximatelyEquals(simu2.x, vec3d.x)
                        || !MathHelper.approximatelyEquals(simu2.z, vec3d.z)) {

                    // player.setVelocity(vec3d.multiply(0.3 / speed));
                    triggerKinetic = 1;
                    movementManagerEvent.context.markForResetRot();
                    if (canControl) {
                        EntityUtils.setEntityPitchSafe(
                                player, (triggerKinetic % 2 == 0) ? (-90f + 1e-3f) : (90f - 1e-3f));
                    } else {
                        PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                    }
                    controlled = true;
                }
            }
            if (!controlled && !canControl) {
                Vec3d vec3d = mc.player.getVelocity().multiply(6);
                Vec3d simu2 = MovTasks.simulateMovement(player, mc.player.getEntityPos(), vec3d, false);
                if (!MathHelper.approximatelyEquals(simu2.x, vec3d.x)
                        || !MathHelper.approximatelyEquals(simu2.z, vec3d.z)) {

                    // player.setVelocity(vec3d.multiply(0.3 / speed));
                    triggerKinetic = 2;
                    movementManagerEvent.context.markForResetRot();
                    if (canControl) {
                        EntityUtils.setEntityPitchSafe(
                                player, (triggerKinetic % 2 == 0) ? (-90f + 1e-3f) : (90f - 1e-3f));
                    } else {
                        PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                    }
                    controlled = true;
                }
            }

            if (!controlled && triggerKinetic > 0) {
                triggerKinetic -= 1;
                movementManagerEvent.context.markForResetRot();
                // EntityUtils.setEntityPitchSafe(player,-player.getPitch());
                if (canControl) {
                    EntityUtils.setEntityPitchSafe(player, (triggerKinetic % 2 == 0) ? (-90f + 1e-3f) : (90f - 1e-3f));
                } else {
                    PlayerStateManager.setPlayerYawSafe(player, player.getYaw() + 180);
                }
                controlled = true;
            }
        }
    }

    //    FireworkRocketEntity lastRecordedWorldFireworkRocket;
    final Set<FireworkRocketEntity> recordedWorldFireworkRockets = ConcurrentHashMap.newKeySet();
    int lastOneFireworkRemovalTime = 0;
    int lastFireworkSpawnTime = 0;
    // boolean lastFireworkIsDeadSignal = false;

    public void onFireworkOwner(Event<MetadataUpdate> firework) {
        if (firework.context().metadata().id() == VDataFlag.ID_FIREWORK_SHOOTER_ID
                && firework.context().entity() instanceof FireworkRocketEntity fireworkEntity
                && mc.player != null
                && mc.player.isGliding()
                && firework.context().metadata().value() instanceof OptionalInt opint
                && opint.isPresent()
                && opint.getAsInt() == mc.player.getId()) {
            recordedWorldFireworkRockets.add(fireworkEntity);
            lastFireworkSpawnTime = Tasks.getTick();
        }
    }

    public void onFireworkRemove(Event<Entity> entityRemoveEvent) {
        if (entityRemoveEvent.context() instanceof FireworkRocketEntity fire) {
            onRemoveFirework(fire);
        }
    }

    /** Ported from upstream 1.1.7.2 ElytraExtra.onFireworkRemovePacketPeek. */
    public void onFireworkRemovePacketPeek(Event<net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket> eventRemoval) {
        if (checkNull()) return;
        new HashSet<>(this.recordedWorldFireworkRockets)
                .stream()
                        .filter(s -> eventRemoval.context.getEntityIds().contains(s.getId()))
                        .forEach(this::onRemoveFirework);
    }

    private boolean containsFireworkRemoval(net.minecraft.network.packet.Packet<?> packet) {
        FireworkRocketEntity lastEntity = recordedWorldFireworkRockets.stream()
                .filter(EntityUtils::isEntityValid)
                .findFirst()
                .orElse(null);
        if (lastEntity == null) {
            return false;
        }
        int id = lastEntity.getId();
        net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket destroyS2C = null;
        if (packet instanceof net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket removeEntity) {
            var intList = removeEntity.getEntityIds();
            if (intList.contains(id)) {
                destroyS2C = removeEntity;
            } else {
                return false;
            }
        } else if (packet instanceof net.minecraft.network.packet.s2c.play.BundleS2CPacket bundlePacket) {
            for (var bundle : bundlePacket.getPackets()) {
                if (bundle instanceof net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket removeEntity) {
                    var intList = removeEntity.getEntityIds();
                    if (intList.contains(id)) {
                        destroyS2C = removeEntity;
                        break;
                    } else {
                        continue;
                    }
                }
            }
        } else {
            return false;
        }
        return destroyS2C != null;
    }

    /** Ported from upstream 1.1.7.2 ElytraExtra.onFireworkRemoval. */
    public void onFireworkRemoval(Event<com.codigohasta.addon.utils.slimefunhelper.events.packets.PacketStorage> eventRemoval) {
        if (eventRemoval.isCancelled()) return;
        if (PacketManager.isAsyncOrNotTransactionS2CPacket(eventRemoval.context.packetType())) {
            return;
        }
        boolean notWork = checkNull() || !fireworksLagDelay.get() || !mc.player.isGliding();

        if (currentDelayingLock != null) {
            eventRemoval.cancel();
            if (notWork
                    || eventRemoval.context.packetType() == net.minecraft.network.packet.PlayPackets.PLAYER_POSITION
                    || eventRemoval.context.timestampMS() > lastStartDelayOrReleaseMs + fireworksDelayMS.get()) {
                currentDelayingLock = null;
                PacketManager.scheduleImmediateFlush();
            }
            return;
        }
        if (notWork) {
            return;
        }

        // restart a lock
        if (eventRemoval.context.timestampMS() > lastStartDelayOrReleaseMs + fireworksDelayMS.get() + 50
                && eventRemoval.context instanceof com.codigohasta.addon.utils.slimefunhelper.events.packets.PacketStorageImpl impl
                && recordedWorldFireworkRockets.stream()
                                .filter(EntityUtils::isEntityValid)
                                .count()
                        == 1) {

            if (containsFireworkRemoval(impl.packet())) {
                eventRemoval.cancel();
                currentDelayingLock = new byte[0];
                lastStartDelayOrReleaseMs = System.currentTimeMillis();
            }
        }
    }

    /** Ported from upstream 1.1.7.2 ElytraExtra.onPacketQueueFlush. */
    public void onPacketQueueFlush(Event<Void> event) {
        currentDelayingLock = null;
        lastStartDelayOrReleaseMs = 0L;
    }

    /** Ported from upstream 1.1.7.2 ElytraExtra.onPacketPing. */
    public void onPacketPing(Event<net.minecraft.network.packet.s2c.common.CommonPingS2CPacket> event) {
        if (checkNull()) return;
        if (event.isCancelled()) return;
        Integer sendTrans = currentDelayingLastTransaction;
        if (sendTrans != null && event.context.getParameter() == sendTrans) {
            currentDelayingLastTransaction = null;
            event.cancel();
        }
    }

    /** Ported from upstream 1.1.7.2 ElytraExtra.onPacketPong. */
    public void onPacketPong(Event<net.minecraft.network.packet.c2s.common.CommonPongC2SPacket> event) {
        if (checkNull()) return;
        if (event.isCancelled()) return;
        if (lastTransactionRecv != null && lastTransactionRecv == event.context.getParameter()) {
            lastTransactionRecv = null;
        }
        Integer sendTrans = currentDelayingLastTransaction;
        if (sendTrans != null && sendTrans == event.context.getParameter()) {
            currentDelayingLastTransaction = null;
            event.cancel();
        }
    }

    /** Ported from upstream 1.1.7.2 ElytraExtra.onArmorGlideDelay. */
    public void onArmorGlideDelay(Event<com.codigohasta.addon.utils.slimefunhelper.events.packets.PacketStorage> event) {
        if (PacketManager.isAsyncOrNotTransactionS2CPacket(event.context.packetType())) {
            return;
        }
        if (event.context instanceof com.codigohasta.addon.utils.slimefunhelper.events.packets.PacketStorageImpl impl
                && impl.packet() instanceof net.minecraft.network.packet.s2c.common.CommonPingS2CPacket pingPacket) {
            lastTransactionRecv = pingPacket.getParameter();
        }
        boolean notWorking = checkNull()
                || !isCurrentArmorGliding()
                || !armorGlideMaxDelayTicks.get().isPresent();
        if (currentDelayingArmorGlide) {
            event.cancel();

            if (event.context instanceof com.codigohasta.addon.utils.slimefunhelper.events.packets.PacketStorageImpl impl
                    && event.<Boolean>getArgs(1)
                    && containsFireworkRemoval(impl.packet())) {
                acceptFireworkRemovalDuringDelay = true;
            }
            if (notWorking
                    || (Tasks.getTick() - PlayerStateManager.INSTANCE.lastStartGlidingTick
                                    >= armorGlideMaxDelayTicks.get().getValue()
                            || (event.context instanceof com.codigohasta.addon.utils.slimefunhelper.events.packets.PacketStorageImpl impl
                                    && impl.packet() instanceof net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket status
                                    && status.getEntity(mc.world) == mc.player
                                    && status.getStatus() == net.minecraft.entity.EntityStatuses.USE_TOTEM_OF_UNDYING))) {
                currentDelayingArmorGlide = false;
                currentDelayingLastTransaction = null;
                acceptFireworkRemovalDuringDelay = false;
                PacketManager.scheduleImmediateFlush();
            }
            return;
        }
        if (notWorking) {
            return;
        }

        // give enough time to peek the transaction packet before metadata update comes, and 10ms isn't a big deal
        if (event.context.timestampMS() > System.currentTimeMillis() - 10) {
            event.cancel();
        }
        if (Tasks.getTick() - PlayerStateManager.INSTANCE.lastStartGlidingTick
                        < armorGlideMaxDelayTicks.get().getValue()
                && event.context instanceof com.codigohasta.addon.utils.slimefunhelper.events.packets.PacketStorageImpl impl) {
            Iterable<net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket> list;
            if (impl.packet() instanceof net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket update
                    && update.id() == mc.player.getId()) {
                list = List.of(update);
            } else if (impl.packet() instanceof net.minecraft.network.packet.s2c.play.BundleS2CPacket bundle) {
                List<net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket> list0 = new ArrayList<>();
                for (var re : bundle.getPackets()) {
                    if (re instanceof net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket update
                            && update.id() == mc.player.getId()) {
                        list0.add(update);
                    }
                }
                if (list0.isEmpty()) return;
                list = list0;
            } else {
                return;
            }
            Boolean finalGlidingOverrideState = null;
            for (var update : list) {
                for (var re : update.trackedValues()) {
                    if (re.id() == VDataFlag.ID_FLAGS) {
                        byte data = (byte) re.value();
                        finalGlidingOverrideState = ((data & (1 << VDataFlag.FALL_FLYING_FLAG_INDEX)) != 0);
                    }
                }
            }
            if (finalGlidingOverrideState == Boolean.FALSE) {
                currentDelayingArmorGlide = true;
                if (lastTransactionRecv != null) {
                    PacketManager.handleQueueIn(new com.codigohasta.addon.utils.slimefunhelper.events.packets.PacketStorageImpl(
                            new net.minecraft.network.packet.s2c.common.CommonPingS2CPacket(lastTransactionRecv),
                            event.context.timestampMS(),
                            impl.connection()));
                    currentDelayingLastTransaction = lastTransactionRecv;
                }
                event.cancel();
            }
        }
    }

    public void onWorldSwitch(Event<ClientPlayerEntity> event) {
        recordedWorldFireworkRockets.clear();
        lastOneFireworkRemovalTime = 0;
        delayQueue.clear();
    }

    private void onRemoveFirework(FireworkRocketEntity rocket) {
        if (recordedWorldFireworkRockets.remove(rocket)) {
            if (!anyAliveRocket()) {
                lastOneFireworkRemovalTime = Tasks.getTick();
            }
        }
    }

    public boolean canFireworkControlMotion() {
        return canFireworkControlMotion(Math.max(1, rocketExtraEffectiveTicks.get()));
    }

    public boolean canFireworkControlMotion(int extraTicks) {
        extraTicks = Math.max(1, extraTicks);
        if (anyAliveRocket()) {
            return true;

        } else if (getTickSinceLastFirework() <= extraTicks) {
            return true;
        }
        return false;
    }

    public int getTickSinceLastFirework() {
        return anyAliveRocket() ? 0 : (Tasks.getTick() - lastOneFireworkRemovalTime);
    }

    public int getTicksSinceLastFireworkSpawn() {
        return anyAliveRocket() ? (Tasks.getTick() - lastFireworkSpawnTime) : -1;
    }

    private boolean anyAliveRocket() {
        return (recordedWorldFireworkRockets.stream().anyMatch(EntityUtils::isEntityValid));
    }


    int cnt = 0;
    final int FIREWORK_DELTA = 10;
    int lastLaunchFindNoFireworks = -1;

    public void onNoFireworks() {
        // 10 s one mention
        if (lastLaunchFindNoFireworks < Tasks.getTick() - 10 * 20) {
            lastLaunchFindNoFireworks = Tasks.getTick();
            if (noEnoughFireworksNotify.get()) {
                logI18NSub("Firework", "message.module.elytra-extra.firework-not-found");
            }
        }
    }

    public void onHasFirework() {
        lastLaunchFindNoFireworks = -1;
    }

    public boolean isCurrentWaitingFireworkLaunch() {
        return com.codigohasta.addon.modules.slimefunhelper.SequencedActionManager.INSTANCE.isWaitingItemResponse(this::canBeUsedAsFireworks)
                || !timerVanilla.canFire()
                || (!delayQueue.isEmpty());
    }

    public boolean checkFireworkUseCondition(boolean vanillaCd) {
        var rocket = findRocket();
        if (rocket != null) {
            onHasFirework();
            int level = getRocketLevel(rocket);
            FireworkTimer timer = timerVanilla;
            boolean onFireworkDelay = currentDelayingLock != null;
            boolean onEndFireworkDelay = lastStartDelayOrReleaseMs > 0
                    && (lastStartDelayOrReleaseMs + fireworksDelayMS.get() - 50L) < System.currentTimeMillis();
            boolean delayingRemovalPacketScheduleLaunchAhead = (onFireworkDelay && onEndFireworkDelay)
                    || (currentDelayingArmorGlide && acceptFireworkRemovalDuringDelay);
            if (!isCurrentWaitingFireworkLaunch()) {
                if (!anyAliveRocket() && getTickSinceLastFirework() >= 1) {
                    // time limit, do not double
                    return true;
                    // use = true;
                }
                if (delayingRemovalPacketScheduleLaunchAhead) {
                    return true;
                }
                // timer use
                if (vanillaCd && timer.tryFire(level)) {
                    return true;
                }
                return false;
            }
        } else {
            onNoFireworks();
        }
        return false;
    }

    public void launchFirework(float pitch, float yaw) {
        if (checkFireworkUseCondition(true)) {
            timerVanilla.fire();
            Tasks.scheduleDelayedPre(
                    () -> {
                        if (checkNull()) return;
                        sendCustomUseFireworkPacket(pitch, yaw);
                    },
                    0);
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (storedNoFallPacket != null) {
            mc.getNetworkHandler().sendPacket(storedNoFallPacket);
            storedNoFallPacket = null;
        }
        // avoid packet Order conflict
        rocketFlush.state(false, () -> {
            if (ViaFabricPlusHooks.isSupportEndTick() && PacketOrderManager.INSTANCE.sprinting) {
                ACTasks.addPostTransactionAction((s) -> {
                    flushRockets();
                });
            } else {
                flushRockets();
            }
        });

        if (this.postTickSwapWithChestSlot != -1) {
            final int idx = this.postTickSwapWithChestSlot;
            switchSlotToArmor(idx);
            this.postTickSwapWithChestSlot = -1;
        }
        // using elytra glide
        if (this.manuallySwitchTick == Tasks.getTick()
                && armorFly.get()
                && !handControl.get()
                && armorGlideTransactionSlot == -1) {
            startArmorFlyTransaction(-1);
        }
        if (isCurrentArmorGliding() && poseFix.get()) {
            mc.player.setPose(EntityPose.STANDING);
        }

        //        if(canContinueArmorGliding()){
        //            flushRockets();
        //        }

        return true;
    }

    int nextLandingSneak;
    boolean lastOnGroundOrInWaterAutoTakeOff;

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (armorGlideTransactionSlot != -1) {
            // fix grimac multiaction c, fix grimac elytra c,
            // ??
            if (MovExtra.INSTANCE.fuckGrimACSprint.get()) {
                PlayerInputUtils.of(player).sprint(false).applyInput(player);
                player.setSprinting(false);
            }
        }
        if (autoTakeOffFlag) {
            if (player.isGliding()) {

                if (PlayerStateManager.INSTANCE.glidingTicks > autoTakeOffRetryGt.get()
                        && !mc.player.isTouchingWater()) {
                    lastOnGroundOrInWaterAutoTakeOff = false;
                    autoTakeOffFlag = false;
                } else {
                    if (autoTakeOffFlag) {
                        lastOnGroundOrInWaterAutoTakeOff = true;
                    } else {
                        lastOnGroundOrInWaterAutoTakeOff = false;
                    }
                }
            } else {
                boolean hasGliding = hasGlidingItem();
                if (hasGliding && mc.player.isOnGround()) {
                    lastOnGroundOrInWaterAutoTakeOff = true;
                    PlayerInputUtils.of(player).jump(true).applyInput(player);
                } else {
                    if (hasGliding && player.checkGliding()) {
                        // do not update lastOnGround..., so they will use the last tick status
                        MovExtra.INSTANCE.sendPacketsForPreStartFallFlying();
                        mc.getNetworkHandler()
                                .sendPacket(new ClientCommandC2SPacket(
                                        mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                        MovExtra.INSTANCE.sendPacketsForPostStartFallFlying();
                        // optimize water fly
                    } else if (!mc.player.isTouchingWater()) {
                        autoTakeOffFlag = false;
                        lastOnGroundOrInWaterAutoTakeOff = false;
                    } else {
                        lastOnGroundOrInWaterAutoTakeOff = true;
                    }
                }
            }
        } else {
            lastOnGroundOrInWaterAutoTakeOff = false;
        }
        if (!player.isGliding() && nextLandingSneak > 0) {
            nextLandingSneak -= 1;
            if (nextLandingSneak <= 1) {
                if (!player.isSneaking()) {
                    PlayerInputUtils.of(player).sneak(true).applyInput(player);
                }
            }
        }

        //        if(armorFly.get() && player.isGliding() && this.armorGlideTransactionSlot != -1){
        //            player.input.playerInput =
        // PlayerInputUtils.of(player.input.playerInput).sprint(false).sneak(false).jump(false).forward(false).backward(false).right(false).left(false).toPlayerInput();
        //        }
    }

    @Override
    public void applyBeforeInputPacketModify(Event<LegalMovementManager> movementManagerEvent) {}

    boolean nextTickIsOnGroundTick = false;
    TimerExecutor noGroundPeriod = new TimerExecutor();
    // Vec3d storedPos = null;
    Packet<?> storedNoFallPacket;
    int lastLagBackTick = 0;

    public void onAcceptTeleportation(Event<TeleportConfirmC2SPacket> eventLagBack) {
        lastLagBackTick = Tasks.getTick();
        nextTickIsOnGroundTick = false;
    }

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (player.isGliding()) {
            player.horizontalCollision = false;
            if (fuckGrimAC.get() && currentTickStartArmorGlideTransaction) {
                var input = PlayerInputUtils.of(player);
                input.forward(false).backward(false).left(false).right(false).jump(true);
                input.applyInput(player);
            }
        }
        if (player.isGliding()) {
            if (isThisTickArmoGlideMovementServerSideGlide() && antiKick.get()) {
                // must send packet to reset flight, which is a common case when player is not freezing or player is
                // freezing using FUtils
                ClientPlayerAccess.of(player).resyncPos();
                FloatingUtils.INSTANCE.setSendPacketIgnoreRotation(true);
                FloatingUtils.INSTANCE.setForceSilent(false);
            } else if (isCurrentArmorGliding()) {
                ClientPlayerAccess.of(player).setResyncMovementPacketTicks(0);
            }
        }
        currentTickStartArmorGlideTransaction = false;
        NoFall noFallModule = MovTasks.getNoFall();
        // handle nofall
        boolean handleNoFall = false;
        noFall:
        if (player.isGliding()
                && noFallLanding.get()
                && !nextTickIsOnGroundTick
                && noFallModule.entityStage == 1
                && (player.getY() <= noFallModule.lastOnGroundHeight - noFallModule.safeDistance
                        || Tasks.getTick() < lastLagBackTick + 3)) {
            if (MovTasks.getElytraJump().enable.get()) {
                break noFall;
            }
            if (noGroundPeriod.run(5)) {
                boolean shouldHandle = player.isOnGround() && !movementManagerEvent.context.playerStatus.onGround;
                if (shouldHandle) {
                    // use direct judgement, do not use rocketBuffer
                    if (anyAliveRocket()) {
                        // controlling tick
                        //  must send a PosOnly
                        // otherwise it will be recognized as a duplicateFull
                        storedNoFallPacket = VPacket.newPositionAndOnGround(
                                player.getX(),
                                movementManagerEvent.context.playerStatus.pos.y + 9E-8,
                                player.getZ(),
                                false,
                                mc.player.horizontalCollision);
                        movementManagerEvent.cancel();
                        ClientPlayerAccess.of(player).resyncPos();
                        player.setOnGround(false);
                        MovTasks.getNoFall()
                                .setLastOnGroundHeight(movementManagerEvent.context.playerStatus.pos.y + 9E-8);
                        handleNoFall = true;
                        break noFall;
                    }
                    if (armorFly.get() && armorGlideTransactionSlot != -1) {
                        player.setOnGround(false);
                        movementManagerEvent.context.playerStatus.restorePos();
                        FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                        handleNoFall = true;
                        if (noFallResetFallDistance.get()) {
                            MovTasks.getNoFall()
                                    .setLastOnGroundHeight(movementManagerEvent.context.playerStatus.pos.y + 9E-8);
                        }
                        break noFall;
                    }
                    // what can I say.
                    storedNoFallPacket = VPacket.newPositionAndOnGround(
                            player.getX(),
                            movementManagerEvent.context.playerStatus.pos.y + 9E-8,
                            player.getZ(),
                            false,
                            mc.player.horizontalCollision);
                    // must restore rotation, must send a PosOnly
                    // otherwise it will be recognized as a duplicateFull
                    movementManagerEvent.cancel();
                    ClientPlayerAccess.of(player).resyncPos();
                    player.setOnGround(false);
                    // storedPos = player.getEntityPos();
                    handleNoFall = true;
                    if (noFallResetFallDistance.get()) {
                        MovTasks.getNoFall()
                                .setLastOnGroundHeight(movementManagerEvent.context.playerStatus.pos.y + 9E-8);
                    }
                }
            }
        }

        if (!handleNoFall) {
            nextTickIsOnGroundTick = false;
        } else {
            nextTickIsOnGroundTick = true;
        }
    }
    // todo: try fix sneak desync caused by grimac
    Vec3d simulationFlight = Vec3d.ZERO;

    public void tickElytraSimulation() {
        if (mc.player.isGliding()) {

        } else {
            simulationFlight = Vec3d.ZERO;
        }
    }

    Vec3d nextRequestVelocity = null;

    public void setOverridingFireworkVelocity(Vec3d vec3d) {
        nextRequestVelocity = vec3d;
    }

    public boolean hasFireworkVelocityOverrides() {
        return nextRequestVelocity != null;
    }

    public Vec3d requestNextOverrideVelocity() {
        if (nextRequestVelocity != null) {
            var re = nextRequestVelocity;
            nextRequestVelocity = null;
            return re;
        }
        return null;
    }

    public Vec3d applyAxisLimit(Vec3d currentMotion, float pitch, float yaw, boolean applyGravity) {
        if (!autoRescale.get()) return currentMotion;
        return switch (autoRescaleAl.get()) {
            case V1 -> applyAxisLimit1(currentMotion, pitch, yaw, true);
            case V2 -> applyAxisLimit2(currentMotion, pitch, yaw, true);
            case V3 -> applyAxisLimit3(currentMotion, pitch, yaw);
            case V4 -> applyAxisLimit4(currentMotion, pitch, yaw);
            case V5, V5_PULLUP_ONLY -> applyAxisLimit5(currentMotion, pitch, yaw);
        };
    }

    public Vec3d applyAxisLimit1(Vec3d currentMotion, float pitch, float yaw, boolean realApply) {
        if (!autoRescale.get()) {
            if (realApply) setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        if (currentMotion.lengthSquared() < 1E-6) {
            if (realApply) setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        Vec3d currentRotation = EntityUtils.pitchYawToRotation(pitch, yaw);
        Vec3d lastTickVelocity = PlayerStateManager.INSTANCE.lastKnownClientVelocity;
        Vec3d thisTickSimulationVelocity =
                PlayerStateManager.INSTANCE.lastInWater || PlayerStateManager.INSTANCE.lastInLava
                        ? EntityUtils.simulateTravelInFluidVelocity(
                                lastTickVelocity,
                                PlayerStateManager.INSTANCE.lastInWater,
                                PlayerStateManager.INSTANCE.lastInLava,
                                true)
                        : EntityUtils.calculateGlidingVelocity(mc.player, lastTickVelocity, currentRotation, true);

        // --- fireworksBox 构造 (保持不变) ---
        double antiTickSkipping = 0.05;
        Vec3d currentLook = currentRotation.normalize();

        double minX = Math.min(-antiTickSkipping, currentLook.getX());
        double minY = Math.min(-antiTickSkipping, currentLook.getY());
        double minZ = Math.min(-antiTickSkipping, currentLook.getZ());
        double maxX = Math.max(antiTickSkipping, currentLook.getX());
        double maxY = Math.max(antiTickSkipping, currentLook.getY());
        double maxZ = Math.max(antiTickSkipping, currentLook.getZ());

        double threshold = Math.min(autoRescaleAmount.get(), currentMotion.length());
        minX *= threshold;
        maxX *= threshold;
        minY *= threshold;
        maxY *= threshold;
        minZ *= threshold;
        maxZ *= threshold;
        minX = Math.max(-threshold, minX);
        maxX = Math.min(threshold, maxX);
        minY = Math.max(-threshold, minY);
        maxY = Math.min(threshold, maxY);
        minZ = Math.max(-threshold, minZ);
        maxZ = Math.min(threshold, maxZ);
        // Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        Vec3d v1 = lastTickVelocity;
        Vec3d v3 = thisTickSimulationVelocity;
        double eMinX = Math.min(0, minX - v1.x);
        double eMaxX = Math.max(0, maxX - v1.x);
        double eMinY = Math.min(0, minY - v1.y);
        double eMaxY = Math.max(0, maxY - v1.y);
        double eMinZ = Math.min(0, minZ - v1.z);
        double eMaxZ = Math.max(0, maxZ - v1.z);
        double zeroPointThreeTest = 0.0;
        double thresoldLeft = ElytraExtra.INSTANCE.autoRescaleThreshold.get();
        double uMinX = v3.x + eMinX - zeroPointThreeTest + thresoldLeft;
        double uMaxX = v3.x + eMaxX + zeroPointThreeTest - thresoldLeft;
        double uMinY = v3.y + eMinY + thresoldLeft;
        double uMaxY = v3.y + eMaxY - thresoldLeft;
        double uMinZ = v3.z + eMinZ - zeroPointThreeTest + thresoldLeft;
        double uMaxZ = v3.z + eMaxZ + zeroPointThreeTest - thresoldLeft;
        // I dont understand.
        double dx = currentMotion.x, dz = currentMotion.z;
        double exceedX = 0.0, exceedZ = 0.0;

        if (dx > 0) exceedX = dx / uMaxX;
        else if (dx < 0) exceedX = dx / uMinX; // 注意 dx 为负，uMinX 也为负，比值 >1 若 dx < uMinX

        if (dz > 0) exceedZ = dz / uMaxZ;
        else if (dz < 0) exceedZ = dz / uMinZ;

        // ??????????????????????????????????????????????????????????????????????????
        // dy

        double maxScale = Math.max(exceedX, exceedZ);

        // 退化情况：所有 scale 为 0
        if (maxScale < 1E-6) {
            if (realApply) setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        Vec3d predictedMotion = EntityUtils.calculateGlidingVelocity(mc.player, currentMotion, currentRotation, true);
        Vec3d clampedMotion = currentMotion.multiply(1 / maxScale);
        // todo : add more angle restrict
        if (clampedMotion.y > 0) {
            clampedMotion = clampedMotion.withAxis(Direction.Axis.Y, uMaxY);
        } else if (clampedMotion.y < 0) {
            clampedMotion = clampedMotion.withAxis(Direction.Axis.Y, uMinY);
        }
        clampedMotion = applySpeedLimit(clampedMotion);
        // 已在盒内，无需缩放
        if (clampedMotion.lengthSquared() < predictedMotion.lengthSquared()) {
            if (realApply) setOverridingFireworkVelocity(null);
            return currentMotion;
        }

        // 需要缩小至盒子边界
        if (realApply) setOverridingFireworkVelocity(clampedMotion); // <-- 保存边界值
        return clampedMotion;
    }

    public Vec3d applyAxisLimit2(Vec3d currentMotion, float pitch, float yaw, boolean apply) {
        if (!autoRescale.get()) {
            if (apply) setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        if (currentMotion.lengthSquared() < 1E-6) {
            if (apply) setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        Vec3d currentRotation = EntityUtils.pitchYawToRotation(pitch, yaw);
        Vec3d lastTickVelocity = PlayerStateManager.INSTANCE.lastKnownClientVelocity;
        Vec3d thisTickSimulationVelocity =
                PlayerStateManager.INSTANCE.lastInWater || PlayerStateManager.INSTANCE.lastInLava
                        ? EntityUtils.simulateTravelInFluidVelocity(
                                lastTickVelocity,
                                PlayerStateManager.INSTANCE.lastInWater,
                                PlayerStateManager.INSTANCE.lastInLava,
                                true)
                        : EntityUtils.calculateGlidingVelocity(mc.player, lastTickVelocity, currentRotation, true);

        // --- fireworksBox 构造 (保持不变) ---
        Vec3d lastPitchYaw = EntityUtils.pitchYawToRotation(
                PlayerStateManager.INSTANCE.lastPitch, PlayerStateManager.INSTANCE.lastYaw);
        double antiTickSkipping = 0.05;
        Vec3d currentLook = currentRotation.normalize();
        Vec3d lastLook = lastPitchYaw.normalize();
        double minX = Math.min(-antiTickSkipping, currentLook.getX()) + Math.min(-antiTickSkipping, lastLook.getX());
        double minY = Math.min(-antiTickSkipping, currentLook.getY()) + Math.min(-antiTickSkipping, lastLook.getY());
        double minZ = Math.min(-antiTickSkipping, currentLook.getZ()) + Math.min(-antiTickSkipping, lastLook.getZ());
        double maxX = Math.max(antiTickSkipping, currentLook.getX()) + Math.max(antiTickSkipping, lastLook.getX());
        double maxY = Math.max(antiTickSkipping, currentLook.getY()) + Math.max(antiTickSkipping, lastLook.getY());
        double maxZ = Math.max(antiTickSkipping, currentLook.getZ()) + Math.max(antiTickSkipping, lastLook.getZ());

        double threshold = Math.min(autoRescaleAmount.get(), currentMotion.length());
        minX *= threshold;
        maxX *= threshold;
        minY *= threshold;
        maxY *= threshold;
        minZ *= threshold;
        maxZ *= threshold;
        minX = Math.max(-threshold, minX);
        maxX = Math.min(threshold, maxX);
        minY = Math.max(-threshold, minY);
        maxY = Math.min(threshold, maxY);
        minZ = Math.max(-threshold, minZ);
        maxZ = Math.min(threshold, maxZ);
        // Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        Vec3d v1 = lastTickVelocity;
        Vec3d v3 = thisTickSimulationVelocity;
        double eMinX = Math.min(0, minX - v1.x);
        double eMaxX = Math.max(0, maxX - v1.x);
        double eMinY = Math.min(0, minY - v1.y);
        double eMaxY = Math.max(0, maxY - v1.y);
        double eMinZ = Math.min(0, minZ - v1.z);
        double eMaxZ = Math.max(0, maxZ - v1.z);
        double zeroPointThreeTest = 0.0;
        double uMinX = v3.x + eMinX - zeroPointThreeTest;
        double uMaxX = v3.x + eMaxX + zeroPointThreeTest;
        double uMinY = v3.y + eMinY;
        double uMaxY = v3.y + eMaxY;
        double uMinZ = v3.z + eMinZ - zeroPointThreeTest;
        double uMaxZ = v3.z + eMaxZ + zeroPointThreeTest;
        // I dont understand.
        // Upstream 1.1.7.2 removed the `!isSupportEndTick()` uMaxY widening that 1.1.6.3 had
        // here; it existed to compensate for the missing end-tick packet and is no longer needed.
        double dx = currentMotion.x, dy = currentMotion.y, dz = currentMotion.z;
        double exceedX = 0.0, exceedY = 0.0, exceedZ = 0.0;

        if (dx > 0) exceedX = dx / uMaxX;
        else if (dx < 0) exceedX = dx / uMinX; // 注意 dx 为负，uMinX 也为负，比值 >1 若 dx < uMinX

        if (dy > 0) exceedY = dy / uMaxY;
        else if (dy < 0) exceedY = dy / uMinY;

        if (dz > 0) exceedZ = dz / uMaxZ;
        else if (dz < 0) exceedZ = dz / uMinZ;

        // ??????????????????????????????????????????????????????????????????????????
        // dy

        double maxScale = Math.max(exceedX, Math.max(exceedY, exceedZ));

        // 退化情况：所有 scale 为 0
        if (maxScale < 1E-6) {
            if (apply) setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        Vec3d predictedMotion = EntityUtils.calculateGlidingVelocity(mc.player, currentMotion, currentRotation, true);
        double limitScale = currentMotion.length() / predictedMotion.length();
        // 已在盒内，无需缩放
        if (maxScale >= limitScale) {
            if (apply) setOverridingFireworkVelocity(null);
            return currentMotion;
        }

        // 需要缩小至盒子边界

        Vec3d clampedMotion = currentMotion.multiply(1 / maxScale);
        // Upstream 1.1.7.2 clamps the shrunk motion to the configured speed limit here.
        clampedMotion = applySpeedLimit(clampedMotion);
        if (apply) setOverridingFireworkVelocity(clampedMotion); // <-- 保存边界值
        return clampedMotion;
    }

    public Vec3d applyAxisLimit3(Vec3d currentMotion, float pitch, float yaw) {
        if (!autoRescale.get()) {
            setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        return ElytraOptimizeUtils.applyAxisLimit3(currentMotion, pitch, yaw, autoRescaleAmount.get());
    }


    /** Ported from upstream 1.1.7.2 ElytraExtra.switchElytra. */
    public boolean switchElytra() {
        if (checkNull()) return false;
        if (hasGlidingItem()) {
            ItemStack currentChest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            boolean switchArmor = isValidElytra(currentChest);
            int slotForUse;
            if (switchArmor) {
                slotForUse = findEmptySlotForElytra();
            } else {
                slotForUse = findElytra();
            }
            if (slotForUse == -1) {
                logI18N("Elytra", "message.module.elytra-extra.no-elytra-place");
                return true;
            }
            if (isCurrentArmorGlidingEnable()) {
                if (switchArmor) {
                    this.armorGlideTransactionSlot = -1;
                    startArmorFlyTransaction(slotForUse);
                } else {
                    switchSlotToArmor(slotForUse);
                    armorGlideTransactionAbort = true;
                }
            } else {
                switchSlotToArmor(slotForUse);
                if (!switchArmor) {
                    broadcastSwapBackSlot(slotForUse);
                }
            }
            return true;
        }
        return false;
    }

    /** Ported from upstream 1.1.7.2 ElytraExtra.sendFirework. */
    private void sendFirework(Hand hand, float lastYaw, float lastPitch) {
        var packet = new PlayerInteractItemC2SPacket(hand, NetworkUtils.generateNextSequence(), lastYaw, lastPitch);
        com.codigohasta.addon.utils.slimefunhelper.utils.InteractUtils.simulateInteractItem(hand);
        mc.getNetworkHandler().sendPacket(packet);
    }

    /** Ported from upstream 1.1.7.2 ElytraExtra.onPreInputEvents. */
    public void onPreInputEvents(Event<Void> event) {
        if (checkNull()) {
            return;
        }
        if (!shouldBlockFireworkAction()) {
            if (anyAliveRocket()) {
                clearRockets();
            } else {
                flushRockets();
            }
        }
    }

    /** Ported from upstream 1.1.7.2 ElytraExtra.applyAxisLimit4. */
    public Vec3d applyAxisLimit4(Vec3d currentMotion, float pitch, float yaw) {
        if (!autoRescale.get()) {
            setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        return ElytraOptimizeUtils.applyAxisLimit4(currentMotion, pitch, yaw, autoRescaleAmount.get());
    }

    /** Ported from upstream 1.1.7.2 ElytraExtra.applyAxisLimit5. */
    public Vec3d applyAxisLimit5(Vec3d currentMotion, float pitch, float yaw) {
        if (!autoRescale.get()) {
            setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        return ElytraOptimizeUtils.applyAxisLimit5_0(currentMotion, pitch, yaw, autoRescaleAmount.get(), true);
    }

    public static enum MotionMode implements ConfigEnum {
        VOID,
        FIRE_WORKS;

        @Override
        public String getConfigEnumType() {
            return "motionmode";
        }
    }

    public static class FireworkTimer {
        int lastTimeFire = 0;
        DoubleRef fireTicks;
        boolean lastTimeWasAuto = false;

        public FireworkTimer(DoubleRef fireTicks) {
            this.fireTicks = fireTicks;
        }

        public boolean canFire() {
            // do not fire too close,
            return Tasks.getTick() > lastTimeFire;
        }

        public boolean tryFire(int level) {
            if (lastTimeFire + (fireTicks.get() * level) < Tasks.getTick()) {
                return true;
            } else {
                return false;
            }
        }

        public void fire() {
            lastTimeFire = Tasks.getTick();
        }
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> presetEvent) {
        //        switch (presetEvent.context.getValue()) {
        //            case HACKING, VANILLA, AC_COMMON-> {
        //                armorMode.set(ArmorFlyMode.LAZY);
        //            }
        //            case AC_MATRIX, AC_VULCAN, AC_GRIM, AC_GRIM_LEGACY -> {
        //                armorMode.set(ArmorFlyMode.TICK);
        //            }
        //        }
        switch (presetEvent.context.getValue()) {
            case AC_GRIM, AC_GRIM_LEGACY -> noKineticMode.set(
                    noKineticMode.get().withValue(new WrapEnum<>(Configs.CompatMode.COMPAT_GRIM)));
            default -> noKineticMode.set(noKineticMode.get().withValue(new WrapEnum<>(Configs.CompatMode.NO_COMPAT)));
        }
        switch (presetEvent.context.getValue()) {
            case AC_GRIM, AC_GRIM_LEGACY -> {
                maceFixMode.set(maceFixMode.get().withValue(new WrapEnum<>(Configs.CompatMode.COMPAT_GRIM)));
            }
            default -> maceFixMode.set(maceFixMode.get().withValue(new WrapEnum<>(Configs.CompatMode.NO_COMPAT)));
        }
        switch (presetEvent.context.getValue()) {
            case AC_GRIM_LEGACY, AC_GRIM -> {
                armorFlyBadPacketFix.set(true);
            }
            default -> {
                armorFlyBadPacketFix.set(false);
            }
        }
    }

    public static enum ArmorFlyMode implements ConfigEnum {
        LAZY,
        TICK_LEGACY,
        TICK;

        @Override
        public String getConfigEnumType() {
            return "armorflymode";
        }
    }

    public static enum Al implements ConfigEnum {
        V1,
        V2,
        V3,
        V4,
        V5,
        V5_PULLUP_ONLY;

        @Override
        public String getConfigEnumType() {
            return "elytra_extra_fireworks_al_type";
        }
    }
}
