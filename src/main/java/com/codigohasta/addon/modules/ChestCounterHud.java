package com.codigohasta.addon.modules;

import com.codigohasta.addon.utils.sakura.render.AbstractRowHud;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.util.math.BlockPos;

public class ChestCounterHud extends AbstractRowHud {

    private static final int RADIUS = 32;

    public ChestCounterHud() {
        super("chest-counter-hud", "箱子计数 HUD。",
            651, 490, 0.9);
    }

    @Override
    protected String iconName() {
        return "chest-counter-widget";
    }

    @Override
    protected void onTickUpdate() {
        if (mc.world == null || mc.player == null) return;

        int singles = 0;
        int doubles = 0;

        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        int cr = (RADIUS >> 4) + 1;

        var chunkManager = mc.world.getChunkManager();
        double r2 = (double) RADIUS * RADIUS;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();

        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                if (!chunkManager.isChunkLoaded(cx, cz)) continue;
                var chunk = chunkManager.getWorldChunk(cx, cz, false);
                if (chunk == null) continue;

                for (var be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof ChestBlockEntity chest)) continue;

                    BlockPos pos = chest.getPos();
                    double dx = pos.getX() + 0.5 - px;
                    double dy = pos.getY() + 0.5 - py;
                    double dz = pos.getZ() + 0.5 - pz;
                    if (dx * dx + dy * dy + dz * dz > r2) continue;

                    var state = chest.getCachedState();
                    ChestType type = state.contains(ChestBlock.CHEST_TYPE)
                        ? state.get(ChestBlock.CHEST_TYPE) : ChestType.SINGLE;

                    if (type == ChestType.SINGLE) singles++;
                    else doubles++;
                }
            }
        }

        this.singleCount = singles;
        this.doubleCount = doubles / 2;
    }

    private int singleCount;
    private int doubleCount;

    @Override
    public void onDeactivate() {
        singleCount = 0;
        doubleCount = 0;
    }

    @Override
    protected String displayText() {
        return "chests: S:" + singleCount + " D:" + doubleCount;
    }
}
