package com.codigohasta.addon.mixin;

import com.codigohasta.addon.utils.SlimefunHelperCompat;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Skips IMG's ported sfIMG mixins while the standalone SlimefunHelper mod is installed.
 *
 * <p>Both projects put the same accessor interfaces onto the same Minecraft classes
 * ({@code ClientPlayerInteractionManager}, {@code ClientPlayerEntity},
 * {@code PlayerInteractBlockC2SPacket}, {@code AbstractBlock.AbstractBlockState}), and several of
 * those interfaces declare identical {@code default} methods. The JVM then refuses the target
 * class with {@code IncompatibleClassChangeError: Conflicting default methods}. Because the
 * clash is between interface defaults on the same target class, it cannot be fixed by renaming
 * methods or packages - one side must not be applied at all.
 *
 * <p>IMG yields: when SlimefunHelper is present every mixin under
 * {@code com.codigohasta.addon.mixin.slimefunhelper} is skipped, so the Minecraft classes only
 * ever receive SlimefunHelper's copy. When SlimefunHelper is absent the whole package applies as
 * before and IMG's own sfIMG implementation is fully functional.
 *
 * <p>This class must stay free of Minecraft references: {@link #shouldApplyMixin} runs during
 * mixin bootstrap, before the game classes are loadable.
 */
public class SfImgMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!SlimefunHelperCompat.isSlimefunHelperLoaded()) return true;
        return !mixinClassName.startsWith(SlimefunHelperCompat.SFIMG_MIXIN_PACKAGE);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(
            String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
