package com.codigohasta.addon.modules.slimefunhelper;

import com.codigohasta.addon.utils.BlockStateCompat;

import com.google.common.util.concurrent.Runnables;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import com.codigohasta.addon.utils.slimefunhelper.accessors.hacks.PlayerInteractionAccess;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;

import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.modules.slimefunhelper.InteractExtra;
import com.codigohasta.addon.modules.slimefunhelper.InvExtra;
import com.codigohasta.addon.modules.slimefunhelper.FloatingUtils;
import com.codigohasta.addon.modules.slimefunhelper.LegacySnapRotManager;
import com.codigohasta.addon.modules.slimefunhelper.PlayerStateManager;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config.EntrySet;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config.Regex;
import com.codigohasta.addon.utils.slimefunhelper.managers.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind;
import com.codigohasta.addon.utils.slimefunhelper.utils.InventoryUtils;
import com.codigohasta.addon.utils.slimefunhelper.utils.WorldUtils;
import com.codigohasta.addon.utils.slimefunhelper.utils.collections.IndexEntry;
import com.codigohasta.addon.utils.slimefunhelper.utils.entity.PlayerInputUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public class PacketMine extends BaseModule {
    public static PacketMine INSTANCE;

    public PacketMine() {
        super("PacketMine");
        bindFlag(autoEnable);
        INSTANCE = this;
    }

    public ModulePath packetMine = makePath(Configs.MINE_CONFIG, "mine-oneblock");

    public final FlagRef autoEnable = flagBuilder(packetMine.add("enable")).build();

    public final KeyBindRef hotkey = moduleEntry(packetMine.addHotkey(), new MultiKeyBind(), packetMine.addEnable())
            .build();

    public final IntRef multiplePackets = intBuilder(packetMine.add("multiple-packets"))
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef realBreak =
            flagBuilder(packetMine.add("simulate-real-break")).build();

    public final FlagRef airBreak =
            flagBuilder(packetMine.add("consider-air-break")).build();

    public final FlagRef swingHand = flagBuilder(packetMine.add("swing-hand")).build();

    public final DoubleRef mineThreshold = builder(packetMine.add("mine-threshold"), DoubleRef.TYPE)
            .defaultValue(0.7)
            .validator(Configs.doubleRange(-0.0001F, 1.0001F))
            .build();

    public final FlagRef autoTool = flagBuilder(packetMine.add("auto-pickaxe")).build();

    public final FlagRef autoToolDoubleBreak =
            flagBuilder(packetMine.add("auto-pickaxe-double-break")).build();

    public final FlagRef groundDeceive =
            flagBuilder(packetMine.add("ground-deceive")).build();

    public final FlagRef groundOnlyWhenNoControl =
            flagBuilder(packetMine.add("ground-only-when-no-control")).build();

    public final FlagRef mineOnce =
            flagBuilder(packetMine.add("only-mine-once-per-click")).build();

    public final FlagRef whiteList =
            flagBuilder(packetMine.add("enable-mine-white-list")).build();

    public final NBTRef<EntrySet<Block>> whiteListRegex = builder(
                    packetMine.add("mine-white-list"), EntrySet.<Block>parameter())
            .defaultValue(new EntrySet<>(new Regex("^()$"), Registries.BLOCK))
            .build();

    BlockPos lastMinePos;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onTick);
        registerListener(Listener.getMineBlockAction(), this::onMineBlockAction);
    }

    public boolean hasMiningTarget() {
        return getCurrentMiningPos() != null;
    }

    public boolean isMining() {
        return hasMiningTarget();
    }

    public boolean isMining(BlockPos pos) {
        return Objects.equals(getCurrentMiningPos(), pos);
    }

    public BlockPos getCurrentMiningPos() {
        if (mc.interactionManager == null) {
            return null;
        }
        return PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
    }

    public void cancelPacketMine(BlockPos pos) {
        BlockPos po = getCurrentMiningPos();
        if (Objects.equals(po, pos)) {
            PlayerInteractionAccess.of(mc.interactionManager).resetCurrentMiningPos();
        }
    }

    public void onTick(Event<ClientPlayerEntity> tickEvent) {
        if (switchCallback != null) {
            switchCallback.run();
            switchCallback = null;
        }
        if (isActive()) {
            if (checkNull()) return;
            BlockPos pos = PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
            if (mineOnce.get() && Objects.equals(pos, lastMinePos)) {
                return;
            }
            tickMine();
        }
    }

    public Runnable switchCallback = null;

    public void tickMine() {
        if (mc.interactionManager != null && mc.player != null) {
            if (switchCallback != null) {
                switchCallback.run();
                switchCallback = null;
            }
            BlockPos pos = PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
            if (pos == null) return;

            Runnable currentTickCallback = null;
            boolean postMineCallback = false;
            float progress = 0;
            if (InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getEntityPos(), pos)) {
                BlockState blockState = mc.world.getBlockState(pos);
                IndexEntry<ItemStack> currentItemSlot = getCurrentUsableTool(blockState);

                ItemStack currentTool = currentItemSlot.val();
                if (canMine(blockState, currentTool)) {
                    Event<Pre> eventPre = new Event<>(Pre.INSTANCE, true, false, pos);
                    prePacketMine.dispatch(eventPre);
                    if (!eventPre.isCancelled()) {
                        if (groundDeceive.get() && !mc.player.isOnGround()) {
                            boolean shouldExecute = true;
                            if (groundOnlyWhenNoControl.get()
                                    && !PlayerInputUtils.of(mc.options).hasMovementControl()) {
                                shouldExecute = false;
                            }
                            if (shouldExecute) {

                                mc.getNetworkHandler()
                                        .sendPacket(LegacySnapRotManager.INSTANCE.createSnapAt(
                                                PlayerStateManager.INSTANCE.lastPitch,
                                                PlayerStateManager.INSTANCE.lastYaw,
                                                true));
                                FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                                FloatingUtils.INSTANCE.setForceOnGroundVia(true);
                            }
                        }
                        Runnable callback = InvExtra.INSTANCE.swapInventoryIndexToHand(currentItemSlot.index());

                        progress = PlayerInteractionAccess.of(mc.interactionManager)
                                .predictCurrentMiningProgressWithTool(currentTool);

                        for (int i = 0; i < multiplePackets.get(); ++i) {
                            if (swingHand.get())
                                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                            PlayerInteractionAccess.of(mc.interactionManager)
                                    .sendBreakPacket(pos, !(realBreak.get() && progress > 0.7F));
                        }
                        currentTickCallback = callback;
                        postMineCallback = true;
                    }
                }
            }
            if (autoToolDoubleBreak.get()
                    && PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos() != null) {
                tickGhostHandDoubleBreak(currentTickCallback, groundDeceive.get());
            } else {
                if (currentTickCallback != null) {
                    currentTickCallback.run();
                }
            }
            if (postMineCallback) {
                postPacketMine.broadcast(Post.INSTANCE, pos, progress);
            } else if (WorldUtils.isChunkLoaded(pos)) {
                BlockState state = mc.world.getBlockState(pos);
                if (state.isAir() || BlockStateCompat.isLiquid(state)) {
                    lastMinePos = pos;
                }
            }
        }
    }

    public void tickGhostHandDoubleBreak(Runnable currentTickCallback, boolean groundDeceive) {
        if (switchCallback != null) {
            return;
        }
        BlockPos failPos = PlayerInteractionAccess.of(mc.interactionManager).getCurrentFailBreakPos();
        if (failPos == null) {
            if (currentTickCallback != null) {
                currentTickCallback.run();
            }
            return;
        }
        BlockState blockState = mc.world.getBlockState(failPos);
        IndexEntry<ItemStack> currentItemSlot = getCurrentUsableTool(blockState);
        ItemStack currentTool = currentItemSlot.val();
        if (canMineFailBreak(blockState, currentTool, groundDeceive)) {
            Runnable callback = InvExtra.INSTANCE.swapInventoryIndexToHand(currentItemSlot.index());
            Runnable currentCallback = currentTickCallback == null ? Runnables.doNothing() : currentTickCallback;
            switchCallback = () -> {
                callback.run();
                currentCallback.run();
            };
        } else {
            if (currentTickCallback != null) {
                currentTickCallback.run();
            }
        }
    }

    public void onMineBlockAction(Event<HitResult> event) {
        if (event.context != null && event.context.getType() == HitResult.Type.BLOCK) {
            lastMinePos = null;
        }
    }

    @NotNull
    public IndexEntry<ItemStack> getCurrentUsableTool(BlockState currentState) {
        if (autoTool.get()) {
            BlockState calS;
            if (currentState.isAir() || BlockStateCompat.isLiquid(currentState)) {
                calS = Blocks.OBSIDIAN.getDefaultState();
            } else {
                calS = currentState;
            }
            var re = InventoryUtils.findBestPlayerItem(
                    item -> {
                        return (double)
                                WorldUtils.getPlayerBlockBreakingSpeedWithCanMineMultiply(mc.player, calS, item);
                    },
                    true,
                    true);
            if (re != null) {
                return re;
            }
        }
        return InventoryUtils.getSelectedItem();
    }

    public boolean isMineable(BlockState state) {
        return state.getBlock().getHardness() >= 0.0F
                && !BlockStateCompat.isLiquid(state)
                && (airBreak.get() || !state.isAir())
                && (!whiteList.get() || !whiteListRegex.get().test(state.getBlock()));
    }

    public boolean canMine(BlockState state, ItemStack tool) {
        // do not mine liquid, that's a disaster
        // do not mine air, shit
        if (isMineable(state)) {
            if (mineThreshold.get() > 0) {
                var access = PlayerInteractionAccess.of(mc.interactionManager);
                float speed = access.predictCurrentMiningProgressWithTool(tool);
                if (groundDeceive.get() && !mc.player.isOnGround()) {
                    speed *= 5;
                }
                return speed > Math.min(0.98, mineThreshold.get());
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean canMineFailBreak(BlockState state, ItemStack tool, boolean groundDeceive) {
        // do not mine liquid, that's a disaster
        // do not mine air, shit
        if (isMineable(state)) {
            var access = PlayerInteractionAccess.of(mc.interactionManager);
            var speed = access.predictFailMiningProgressWithTool(tool, 0);
            if (groundDeceive && !mc.player.isOnGround()) {
                speed *= 5;
            }
            return speed > 0.99;
        } else {
            return false;
        }
    }



    public static final com.codigohasta.addon.utils.slimefunhelper.events.Channel<Pre> prePacketMine = new com.codigohasta.addon.utils.slimefunhelper.events.Channel<>();



    public static final com.codigohasta.addon.utils.slimefunhelper.events.Channel<Post> postPacketMine = new com.codigohasta.addon.utils.slimefunhelper.events.Channel<>();

    public static class Pre {
        public static final Pre INSTANCE = new Pre();

        private Pre() {}
    }

    public static class Post {
        public static final Post INSTANCE = new Post();

        private Post() {}
    }
}
