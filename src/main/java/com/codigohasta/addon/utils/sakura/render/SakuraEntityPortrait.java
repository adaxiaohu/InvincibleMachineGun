package com.codigohasta.addon.utils.sakura.render;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 解析生物的头像贴图 <b>与头部在贴图上的 UV 区域</b>。
 *
 * <p><b>为什么需要 UV</b>：并不是所有生物的头都在贴图 {@code (8,8)}。
 * 双足生物（僵尸/骷髅/苦力怕…）确实在那里，但蜘蛛的头在 {@code (40,12)} 一类的偏移位置。
 * 早先一律按 {@code (8,8)} 取，蜘蛛拿到的就是腿的图案——这就是「头像拼得不对」的原因。
 *
 * <p><b>取 UV 的办法</b>：直接问模型。{@link ModelPart.Vertex} 保存了每个面的贴图坐标，
 * 头部立方体中朝向 {@code -Z}（正面）的那个 quad 就是脸。这样不依赖硬编码表，
 * 任何生物（含模组生物）都能自动拿到正确的脸。
 *
 * <p><b>单位无关的换算</b>：不同版本里 {@code Vertex.u/v} 可能是「贴图像素」也可能是
 * 「归一化 0~1」，不便假定。这里用几何关系消掉单位：
 * <pre>
 *   uvPerPixel = (u 跨度) / (立方体的模型宽度)
 * </pre>
 * 因为模型像素与贴图像素在 MC 实体模型里是 1:1 的，所以
 * {@code 贴图u = u / uvPerPixel}。这个式子对两种单位都成立。
 *
 * <p>贴图按 {@code UUID} 缓存（村民职业、马的花色等逐实体不同），
 * UV 按实体类型缓存（只取决于模型）。
 */
public final class SakuraEntityPortrait {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** 兜底：双足布局的脸在 (8,8)，大小 8x8。 */
    private static final HeadUV BIPED_FALLBACK = new HeadUV(8f, 8f, 8f, 8f);

    private static final Map<UUID, Identifier> TEXTURE_CACHE = new HashMap<>();
    private static final Map<EntityType<?>, HeadUV> UV_CACHE = new HashMap<>();
    private static boolean failureLogged;

    private SakuraEntityPortrait() {}

    /**
     * 头像解析结果。
     *
     * @param u     头部正面左上角在贴图上的 X（贴图像素）
     * @param v     头部正面左上角在贴图上的 Y（贴图像素）
     * @param sizeX 脸部在贴图上的宽（贴图像素）
     * @param sizeY 脸部在贴图上的高（贴图像素）
     */
    public record HeadUV(float u, float v, float sizeX, float sizeY) {}

    /** 生物头像的完整解析结果：贴图 + UV 区域。 */
    public record Portrait(Identifier texture, HeadUV uv) {}

    /**
     * 解析该生物的头像。
     *
     * <p>必须在渲染线程调用。
     *
     * @return 解析结果；无法解析（或该实体是玩家）时返回 {@code null}
     */
    public static Portrait portraitFor(LivingEntity entity) {
        if (entity == null) return null;
        // 玩家走皮肤路径（含离线兜底皮肤），不走这里
        if (entity instanceof PlayerEntity) return null;

        try {
            EntityRenderManager dispatcher = mc.getEntityRenderDispatcher();
            EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
            if (!(renderer instanceof LivingEntityRenderer<?, ?, ?>)) return null;

            // getAndUpdateRenderState 返回该实体对应的具体 RenderState 子类型
            // （生物为 LivingEntityRenderState），向下转型后交给 getTexture。
            if (!(dispatcher.getAndUpdateRenderState(entity, 1f)
                    instanceof LivingEntityRenderState state)) {
                return null;
            }

            @SuppressWarnings({"rawtypes", "unchecked"})
            Identifier texture = ((LivingEntityRenderer) renderer).getTexture(state);
            if (texture == null) return null;

            HeadUV uv = headUvFor(entity, renderer);
            return new Portrait(texture, uv);
        } catch (Throwable t) {
            if (!failureLogged) {
                failureLogged = true;
                MeteorClient.LOG.error("[IMG] 解析生物头像失败，将使用占位图", t);
            }
            return null;
        }
    }

