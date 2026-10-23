package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.modules.slimefunhelper.BlockRotate;
import com.codigohasta.addon.modules.slimefunhelper.PrinterRewrite;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.PlayerInteractBlockC2SPacketAccess;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.events.impl.UseItemOnBlock;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.ArrayDeque;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source placement context capture, including the state before client prediction. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class BlockInteractionEventsMixin {
    @Shadow @Final private MinecraftClient client;
    @Unique private final ArrayDeque<MutableBoolean> lastInteractCaptureBlockPlace = new ArrayDeque<>(4);

    @Unique private static boolean sf$placementActive() {
        return (PrinterRewrite.INSTANCE != null && PrinterRewrite.INSTANCE.isActive())
                || (BlockRotate.INSTANCE != null && BlockRotate.INSTANCE.isActive());
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void onPreInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir, @Local(argsOnly = true) LocalRef<BlockHitResult> hand2) {
        if (!sf$placementActive()) return;
        ItemStack currentStack = player.getStackInHand(hand);
        Event<UseItemOnBlock> blockHitResultEvent =
                new Event<>(new UseItemOnBlock(hitResult, ActionResult.SUCCESS, java.util.Optional.empty(), hand, currentStack), true, true);
        Listener.getPrePlayerUseItemAtBlock().dispatch(blockHitResultEvent);
        if (blockHitResultEvent.isCancelled()) {
            cir.setReturnValue(blockHitResultEvent.context.actionResult());
        } else {
            BlockHitResult hitResult2 = blockHitResultEvent.context.hitResult();
            if (hitResult2 != hitResult) hand2.set(hitResult2);
        }
    }

    @WrapOperation(method = "interactBlock", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V"))
    private void onInteractBlockAction(ClientPlayerInteractionManager instance, ClientWorld world,
            SequencedPacketCreator packetCreator, Operation<Void> original,
            @Local(argsOnly = true) Hand hand, @Local(argsOnly = true) BlockHitResult hitResult,
            @Local MutableObject<ActionResult> actionResult) {
        if (!sf$placementActive()) {
            original.call(instance, world, packetCreator);
            return;
        }
        ItemStack stackCopy = client.player.getStackInHand(hand).copy();
        BlockState state = client.world.getBlockState(hitResult.getBlockPos());
        MutableBoolean placeBlock = new MutableBoolean(false);
        net.minecraft.util.math.BlockPos predictingPlace =
                new net.minecraft.item.ItemPlacementContext(client.player, hand, stackCopy, hitResult).getBlockPos();
        original.call(instance, world, (SequencedPacketCreator) (seq) -> {
            lastInteractCaptureBlockPlace.addLast(placeBlock);
            try {
                var packet = packetCreator.predict(seq);
                if (packet instanceof PlayerInteractBlockC2SPacketAccess access) {
                    access.setUseContext(new PlayerInteractBlockC2SPacketAccess.UseContext(
                            stackCopy, state, actionResult.get(),
                            placeBlock.booleanValue() ? java.util.Optional.of(predictingPlace) : java.util.Optional.empty()));
                }
                return packet;
            } finally {
                lastInteractCaptureBlockPlace.removeLast();
            }
        });
        ActionResult acc = actionResult.get();
        Event<UseItemOnBlock> eventResult =
                new Event<>(new UseItemOnBlock(hitResult, acc,
                        placeBlock.booleanValue() ? java.util.Optional.of(predictingPlace) : java.util.Optional.empty(),
                        hand, stackCopy), false, true);
        Listener.getPostPlayerUseItemAtBlock().dispatch(eventResult);
        actionResult.setValue(eventResult.context.actionResult());
    }

    @Inject(method = "interactBlockInternal", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/item/ItemStack;useOnBlock(Lnet/minecraft/item/ItemUsageContext;)Lnet/minecraft/util/ActionResult;"))
    private void onInteractBlockInternalCaptureBlockPlace(ClientPlayerEntity player, Hand hand,
            BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        var re = lastInteractCaptureBlockPlace.peekLast();
        if (re != null) re.setValue(true);
    }
}
