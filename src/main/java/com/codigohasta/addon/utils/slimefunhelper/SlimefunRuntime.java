package com.codigohasta.addon.utils.slimefunhelper;

import com.codigohasta.addon.utils.SlimefunHelperCompat;
import com.codigohasta.addon.modules.slimefunhelper.*;
import com.codigohasta.addon.utils.slimefunhelper.hacks.*;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager;
import com.codigohasta.addon.utils.slimefunhelper.events.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.Tasks;
import com.codigohasta.addon.utils.slimefunhelper.utils.entity.PlayerInputUtils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.*;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.events.game.*;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.orbit.EventHandler;

import java.util.*;


public final class SlimefunRuntime {


    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final List<BaseModule> modules = new ArrayList<>();

    private static ClientPlayerEntity current;
    private static LegalMovementManager movement;
    private static boolean inTick;
    private static boolean installed;

    // ==================== 初始化与注册 ====================
    //注意!此处是sfIMG部分的模块注册，IMG 的模块要去主类addontemplate文件里注册。

    private static final boolean SF_HELPER_PRESENT = SlimefunHelperCompat.isSlimefunHelperLoaded();

    public static void initialize() {
        if (installed) return;

        // 移动相关
        add(new MovExtra());
        add(new NoSlowDown());
        add(new AutoResync());
        add(new NoGround());

        // 方块 / 状态管理
        add(new FakeBlockManager());
        add(new PlayerStateManager());
        add(new PacketOrderManager());
        add(new PostManager());
        add(new InvExtra());

        // 位置 / 挖掘
        add(new LegacySnapRotManager());
        add(new QueueMine());
        add(new PacketMine());
        add(new FastMine());
        add(new InteractExtra());
        add(new com.codigohasta.addon.modules.slimefunhelper.SequencedActionManager());
        add(new DisablerManager());
        add(new MultiPlaceDisabler());
        add(new PlayerInputManager());
        add(new BlockRotate());
        add(new PrinterRewrite());
        add(new PositionPredict());
        add(new TargetSelector());

        // 战斗
        add(new CombatExtra());
        add(new Attack());
        add(new AttackAura());
        add(new SpearEnhance());
        add(new Blink());

        // 摔落 / 区块 / 鞘翅
        add(new NoFall());
        add(new AntiChunkLag());
        add(new ElytraExtra());
        add(new ElytraFlight());

        // 鞘翅进阶
        add(new ElytraGrimAccelerate());
        add(new ElytraJump());
        add(new ElytraBot());
        add(new FloatingUtils());

        // 弓箭 / 长矛 / 暴击
        add(new BowTp());
        add(new BowEnhance());
        add(new SpearAttack());
        add(new Criticals());
        add(new ProjectileEnhance());

        // 界面 / 冲刺 / 反 Xray / 鞘翅搜索
        add(new GuiMove());
        add(new Sprint());
        add(new Velocity());
        add(new BadPacketsFix());
        add(new BoatVClip());
        add(new BackTrack());
        add(new EnderEyeLog());
        add(new ForwardTp());
        add(new SetBackLog());
        add(new Flight());
        add(new StepHeight());
        add(new AntiLiquid());
        add(new BaritoneFix());
        add(new FastCraft());
        add(new ChatExtra());
        add(new ClientSideCommand());
        add(new InGuiChatBox());
        // add(new AntiAXray());
        // add(new ElytraFinder());

        // 初始化每个模块
        // 有 SlimefunHelper 时跳过运行时装配：它的 mixin 已被 SfImgMixinPlugin 跳过，
        // 事件通道不会有人投递，装配只会白跑并让模块看起来可用。
        if (!SF_HELPER_PRESENT) {
            for (var module : modules) {
                module.onCreate();
                module.registerAll();
            }

            WorldTasks.init();
            InvTasks.initialize();
        }

        installed = true;

        if (SF_HELPER_PRESENT) {
            // 完全不订阅运行时事件：sfIMG 的 mixin 已被 SfImgMixinPlugin 跳过，事件通道不会有人投递，
            // 订阅只会让 onLeft/onRender 等清理逻辑去碰已经失效的 accessor。
            com.codigohasta.addon.AddonTemplate.LOG.info(
                    "SlimefunHelper detected: IMG sfIMG subsystem stood down, {} modules kept registered for config compatibility",
                    modules.size()
            );
            return;
        }

        MeteorClient.EVENT_BUS.subscribe(new SlimefunRuntime());

        com.codigohasta.addon.AddonTemplate.LOG.info(
                "SlimefunHelper migration initialized: {} independent modules",
                modules.size()
        );
    }

