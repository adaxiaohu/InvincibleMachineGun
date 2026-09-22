package com.codigohasta.addon.mixin.slimefunhelper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import net.minecraft.client.network.PendingUpdateManager;
@Mixin(PendingUpdateManager.class)
public interface PendingUpdateManagerAccessor {@Accessor("sequence")void sf$setSequence(int value);}