    /** 取（并缓存）该实体类型头部的 UV 区域。 */
    private static HeadUV headUvFor(LivingEntity entity, EntityRenderer<? super LivingEntity, ?> renderer) {
        EntityType<?> type = entity.getType();
        HeadUV cached = UV_CACHE.get(type);
        if (cached != null) return cached;

        HeadUV resolved = resolveHeadUv(renderer);
        UV_CACHE.put(type, resolved);
        return resolved;
    }

    /**
     * 从模型里读出头部正面（朝 {@code -Z}）的 UV 区域。
     *
     * <p><b>找不到 {@code head} 部位时不能直接回退到 (8,8)</b>：鱿鱼、蜜蜂、恶魂等
     * 模型根本没有名为 head 的部位（鱿鱼只有 {@code body} 与 {@code tentacle0..7}），
     * 它们的「头」就是身体。若此时硬套双足布局 {@code (8,8)}，取到的是贴图上一块
     * 与头无关的区域——这正是鱿鱼头像「拼得不对」的原因。
     * 因此改为退而取**整个模型中体积最大的立方体**的正面。
     *
     * <p>实测（离线调用原版模型工厂得到）：鱿鱼最大立方体是 12x16x12 的身体，
     * 其正面为 (12,12)、尺寸 12x16，与原版贴图布局一致。
     */
    private static HeadUV resolveHeadUv(EntityRenderer<? super LivingEntity, ?> renderer) {
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object model = ((LivingEntityRenderer) renderer).getModel();
            if (!(model instanceof net.minecraft.client.model.Model<?> entityModel)) return BIPED_FALLBACK;

            ModelPart root = entityModel.getRootPart();
            if (root == null) return BIPED_FALLBACK;

            String headPath = findHeadPath(root);

            // 先只在 head 子树里找；没有再退到全模型（鱿鱼等没有 head 部位）
            HeadUV uv = readFrontFaceUv(root, headPath);
            if (uv == null) uv = readFrontFaceUv(root, null);

            return uv != null ? uv : BIPED_FALLBACK;
        } catch (Throwable ignored) {
            return BIPED_FALLBACK;
        }
    }

    /**
     * 找出用作「头」的部位。
     *
     * <p>马的 {@code head} 挂在 {@code head_parts} 之下、并不在根上，所以必须递归查找。
     * 实现上直接复用 {@code forEachCuboid} 给出的部位路径（形如 {@code /head}、
     * {@code /head_parts/head}），避免用反射读 {@code ModelPart} 的私有字段——
     * 发布用的 jar 会被重映射成 intermediary，私有字段名（如 {@code children}）在运行时
     * 并不存在，反射字符串不会被重映射，那样写一定会在正式包里失败。
     */
    private static String findHeadPath(ModelPart root) {
        List<String> paths = collectPaths(root);

        for (String path : paths) {
            if (endsWithSegment(path, "head")) return path;
        }
        for (String path : paths) {
            for (String candidate : HEAD_CHILD_NAMES) {
                if (endsWithSegment(path, candidate)) return path;
            }
        }
        return null;
    }

    /** 收集模型中所有立方体所属部位的路径（如 {@code /head/beak}）。 */
    private static List<String> collectPaths(ModelPart root) {
        List<String> paths = new ArrayList<>();
        root.forEachCuboid(new MatrixStack(), (entry, name, index, cuboid) -> paths.add(name));
        return paths;
    }

    /** 路径的最后一段是否等于 {@code segment}（避免 {@code head_parts} 被 {@code head} 误判）。 */
    private static boolean endsWithSegment(String path, String segment) {
        if (path == null) return false;
        int slash = path.lastIndexOf('/');
        String last = (slash >= 0) ? path.substring(slash + 1) : path;
        return last.equalsIgnoreCase(segment);
    }

    /** 常见模组/原版对头部的命名。 */
    private static final String[] HEAD_CHILD_NAMES = {
        "head", "Head", "skull", "head_main", "bipedHead"
    };

    /**
     * 在模型里找朝 -Z 的正面，读出它的 UV 与其像素尺寸。
     *
     * <p><b>为什么用 -Z</b>：模型空间的正面是 NORTH(-Z)（渲染时
     * {@code setupTransforms} 会按 {@code 180 - bodyYaw} 旋转，把 -Z 转到实体朝向）。
     * 这与原版 box-UV 布局一致：正面位于 {@code (贴图偏移u + 深度, 贴图偏移v + 深度)}。
     * 两个已知点都印证了这一点——双足头部偏移 (0,0) 深度 8 → 正面 (8,8)；
     * 蜘蛛头部偏移 (32,4) 深度 8 → 正面 (40,12)，与实拍贴图一致。
     *
     * <p><b>为什么选体积最大的立方体</b>：一个「head」部位可能包含多个立方体，
     * 例如村民的头 + 鼻子、鸡的头 + 喙。取第一个会拿到鼻子这类装饰，
     * 因此改取体积最大者——头主体必然最大。
     *
     * @param onlyUnder 只考虑该路径及其子树下的立方体；{@code null} 表示考虑全部
     * @return 解析结果；没有找到可用的正面时返回 {@code null}
     */
    private static HeadUV readFrontFaceUv(ModelPart root, String onlyUnder) {
        HeadUV[] result = new HeadUV[1];
        float[] bestVolume = new float[1];

        root.forEachCuboid(new MatrixStack(), (entry, path, index, cuboid) -> {
            if (onlyUnder != null && !isUnder(path, onlyUnder)) return;

            float volume = (cuboid.maxX - cuboid.minX)
                * (cuboid.maxY - cuboid.minY)
                * (cuboid.maxZ - cuboid.minZ);
            if (result[0] != null && volume <= bestVolume[0]) return;

            for (ModelPart.Quad quad : cuboid.sides) {
                // MC 约定：-Z（NORTH）为实体正面
                if (quad.direction().z() > -0.5f) continue;

                ModelPart.Vertex[] vertices = quad.vertices();
                if (vertices.length < 4) continue;

                float minU = Float.MAX_VALUE, minV = Float.MAX_VALUE;
                float maxU = -Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
                for (ModelPart.Vertex vertex : vertices) {
                    minU = Math.min(minU, vertex.u());
                    maxU = Math.max(maxU, vertex.u());
                    minV = Math.min(minV, vertex.v());
                    maxV = Math.max(maxV, vertex.v());
                }

                // 该立方体在模型空间的实际尺寸就是脸在贴图上的像素尺寸
                // （MC 实体模型里模型像素与贴图像素是 1:1）
                float pixelW = cuboid.maxX - cuboid.minX;
                float pixelH = cuboid.maxY - cuboid.minY;
                float du = maxU - minU;
                float dv = maxV - minV;
                if (du <= 0f || dv <= 0f || pixelW <= 0f || pixelH <= 0f) continue;

                // uvPerPixel 消掉 u/v 的单位差异（像素制或归一化制都适用）
                float uPerPixel = du / pixelW;
                float vPerPixel = dv / pixelH;

                result[0] = new HeadUV(minU / uPerPixel, minV / vPerPixel, pixelW, pixelH);
                bestVolume[0] = volume;
                return;
            }
        });

        return result[0];
    }

    /** {@code path} 是否等于 {@code base} 或位于其子树下。 */
    private static boolean isUnder(String path, String base) {
        if (path == null || base == null) return false;
        return path.equals(base) || path.startsWith(base + "/");
    }

    public static void clear() {
        TEXTURE_CACHE.clear();
        UV_CACHE.clear();
    }
}
