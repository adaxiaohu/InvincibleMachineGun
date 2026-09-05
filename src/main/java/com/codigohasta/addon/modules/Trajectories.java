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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

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
        if (mc.player == null || mc.level == null) return;

        // 渲染飞行中的抛射物轨迹
        renderFlyingProjectiles(event);

        // 渲染手中物品的预测抛物线
        renderHandTrajectory(event);
    }

    private void renderFlyingProjectiles(Render3DEvent event) {
        if (!pearlEnabled.get() && !arrowEnabled.get() && !xpEnabled.get()
            && !windChargeEnabled.get() && !throwableEnabled.get() && !tridentEnabled.get() && !otherEnabled.get()) return;

        for (Entity en : mc.level.entitiesForRendering()) {
            if (en instanceof ThrownEnderpearl && pearlEnabled.get()) {
                calcTrajectory(en, pearlColor.get(), event, true);
            } else if (en instanceof ThrownExperienceBottle && xpEnabled.get()) {
                calcTrajectory(en, xpColor.get(), event, false);
            } else if (en instanceof Arrow && arrowEnabled.get()) {
                calcTrajectory(en, arrowColor.get(), event, true);
            } else if ((en instanceof WindCharge || en instanceof BreezeWindCharge) && windChargeEnabled.get()) {
                calcTrajectory(en, windChargeColor.get(), event, false);
            } else if ((en instanceof Snowball || en instanceof ThrownEgg
                || en instanceof ThrownSplashPotion || en instanceof ThrownLingeringPotion) && throwableEnabled.get()) {
                calcTrajectory(en, throwableColor.get(), event, false);
            } else if (en instanceof ThrownTrident && tridentEnabled.get()) {
                calcTrajectory(en, tridentColor.get(), event, true);
            } else if (otherEnabled.get() && isOtherProjectile(en)) {
                calcTrajectory(en, otherColor.get(), event, false);
            }
        }
    }

    private boolean isOtherProjectile(Entity en) {
        return en instanceof SpectralArrow
            || en instanceof FireworkRocketEntity
            || en instanceof FishingHook
            || en instanceof LlamaSpit
            || en instanceof ShulkerBullet
            || en instanceof LargeFireball
            || en instanceof SmallFireball
            || en instanceof WitherSkull
            || en instanceof DragonFireball;
    }

    private void renderHandTrajectory(Render3DEvent event) {
        if (!mc.options.getCameraType().isFirstPerson()) return;

        // 依次检查主手和副手，找到第一个启用的物品类型
        for (InteractionHand checkHand : new InteractionHand[]{InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND}) {
            ItemStack stack = checkHand == InteractionHand.MAIN_HAND ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
            Item item = stack.getItem();

            SettingColor color = getHandColorForItem(item);
            if (color == null) continue;

            float tickDelta = event.tickDelta;
            double x = Mth.lerp(tickDelta, mc.player.xOld, mc.player.getX());
            double y = Mth.lerp(tickDelta, mc.player.yOld, mc.player.getY());
            double z = Mth.lerp(tickDelta, mc.player.zOld, mc.player.getZ());

            if (item instanceof CrossbowItem) {
                var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                boolean multishot = EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Enchantments.MULTISHOT), stack) != 0;
                if (multishot) {
                    calcTrajectory(item, mc.player.getYRot() - 10.0F, x, y, z, color, event);
                    calcTrajectory(item, mc.player.getYRot(), x, y, z, color, event);
                    calcTrajectory(item, mc.player.getYRot() + 10.0F, x, y, z, color, event);
                } else {
                    calcTrajectory(item, mc.player.getYRot(), x, y, z, color, event);
                }
            } else {
                calcTrajectory(item, mc.player.getYRot(), x, y, z, color, event);
            }
            return; // 只渲染第一个启用的手中物品轨迹
        }
    }

    private @Nullable SettingColor getHandColorForItem(Item item) {
        if (item instanceof BowItem && handBow.get()) return handBowColor.get();
        if (item instanceof CrossbowItem && handCrossbow.get()) return handCrossbowColor.get();
        if (item instanceof EnderpearlItem && handPearl.get()) return handPearlColor.get();
        if (item instanceof TridentItem && handTrident.get()) return handTridentColor.get();
        if (item instanceof ExperienceBottleItem || item instanceof SnowballItem
            || item instanceof EggItem || item instanceof SplashPotionItem
            || item instanceof LingeringPotionItem) {
            if (handThrowable.get()) return handThrowableColor.get();
        }
        return null;
    }

    private void calcTrajectory(Entity e, SettingColor color, Render3DEvent event, boolean arrowPhysics) {
        double motionX = e.getDeltaMovement().x;
        double motionY = e.getDeltaMovement().y;
        double motionZ = e.getDeltaMovement().z;
        if (motionX == 0.0 && motionY == 0.0 && motionZ == 0.0) return;

        // 凋灵之首：无阻力无重力，直线飞行
        boolean noDragNoGravity = e instanceof WitherSkull;
        // 风弹：无重力，几乎直线
        boolean noGravity = e instanceof WindCharge || e instanceof BreezeWindCharge;

        double x = e.getX();
        double y = e.getY();
        double z = e.getZ();

        for (int i = 0; i < 300; i++) {
            Vec3 lastPos = new Vec3(x, y, z);
            x += motionX;
            y += motionY;
            z += motionZ;

            if (!noDragNoGravity) {
                if (mc.level.getBlockState(BlockPos.containing(x, y, z)).getBlock() == Blocks.WATER) {
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

            Vec3 pos = new Vec3(x, y, z);

            if (y <= -65.0) break;

            BlockHitResult bhr = mc.level.clip(new ClipContext(lastPos, pos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (bhr != null && (bhr.getType() == HitResult.Type.BLOCK || bhr.getType() == HitResult.Type.ENTITY)) {
                break;
            }

            int alpha = Mth.clamp((int) (255.0F * ((i + 1) / 10.0F)), 0, 255);
            event.renderer.line(lastPos.x, lastPos.y, lastPos.z, pos.x, pos.y, pos.z, new SettingColor(color.r, color.g, color.b, alpha));
        }
    }

    private void calcTrajectory(Item item, float yaw, double x, double y, double z, SettingColor color, Render3DEvent event) {
        y = y + mc.player.getEyeHeight(mc.player.getPose()) - 0.1000000014901161;
        if (item == mc.player.getMainHandItem().getItem()) {
            x -= Mth.cos(yaw / 180.0F * (float) Math.PI) * 0.16F;
            z -= Mth.sin(yaw / 180.0F * (float) Math.PI) * 0.16F;
        } else {
            x += Mth.cos(yaw / 180.0F * (float) Math.PI) * 0.16F;
            z += Mth.sin(yaw / 180.0F * (float) Math.PI) * 0.16F;
        }

        float maxDist = getDistance(item);
        double motionX = -Mth.sin(yaw / 180.0F * (float) Math.PI) * Mth.cos(mc.player.getXRot() / 180.0F * (float) Math.PI) * maxDist;
        double motionY = -Mth.sin((mc.player.getXRot() - getThrowPitch(item)) / 180.0F * 3.141593F) * maxDist;
        double motionZ = Mth.cos(yaw / 180.0F * (float) Math.PI) * Mth.cos(mc.player.getXRot() / 180.0F * (float) Math.PI) * maxDist;

        float power = mc.player.getTicksUsingItem() / 20.0F;
        power = (power * power + power * 2.0F) / 3.0F;
        if (power > 1.0F) power = 1.0F;

        float distance = Mth.sqrt((float) (motionX * motionX + motionY * motionY + motionZ * motionZ));
        motionX /= distance;
        motionY /= distance;
        motionZ /= distance;
        float pow = (item instanceof BowItem ? power * 2.0F : (item instanceof CrossbowItem ? 2.2F : 1.0F)) * getThrowVelocity(item);
        motionX *= pow;
        motionY *= pow;
        motionZ *= pow;
        motionX += mc.player.getDeltaMovement().x();
        motionY += mc.player.getDeltaMovement().y();
        motionZ += mc.player.getDeltaMovement().z();

        boolean arrowPhysics = item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem;

        for (int i = 0; i < 300; i++) {
            Vec3 lastPos = new Vec3(x, y, z);
            x += motionX;
            y += motionY;
            z += motionZ;

            if (mc.level.getBlockState(BlockPos.containing(x, y, z)).getBlock() == Blocks.WATER) {
                motionX *= 0.8;
                motionY *= 0.8;
                motionZ *= 0.8;
            } else {
                motionX *= 0.99;
                motionY *= 0.99;
                motionZ *= 0.99;
            }

            motionY -= arrowPhysics ? 0.05F : 0.03F;

            Vec3 pos = new Vec3(x, y, z);

            for (Entity ent : mc.level.entitiesForRendering()) {
                if (!(ent instanceof Arrow)
                    && !ent.equals(mc.player)
                    && ent.getBoundingBox().intersects(new AABB(x - 0.3, y - 0.3, z - 0.3, x + 0.3, y + 0.3, z + 0.3))) {
                    AABB bb = ent.getBoundingBox();
                    event.renderer.box(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, color, color, ShapeMode.Lines, 0);
                    break;
                }
            }

            BlockHitResult bhr = mc.level.clip(new ClipContext(lastPos, pos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                AABB bb = new AABB(bhr.getBlockPos());
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
