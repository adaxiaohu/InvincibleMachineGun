package com.codigohasta.addon.mixin.slimefunhelper;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.*;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.codigohasta.addon.utils.slimefunhelper.events.*;
@Mixin(net.minecraft.client.network.ClientPlayNetworkHandler.class)
public abstract class BadPacketsTagsMixin {
    @ModifyExpressionValue(
            method = "startTagReload",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/registry/tag/TagPacketSerializer$Serialized;toRegistryTags(Lnet/minecraft/registry/Registry;)Lnet/minecraft/registry/tag/TagGroupLoader$RegistryTags;"))
    private <T> TagGroupLoader.RegistryTags<T> onRegistryTagReload(
            TagGroupLoader.RegistryTags<T> original,
            @Local(argsOnly = true) RegistryKey<? extends Registry<? extends T>> registryKey) {
        Map<TagKey<T>, List<RegistryEntry<T>>> tagMap = original.tags();
        Event<Map<TagKey<T>, List<RegistryEntry<T>>>> event = new Event<>(tagMap, false, true, original.key());
        var module = com.codigohasta.addon.modules.slimefunhelper.BadPacketsFix.INSTANCE;
        if (module != null && module.isActive()) module.fixTagsBadPackets(event);
        if (event.context != tagMap) {
            return new TagGroupLoader.RegistryTags<>(original.key(), event.context);
        }
        return original;
    }
}