    /**
     * Keeps Meteor's ClickGUI visibility in sync with whether SlimefunHelper is installed.
     *
     * <p>While SlimefunHelper is present the sfIMG modules must not be shown: their supporting
     * mixins are skipped, so they are inert, and exposing them would offer the player switches that
     * silently do nothing. Meteor has no hidden-category or "registered but invisible" flag; the
     * only supported mechanism is {@code Config.hiddenModules}, which {@code ModulesScreen}
     * consults while building its category windows. Hiding every sfIMG module leaves the
     * {@code sfIMG} category with no visible module, and Meteor then omits the empty window.
     *
     * <p>Because {@code Config} is persisted, hiding must be <b>reversible</b>: an earlier build
     * hid the modules but never unhid them, so after running with SlimefunHelper once, single-mod
     * installs lost the {@code sfIMG} category for good. To undo exactly our own edit without
     * clobbering a player's own preferences, the modules are only unhidden when <em>every</em>
     * sfIMG module is currently hidden - that is the signature our hide leaves behind. If the
     * player deliberately hid only a few sfIMG modules, some remain visible and their choice is
     * left untouched.
     *
     * <p>Must run after {@code Systems.load()}: that reloads {@code Config} from disk and would
     * otherwise overwrite this list on a first launch. The {@code @PostInit} hook below runs at
     * exactly that point. Repeated calls are harmless.
     */
    @meteordevelopment.meteorclient.utils.PostInit
    public static void applyCompatHideIfNeeded() {
        if (!installed) return;

        var config = meteordevelopment.meteorclient.systems.config.Config.get();
        if (config == null) return;

        var hidden = config.hiddenModules.get();
        boolean changed = false;

        if (SF_HELPER_PRESENT) {
            for (var module : modules) {
                if (!hidden.contains(module)) {
                    hidden.add(module);
                    changed = true;
                }
            }
        } else if (allModulesHidden(hidden)) {
            // 这些隐藏是本 Mod 早先加进去的（含旧版本只加不减留下的状态），单开时必须恢复显示。
            changed = hidden.removeAll(modules);
            if (changed) {
                com.codigohasta.addon.AddonTemplate.LOG.info(
                        "SlimefunHelper absent: restored sfIMG module visibility for {} modules",
                        modules.size()
                );
            }
        }

        // Persist immediately so the list survives even if the session ends without a normal save.
        if (changed) config.save();
    }

    /** True when every sfIMG module is currently in Meteor's hidden list. */
    private static boolean allModulesHidden(java.util.List<meteordevelopment.meteorclient.systems.modules.Module> hidden) {
        if (modules.isEmpty()) return false;
        for (var module : modules) {
            if (!hidden.contains(module)) return false;
        }
        return true;
    }

    private static void add(BaseModule module) {
        modules.add(module);
        Modules.get().add(module);
    }

    // ==================== 状态查询 ====================

    public static boolean ready() {
        // Stood down: every sfIMG event channel and mixin is absent, so nothing may run.
        if (SF_HELPER_PRESENT) return false;
        return installed && mc.player != null && mc.world != null;
    }

    public static boolean modifying() {
        if (!ready()) return false;
        for (var module : modules) {
            if (module.isActive()) return true;
        }
        return false;
    }

    public static boolean extraActive() {
        return ready() && ElytraExtra.INSTANCE.isActive();
    }

    public static LegalMovementManager getMovement() {
        return movement;
    }

    // ==================== 模块禁用与清理 ====================

    public static void onModuleDisabled() {
        if (!installed) return;

        for (var module : modules) {
            if (module.isActive()) return;
        }

        inTick = false;
        FlightPacketDiagnostics.clear();
        LegacySnapRotManager.INSTANCE.reset();

        ACTasks.clear();
        InvTasks.clearClickQueue();
        Tasks.reset();
        PacketManager.clear();
        WorldTasks.cancelAllPendingChunkTasks();
        RenderTasks.clear();

        MovTasks.getNoFall().resetRuntimeState();
        FloatingUtils.INSTANCE.resetRuntimeState();
        SpearEnhance.INSTANCE.setForceSpearReset(false);

        // Stood down: ClientPlayerAccess is one of the skipped sfimg mixins, so this cast would
        // throw ClassCastException on a live player.
        if (!SF_HELPER_PRESENT && mc.player != null) {
            com.codigohasta.addon.utils.slimefunhelper.accessors.access.ClientPlayerAccess
                    .of(mc.player)
                    .setForceNoFall(false);
        }

        current = null;
        movement = null;

        MovTasks.PLAYER_PIPELINE_0.resetForNewPlayer(null);
    }

