package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import org.jetbrains.annotations.Nullable;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.BreezeWindChargeEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.LlamaSpitEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.SpectralArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.entity.projectile.thrown.LingeringPotionEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.entity.projectile.thrown.SplashPotionEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class Trajectories extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // -- 手中物品预测 (按物品类型独立开关) --
    private final Setting<Boolean> handBow = sgGeneral.add(new BoolSetting.Builder()
        .name("HandBow").description("手持弓时显示抛物线预测").defaultValue(true).build());
    private final Setting<SettingColor> handBowColor = sgGeneral.add(new ColorSetting.Builder()
        .name("HandBowColor").description("弓的抛物线颜色").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(handBow::get).build());

    private final Setting<Boolean> handCrossbow = sgGeneral.add(new BoolSetting.Builder()
        .name("HandCrossbow").description("手持弩时显示抛物线预测").defaultValue(true).build());
    private final Setting<SettingColor> handCrossbowColor = sgGeneral.add(new ColorSetting.Builder()
        .name("HandCrossbowColor").description("弩的抛物线颜色").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(handCrossbow::get).build());

    private final Setting<Boolean> handPearl = sgGeneral.add(new BoolSetting.Builder()
        .name("HandPearl").description("手持末影珍珠时显示抛物线预测").defaultValue(true).build());
    private final Setting<SettingColor> handPearlColor = sgGeneral.add(new ColorSetting.Builder()
        .name("HandPearlColor").description("末影珍珠抛物线颜色").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(handPearl::get).build());

    private final Setting<Boolean> handTrident = sgGeneral.add(new BoolSetting.Builder()
        .name("HandTrident").description("手持三叉戟时显示抛物线预测").defaultValue(true).build());
    private final Setting<SettingColor> handTridentColor = sgGeneral.add(new ColorSetting.Builder()
        .name("HandTridentColor").description("三叉戟抛物线颜色").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(handTrident::get).build());

    private final Setting<Boolean> handThrowable = sgGeneral.add(new BoolSetting.Builder()
        .name("HandThrowable").description("手持雪球/鸡蛋/药水/经验瓶时显示抛物线预测").defaultValue(true).build());
    private final Setting<SettingColor> handThrowableColor = sgGeneral.add(new ColorSetting.Builder()
        .name("HandThrowableColor").description("投掷物抛物线颜色").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(handThrowable::get).build());

    // -- 飞行中抛射物 --
    private final Setting<Boolean> pearlEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("Pearl").description("显示末影珍珠的抛物线轨迹").defaultValue(true).build());
    private final Setting<SettingColor> pearlColor = sgGeneral.add(new ColorSetting.Builder()
        .name("PearlColor").description("末影珍珠轨迹颜色").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(pearlEnabled::get).build());

    private final Setting<Boolean> arrowEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("Arrow").description("显示箭的抛物线轨迹").defaultValue(true).build());
    private final Setting<SettingColor> arrowColor = sgGeneral.add(new ColorSetting.Builder()
        .name("ArrowColor").description("箭轨迹颜色").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(arrowEnabled::get).build());

    private final Setting<Boolean> xpEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("XP").description("显示经验瓶的抛物线轨迹").defaultValue(true).build());
    private final Setting<SettingColor> xpColor = sgGeneral.add(new ColorSetting.Builder()
        .name("XPColor").description("经验瓶轨迹颜色").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(xpEnabled::get).build());

    private final Setting<Boolean> windChargeEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("WindCharge").description("显示风弹的抛物线轨迹").defaultValue(true).build());
    private final Setting<SettingColor> windChargeColor = sgGeneral.add(new ColorSetting.Builder()
        .name("WindChargeColor").description("风弹轨迹颜色").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(windChargeEnabled::get).build());

    private final Setting<Boolean> throwableEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("Throwable").description("显示雪球/鸡蛋/药水的抛物线轨迹").defaultValue(true).build());
    private final Setting<SettingColor> throwableColor = sgGeneral.add(new ColorSetting.Builder()
        .name("ThrowableColor").description("雪球/鸡蛋/药水轨迹颜色").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(throwableEnabled::get).build());

    private final Setting<Boolean> tridentEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("Trident").description("显示三叉戟的抛物线轨迹").defaultValue(true).build());
    private final Setting<SettingColor> tridentColor = sgGeneral.add(new ColorSetting.Builder()
        .name("TridentColor").description("三叉戟轨迹颜色").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(tridentEnabled::get).build());

    private final Setting<Boolean> otherEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("Other").description("显示其他抛射物的轨迹(光灵箭/烟花/浮漂/火球/凋灵头/龙息弹等)").defaultValue(true).build());
    private final Setting<SettingColor> otherColor = sgGeneral.add(new ColorSetting.Builder()
        .name("OtherColor").description("其他抛射物轨迹颜色").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(otherEnabled::get).build());

    public Trajectories() {
        super(AddonTemplate.CATEGORY, "投射物轨迹显示", "抛物线预测 - 显示抛射物和投掷物的飞行轨迹来自AlienV4的Trajectories模块。");
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        // 渲染飞行中的抛射物轨迹
        renderFlyingProjectiles(event);

        // 渲染手中物品的预测抛物线
        renderHandTrajectory(event);
    }

    private void renderFlyingProjectiles(Render3DEvent event) {
        if (!pearlEnabled.get() && !arrowEnabled.get() && !xpEnabled.get()
            && !windChargeEnabled.get() && !throwableEnabled.get() && !tridentEnabled.get() && !otherEnabled.get()) return;

        for (Entity en : mc.world.getEntities()) {
            if (en instanceof EnderPearlEntity && pearlEnabled.get()) {
                calcTrajectory(en, pearlColor.get(), event, true);
            } else if (en instanceof ExperienceBottleEntity && xpEnabled.get()) {
                calcTrajectory(en, xpColor.get(), event, false);
            } else if (en instanceof ArrowEntity && arrowEnabled.get()) {
                calcTrajectory(en, arrowColor.get(), event, true);
            } else if ((en instanceof WindChargeEntity || en instanceof BreezeWindChargeEntity) && windChargeEnabled.get()) {
                calcTrajectory(en, windChargeColor.get(), event, false);
            } else if ((en instanceof SnowballEntity || en instanceof EggEntity
                || en instanceof SplashPotionEntity || en instanceof LingeringPotionEntity) && throwableEnabled.get()) {
                calcTrajectory(en, throwableColor.get(), event, false);
            } else if (en instanceof TridentEntity && tridentEnabled.get()) {
                calcTrajectory(en, tridentColor.get(), event, true);
            } else if (otherEnabled.get() && isOtherProjectile(en)) {
                calcTrajectory(en, otherColor.get(), event, false);
            }
        }
    }

    private boolean isOtherProjectile(Entity en) {
        return en instanceof SpectralArrowEntity
            || en instanceof FireworkRocketEntity
            || en instanceof FishingBobberEntity
            || en instanceof LlamaSpitEntity
            || en instanceof ShulkerBulletEntity
            || en instanceof FireballEntity
            || en instanceof SmallFireballEntity
            || en instanceof WitherSkullEntity
            || en instanceof DragonFireballEntity;
    }

    private void renderHandTrajectory(Render3DEvent event) {
        if (!mc.options.getPerspective().isFirstPerson()) return;

        // 依次检查主手和副手，找到第一个启用的物品类型
        for (Hand checkHand : new Hand[]{Hand.MAIN_HAND, Hand.OFF_HAND}) {
            ItemStack stack = checkHand == Hand.MAIN_HAND ? mc.player.getMainHandStack() : mc.player.getOffHandStack();
            Item item = stack.getItem();

            SettingColor color = getHandColorForItem(item);
            if (color == null) continue;

            float tickDelta = event.tickDelta;
            double x = MathHelper.lerp(tickDelta, mc.player.lastRenderX, mc.player.getX());
            double y = MathHelper.lerp(tickDelta, mc.player.lastRenderY, mc.player.getY());
            double z = MathHelper.lerp(tickDelta, mc.player.lastRenderZ, mc.player.getZ());

            if (item instanceof CrossbowItem) {
                var registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                boolean multishot = EnchantmentHelper.getLevel(registry.getOrThrow(Enchantments.MULTISHOT), stack) != 0;
                if (multishot) {
                    calcTrajectory(item, mc.player.getYaw() - 10.0F, x, y, z, color, event);
                    calcTrajectory(item, mc.player.getYaw(), x, y, z, color, event);
                    calcTrajectory(item, mc.player.getYaw() + 10.0F, x, y, z, color, event);
                } else {
                    calcTrajectory(item, mc.player.getYaw(), x, y, z, color, event);
                }
            } else {
                calcTrajectory(item, mc.player.getYaw(), x, y, z, color, event);
            }
            return; // 只渲染第一个启用的手中物品轨迹
        }
    }

    private @Nullable SettingColor getHandColorForItem(Item item) {
        if (item instanceof BowItem && handBow.get()) return handBowColor.get();
        if (item instanceof CrossbowItem && handCrossbow.get()) return handCrossbowColor.get();
        if (item instanceof EnderPearlItem && handPearl.get()) return handPearlColor.get();
        if (item instanceof TridentItem && handTrident.get()) return handTridentColor.get();
        if (item instanceof ExperienceBottleItem || item instanceof SnowballItem
            || item instanceof EggItem || item instanceof SplashPotionItem
            || item instanceof LingeringPotionItem) {
            if (handThrowable.get()) return handThrowableColor.get();
        }
        return null;
    }

    private void calcTrajectory(Entity e, SettingColor color, Render3DEvent event, boolean arrowPhysics) {
        double motionX = e.getVelocity().x;
        double motionY = e.getVelocity().y;
        double motionZ = e.getVelocity().z;
        if (motionX == 0.0 && motionY == 0.0 && motionZ == 0.0) return;

        // 凋灵之首：无阻力无重力，直线飞行
        boolean noDragNoGravity = e instanceof WitherSkullEntity;
        // 风弹：无重力，几乎直线
        boolean noGravity = e instanceof WindChargeEntity || e instanceof BreezeWindChargeEntity;

        double x = e.getX();
        double y = e.getY();
        double z = e.getZ();

        for (int i = 0; i < 300; i++) {
            Vec3d lastPos = new Vec3d(x, y, z);
            x += motionX;
            y += motionY;
            z += motionZ;

            if (!noDragNoGravity) {
                if (mc.world.getBlockState(BlockPos.ofFloored(x, y, z)).getBlock() == Blocks.WATER) {
                    motionX *= 0.8;
                    motionY *= 0.8;
                    motionZ *= 0.8;
                } else {
                    motionX *= 0.99;
                    motionY *= 0.99;
                    motionZ *= 0.99;
                }
            }

            if (!noDragNoGravity && !noGravity) {
                motionY -= arrowPhysics ? 0.05F : 0.03F;
            }

            Vec3d pos = new Vec3d(x, y, z);

            if (y <= -65.0) break;

            BlockHitResult bhr = mc.world.raycast(new RaycastContext(lastPos, pos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (bhr != null && (bhr.getType() == HitResult.Type.BLOCK || bhr.getType() == HitResult.Type.ENTITY)) {
                break;
            }

            int alpha = MathHelper.clamp((int) (255.0F * ((i + 1) / 10.0F)), 0, 255);
            event.renderer.line(lastPos.x, lastPos.y, lastPos.z, pos.x, pos.y, pos.z, new SettingColor(color.r, color.g, color.b, alpha));
        }
    }

    private void calcTrajectory(Item item, float yaw, double x, double y, double z, SettingColor color, Render3DEvent event) {
        y = y + mc.player.getEyeHeight(mc.player.getPose()) - 0.1000000014901161;
        if (item == mc.player.getMainHandStack().getItem()) {
            x -= MathHelper.cos(yaw / 180.0F * (float) Math.PI) * 0.16F;
            z -= MathHelper.sin(yaw / 180.0F * (float) Math.PI) * 0.16F;
        } else {
            x += MathHelper.cos(yaw / 180.0F * (float) Math.PI) * 0.16F;
            z += MathHelper.sin(yaw / 180.0F * (float) Math.PI) * 0.16F;
        }

        float maxDist = getDistance(item);
        double motionX = -MathHelper.sin(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(mc.player.getPitch() / 180.0F * (float) Math.PI) * maxDist;
        double motionY = -MathHelper.sin((mc.player.getPitch() - getThrowPitch(item)) / 180.0F * 3.141593F) * maxDist;
        double motionZ = MathHelper.cos(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(mc.player.getPitch() / 180.0F * (float) Math.PI) * maxDist;

        float power = mc.player.getItemUseTime() / 20.0F;
        power = (power * power + power * 2.0F) / 3.0F;
        if (power > 1.0F) power = 1.0F;

        float distance = MathHelper.sqrt((float) (motionX * motionX + motionY * motionY + motionZ * motionZ));
        motionX /= distance;
        motionY /= distance;
        motionZ /= distance;
        float pow = (item instanceof BowItem ? power * 2.0F : (item instanceof CrossbowItem ? 2.2F : 1.0F)) * getThrowVelocity(item);
        motionX *= pow;
        motionY *= pow;
        motionZ *= pow;
        motionX += mc.player.getVelocity().getX();
        motionY += mc.player.getVelocity().getY();
        motionZ += mc.player.getVelocity().getZ();

        boolean arrowPhysics = item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem;

        for (int i = 0; i < 300; i++) {
            Vec3d lastPos = new Vec3d(x, y, z);
            x += motionX;
            y += motionY;
            z += motionZ;

            if (mc.world.getBlockState(BlockPos.ofFloored(x, y, z)).getBlock() == Blocks.WATER) {
                motionX *= 0.8;
                motionY *= 0.8;
                motionZ *= 0.8;
            } else {
                motionX *= 0.99;
                motionY *= 0.99;
                motionZ *= 0.99;
            }

            motionY -= arrowPhysics ? 0.05F : 0.03F;

            Vec3d pos = new Vec3d(x, y, z);

            for (Entity ent : mc.world.getEntities()) {
                if (!(ent instanceof ArrowEntity)
                    && !ent.equals(mc.player)
                    && ent.getBoundingBox().intersects(new Box(x - 0.3, y - 0.3, z - 0.3, x + 0.3, y + 0.3, z + 0.3))) {
                    Box bb = ent.getBoundingBox();
                    event.renderer.box(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, color, color, ShapeMode.Lines, 0);
                    break;
                }
            }

            BlockHitResult bhr = mc.world.raycast(new RaycastContext(lastPos, pos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                Box bb = new Box(bhr.getBlockPos());
                event.renderer.box(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, color, color, ShapeMode.Lines, 0);
                break;
            }

            if (y <= -65.0) break;

            if (motionX != 0.0 || motionY != 0.0 || motionZ != 0.0) {
                event.renderer.line(lastPos.x, lastPos.y, lastPos.z, pos.x, pos.y, pos.z, color);
            }
        }
    }

    private float getDistance(Item item) {
        return item instanceof BowItem ? 1.0F : 0.4F;
    }

    private float getThrowVelocity(Item item) {
        if (item instanceof SplashPotionItem || item instanceof LingeringPotionItem) {
            return 0.5F;
        } else if (item instanceof ExperienceBottleItem) {
            return 0.59F;
        } else {
            return item instanceof TridentItem ? 2.0F : 1.5F;
        }
    }

    private int getThrowPitch(Item item) {
        return !(item instanceof SplashPotionItem) && !(item instanceof LingeringPotionItem) && !(item instanceof ExperienceBottleItem) ? 0 : 20;
    }
}
