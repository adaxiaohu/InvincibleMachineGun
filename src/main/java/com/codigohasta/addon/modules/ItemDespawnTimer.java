package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ItemDespawnTimer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染设置");
    private final SettingGroup sgColor = settings.createGroup("颜色设置");
    private final SettingGroup sgSound = settings.createGroup("音效设置");

    // --- 通用设置 ---
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("缩放大小")
        .description("文字的显示大小。")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Double> heightOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("高度偏移")
        .description("文字显示在物品上方的距离。")
        .defaultValue(0.75)
        .min(0.0)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
        .name("自定义前缀")
        .description("显示在倒计时前面的文字。")
        .defaultValue("") 
        .build()
    );

    private final Setting<Boolean> blink = sgGeneral.add(new BoolSetting.Builder()
        .name("最后10秒闪烁")
        .description("当剩余时间少于10秒时，文字变红闪烁。")
        .defaultValue(true)
        .build()
    );

    // --- 音效设置 ---
    private final Setting<Boolean> playSound = sgSound.add(new BoolSetting.Builder()
        .name("播放提示音")
        .description("当物品即将消失时播放音效。")
        .defaultValue(true)
        .build()
    );

    private final Setting<SoundType> soundType = sgSound.add(new EnumSetting.Builder<SoundType>()
        .name("音效类型")
        .description("选择播放的提示音效。")
        .defaultValue(SoundType.PLING)
        .visible(playSound::get)
        .build()
    );

    private final Setting<Double> soundVolume = sgSound.add(new DoubleSetting.Builder()
        .name("音量")
        .description("提示音的音量大小。")
        .defaultValue(1.0)
        .min(0.1)
        .max(2.0)
        .visible(playSound::get)
        .build()
    );

    // --- 渲染设置 ---
    private final Setting<SettingColor> backgroundColor = sgRender.add(new ColorSetting.Builder()
        .name("背景颜色")
        .description("文字背景板的颜色。")
        .defaultValue(new SettingColor(0, 0, 0, 75))
        .build()
    );

    // --- 颜色设置 ---
    private final Setting<ColorMode> colorMode = sgColor.add(new EnumSetting.Builder<ColorMode>()
        .name("颜色模式")
        .description("文字颜色的显示方式。")
        .defaultValue(ColorMode.Gradient)
        .build()
    );

    private final Setting<SettingColor> staticColor = sgColor.add(new ColorSetting.Builder()
        .name("静态颜色")
        .description("当模式为静态时的颜色。")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> colorMode.get() == ColorMode.Static)
        .build()
    );

    private final Setting<SettingColor> startColor = sgColor.add(new ColorSetting.Builder()
        .name("开始颜色")
        .description("时间充足时的颜色（例如：绿色）。")
        .defaultValue(new SettingColor(25, 252, 25)) 
        .visible(() -> colorMode.get() == ColorMode.Gradient)
        .build()
    );

    private final Setting<SettingColor> middleColor = sgColor.add(new ColorSetting.Builder()
        .name("中间颜色")
        .description("剩余约1分钟时的颜色（例如：黄色）。")
        .defaultValue(new SettingColor(255, 255, 25)) 
        .visible(() -> colorMode.get() == ColorMode.Gradient)
        .build()
    );

    private final Setting<SettingColor> endColor = sgColor.add(new ColorSetting.Builder()
        .name("结束颜色")
        .description("即将消失时的颜色（例如：红色）。")
        .defaultValue(new SettingColor(255, 25, 25)) 
        .visible(() -> colorMode.get() == ColorMode.Gradient)
        .build()
    );

    public ItemDespawnTimer() {
        super(AddonTemplate.CATEGORY, "物品消失时间显示", "显示掉落物消失前的剩余倒计时。");
    }

    private final Vector3d pos = new Vector3d();
    private final Color BLINK_COLOR = new Color(255, 0, 0);

    // 缓存系统：UUID -> 该物品预计消失的系统时间(毫秒)
    private final Map<UUID, Long> despawnCache = new ConcurrentHashMap<>();

    // 当玩家加入新游戏/服务器时，清空缓存，防止UUID冲突
    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        despawnCache.clear();
    }

    // 音效与缓存更新逻辑
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        boolean shouldPlay = false;
        boolean fastPlay = false;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                // 计算真实剩余 tick
                int ticksLeft = getRealTicksLeft(itemEntity);

                // 如果已经消失或无限寿命，跳过
                if (ticksLeft <= 0 || ticksLeft > 6000) continue;

                // 音效逻辑
                if (playSound.get()) {
                    if (ticksLeft > 0 && ticksLeft <= 200) {
                        if (ticksLeft <= 60) {
                            if (ticksLeft % 5 == 0) fastPlay = true;
                        } else {
                            if (ticksLeft % 20 == 0) shouldPlay = true;
                        }
                    }
                }
            }
        }

        if (fastPlay || shouldPlay) {
            float pitch = fastPlay ? 2.0f : 1.0f;
            mc.world.playSound(
                mc.player,
                mc.player.getBlockPos(),
                soundType.get().getSound(),
                SoundCategory.PLAYERS,
                soundVolume.get().floatValue(),
                pitch
            );
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                renderTimer(itemEntity, event);
            }
        }
    }

    // --- 核心逻辑：获取真实剩余时间 ---
    private int getRealTicksLeft(ItemEntity item) {
        int itemAge = item.getItemAge();
        if (itemAge == -32768) return 999999; // 无限寿命

        UUID uuid = item.getUuid();
        long now = System.currentTimeMillis();
        int maxAge = 6000;
        
        // 基于当前物品 Age 计算的预计消失时间
        long estimatedDespawnTime = now + (long)((maxAge - itemAge) * 50L); // 1 tick = 50ms

        if (!despawnCache.containsKey(uuid)) {
            // 如果缓存里没有，说明是新发现的物品，记录它
            despawnCache.put(uuid, estimatedDespawnTime);
            return maxAge - itemAge;
        } else {
            long cachedDespawnTime = despawnCache.get(uuid);
            
            // 核心修复逻辑：
            // 如果服务器发来的 itemAge 很小（例如0，说明重置了），但缓存里记录的时间比这早，
            // 说明我们之前见过这个物品，应该信任缓存！
            
            // 计算基于缓存的剩余 tick
            long millisLeft = cachedDespawnTime - now;
            int cachedTicksLeft = (int)(millisLeft / 50L);

            // 如果物品当前的 age 显示它还能活很久（比如 5分钟），但缓存显示它其实只剩 1分钟了
            // 那么说明是 重连/区块加载 导致的重置，我们使用缓存值。
            if ((maxAge - itemAge) > cachedTicksLeft + 20) { // +20 tick 容错
                return cachedTicksLeft;
            } else {
                // 如果物品当前的 age 比缓存的更老（说明服务器同步了正确的数据），更新缓存
                if (estimatedDespawnTime < cachedDespawnTime - 1000) { // 偏差大于1秒
                    despawnCache.put(uuid, estimatedDespawnTime);
                }
                return maxAge - itemAge;
            }
        }
    }

    private void renderTimer(ItemEntity item, Render2DEvent event) {
        // 使用智能算法获取剩余时间
        int ticksLeft = getRealTicksLeft(item);
        
        if (ticksLeft > 6000) return; // 无限寿命不显示
        if (ticksLeft <= 0) return;

        double secondsLeft = ticksLeft / 20.0;
        String timeStr = String.format("%.1fs", secondsLeft);
        if (secondsLeft > 60) {
            int mins = (int) secondsLeft / 60;
            double secs = secondsLeft % 60;
            timeStr = String.format("%d:%04.1f", mins, secs);
        }
        
        String finalContent = prefix.get() + timeStr;

        Color finalColor;

        if (blink.get() && ticksLeft < 200 && (ticksLeft % 10 < 5)) {
            finalColor = BLINK_COLOR; 
        } else {
            if (colorMode.get() == ColorMode.Static) {
                finalColor = staticColor.get();
            } else {
                int midPoint = 1200;
                int maxAge = 6000;
                if (ticksLeft >= midPoint) {
                    double progress = (double) (ticksLeft - midPoint) / (maxAge - midPoint);
                    progress = Math.min(1.0, Math.max(0.0, progress));
                    finalColor = interpolate(middleColor.get(), startColor.get(), progress);
                } else {
                    double progress = (double) ticksLeft / midPoint;
                    progress = Math.min(1.0, Math.max(0.0, progress));
                    finalColor = interpolate(endColor.get(), middleColor.get(), progress);
                }
            }
        }

        Utils.set(pos, item, event.tickDelta);
        pos.add(0, item.getHeight() + heightOffset.get(), 0);

        if (NametagUtils.to2D(pos, scale.get())) {
            renderNametag(finalContent, finalColor, event);
        }
    }

    private Color interpolate(Color start, Color end, double progress) {
        int r = (int) (start.r + (end.r - start.r) * progress);
        int g = (int) (start.g + (end.g - start.g) * progress);
        int b = (int) (start.b + (end.b - start.b) * progress);
        int a = (int) (start.a + (end.a - start.a) * progress);
        return new Color(r, g, b, a);
    }

    private void renderNametag(String textStr, Color color, Render2DEvent event) {
        TextRenderer text = TextRenderer.get();
        NametagUtils.begin(pos, event.drawContext);

        double width = text.getWidth(textStr, true);
        double height = text.getHeight(true);
        double widthHalf = width / 2;

        drawBg(-widthHalf, -height, width, height, event.drawContext.getMatrices());

        text.beginBig();
        text.render(textStr, -widthHalf, -height, color, true);
        text.end();

        NametagUtils.end(event.drawContext);
    }

    private void drawBg(double x, double y, double width, double height, MatrixStack matrices) {
        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(x - 1, y - 1, width + 2, height + 2, backgroundColor.get());
        Renderer2D.COLOR.render(matrices);
    }

    public enum ColorMode {
        Static,
        Gradient
    }

    public enum SoundType {
        PLING(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value()),
        ORB(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP),
        ANVIL(SoundEvents.BLOCK_ANVIL_LAND),
        CLICK(SoundEvents.UI_BUTTON_CLICK.value()),
        POP(SoundEvents.ENTITY_ITEM_PICKUP);

        private final SoundEvent sound;

        SoundType(SoundEvent sound) {
            this.sound = sound;
        }

        public SoundEvent getSound() {
            return sound;
        }
    }
}