    // ==================== 玩家管理 ====================

    private static void ensurePlayer(ClientPlayerEntity player) {
        if (player != current) {
            resetBatch3State();
            current = player;
            movement = new LegalMovementManager();

            MovTasks.PLAYER_PIPELINE_0.resetForNewPlayer(player);
            movement.addMovementModifier(MovTasks.PLAYER_PIPELINE_0);

            PlayerStateManager.INSTANCE.reset();
            PositionPredict.INSTANCE.reset();

            Listener.getPlayerInitConfiguration().fire(player);
            Listener.getGameJoinPoint().fire(player);
            Listener.getWorldSwitchPoint().fire(mc.world);
            // Ported from upstream 1.1.7.2 ClientPlayNetworkHandlerEvents: respawn is also a
            // game-join entry point for the player-carrying respawn channel.
            Listener.getPlayerRespawnPoint().fire(player);
        }
    }

    // ==================== Tick 流程 ====================
    private static void resetBatch3State() {
        InvTasks.clearClickQueue();
        for (var module : modules) {
            if (module instanceof Flight flight) flight.resetRuntimeState();
            else if (module instanceof ForwardTp tp) tp.resetRuntimeState();
            else if (module instanceof SetBackLog log) log.resetRuntimeState();
            else if (module instanceof StepHeight step) step.resetRuntimeState();
            else if (module instanceof AntiLiquid liquid) liquid.resetRuntimeState();
            else if (module instanceof BaritoneFix baritone) baritone.resetRuntimeState();
            else if (module instanceof FastCraft craft) craft.resetRuntimeState();
            else if (module instanceof BackTrack backtrack) backtrack.onShutdown();
            else if (module instanceof BoatVClip || module instanceof EnderEyeLog) module.onDisableModule();
        }
    }

    public static void preTick() {
        if (!ready()) return;

        ensurePlayer(mc.player);

        for (var module : modules) {
            module.pollKeys();
        }

        if (!modifying()) return;
        Tasks.tick();

        if (!modifying()) return;
        Listener.getPreTick().broadcast(null);
        Listener.getPreGameTick().broadcast(mc.player);
    }

    public static void postGameTick() {
        if (modifying()) {
            Listener.getPostGameTick().broadcast(mc.player);
        }
    }

    public static void postTick() {
        if (modifying()) {
            Tasks.postTick();
            if (modifying()) {
                Listener.getPostTick().broadcast(null);
            }
        }
    }

    // ==================== 玩家移动管线 ====================

    public static void prePlayer(ClientPlayerEntity player) {
        if (!modifying() || player != mc.player) return;

        ensurePlayer(player);

        if (!modifying() || movement == null) return;

        inTick = true;
        movement.preProgress(player);
    }

    public static void afterInput(ClientPlayerEntity player) {
        if (!inTick) return;

        var original = player.input.playerInput;
        movement.postInputTick(player);

        if (inTick && !original.equals(player.input.playerInput)) {
            var input = PlayerInputUtils.of(player);
            var vector = new Vec2f(input.sidewaysSpeed(), input.forwardSpeed());

            player.input.movementVector = com.codigohasta.addon.utils.slimefunhelper.hooks.ViaFabricPlusHooks
                    .getInstance()
                    .getCurrentVersion()
                    .isLowerOrEqualTo(21, 4)
                    ? vector
                    : vector.normalize();
        }
    }

    public static boolean beforeMovement(ClientPlayerEntity player) {
        if (!inTick) return true;

        var e = Listener.getClientPlayerSendMovementPoint().fire(player);
        if (!inTick) return true;

        boolean apply = player.hasVehicle()
                ? movement.preInputProgress(player)
                : movement.preMovementProgress(player);

        return !inTick || (apply && !e.isCancelled());
    }

    public static boolean beforeTravel(ClientPlayerEntity player, Event<Vec3d> input) {
        if (!inTick) return true;

        // Ported from upstream 1.1.7.2 PlayerEntityEvents.onPlayerTravel: the travel channel
        // fires before the legal-movement pre-travel tick.
        Listener.getPlayerTravelingTick().dispatch(input);

        boolean apply = movement.preTravelTick(player, input) && !input.isCancelled();
        if (!apply && inTick) {
            movement.postTravelTick(player, input);
        }
        return apply;
    }

    public static void afterTravel(ClientPlayerEntity player, Vec3d input) {
        if (inTick) {
            movement.postTravelTick(player, new Event<>(input, false, false, player));
        }
    }

    public static void postPlayer(ClientPlayerEntity player) {
        if (!inTick) return;

        try {
            Listener.getClientPlayerPostSendMovementPoint().broadcast(player);
            if (inTick) {
                movement.postProgress(player);
            }
        } finally {
            inTick = false;
        }
    }

