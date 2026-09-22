package com.codigohasta.addon.utils.slimefunhelper.hacks;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;
import com.codigohasta.addon.utils.slimefunhelper.events.Channel;
import com.codigohasta.addon.utils.slimefunhelper.events.impl.BlockUpdate;
import com.codigohasta.addon.utils.slimefunhelper.utils.CommonUtils;
import com.codigohasta.addon.utils.slimefunhelper.utils.WorldUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

public class WorldTasks {
    public static void init() {}

    public static Map<ChunkPos, CompletableFuture<Void>> pendingUpdateTasks = new ConcurrentHashMap<>();
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    // optimize, do not block main thread
    private static final ExecutorService scanExecutor = new ThreadPoolExecutor(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(2500),
            task -> { Thread thread = new Thread(task, "sf-world-scan"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.CallerRunsPolicy());
    private static final Executor processExecutor = Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "sf-world-results"); thread.setDaemon(true); return thread; });
    private static final java.util.concurrent.atomic.AtomicLong scanEpoch = new java.util.concurrent.atomic.AtomicLong();
    static int tickCounter = 0;

    public static void onTick(Event<ClientPlayerEntity> eventUpdate) {
        if (++tickCounter > 5) {
            tickCounter = 0;
            Set<ChunkPos> chunkPoses = pendingUpdateTasks.keySet();
            List<ChunkPos> removal = new ArrayList<>(32);
            for (var key : chunkPoses) {
                if (!mc.world.getChunkManager().isChunkLoaded(key.x, key.z)) {
                    removal.add(key);
                }
            }
            for (ChunkPos chunkPos : removal) {
                cancelPendingChunkTask(chunkPos);
            }
        }
    }

    public static void cancelPendingChunkTask(ChunkPos chunkPos) {
        mc.execute(() -> pendingUpdateTasks.remove(chunkPos));
    }

    public static void cancelAllPendingChunkTasks() {
        scanEpoch.incrementAndGet();
        pendingUpdateTasks.clear();
    }

    public static void onWorldChange(Event<World> event) {
        cancelAllPendingChunkTasks();
    }

    public static void onGameExit(Event<Void> event) {
        cancelAllPendingChunkTasks();
    }

    public static void scheduleChunkTask(ChunkPos pos, Runnable task, boolean async) {
        long epoch = scanEpoch.get();
        World world = mc.world;
        Runnable runnable = () -> { if (epoch == scanEpoch.get() && world == mc.world && mc.player != null && shouldExecuteWorldScan()) task.run(); };
        if (async) {
            pendingUpdateTasks.compute(pos, (v, t) -> {
                if (t == null) {
                    return CompletableFuture.runAsync(runnable, scanExecutor);
                } else {
                    return t.thenRunAsync(runnable, scanExecutor);
                }
            });
        } else {
            pendingUpdateTasks.compute(pos, (v, t) -> {
                if (t == null) {
                    return CompletableFuture.runAsync(runnable, mc);
                } else {
                    return t.thenRunAsync(runnable, mc);
                }
            });
        }
    }

    public static boolean shouldExecuteWorldScan() {
        if (!Listener.getPreWorldScannListener().isEmpty()) {
            Event<Boolean> requestEvent = new Event<>(false, false, true);
            Listener.getPreWorldScannListener().dispatch(requestEvent);
            return requestEvent.context == Boolean.TRUE;
        }
        return false;
    }

    public static void restartWorldScanner() {
        Listener.getResetWorldScannListener().broadcast(null);
        cancelAllPendingChunkTasks();
        if (mc.player == null || mc.world == null) {
            return;
        }
        refreshAllChunks();
    }

    public static void refreshAllChunks() {
        if (shouldExecuteWorldScan()) {
            for (Chunk chunk : CommonUtils.chunks(false)) {
                ChunkPos chunkPos = chunk.getPos();
                scheduleChunkTask(chunkPos, () -> onChunkReScann(chunkPos), true);
            }
        }
    }

    private static void onChunkReScann(ChunkPos chunkPos) {
        if (mc.player == null || mc.world == null) return;
        long epoch = scanEpoch.get();
        World world = mc.world;
        if (mc.world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
            Chunk chunk = mc.world.getChunkManager().getWorldChunk(chunkPos.x, chunkPos.z);
            if (chunk != null) {
                List<BiPredicate<BlockPos, BlockState>> statePredicates = new ArrayList<>();
                Listener.getWorldScannChunkBlockFilterList().broadcast(statePredicates);
                if (statePredicates.isEmpty()) {
                    return;
                }
                BiPredicate<BlockPos, BlockState> predicate =
                        (b, s) -> statePredicates.stream().anyMatch(s1 -> s1.test(b, s));
                Map<BlockPos, BlockState> stateMap = WorldUtils.scannChunk(chunk, predicate);
                processExecutor.execute(() -> {
                    if (epoch != scanEpoch.get() || world != mc.world || mc.player == null) return;
                    Event<Map<BlockPos, BlockState>> chunkUpdate = new Event<>(stateMap, false, false, chunkPos);
                    Listener.getWorldScannChunkResult().dispatch(chunkUpdate);
                });
            }
        }
    }

    private static void onBlockStateUpdate(Event<BlockUpdate> event) {
        if (shouldExecuteWorldScan()) {
            BlockPos pos = event.context.pos().toImmutable();
            ChunkPos chunkPos = new ChunkPos(pos);
            scheduleChunkTask(chunkPos, () -> onSingleBlockValueChange(pos), true);
        }
    }

    private static void onSingleBlockValueChange(BlockPos pos) {
        if (mc.player == null || mc.world == null) return;
        long epoch = scanEpoch.get();
        World world = mc.world;
        ChunkPos chunkPos = CommonUtils.toChunk(pos);
        if (mc.world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
            BlockState state = mc.world.getBlockState(pos);
            scanExecutor.execute(() -> {
                if (epoch != scanEpoch.get() || world != mc.world || mc.player == null) return;
                Event<BlockState> stateUpdate = new Event<>(state, false, false, pos, chunkPos);
                Listener.getWorldScannBlockResult().getChannel(state.getBlock()).dispatch(stateUpdate);
            });
        }
    }

    public static void onChunkUpdate(Event<ChunkPos> chunkDataS2CPacketEvent) {
        if (mc.world == null || mc.player == null) return;
        if (shouldExecuteWorldScan()) {
            ChunkPos pos = chunkDataS2CPacketEvent.context;
            Chunk updatedChunk = mc.world.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            if (updatedChunk != null) {
                // because of chunk update, cancel all the last
                cancelPendingChunkTask(pos);
                scheduleChunkTask(pos, () -> onChunkReScann(pos), true);
            }
        }
    }

    private static <W> void registerListener(Channel<W> listener, Consumer<Event<W>> handler) {
        listener.add(event->{if(mc.player!=null&&mc.world!=null)handler.accept(event);},0);
    }

    static {
        registerListener(Listener.getBlockUpdateListener(), WorldTasks::onBlockStateUpdate);
        registerListener(Listener.getChunkUpdateListener(), WorldTasks::onChunkUpdate);
        registerListener(Listener.getWorldSwitchPoint(), WorldTasks::onWorldChange);
        registerListener(Listener.getServerLeavePoint(), WorldTasks::onGameExit);
        registerListener(Listener.getPostGameTick(), WorldTasks::onTick);
    }
}
