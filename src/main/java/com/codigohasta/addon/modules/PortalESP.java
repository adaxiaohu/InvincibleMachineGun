package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PortalESP extends Module {

    private final SettingGroup sgNether = settings.createGroup("下界传送门");
    private final SettingGroup sgEnd = settings.createGroup("末地传送门");
    private final SettingGroup sgGateway = settings.createGroup("末地折跃门");
    private final SettingGroup sgRender = settings.createGroup("渲染设置");

    // --- 下界传送门设置 ---
    private final Setting<Boolean> netherEnabled = sgNether.add(new BoolSetting.Builder()
        .name("启用下界传送门")
        .description("显示下界传送门方块的透视框。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> netherShapeMode = sgNether.add(new EnumSetting.Builder<ShapeMode>()
        .name("渲染模式")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> netherSideColor = sgNether.add(new ColorSetting.Builder()
        .name("填充颜色")
        .defaultValue(new SettingColor(200, 0, 255, 50))
        .visible(() -> netherShapeMode.get() != ShapeMode.Lines)
        .build()
    );

    private final Setting<SettingColor> netherLineColor = sgNether.add(new ColorSetting.Builder()
        .name("边框颜色")
        .defaultValue(new SettingColor(200, 0, 255, 255))
        .visible(() -> netherShapeMode.get() != ShapeMode.Sides)
        .build()
    );

    // --- 末地传送门设置 ---
    private final Setting<Boolean> endEnabled = sgEnd.add(new BoolSetting.Builder()
        .name("启用末地传送门")
        .description("显示末地传送门方块的透视框。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> endShapeMode = sgEnd.add(new EnumSetting.Builder<ShapeMode>()
        .name("渲染模式")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> endSideColor = sgEnd.add(new ColorSetting.Builder()
        .name("填充颜色")
        .defaultValue(new SettingColor(0, 255, 200, 50))
        .visible(() -> endShapeMode.get() != ShapeMode.Lines)
        .build()
    );

    private final Setting<SettingColor> endLineColor = sgEnd.add(new ColorSetting.Builder()
        .name("边框颜色")
        .defaultValue(new SettingColor(0, 255, 200, 255))
        .visible(() -> endShapeMode.get() != ShapeMode.Sides)
        .build()
    );

    // --- 末地折跃门设置 (新功能) ---
    private final Setting<Boolean> gatewayEnabled = sgGateway.add(new BoolSetting.Builder()
        .name("启用末地折跃门")
        .description("显示末地折跃门方块(End Gateway)的透视框。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> gatewayShapeMode = sgGateway.add(new EnumSetting.Builder<ShapeMode>()
        .name("渲染模式")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> gatewaySideColor = sgGateway.add(new ColorSetting.Builder()
        .name("填充颜色")
        .defaultValue(new SettingColor(255, 255, 0, 50)) // 默认黄色以区分
        .visible(() -> gatewayShapeMode.get() != ShapeMode.Lines)
        .build()
    );

    private final Setting<SettingColor> gatewayLineColor = sgGateway.add(new ColorSetting.Builder()
        .name("边框颜色")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .visible(() -> gatewayShapeMode.get() != ShapeMode.Sides)
        .build()
    );

    // --- 渲染距离优化 ---
    private final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
        .name("渲染距离")
        .description("只渲染此距离内的传送门方块。")
        .defaultValue(128)
        .min(32)
        .max(512)
        .build()
    );

    // 缓存集合
    private final Set<BlockPos> netherPortals = Collections.synchronizedSet(new HashSet<>());
    private final Set<BlockPos> endPortals = Collections.synchronizedSet(new HashSet<>());
    private final Set<BlockPos> gateways = Collections.synchronizedSet(new HashSet<>());

    public PortalESP() {
        super(AddonTemplate.CATEGORY, "PortalESP", "给下界、末地及折跃门方块画框。");
    }

    @Override
    public void onActivate() {
        clearPortals();
        reloadChunks();
    }

    @Override
    public void onDeactivate() {
        clearPortals();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearPortals();
    }

    // --- 扫描逻辑 ---

    private void reloadChunks() {
        if (mc.world == null || mc.player == null) return;
        
        int renderDist = mc.options.getClampedViewDistance();
        ChunkPos playerPos = mc.player.getChunkPos();

        for (int x = -renderDist; x <= renderDist; x++) {
            for (int z = -renderDist; z <= renderDist; z++) {
                WorldChunk chunk = mc.world.getChunk(playerPos.x + x, playerPos.z + z);
                if (chunk != null && !chunk.isEmpty()) {
                    scanChunk(chunk);
                }
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world != null) {
            WorldChunk chunk = event.chunk();
            if (chunk != null) {
                scanChunk(chunk);
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;
        BlockState newState = event.newState;
        BlockState oldState = event.oldState;

        // 1. 下界传送门
        if (oldState.getBlock() == Blocks.NETHER_PORTAL && newState.getBlock() != Blocks.NETHER_PORTAL) {
            netherPortals.remove(pos);
        } else if (newState.getBlock() == Blocks.NETHER_PORTAL) {
            netherPortals.add(pos.toImmutable());
        }

        // 2. 末地传送门
        if (oldState.getBlock() == Blocks.END_PORTAL && newState.getBlock() != Blocks.END_PORTAL) {
            endPortals.remove(pos);
        } else if (newState.getBlock() == Blocks.END_PORTAL) {
            endPortals.add(pos.toImmutable());
        }

        // 3. 末地折跃门 (新增)
        if (oldState.getBlock() == Blocks.END_GATEWAY && newState.getBlock() != Blocks.END_GATEWAY) {
            gateways.remove(pos);
        } else if (newState.getBlock() == Blocks.END_GATEWAY) {
            gateways.add(pos.toImmutable());
        }
    }

    private void scanChunk(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section.isEmpty()) continue;

            // 高度修正
            int yOffset = chunk.sectionIndexToCoord(i) << 4;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block block = state.getBlock();

                        if (block == Blocks.NETHER_PORTAL || block == Blocks.END_PORTAL || block == Blocks.END_GATEWAY) {
                            BlockPos pos = new BlockPos(
                                chunkX * 16 + x,
                                yOffset + y,
                                chunkZ * 16 + z
                            );

                            if (block == Blocks.NETHER_PORTAL) {
                                netherPortals.add(pos);
                            } else if (block == Blocks.END_PORTAL) {
                                endPortals.add(pos);
                            } else if (block == Blocks.END_GATEWAY) {
                                gateways.add(pos); // 新增折跃门扫描
                            }
                        }
                    }
                }
            }
        }
    }

    private void clearPortals() {
        netherPortals.clear();
        endPortals.clear();
        gateways.clear();
    }

    // --- 渲染逻辑 ---

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;
        
        if (netherEnabled.get()) {
            synchronized (netherPortals) {
                renderSet(event, netherPortals, netherSideColor.get(), netherLineColor.get(), netherShapeMode.get());
            }
        }

        if (endEnabled.get()) {
            synchronized (endPortals) {
                renderSet(event, endPortals, endSideColor.get(), endLineColor.get(), endShapeMode.get());
            }
        }

        if (gatewayEnabled.get()) {
            synchronized (gateways) {
                renderSet(event, gateways, gatewaySideColor.get(), gatewayLineColor.get(), gatewayShapeMode.get());
            }
        }
    }

    private void renderSet(Render3DEvent event, Set<BlockPos> positions, SettingColor sideColor, SettingColor lineColor, ShapeMode shapeMode) {
        int rangeSq = renderDistance.get() * renderDistance.get();
        Vec3d cameraPos = mc.player.getEyePos();
double cameraX = cameraPos.x;
double cameraY = cameraPos.y;
double cameraZ = cameraPos.z;

        for (BlockPos pos : positions) {
            double distSq = pos.getSquaredDistance(cameraX, cameraY, cameraZ);
            if (distSq > rangeSq) continue;

            event.renderer.box(
                pos,
                sideColor,
                lineColor,
                shapeMode,
                0
            );
        }
    }
}