package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author OLEPOSSU
 * Modified to allow ghost rendering (fade out slowly)
 */

public class MineESP extends Module {
    public MineESP() {
        super(AddonTemplate.CATEGORY, "挖掘提示", "高亮显示其他玩家正在挖掘的方块。不好用");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("范围")
        .description("只渲染此范围内的挖掘活动。")
        .defaultValue(15)
        .min(0)
        .sliderRange(0, 50)
        .build()
    );
    private final Setting<Double> maxTime = sgGeneral.add(new DoubleSetting.Builder()
        .name("保留时间")
        .description("停止挖掘后，渲染框保留并淡出的时间（秒）。")
        .defaultValue(3) // 默认改小一点，因为现在会留存了
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    //--------------------渲染--------------------//
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("渲染方框的哪个部分。")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    public final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色")
        .description("方框线条的颜色。")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );
    public final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("侧面颜色")
        .description("方框侧面的颜色。")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );

    //--------------------文本显示设置--------------------//
    private final Setting<Boolean> renderName = sgRender.add(new BoolSetting.Builder()
        .name("显示名字")
        .description("在方框内显示挖掘者的名字。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderProgress = sgRender.add(new BoolSetting.Builder()
        .name("显示进度")
        .description("显示挖掘进度的百分比。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smoothProgress = sgRender.add(new BoolSetting.Builder()
        .name("平滑数值")
        .description("模拟 1-100 的平滑进度数值（视觉效果）。")
        .defaultValue(true)
        .visible(renderProgress::get)
        .build()
    );

    private final Setting<Boolean> renderBlock = sgRender.add(new BoolSetting.Builder()
        .name("显示方块名")
        .description("显示正在被挖掘的方块名称。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> textScale = sgRender.add(new DoubleSetting.Builder()
        .name("文本大小")
        .description("名字和进度的缩放比例。")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(3.0)
        .visible(() -> renderName.get() || renderProgress.get() || renderBlock.get())
        .build()
    );

    private final Setting<SettingColor> textColor = sgRender.add(new ColorSetting.Builder()
        .name("文本颜色")
        .description("文本显示的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> renderName.get() || renderProgress.get() || renderBlock.get())
        .build()
    );

    private static class RenderInfo {
        final BlockPos pos;
        final int id;
        long time; // 最后一次更新时间
        int serverStage;
        float animatedProgress;
        String cachedBlockName; // 缓存方块名，防止变成空气后获取不到名字

        public RenderInfo(BlockPos pos, int id, long time, int serverStage, String blockName) {
            this.pos = pos;
            this.id = id;
            this.time = time;
            this.serverStage = serverStage;
            this.animatedProgress = (serverStage + 1) * 10;
            this.cachedBlockName = blockName;
        }
    }

    private final List<RenderInfo> renders = new ArrayList<>();
    private final ConcurrentLinkedQueue<RenderInfo> pendingUpdates = new ConcurrentLinkedQueue<>();

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        // 1. 处理队列中的更新
        while (!pendingUpdates.isEmpty()) {
            RenderInfo update = pendingUpdates.poll();
            
            RenderInfo existing = null;
            for (RenderInfo r : renders) {
                if (r.id == update.id && r.pos.equals(update.pos)) {
                    existing = r;
                    break;
                }
            }

            // 如果收到停止挖掘的包 (stage > 9)，我们不做任何处理
            // 这样 existing 对象就会保留在列表中，依靠 time 自然超时
            // 如果收到的是挖掘包 (0-9)，则更新时间和进度
            if (update.serverStage >= 0 && update.serverStage <= 9) {
                if (existing != null) {
                    existing.time = System.currentTimeMillis(); // 刷新时间，重置淡出计时
                    existing.serverStage = update.serverStage;
                    // 如果方块还没变成空气，更新一下缓存名字
                    if (!update.cachedBlockName.equals("Air")) {
                         existing.cachedBlockName = update.cachedBlockName;
                    }
                } else {
                    renders.add(update);
                }
            }
        }

        // 2. 清理超时 (只根据时间清理，不再根据是否是空气清理)
        renders.removeIf(r -> System.currentTimeMillis() > r.time + Math.round(maxTime.get() * 1000));

        // 3. 渲染
        renders.forEach(r -> {
            // 计算生命周期进度 (0.0 -> 1.0)
            double lifeDelta = Math.min((System.currentTimeMillis() - r.time) / (maxTime.get() * 1000d), 1);
            
            // 进度条平滑动画
            float targetProgress = (r.serverStage + 1) * 10f;
            if (smoothProgress.get()) {
                r.animatedProgress = MathHelper.lerp(0.2f, r.animatedProgress, targetProgress);
            } else {
                r.animatedProgress = targetProgress;
            }

            // 渲染方框，颜色随 lifeDelta 变淡
            event.renderer.box(getBox(r.pos, getBoxProgress(Math.min(lifeDelta * 4, 1))), 
                getColor(sideColor.get(), 1 - lifeDelta), 
                getColor(lineColor.get(), 1 - lifeDelta), 
                shapeMode.get(), 0);
        });
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!renderName.get() && !renderProgress.get() && !renderBlock.get()) return;

        renders.forEach(r -> {
            StringBuilder textToRender = new StringBuilder();

            if (renderName.get()) {
                Entity entity = mc.world.getEntityById(r.id);
                if (entity != null) {
                    textToRender.append(entity.getName().getString());
                } else {
                    textToRender.append("Unknown");
                }
            }

            if (renderProgress.get()) {
                if (!textToRender.isEmpty()) textToRender.append(" ");
                int displayVal = (int) Math.min(100, Math.ceil(r.animatedProgress));
                textToRender.append(displayVal).append("%");
            }

            if (renderBlock.get()) {
                if (!textToRender.isEmpty()) textToRender.append(" ");
                // 优先使用缓存的名字，因为方块可能已经被挖掉了(变成空气了)
                textToRender.append(r.cachedBlockName);
            }

            if (!textToRender.isEmpty()) {
                Vector3d pos = new Vector3d(r.pos.getX() + 0.5, r.pos.getY() + 0.5, r.pos.getZ() + 0.5);

                if (NametagUtils.to2D(pos, textScale.get())) {
                    NametagUtils.begin(pos);
                    String text = textToRender.toString();
                    
                    // 计算淡出
                    double delta = Math.min((System.currentTimeMillis() - r.time) / (maxTime.get() * 1000d), 1);
                    Color finalColor = getColor(textColor.get(), 1 - delta);
                    
                    TextRenderer.get().begin(1.0, false, true);
                    double w = TextRenderer.get().getWidth(text);
                    double h = TextRenderer.get().getHeight();
                    TextRenderer.get().render(text, -w / 2.0, -h / 2.0, finalColor, true);
                    TextRenderer.get().end();
                    NametagUtils.end();
                }
            }
        });
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (event.packet instanceof BlockBreakingProgressS2CPacket packet) {
            BlockPos pos = packet.getPos();
            String blockName = "Unknown";
            if (mc.world != null) {
                BlockState state = mc.world.getBlockState(pos);
                // 只有当方块不是空气时才获取名字，否则标记为 Air
                if (!state.isAir()) {
                    blockName = state.getBlock().getName().getString();
                } else {
                    blockName = "Air";
                }
            }

            pendingUpdates.add(new RenderInfo(pos, packet.getEntityId(), System.currentTimeMillis(), packet.getProgress(), blockName));
        }
    }

    private Color getColor(Color color, double delta) {
        return new Color(color.r, color.g, color.b, (int) Math.floor(color.a * delta));
    }

    private double getBoxProgress(double delta) {
        return 1 - Math.pow(1 - (delta), 5);
    }

    private Box getBox(BlockPos pos, double progress) {
        return new Box(
            pos.getX() + 0.5 - progress / 2, pos.getY() + 0.5 - progress / 2, pos.getZ() + 0.5 - progress / 2, 
            pos.getX() + 0.5 + progress / 2, pos.getY() + 0.5 + progress / 2, pos.getZ() + 0.5 + progress / 2
        );
    }
}