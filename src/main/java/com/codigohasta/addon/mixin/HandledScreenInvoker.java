package com.codigohasta.addon.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 允许模块调用 HandledScreen 的原生槽位点击入口。
 *
 * TweakerMore 的自动清空容器最终通过 Item Scroller 调用容器 GUI 的 slotClicked/onMouseClick，
 * 而不是自己先拿到鼠标光标再做二次操作。这个 Invoker 用于复现同一路径。
 */
@Mixin(HandledScreen.class)
public interface HandledScreenInvoker {
    @Invoker("onMouseClick")
    void codigohasta$invokeOnMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);
}
