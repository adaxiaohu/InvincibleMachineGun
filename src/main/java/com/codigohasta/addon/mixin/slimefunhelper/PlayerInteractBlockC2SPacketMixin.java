package com.codigohasta.addon.mixin.slimefunhelper;

import com.codigohasta.addon.utils.slimefunhelper.accessors.access.PlayerInteractBlockC2SPacketAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;


@Environment(EnvType.CLIENT)
@Mixin(PlayerInteractBlockC2SPacket.class)
public abstract class PlayerInteractBlockC2SPacketMixin implements PlayerInteractBlockC2SPacketAccess {

    @Override
    @Mutable
    @Accessor("hand")
    public abstract void setHand(Hand hand);

    @Override
    @Mutable
    @Accessor("blockHitResult")
    public abstract void setBlockHitResult(BlockHitResult blockHitResult);

    @Override
    @Mutable
    @Accessor("sequence")
    public abstract void setSequence(int sequence);

    @Unique
    UseContext useContext;
    @Override public UseContext getUseContext() { return useContext; }
    @Override public void setUseContext(UseContext context) { useContext = context; }
}
