package com.codigohasta.addon.utils.slimefunhelper.hacks;
import java.awt.Color;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.BlockHitResult;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.managers.Tasks;
import com.codigohasta.addon.utils.slimefunhelper.utils.*;
import com.codigohasta.addon.modules.slimefunhelper.PlayerStateManager;
public final class RenderTasks {
private static final MinecraftClient mc=MinecraftClient.getInstance();
    public static Color STATIC_DEBUG_COLOR = null;
    public static int DEBUG_TICK = 16;
    public static boolean DEBUG_RENDER_STANDING = false;
    public static boolean DEBUG_RENDER_COLLISION = false;
    public static boolean DEBUG_RENDER_COMBAT = false;
    public static boolean DEBUG_RENDER_COLLISION_RENDERING = false;
    public static boolean DEBUG_RENDER_BOWAIM = false;
    public static boolean DEBUG_RENDER_SPEAR = false;
    public static boolean DEBUG_RENDER_INTERACTION = false;

    public static void debugBoxMov(Box box, Vec3d move) {
        if (DEBUG_RENDER_COLLISION_RENDERING && DEBUG_RENDER_COLLISION) {
            RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                    DEBUG_TICK, new BoxMoveTarget(box, move, STATIC_DEBUG_COLOR, Color.RED)));
        }
    }

    public static void debugBox(Box box) {
        if (DEBUG_RENDER_COLLISION_RENDERING && DEBUG_RENDER_COLLISION) {
            RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                    DEBUG_TICK, new BoxObject(box.getMinPos(), box.getMaxPos(), STATIC_DEBUG_COLOR)));
        }
    }

    public static void debugBlockHitResult(BlockHitResult packetHitResult) {
        if (DEBUG_RENDER_INTERACTION) {
            RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                    RenderTasks.DEBUG_TICK,
                    new RenderTasks.BoxObject(
                            Vec3d.of(packetHitResult.getBlockPos()),
                            Vec3d.of(packetHitResult.getBlockPos()).add(1, 1, 1),
                            Color.WHITE)));
            RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
                    RenderTasks.DEBUG_TICK,
                    new RenderTasks.BoxObject(
                            packetHitResult.getPos().add(-0.1, -0.1, -0.1),
                            packetHitResult.getPos().add(0.1, 0.1, 0.1),
                            Color.RED)));
        }
    }

    public static void onDebugRenderTick(Event<MatrixStack> event) {
        if (RenderTasks.DEBUG_RENDER_STANDING) {
            if (mc.player != null) {
                try {
                    RenderUtils.startDrawVirtual(event.context());
                    BlockPos pos = PlayerStateManager.INSTANCE.lastVelocityAffectingPos;
                    RenderUtils.drawOutlinedBox(
                            event.context(),
                            pos.toCenterPos().add(RenderTasks.FROM),
                            pos.toCenterPos().add(RenderTasks.TO),
                            Color.GREEN);

                    Vec3d vec3d = mc.player.getEntityPos();
                    Vec3d vec3dSupportingBlock = vec3d.subtract(0, 0.500001F, 0);
                    BlockPos underBlock = BlockPos.ofFloored(vec3dSupportingBlock);
                    RenderUtils.drawOutlinedBox(
                            event.context(),
                            underBlock.toCenterPos().add(RenderTasks.FROM),
                            underBlock.toCenterPos().add(RenderTasks.TO),
                            Color.MAGENTA);
                } finally {
                    RenderUtils.stopDrawVirtual(event.context());
                }
            }
        }
    }

    public static void drawBox(Box box, int timeTick, Color color) {
        RenderTasks.registerVirtualRenderTask(
                new RenderTasks.RenderTask(timeTick, new BoxObject(box.getMinPos(), box.getMaxPos(), color)));
    }

    public static void drawBoxMov(Box box, Vec3d move, int tick, Color color) {
        RenderTasks.registerVirtualRenderTask(
                new RenderTasks.RenderTask(tick, new BoxMoveTarget(box, move, color, color)));
    }

    public static void drawLine(Vec3d from, Vec3d deltaMove, int timeTick, Color color) {
        RenderTasks.registerVirtualRenderTask(new RenderTask(timeTick, new LineObject(from, deltaMove).color(color)));
    }

    // the visit to renderBlocks need synchronized for thread safety, as they involved for-loop and remove
    private static final Set<VirtualRenderTask> renderBlocks = new LinkedHashSet<>();

    public static void registerVirtualRenderTask(VirtualRenderTask task) {
        synchronized (renderBlocks) {
            task.startRender();
            renderBlocks.add(task);
        }
    }

    public static final Vec3d FROM = new Vec3d(-0.5, -0.5, -0.5);
    public static final Vec3d SMALL_FROM = new Vec3d(-0.2, -0.2, -0.2);
    public static final Vec3d TO = new Vec3d(0.5, 0.5, 0.5);
    public static final Vec3d SMALL_TO = new Vec3d(0.2, 0.2, 0.2);

    private static void onRenderVirtualTasks(Event<MatrixStack> stackE) {
        if (renderBlocks.isEmpty()) return;
        synchronized (renderBlocks) {
            var stack = stackE.context;
            float ticksDelta = stackE.getArgs(0);
            RenderUtils.startDrawVirtual(stack);
            try {
                Iterator<VirtualRenderTask> tasks = renderBlocks.iterator();
                while (tasks.hasNext()) {
                    VirtualRenderTask renderTask = tasks.next();
                    if (renderTask.stillRender()) {
                        renderTask.renderVirtual(stack, ticksDelta);
                    } else {
                        renderTask.stopRender();
                        tasks.remove();
                    }
                }
            } finally {
                RenderUtils.stopDrawVirtual(stack);
            }
        }
    }
    //    public static interface StaticRenderTask {
    //        boolean stillRender();
    //        VertexBuffer getRenderAction();
    //    }

    public static class TaskBuilder {
        int tickLeft = -1;
        BooleanSupplier autoStop;
        Runnable stopFuture;
        List<RenderObject> renderObjects = new ArrayList<>();

        public TaskBuilder time(int tickLeft) {
            this.tickLeft = tickLeft;
            return this;
        }

        public TaskBuilder add(RenderObject renderObject) {
            renderObjects.add(renderObject);
            return this;
        }

        public TaskBuilder autoStop(BooleanSupplier autoStop) {
            this.autoStop = autoStop;
            return this;
        }

        public TaskBuilder stopFuture(Runnable runnable) {
            this.stopFuture = runnable;
            return this;
        }

        public RenderTask build() {
            RenderTask renderTask;
            if (tickLeft <= 0) {
                renderTask = new RenderTask(renderObjects.toArray(RenderObject[]::new));
            } else {
                renderTask = new RenderTask(tickLeft, renderObjects.toArray(RenderObject[]::new));
            }
            if (autoStop != null) {
                renderTask.setAutoStop(autoStop);
            }
            if (stopFuture != null) {
                renderTask.setStopFuture(stopFuture);
            }
            return renderTask;
        }
    }

    public static TaskBuilder builder() {
        return new TaskBuilder();
    }

    public static interface VirtualRenderTask {
        void renderVirtual(MatrixStack stack, float partialTicks);

        public void startRender();

        public void stopRender();

        boolean stillRender();
    }

    public static class RenderTask implements VirtualRenderTask {
        int endTick;
        RenderObject[] renderObjects;
        boolean registered = false;
        BooleanSupplier autoStopPredicate = null;
        Runnable stopFuture = null;

        public RenderTask(int tick, RenderObject... renderObjects) {
            this.endTick = tick + Tasks.getTick();
            this.renderObjects = renderObjects;
        }

        public RenderTask(RenderObject... renderObjects) {
            this.endTick = Integer.MAX_VALUE;
            this.renderObjects = renderObjects;
        }

        public RenderTask refreshTimer(int val) {
            this.endTick = val + Tasks.getTick();
            return this;
        }

        public void stopRender() {
            this.registered = false;
            this.endTick = -1;
            if (stopFuture != null) {
                stopFuture.run();
            }
        }

        public RenderTask cancelTimer() {
            this.endTick = Integer.MAX_VALUE;
            return this;
        }

        public RenderTask setAutoStop(BooleanSupplier autoStopPredicate) {
            this.autoStopPredicate = autoStopPredicate;
            return this;
        }

        public RenderTask setStopFuture(Runnable stopFuture) {
            this.stopFuture = stopFuture;
            return this;
        }

        @Override
        public void renderVirtual(MatrixStack stack, float partialTicks) {
            for (RenderObject renderObject : renderObjects) {
                renderObject.render(stack, partialTicks);
            }
        }

        @Override
        public boolean stillRender() {
            return registered
                    && Tasks.getTick() <= this.endTick
                    && (autoStopPredicate == null || !autoStopPredicate.getAsBoolean());
        }

        public void startRender() {
            if (!registered) {
                registered = true;
                RenderTasks.registerVirtualRenderTask(this);
            }
        }
    }

    public static interface RenderObject {
        public void render(MatrixStack stack, float partialTicks);
    }



    public static class LineObject implements RenderObject {
 public LineObject color(Color color){this.color=color;return this;}
        Vec3d start;
        Vec3d movement;
        Color color = Color.GREEN;

        public LineObject(Vec3d start, Vec3d movement) {
            this.start = start;
            this.movement = movement;
        }

        @Override
        public void render(MatrixStack stack, float partialTicks) {
            RenderUtils.drawLineVirtual(stack, start, start.add(movement), color);
        }
    }



    public static class BoxObject implements RenderObject {

        Vec3d startVec;
        Vec3d endVec;
        Color color;
        float opacity = 0.25F;

        public BoxObject(Box box, Color color) {
            this(box.getMinPos(), box.getMaxPos(), color);
        }

        public BoxObject(Vec3d start, Vec3d end, Color color) {
            this.startVec = start;
            this.endVec = end;
            this.color = color;
        }

        @Override
        public void render(MatrixStack stack, float partialTicks) {
            RenderUtils.drawSolidBox(stack, startVec, endVec, ColorUtils.withAlpha(color, opacity));
        }
    }




    public static class BoxOutlineObject implements RenderObject {
 public BoxOutlineObject(Vec3d a,Vec3d b,Color c){startVec=a;endVec=b;color=c;}
        Vec3d startVec;
        Vec3d endVec;
        Color color;

        @Override
        public void render(MatrixStack stack, float partialTicks) {
            RenderUtils.drawOutlinedBox(stack, startVec, endVec, color);
        }
    }




    public static class BoxMoveTarget implements RenderObject {
 public BoxMoveTarget(Box b,Vec3d d,Color c1,Color c2){startBox=b;delta=d;color1=c1;color2=c2;}
        Box startBox;
        Vec3d delta;
        Color color1;
        Color color2;

        public BoxMoveTarget(Box startBox, Vec3d delta) {
            this(startBox, delta, Color.GREEN, Color.RED);
        }

        @Override
        public void render(MatrixStack stack, float partialTicks) {
            Color color = ColorUtils.withAlpha(color1, 0.25F);
            RenderUtils.drawSolidBox(stack, startBox.getMinPos(), startBox.getMaxPos(), color);
            RenderUtils.drawSolidBox(
                    stack, startBox.getMinPos().add(delta), startBox.getMaxPos().add(delta), color);
            for (var ver : CollisionUtil.getBoxVertices(startBox))
                RenderUtils.drawLineVirtual(stack, ver, ver.add(delta), color2);
        }
    }

    public static class LineToTargetObject implements RenderObject {
 public LineToTargetObject(Vec3d v,Color c){vec3d=v;color=c;}
        Vec3d vec3d;
        Color color;

        @Override
        public void render(MatrixStack stack, float partialTicks) {
            Vec3d camera = RenderUtils.getCameraPos();
            Vec3d camerToBlock = this.vec3d.subtract(camera);
            Vec3d cursorPos = RenderUtils.getTracerOrigin(1.0f);
            RenderUtils.drawLineVirtualCameraCoord(stack, cursorPos, camerToBlock, color);
        }
    }

public static void renderPending(){if(RenderUtils.current!=null)onRenderVirtualTasks(new Event<>(RenderUtils.current.matrices,false,false,RenderUtils.current.tickDelta));}
public static void clear(){synchronized(renderBlocks){for(var task:renderBlocks)task.stopRender();renderBlocks.clear();}}
}
