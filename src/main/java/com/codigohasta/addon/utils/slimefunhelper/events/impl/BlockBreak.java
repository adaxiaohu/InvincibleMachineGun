package com.codigohasta.addon.utils.slimefunhelper.events.impl;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public class BlockBreak implements SequencedAction {
    BlockPos blockPos;
    Stage stage;
    ItemStack stack;
    float progress;

    public BlockBreak(BlockPos blockPos, Stage stage, ItemStack stack, float progress) {
        this.blockPos = blockPos;
        this.stage = stage;
        this.stack = stack;
        this.progress = progress;
    }

    public BlockPos blockPos() { return blockPos; }
    public BlockBreak blockPos(BlockPos value) { blockPos = value; return this; }
    public Stage stage() { return stage; }
    public BlockBreak stage(Stage value) { stage = value; return this; }
    public ItemStack stack() { return stack; }
    public BlockBreak stack(ItemStack value) { stack = value; return this; }
    public float progress() { return progress; }
    public BlockBreak progress(float value) { progress = value; return this; }

    public enum Stage {
        PRE,
        POST;
    }
}
