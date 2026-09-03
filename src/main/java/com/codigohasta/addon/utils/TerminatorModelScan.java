package com.codigohasta.addon.utils;

import com.codigohasta.addon.mixin.ModelPartAccessor;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Restricts one synchronous entity-model render to a real anatomical model branch.
 * The T-800 HUD wraps WireframeEntityRenderer with this context, so normal entity,
 * item and world rendering remain untouched.
 */
public final class TerminatorModelScan {
    private static final ThreadLocal<Session> ACTIVE = new ThreadLocal<>();

    private TerminatorModelScan() {}

    public static void begin(double progress) {
        ACTIVE.set(new Session(Math.max(0.0, Math.min(0.999999, progress))));
    }

    public static Result end() {
        Session session = ACTIVE.get();
        ACTIVE.remove();
        return session == null ? null : session.result;
    }

    /** Called only by MixinTerminatorModelRender at Model.render(...). */
    public static boolean renderSelectedPart(ModelPart root, MatrixStack matrices,
                                             VertexConsumer vertices, int light,
                                             int overlay, int color) {
        Session session = ACTIVE.get();
        if (session == null) return false;

        // Entity features can submit armor/overlay models after the base model. The
        // first model is the animated entity body; suppress later feature models so
        // one scan stage cannot accidentally contain a complete armor model.
        if (session.modelCaptured) return true;
        session.modelCaptured = true;

        List<PartBranch> branches = findStructuralBranches(root);
        if (branches.isEmpty()) return true;

        int index = Math.min(branches.size() - 1, (int) (session.progress * branches.size()));
        PartBranch branch = branches.get(index);
        session.result = new Result(index, branches.size(), displayName(branch.name));

        matrices.push();
        try {
            // Model.render normally starts at the root. Reapply every ancestor
            // transform, then let the selected branch render its own real cuboids
            // and descendants with their current animation pose.
            for (ModelPart ancestor : branch.ancestors) ancestor.applyTransform(matrices);
            branch.part.render(matrices, vertices, light, overlay, color);
        } finally {
            matrices.pop();
        }
        return true;
    }

    private static List<PartBranch> findStructuralBranches(ModelPart root) {
        List<ModelPart> ancestors = new ArrayList<>();
        ModelPart current = root;

        // Minecraft models sometimes wrap all anatomy in one or more empty root
        // nodes. Descend through those wrappers until the model actually branches.
        for (int depth = 0; depth < 8; depth++) {
            List<Map.Entry<String, ModelPart>> children = visibleChildren(current);
            if (children.size() != 1) {
                if (children.isEmpty()) return List.of(new PartBranch("body", List.copyOf(ancestors), current));
                List<ModelPart> prefix = new ArrayList<>(ancestors);
                prefix.add(current);
                List<PartBranch> result = new ArrayList<>(children.size());
                for (Map.Entry<String, ModelPart> child : children) {
                    result.add(new PartBranch(child.getKey(), List.copyOf(prefix), child.getValue()));
                }
                return result;
            }

            ancestors.add(current);
            current = children.get(0).getValue();
        }

        return List.of(new PartBranch("body", List.copyOf(ancestors), current));
    }

    private static List<Map.Entry<String, ModelPart>> visibleChildren(ModelPart part) {
        Map<String, ModelPart> children = ((ModelPartAccessor) (Object) part).img$getChildren();
        List<Map.Entry<String, ModelPart>> result = new ArrayList<>(children.size());
        for (Map.Entry<String, ModelPart> child : children.entrySet()) {
            if (child.getValue().visible && !child.getValue().isEmpty()) result.add(child);
        }
        return result;
    }

    private static String displayName(String raw) {
        if (raw == null || raw.isBlank()) return "BODY";
        return raw.replace('_', ' ').replace('-', ' ').toUpperCase(Locale.ROOT);
    }

    public record Result(int partIndex, int partCount, String partName) {}

    private record PartBranch(String name, List<ModelPart> ancestors, ModelPart part) {}

    private static final class Session {
        private final double progress;
        private boolean modelCaptured;
        private Result result;

        private Session(double progress) {
            this.progress = progress;
        }
    }
}
