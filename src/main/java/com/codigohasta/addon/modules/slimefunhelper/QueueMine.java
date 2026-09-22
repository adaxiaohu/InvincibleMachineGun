package com.codigohasta.addon.modules.slimefunhelper;

import com.codigohasta.addon.utils.BlockStateCompat;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import com.codigohasta.addon.utils.slimefunhelper.accessors.hacks.PlayerInteractionAccess;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.events.RenderListener;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.modules.slimefunhelper.InteractExtra;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config.WrapColor;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.render.RenderCollectors;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.Tasks;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.IntRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.KeyBindRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.NBTRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind;
import com.codigohasta.addon.utils.slimefunhelper.utils.RenderUtils;
import com.codigohasta.addon.utils.slimefunhelper.utils.render.RenderCollector;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class QueueMine extends BaseModule {
    public static QueueMine INSTANCE;

    public QueueMine() {
        super("QueueMine");
        INSTANCE = this;
        bindFlag(enable);
    }

    public ModulePath packetMine = makePath(Configs.MINE_CONFIG, "queue-mine");
    public final FlagRef enable = flagBuilder(packetMine.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(packetMine.addHotkey(), new MultiKeyBind(), packetMine.addEnable())
            .build();

    public final IntRef queueSize =
            intBuilder(packetMine.add("queue-size")).defaultValue(2).build();

    public final FlagRef useDoubleBreak =
            flagBuilder(packetMine.add("double-break")).build();

    public final FlagRef ignoreCooldown =
            flagBuilder(packetMine.add("ignore-cooldown")).build();

    public final FlagRef supportDoubleBreakGhostHand =
            flagBuilder(packetMine.add("double-break-ghost-hand")).build();
    //
    //    public final FlagRef rotate = flagBuilder(packetMine.add("rotate-when-break"))
    //        .build();

    public final FlagRef render = flagBuilder(packetMine.add("render")).build();

    public final NBTRef<WrapColor> color = builder(packetMine.add("render-color"), WrapColor.class)
            .defaultValue(new WrapColor((Color.GREEN)))
            .build();

    public final Queue<BlockPos> breakRequest = new ArrayDeque<>();

    public void registerAll() {
        super.registerAll();
        registerServiceListener(Listener.getPostHandleInputEvents(), this::onPostInputEvent);
        registerListener(Listener.getMineBlockAction(), this::handleQueueSumbit);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
    }

    int lastSumitTick = 0;
    RenderCollector<Box> outline = RenderCollectors.createBoxCollector(true, false, false);

    public void onPostInputEvent(Event<Void> eventVoid) {
        outline.clear();
        if (checkNull()) return;
        if (!isActive() && breakRequest.isEmpty()) return;
        if (lastSumitTick != Tasks.getTick()) {
            tickQueue();
        }
        if (useDoubleBreak.get()
                && supportDoubleBreakGhostHand.get()
                && PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos() != null) {
            PacketMine.INSTANCE.tickGhostHandDoubleBreak(null, false);
        }
        for (var re : breakRequest) {
            outline.submit(new Box(re).expand(-0.2), color.get().withAlpha(255));
        }
    }

    public void tickQueue() {
        boolean shouldTryStartBreak = true;
        if (!ignoreCooldown.get() && MineExtra.INSTANCE.getMiningPacketCooldown(1) > 0) {
            shouldTryStartBreak = false;
        }
        var access = PlayerInteractionAccess.of(mc.interactionManager);
        if (access == null) return;

        if (shouldTryStartBreak) {
            boolean currentCanDoubleBreak = access.getCurrentFailBreakPos() == null && useDoubleBreak.get();
            while (!breakRequest.isEmpty()) {
                BlockPos posLatest = breakRequest.peek();
                if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getEntityPos(), posLatest)) {
                    breakRequest.poll();
                    continue;
                }
                BlockState state = mc.world.getBlockState(posLatest);
                if (state.getBlock().getHardness() < 0.0F || BlockStateCompat.isLiquid(state) || state.isAir()) {
                    breakRequest.poll();
                    continue;
                }
                BlockPos currentMiningPos = access.getCurrentMiningPos();
                if (!Objects.equals(currentMiningPos, posLatest)) {
                    // change
                    //                if(rotate.get()){
                    //
                    //                }
                    access.sendStartBreakPacket(posLatest);
                    if (currentCanDoubleBreak) {
                        access.sendFailBreakCurrentPos(null);
                        currentCanDoubleBreak = false;
                        breakRequest.poll();
                    } else {
                        if (Objects.equals(access.getCurrentMiningPos(), posLatest)) {
                            access.sendAbortBreakPacket();
                            break;
                        } else {
                            // instant break, next block
                            breakRequest.poll();
                        }
                    }
                } else if (currentCanDoubleBreak) {
                    access.sendFailBreakCurrentPos(null);
                    currentCanDoubleBreak = false;
                    breakRequest.poll();
                } else {
                    break;
                }
            }
        }
        if (!breakRequest.isEmpty()) {
            BlockPos posLatest = breakRequest.peek();
            if (Objects.equals(posLatest, access.getCurrentMiningPos())
                    && InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getEntityPos(), posLatest)) {
                if (access.breakIfComplete()) {
                    breakRequest.poll();
                }
            }
        }
    }

    public void handleQueueSumbit(Event<HitResult> event) {
        if (enable.get()) {
            HitResult hitResult = event.context;
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                boolean att = event.getArgs(0);
                BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                BlockPos pos = blockHitResult.getBlockPos().toImmutable();
                event.cancel();
                if (sumitMine(pos)) {
                    lastSumitTick = Tasks.getTick();
                    tickQueue();
                    if (breakRequest.size() > queueSize.get()) {
                        breakRequest.poll();
                    }
                }
            }
        }
    }

    public boolean sumitMine(BlockPos pos) {
        if (!breakRequest.isEmpty() && breakRequest.stream().anyMatch(pos::equals)) {
            return false;
        }
        if (Objects.equals(PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos(), pos)) {
            return false;
        }
        breakRequest.add(pos);
        return true;
    }

    public void onRender(Event<MatrixStack> eventRender) {
        if (render.get()) {
            RenderUtils.startDrawVirtual(eventRender.context);
            try {
                outline.render3D(eventRender.context);
            } finally {
                RenderUtils.stopDrawVirtual(eventRender.context);
            }
        }
    }
@Override public void onDisableModule(){breakRequest.clear();if(!checkNull()&&mc.interactionManager!=null)mc.interactionManager.cancelBlockBreaking();}
}
