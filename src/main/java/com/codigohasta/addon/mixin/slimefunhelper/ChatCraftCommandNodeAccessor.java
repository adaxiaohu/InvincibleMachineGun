package com.codigohasta.addon.mixin.slimefunhelper;
import com.mojang.brigadier.tree.*;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=CommandNode.class, remap=false)
public interface ChatCraftCommandNodeAccessor {
    @Accessor("children") Map<String, CommandNode<?>> sfimg$children();
    @Accessor("literals") Map<String, LiteralCommandNode<?>> sfimg$literals();
    @Accessor("arguments") Map<String, ArgumentCommandNode<?,?>> sfimg$arguments();
}