    // ==================== 包发 ====================

    public static Packet<?> prepareOutgoing(Packet<?> packet, net.minecraft.network.ClientConnection connection) {
        if (!modifying()) return packet;

        Packet<?> updated = Listener.getPacketPoint().routeOutgoing(packet, PacketManager::enqueue, connection);
        if (updated != null) {
            PacketManager.transferCallbacks(packet, updated);
        }
        return updated;
    }

    public static void sent(Packet<?> packet, net.minecraft.network.ClientConnection connection) {
        if (modifying()) {
            FlightPacketDiagnostics.sent(packet);
            Listener.getPacketPostSendPoint().broadcast(packet, connection);
        }
    }

    public static void scheduled(Packet<?> packet, net.minecraft.network.ClientConnection connection) {
        if (modifying()) {
            Listener.getPacketPostScheduleSendPoint().broadcast(packet, connection);
        }
    }

    // ==================== 包接 ====================

    public static void received(Packet<?> packet) {
        if (!modifying()) return;

        FlightPacketDiagnostics.received(packet);

        if (packet instanceof PlayerPositionLookS2CPacket p) {
            FlightPacketDiagnostics.corrected(p);
        }

        Listener.getPacketPostHandlePoint().broadcast(packet, mc.getNetworkHandler());
    }

    public static Packet<?> acceptIncoming(Packet<?> packet, net.minecraft.network.ClientConnection connection) {
        if (packet == null || !modifying()) return packet;
        return Listener.acceptS2CPacket(connection, packet);
    }

    public static Packet<?> beforeReceived(Packet<?> packet) {
        if (!modifying()) return packet;
        return Listener.getPacketPreHandlePoint().preHandle(packet, mc.getNetworkHandler()) ? packet : null;
    }

    // ==================== 游戏事件 ====================

    @EventHandler
    private void onLeft(GameLeftEvent event) {
        resetBatch3State();
        FlightPacketDiagnostics.clear();
        ServerFlightState.clear();

        if (installed) {
            LegacySnapRotManager.INSTANCE.reset();

            for (var module : modules) {
                if (module instanceof Criticals
                        || module instanceof SpearAttack
                        || module instanceof ElytraFinder
                        || module instanceof Velocity
                        || module instanceof AutoResync
                        || module instanceof PrinterRewrite
                        || module instanceof BlockRotate
                        || module instanceof PlayerInputManager
                        || module instanceof MultiPlaceDisabler
                        || module instanceof ClientSideCommand
                        || module instanceof ChatExtra
                        || module instanceof InGuiChatBox
                        || module instanceof BowEnhance) {
                    module.onDisableModule();
                }
            }
        }

        inTick = false;
        current = null;
        movement = null;
        ACTasks.clear();
        Tasks.reset();
        PacketManager.clear();
        WorldTasks.cancelAllPendingChunkTasks();
        RenderTasks.clear();

        if (installed) {
            MovTasks.getNoFall().resetRuntimeState();
            FloatingUtils.INSTANCE.resetRuntimeState();
            SpearEnhance.INSTANCE.setForceSpearReset(false);
            MovTasks.PLAYER_PIPELINE_0.resetForNewPlayer(null);
        }

        if (installed) {
            DisablerManager.INSTANCE.onDisconnect(new Event<>(null, false, false));
            PlayerStateManager.INSTANCE.onLeave(new Event<>(null, false, false));
            PositionPredict.INSTANCE.reset();
            ElytraExtra.INSTANCE.onWorldSwitch(new Event<>(null, false, false));
            ElytraBot.INSTANCE.onDisableModule();
            QueueMine.INSTANCE.onDisableModule();
            FakeBlockManager.INSTANCE.onWorldSwitch(new Event<>(null, false, false));
            Blink.INSTANCE.onDisconnect(new Event<>(null, false, false));
        }
    }

    // ==================== 渲染事件 ====================

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!modifying()) return;

        com.codigohasta.addon.utils.slimefunhelper.utils.RenderUtils.current = event;
        try {
            RenderListener.getRender3DEvent().fire(event.matrices, event.tickDelta);
            RenderTasks.renderPending();
        } finally {
            com.codigohasta.addon.utils.slimefunhelper.utils.RenderUtils.current = null;
        }
    }

    @EventHandler
    private void onRender2d(meteordevelopment.meteorclient.events.render.Render2DEvent event) {
        if (modifying()) {
            RenderListener.getRender2DEvent().broadcast(event.drawContext, event.tickDelta);
        }
    }
}
