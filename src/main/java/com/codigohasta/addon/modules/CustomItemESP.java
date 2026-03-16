package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3d;

import java.util.List;

public class CustomItemESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBox = settings.createGroup("3D 渲染框");
    private final SettingGroup sgText = settings.createGroup("2D 名字标签");

    // --- 通用设置 ---
    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("物品列表")
        .description("选择需要高亮显示的特定掉落物。")
        .build()
    );

    // --- 3D 渲染框设置 ---
    private final Setting<Boolean> renderBox = sgBox.add(new BoolSetting.Builder()
        .name("启用渲染框")
        .description("是否在掉落物周围绘制方框。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgBox.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("方框的渲染样式。")
        .defaultValue(ShapeMode.Both)
        .visible(renderBox::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgBox.add(new ColorSetting.Builder()
        .name("填充颜色")
        .description("方框内部填充的颜色。")
        .defaultValue(new SettingColor(255, 215, 0, 40)) // 金色半透明
        .visible(renderBox::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgBox.add(new ColorSetting.Builder()
        .name("边框颜色")
        .description("方框线条的颜色。")
        .defaultValue(new SettingColor(255, 215, 0, 200)) // 金色不透明
        .visible(renderBox::get)
        .build()
    );

    // --- 2D 名字标签设置 ---
    private final Setting<Boolean> renderName = sgText.add(new BoolSetting.Builder()
        .name("启用名字标签")
        .description("是否在掉落物上方显示名字。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scale = sgText.add(new DoubleSetting.Builder()
        .name("标签缩放")
        .description("名字标签的大小缩放。")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(2.5)
        .visible(renderName::get)
        .build()
    );

    private final Setting<Boolean> showCount = sgText.add(new BoolSetting.Builder()
        .name("显示数量")
        .description("在名字旁边显示物品堆叠数量 (例如 x64)。")
        .defaultValue(true)
        .visible(renderName::get)
        .build()
    );

    private final Setting<SettingColor> nameColor = sgText.add(new ColorSetting.Builder()
        .name("文字颜色")
        .description("掉落物名字的颜色。")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(renderName::get)
        .build()
    );

    private final Setting<Boolean> renderBackground = sgText.add(new BoolSetting.Builder()
        .name("背景板")
        .description("是否渲染黑色半透明背景。")
        .defaultValue(true)
        .visible(renderName::get)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgText.add(new ColorSetting.Builder()
        .name("背景颜色")
        .description("文字背景板的颜色。")
        .defaultValue(new SettingColor(0, 0, 0, 75))
        .visible(() -> renderName.get() && renderBackground.get())
        .build()
    );

    // 内部变量用于渲染计算
    private final Vector3d pos = new Vector3d();

    public CustomItemESP() {
        super(AddonTemplate.CATEGORY, "custom-item-esp", "为特定的掉落物绘制高亮框和名字标签。");
    }

    // --- 3D 渲染 (方框) ---
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderBox.get()) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getStack();
                
                // 检查是否在白名单中
                if (items.get().contains(stack.getItem())) {
                    renderItemBox(event, itemEntity);
                }
            }
        }
    }

    private void renderItemBox(Render3DEvent event, ItemEntity entity) {
        // 掉落物的碰撞箱通常较小，我们可以手动指定一个好看的大小，或者直接用 entity.getBoundingBox()
        Box box = entity.getBoundingBox();
        event.renderer.box(box, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    // --- 2D 渲染 (名字标签) ---
    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!renderName.get()) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getStack();

                // 检查是否在白名单中
                if (items.get().contains(stack.getItem())) {
                    // 传入 drawContext 以便获取 Matrices
                    renderItemNametag(itemEntity, stack, event.tickDelta, event.drawContext);
                }
            }
        }
    }

    private void renderItemNametag(ItemEntity entity, ItemStack stack, double tickDelta, DrawContext drawContext) {
        // 设置位置向量 (直接调用内部的 set 方法，不再引用外部类)
        setVector(pos, entity, tickDelta);
        
        // 稍微向上偏移，避免挡住物品本身
        pos.add(0, 0.75, 0);

        // 如果在屏幕范围内，则开始渲染
        if (NametagUtils.to2D(pos, scale.get())) {
            NametagUtils.begin(pos);
            TextRenderer textRenderer = TextRenderer.get();

            // 构建显示文本
            String name = Names.get(stack);
            String displayString = name;
            if (showCount.get() && stack.getCount() > 1) {
                displayString += " x" + stack.getCount();
            }

            // 计算尺寸
            double width = textRenderer.getWidth(displayString);
            double height = textRenderer.getHeight();
            double widthHalf = width / 2;

            // 绘制背景 (修复: 传入 drawContext.getMatrices())
            if (renderBackground.get()) {
                Renderer2D.COLOR.begin();
                Renderer2D.COLOR.quad(-widthHalf - 2, -height / 2 - 2, width + 4, height + 4, backgroundColor.get());
                Renderer2D.COLOR.render(drawContext.getMatrices()); // 这里修复了第一个错误
            }

            // 绘制文字 (居中)
            textRenderer.beginBig();
            textRenderer.render(displayString, -widthHalf, -height / 2, nameColor.get(), true);
            textRenderer.end();

            NametagUtils.end();
        }
    }

    // 内部工具方法，替代 UtilsRef (修复了第二个错误)
    private void setVector(Vector3d pos, Entity entity, double tickDelta) {
        double x = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double y = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double z = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());
        pos.set(x, y, z);
    }
}