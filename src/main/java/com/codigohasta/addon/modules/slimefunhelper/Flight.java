package com.codigohasta.addon.modules.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientPlayerAccess;
import com.codigohasta.addon.utils.slimefunhelper.accessors.hacks.KeyBindAccess;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.events.impl.EventContainer;
import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.move.MovementSupport;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePreset;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.HotKeyUtils;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.move.FlightVelocity;
import com.codigohasta.addon.utils.slimefunhelper.managers.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.Tasks;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind;
import com.codigohasta.addon.utils.slimefunhelper.utils.Debug;
import com.codigohasta.addon.utils.slimefunhelper.utils.EntityUtils;
import com.codigohasta.addon.utils.slimefunhelper.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.util.math.Vec3d;

public class Flight extends BaseModule implements LegalMovementManager.MovementModifier {
    public static Flight INSTANCE;
    private static LegalMovementManager.DelegateMovementModifier instance;
    public final ModulePath moveSafety = makePath(Configs.MOV_CONFIG, "move-safety");
    public final ModulePath flight = moveSafety.add("flight");
    public final ModulePath moveSpeed = makePath(Configs.MOV_CONFIG, "move-speed");

    public Flight() {
        super("Flight");
        INSTANCE = this;
        bindFlag(canFly);
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final FlagRef canFly =
            flagBuilder(flight.add("flight-enable")).defaultValue(false).build();

    public final KeyBindRef keybind = moduleEntry(
                    flight.add("flight-enable-hotkey"),
                    new MultiKeyBind(),
                    flight.add("flight-enable"),
                    moduleMeta(() -> this.flightMode))
            .build();

    public final EnumRef<Mode> flightMode = builder(flight.add("flight-mode"), Mode.class)
            .defaultValue(Mode.CREATIVE)
            .build();

    public final KeyBindRef switchMode = hotkey(flight.add("flight-mode-switch-hotkey"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::toggleMode))
            .build();

    public final FlagRef doAntiKick =
            builder(flight.add("antikick"), Boolean.class).defaultValue(true).build();

    public final IntRef antiKickPeriod =
            intBuilder(flight.add("antikick-period")).defaultValue(60).build();
    public final FlagRef overrideFlySpeed = builder(moveSpeed.add("fly-speed"), Boolean.class)
            .defaultValue(false)
            .build();

    public final KeyBindRef overridingSpeedKeybind = moduleEntry(
                    moveSpeed.add("toggle-flight-speed"), new MultiKeyBind(), moveSpeed.add("fly-speed"))
            .build();

    public final DoubleRef overrideFlySpeedCreative = builder(moveSpeed.add("fly-speed-creative"), Double.class)
            .defaultValue(0.8)
            .build();

    public final DoubleRef overrideFlySpeedSurvival = builder(moveSpeed.add("fly-speed-survival"), Double.class)
            .defaultValue(0.4)
            .build();

    public final FlagRef overrideWalkSpeed =
            flagBuilder(moveSpeed.add("walk-speed")).build();

    public final KeyBindRef overridingWalkSpeedKeybind = moduleEntry(
                    moveSpeed.add("toggle-walk-speed"), new MultiKeyBind(), moveSpeed.add("walk-speed"))
            .build();
    public final DoubleRef overridingWalkSpeedAll = builder(moveSpeed.add("walk-speed-override"), Double.class)
            .defaultValue(0.1)
            .build();

    public final FlagRef onGroundWhenMine =
            flagBuilder(flight.add("onground-when-mine")).build();


    public boolean serverSideCanFly = false;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private ClientPlayerEntity abilityOwner;
    private boolean originalFlying;
    private float serverFlySpeed, serverWalkSpeed;
    private int generation;
    private boolean keysSuppressed;
    private Boolean miningGround;

    @Override public void onEnableModule() { captureAbilities(); }

    private void captureAbilities() {
        if (mc.player == null || abilityOwner == mc.player) return;
        resetRuntimeState();
        var abilities = mc.player.getAbilities();
        serverSideCanFly = com.codigohasta.addon.utils.slimefunhelper.ServerFlightState.allowed(mc.player);
        originalFlying = abilities.flying;
        serverFlySpeed = abilities.getFlySpeed();
        serverWalkSpeed = abilities.getWalkSpeed();
        abilityOwner = mc.player;
    }

    public void resetRuntimeState() {
        generation++;
        if (abilityOwner != null) {
            var abilities = abilityOwner.getAbilities();
            abilities.allowFlying = serverSideCanFly;
            abilities.flying = serverSideCanFly && originalFlying;
            abilities.setFlySpeed(serverFlySpeed);
            abilities.setWalkSpeed(serverWalkSpeed);
            if (miningGround != null) abilityOwner.setOnGround(miningGround);
        }
        abilityOwner = null;
        miningGround = null;
        if (keysSuppressed) restoreKeyPresses();
        antiKickCount = 0;
        antiKickOffset0 = 0;
        escapeMotionReset = true;
        preservedLastMotion = 0;
        waitingForServerResponse = false;
        floatingCount = 0;
        lastFloatingPosition = 0;
        mineTick = 0;
        lastTimeModifyOnGround = 0;
        lastStartMinePacket = Integer.MIN_VALUE;
    }

    public boolean controlsAbilities(PlayerAbilities abilities) {
        return isActive() && abilityOwner != null && abilityOwner.getAbilities() == abilities;
    }

    @Override public void onDisableModule() { resetRuntimeState(); }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(PlayerAbilitiesS2CPacket.class), this::onAbility);
        registerListener(Listener.getPacketPoint().getChannel(UpdatePlayerAbilitiesC2SPacket.class), this::onAbilityUpdate);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
        registerListener(Listener.getPacketPoint().getChannel(PlayerActionC2SPacket.class), this::onStartMine);
        registerListener(Listener.getPacketPoint().getChannel(PlayerInputC2SPacket.class), this::onInterceptFlyInput);
    }

    public void onAbility(Event<PlayerAbilitiesS2CPacket> event) {
        var packet1 = event.context();
        captureAbilities();
        serverSideCanFly = packet1.allowFlying();
        originalFlying = packet1.isFlying();
        serverFlySpeed = packet1.getFlySpeed();
        serverWalkSpeed = packet1.getWalkSpeed();
        com.codigohasta.addon.utils.slimefunhelper.ServerFlightState.received(mc.player, serverSideCanFly);
        if (mc.player != null) {
            PlayerAbilities abilities = mc.player.getAbilities();
            abilities.creativeMode = packet1.isCreativeMode();
            abilities.invulnerable = packet1.isInvulnerable();
            if (!isActive()) {
                abilities.allowFlying = packet1.allowFlying();
            }
            if (!overrideFlySpeed.get()) {
                abilities.setFlySpeed(packet1.getFlySpeed());
            }
            abilities.setWalkSpeed(packet1.getWalkSpeed());
        } else {
            Tasks.scheduleDelayed(
                    () -> {
                        onAbility(event);
                    },
                    10);
        }
        event.cancel();
    }

    public void onAbilityUpdate(Event<UpdatePlayerAbilitiesC2SPacket> event) {
        if (isActive() && !serverSideCanFly) {
            event.cancel();
        }
    }

    public double getOverridingFlySpeed() {
        return (mc.player != null && mc.interactionManager.getCurrentGameMode().isCreative())
                ? overrideFlySpeedCreative.get()
                : overrideFlySpeedSurvival.get();
    }

    public double getOverridingWalkSpeed() {
        return overridingWalkSpeedAll.get();
    }

    public void toggleMode() {
        Mode mode = flightMode.get();
        int ordinal = mode.ordinal() + 1;
        Mode[] flightModes = Mode.values();
        Mode newMode = flightModes[ordinal % flightModes.length];
        flightMode.set(newMode);
        Debug.chat("toggle flight mode to", newMode.getDisplay());
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        if (!isActive()) return;
        captureAbilities();
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (com.codigohasta.addon.utils.slimefunhelper.FlightImpulseGuard.preserving()) return;
        if (isActive()) {
            if (!player.getAbilities().allowFlying) {
                player.getAbilities().allowFlying = true;
            }
        } else {
            player.getAbilities().allowFlying = serverSideCanFly;
        }
        boolean handled =
                switch (flightMode.get()) {
                    case CREATIVE -> {
                        dispatchAntiKick(player);
                        yield true;
                    }
                    case MOTION -> {
                        if (isActive() && mc.player.getAbilities().flying) {
                            PlayerInputUtils.Input input = PlayerInputUtils.of(mc.options);
                            Vec3d movementInput =
                                    new Vec3d(input.sidewaysSpeed(), input.upwardSpeed(), input.forwardSpeed());
                            Vec3d velocity = EntityUtils.movementInputToVelocity(
                                    movementInput, (float) (5 * getOverridingFlySpeed()), player.getYaw());
                            player.setVelocity(velocity);
                            velocity = dispatchAntiKickMotion(velocity);
                            FlightVelocity flightVelocity = new FlightVelocity(
                                    velocity, 5 * getOverridingFlySpeed(), FlightVelocity.Mode.MOTION_FLIGHT);
                            Listener.getCustomListener()
                                    .broadcast(new EventContainer<>(FlightVelocity.class, flightVelocity));
                            velocity = flightVelocity.toVelocity();
                            player.setVelocity(velocity);
                            yield true;
                        }
                        yield false;
                    }
                    case JETPACK -> {
                        if (isActive()) {
                            PlayerInputUtils.Input input = PlayerInputUtils.of(mc.options);
                            if (input.jump()) {
                                Vec3d vec3d = new Vec3d(0, 1, 0);
                                vec3d = dispatchAntiKickMotion(vec3d);
                                if (vec3d.y > 0.8) {
                                    player.jump();
                                } else {
                                    Vec3d playerVec = player.getVelocity();
                                    player.setVelocity(playerVec.x, vec3d.y, playerVec.z);
                                }
                                yield true;
                            }
                        }
                        yield false;
                    }
                };
        if (!handled && isActive() && mc.player.getAbilities().flying) {
            dispatchAntiKick(player);
        }
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        LegalMovementManager.MovementModifier.super.applyAfterInputTick(movementManagerEvent);
    }

    public void dispatchAntiKick(ClientPlayerEntity player) {
        boolean fakeGilde = false;
        if (((isActive() && doAntiKick.get()))) {
            antiKick(player, fakeGilde);
        }
    }

    private int mineTick = 0;
    private int lastTimeModifyOnGround = 0;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        if (!isActive()) return;
        var player = movementManagerEvent.context.playerStatus.entity;
        boolean onGround = player.isOnGround();
        miningGround = null;
        if (!onGround && onGroundWhenMine.get() && isInMiningAction() && !player.getAbilities().creativeMode) {
            mineTick = 2;
            miningGround = onGround;
            player.setOnGround(true);
            ClientPlayerAccess.of(player).resyncOnGround();
            lastTimeModifyOnGround = 2;
        } else if (mineTick > 0) {
            --mineTick;
            miningGround = onGround;
            player.setOnGround(true);
            ClientPlayerAccess.of(player).resyncOnGround();
            lastTimeModifyOnGround = onGround ? 1 : 2;
        }
    }

    public boolean isInMiningAction() {
        return mc.interactionManager.isBreakingBlock() || (lastStartMinePacket + 1 >= Tasks.getTick());
    }

    private int lastStartMinePacket = 0;

    public void onStartMine(Event<PlayerActionC2SPacket> actionPacket) {
        if (onGroundWhenMine.get()
                && actionPacket.context().getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
            lastStartMinePacket = Tasks.getTick();
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        lastTimeModifyOnGround = 0;
        return true;
    }

    private static final double antiKickOffset = 0.032D;
    private int antiKickCount = 0;
    private double antiKickOffset0;
    private boolean escapeMotionReset = false;
    private double preservedLastMotion = 0.0D;
    private boolean waitingForServerResponse;
    public void antiKick(ClientPlayerEntity player, boolean fakeGliding) {
        if (MovementSupport.seenAsFloating(fakeGliding)) {
            antiKickCount++;
        } else {
            antiKickCount = 0;
        }
        if (antiKickCount > antiKickPeriod.get()) {
            antiKickCount = 0;
            escapeMotionReset = !shouldResetMotion();
            preservedLastMotion = player.getVelocity().y;
            setMotionY(-antiKickOffset);
            waitingForServerResponse = true; // Tasks.getTickRandom()%3 == 0;
            antiKickOffset0 = antiKickOffset - 0.008;
            return;
        }
        if (!escapeMotionReset) {
            if (waitingForServerResponse) {
                setMotionY(-antiKickOffset);
                antiKickOffset0 += antiKickOffset - 0.008;
                waitingForServerResponse = false;

            } else {
                setMotionY(antiKickOffset0 + preservedLastMotion - 0.0);
                antiKickOffset0 = 0D;
                preservedLastMotion = 0.0D;
                int currentGeneration = generation;
                Tasks.scheduleDelayed(() -> { if (generation == currentGeneration) restoreKeyPresses(); }, 1);
                escapeMotionReset = true;
            }
        }


    }

    public Vec3d dispatchAntiKickMotion(Vec3d controlMotion) {
        boolean fakeGilde = false;
        if ((fakeGilde || (isActive() && doAntiKick.get()))) {
            return processAntiKickMotion(controlMotion, fakeGilde);
        }
        return controlMotion;
    }

    double lastFloatingPosition = 0.0D;
    int floatingCount = 0;

    public boolean shouldResetMotion() {
        double floatingPosition = mc.player.getY();
        if (floatingCount == 0) {
            floatingCount = 1;
            lastFloatingPosition = floatingPosition;
            return false;
        }
        if (Math.abs(floatingPosition - lastFloatingPosition) > antiKickOffset * 3) {
            double oldFloatingPosition = lastFloatingPosition;
            lastFloatingPosition = floatingPosition;
            floatingCount = 1;
            if (oldFloatingPosition < lastFloatingPosition) {
                return true;
            } else {
                if (MovTasks.ENGIN.checkEnvironmentCollision(
                        mc.player, mc.player.getEntityPos().add(0, -3 * antiKickOffset, 0), false)) {
                    return true;
                }
                return false;
            }
        } else {
            lastFloatingPosition = floatingPosition;
            floatingCount += 1;
            if (floatingCount > 10) {
                return true;
            }
            return false;
        }
    }

    public Vec3d processAntiKickMotion(Vec3d controlMotion, boolean fakeGlide) {
        if (MovementSupport.seenAsFloating(fakeGlide)) {
            antiKickCount++;
        } else {
            antiKickCount = 0;
        }
        if (antiKickCount > antiKickPeriod.get()) {
            antiKickCount = 0;
            escapeMotionReset = !shouldResetMotion();
            preservedLastMotion = controlMotion.y;
            waitingForServerResponse = true; // Tasks.getTickRandom()%3 == 0;
            antiKickOffset0 = antiKickOffset - 0.008;
            return new Vec3d(controlMotion.x, -antiKickOffset, controlMotion.z);
        }
        if (!escapeMotionReset) {
            if (waitingForServerResponse) {
                antiKickOffset0 += antiKickOffset - 0.008;
                waitingForServerResponse = false;
                return new Vec3d(controlMotion.x, -antiKickOffset, controlMotion.z);
            } else {
                antiKickOffset0 = 0D;
                preservedLastMotion = 0.0D;
                escapeMotionReset = true;
                return new Vec3d(controlMotion.x, antiKickOffset0 + preservedLastMotion - 0.0, controlMotion.z);
            }
        }
        return controlMotion;

    }

    public void onInterceptFlyInput(Event<PlayerInputC2SPacket> inputPacketEvent) {
    }

    private void setMotionY(double motionY) {
        keysSuppressed = true;

        mc.options.sneakKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x, motionY, velocity.z);
    }

    private void restoreKeyPresses() {
        keysSuppressed = false;
        if (mc.currentScreen == null) {

            KeyBindAccess.of(mc.options.jumpKey).resetKeyState();
            KeyBindAccess.of(mc.options.sneakKey).resetKeyState();
        } else {
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
        }
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> presetEvent) {
        var modulePreset = presetEvent.context().getValue();
        switch (modulePreset) {
            case AC_GRIM, AC_GRIM_LEGACY, AC_MATRIX -> {
                canFly.set(false);
            }
            default -> {
                canFly.set(true);
            }
        }
        switch (modulePreset) {
            case AC_GRIM, AC_GRIM_LEGACY -> {
                overrideFlySpeed.set(false);
                overrideWalkSpeed.set(false);
            }
        }
        switch (modulePreset) {
            case HACKING -> {
                onGroundWhenMine.set(true);
            }
            default -> {
                onGroundWhenMine.set(false);
            }
        }
    }

    public enum Mode implements ConfigEnum {
        CREATIVE,
        MOTION,
        JETPACK;

        @Override
        public String getConfigEnumType() {
            return "flight_mode";
        }
    }
}
