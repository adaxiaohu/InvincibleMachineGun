package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity;

import java.util.*;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.managers.Tasks;
import com.codigohasta.addon.utils.slimefunhelper.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.TrackedPosition;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.Vec3d;

public class PredictorImpl implements Predictor {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private int ownerId;
    private final Deque<KnownPosition> positions = new ArrayDeque<>();
    private static final int MAX_HISTORY = 80;
    private final TrackedPosition currentTrackedPosition = new TrackedPosition();
    private float currentSyncedPitch;
    private float currentSyncedYaw;

    /**
     * IMG's mixin builds the predictor from its owner. Upstream instead constructs a bare
     * instance and calls {@link #initializeTrackedPosition} from the spawn packet; this
     * constructor performs that initialisation from the entity so IMG's call site stays valid.
     */
    public PredictorImpl(Entity owner) {
        initializeTrackedPosition(
                owner.getId(),
                owner.getEntityPos().x,
                owner.getEntityPos().y,
                owner.getEntityPos().z,
                owner.getPitch(),
                owner.getYaw());
    }

    public void initializeTrackedPosition(int owner, double x, double y, double z, float pitch, float yaw) {
        this.ownerId = owner;
        this.currentTrackedPosition.setPos(new Vec3d(x, y, z));
        this.currentSyncedPitch = pitch;
        this.currentSyncedYaw = yaw;
    }

    public void tick() {
        synchronized (this) {
            while (positions.size() > MAX_HISTORY) {
                positions.removeFirst();
            }
            if (mc.player.getId() == this.ownerId) {
                // 本地玩家在服务端包之间会被客户端输入移动。
                // 让同步位置跟上客户端实体，直到服务端位置包给出新的基准位置。
                setSyncedPosition(mc.player.getEntityPos());
                currentSyncedPitch = mc.player.getPitch();
                currentSyncedYaw = mc.player.getYaw();
                addRecord(new KnownPosition(currentTrackedPosition.getPos(), Tasks.getTick()));
            }
        }
    }

    public void onEntityPositionPost(Event<EntityPositionS2CPacket> event) {
        EntityPositionS2CPacket packet = event.context();
        if (packet.entityId() != ownerId) return;
        synchronized (this) {
            EntityPosition position = apply(packet.change(), packet.relatives());
            setSyncedPosition(position.position());
            currentSyncedPitch = position.pitch();
            currentSyncedYaw = position.yaw();
            addRecord(new KnownPosition(currentTrackedPosition.getPos(), Tasks.getTick()));
        }
    }

    public void onEntityPositionSyncPost(Event<EntityPositionSyncS2CPacket> event) {
        EntityPositionSyncS2CPacket packet = event.context();
        if (packet.id() != ownerId) return;
        synchronized (this) {
            EntityPosition position = packet.values();
            setSyncedPosition(position.position());
            currentSyncedPitch = position.pitch();
            currentSyncedYaw = position.yaw();
            addRecord(new KnownPosition(currentTrackedPosition.getPos(), Tasks.getTick()));
        }
    }

    public void onEntityPositionMove(Event<EntityS2CPacket> event) {
        EntityS2CPacket packet = event.context();
        if (packet.id != ownerId) return;
        synchronized (this) {
            if (mc.player.getId() == ownerId || packet.isPositionChanged()) {
                Vec3d position = currentTrackedPosition.withDelta(
                        (long) packet.getDeltaX(), (long) packet.getDeltaY(), (long) packet.getDeltaZ());
                setSyncedPosition(position);
            }
            if (packet.hasRotation()) {
                currentSyncedYaw = packet.getYaw();
                currentSyncedPitch = packet.getPitch();
            }
            addRecord(new KnownPosition(currentTrackedPosition.getPos(), Tasks.getTick()));
        }
    }

    private EntityPosition apply(EntityPosition position, Set<PositionFlag> flags) {
        EntityPosition current =
                new EntityPosition(currentTrackedPosition.getPos(), Vec3d.ZERO, currentSyncedYaw, currentSyncedPitch);
        return EntityPosition.apply(current, position, flags);
    }

    private void setSyncedPosition(Vec3d position) {
        currentTrackedPosition.setPos(position);
    }

    /**
     * 基于最近两个已知位置（收到的记录）计算当前移动速度（每 tick 的位移向量）
     * @return 速度向量；若 tick 差为 0，则返回位移差
     */
    public Vec3d getKnownDeltaMovement() {
        synchronized (this) {
            if (positions.size() < 2) return Vec3d.ZERO;
            Iterator<KnownPosition> it = positions.descendingIterator();
            KnownPosition newest = it.next();
            KnownPosition second = it.next();
            int dt = newest.tick() - second.tick();
            if (dt == 0) {
                // 同一 tick 内无法计算速度，返回位移差
                return newest.vec3d().subtract(second.vec3d());
            }
            Vec3d displacement = newest.vec3d().subtract(second.vec3d());
            return displacement.multiply(1.0 / dt);
        }
    }

    @Override
    public Vec3d getCurrentPos() {
        synchronized (this) {
            return currentTrackedPosition.getPos();
        }
    }

    /**
     * 预测未来位置
     * @param ticksLater 未来刻数
     * @param method 1=线性回归, 2=二次回归, 3=NVPredictor
     * @param useTicksBefore 只使用过去 useTicksBefore 刻内的历史记录
     */
    public Vec3d predict(int ticksLater, int method, int useTicksBefore) {
        Vec3d trackedPos;
        List<KnownPosition> histRecords = new ArrayList<>();
        KnownPosition lastKnown = null;
        int currentTick = Tasks.getTick();
        boolean add = false;

        synchronized (this) {
            trackedPos = currentTrackedPosition.getPos();
            for (KnownPosition pos : positions) {
                if (pos.tick() >= currentTick - useTicksBefore) {
                    add = true;
                    if (lastKnown != null) {
                        histRecords.add(lastKnown);
                    }
                }
                lastKnown = pos;
            }
        }
        if (ticksLater == 0) return trackedPos;
        Vec3d currentPos = trackedPos;
        // 如果最后一个需要加入。那么 add 必然为 true
        if (lastKnown != null && add) {
            histRecords.add(lastKnown);
        }
        // 如果没有，则直接返回
        if (histRecords.isEmpty()) {
            return currentPos;
        }
        // 最后刻
        // 例如: 我们选取 10000 - 10005 共 6 个时间刻进行预测
        // 当前 数据点 10001 10003 10004
        // lastTick0 = 10004
        // firstTick0 = 10001
        // startTick = 10000
        // 我们需要取 history = 10000, 10001, 10002, 10003, 10004 这 5 个点
        int lastTick0 = histRecords.get(histRecords.size() - 1).tick();
        int firstTick0 = histRecords.get(0).tick();
        int startTick0 = currentTick - useTicksBefore;
        if (currentTick + ticksLater < firstTick0) {
            return histRecords.get(0).vec3d();
        }
        if (startTick0 >= lastTick0) {
            // 窗口内没有记录，直接返回当前
            return currentPos;
        }
        // 同步到 startTick0
        if (firstTick0 > startTick0) {
            KnownPosition firstPosition = histRecords.get(0);
            histRecords.add(0, new KnownPosition(firstPosition.vec3d(), startTick0));
            firstTick0 = startTick0;
        }
        // startTick0, startTick0 + 1, .... lastTick0
        int usableTicks = lastTick0 - startTick0 + 1;
        // lastTick0 + 1 ... currentTick 之间是空的，需要在 ticksAfter 加入
        int blankTicks = currentTick - lastTick0;
        if (usableTicks < 2) {
            return currentPos;
        }

        Vec3d[] history = new Vec3d[usableTicks];
        int currentIndex = 0;
        KnownPosition pos = histRecords.get(currentIndex);
        KnownPosition lastPos = null;
        for (int i = 0; i < history.length; ++i) {
            int realTick = startTick0 + i;
            // realTick <= historyRecords.getLast().tick()
            while (true) {
                if (pos.tick() == realTick) {
                    history[i] = pos.vec3d();
                    break;
                }
                if (lastPos != null && lastPos.tick() < realTick && pos.tick() > realTick) {
                    // pos.tick > realTick > lastPos.tick
                    history[i] = pos.vec3d()
                            .multiply(pos.tick() - realTick)
                            .add(lastPos.vec3d().multiply(realTick - lastPos.tick()))
                            .multiply(1.0D / (pos.tick() - lastPos.tick()));
                    break;
                }
                lastPos = pos;
                currentIndex += 1;
                if (currentIndex >= histRecords.size()) {
                    // impossible
                    throw new RuntimeException("?   WTF");
                }
                pos = histRecords.get(currentIndex);
            }
        }
        int futureSteps = blankTicks + ticksLater;
        if (futureSteps <= 0) {
            return history[history.length - 1 + futureSteps];
        }
        switch (method) {
            case 1 -> {
                return MathUtils.linearPrediction(history, futureSteps);
            }
            case 2 -> {
                return MathUtils.quadraticPrediction(history, futureSteps);
            }
            case 3 -> {
                Vec3d[] ring = Arrays.copyOf(history, history.length);
                int currentIdx = history.length - 1;
                return new MathUtils.NVPredictor(ring, () -> currentIdx).compute(futureSteps);
            }
            case 4 -> {
                Vec3d[] ring = Arrays.copyOf(history, history.length);
                int currentIdx = history.length - 1;
                return new MathUtils.RotationalPredictor(ring, () -> currentIdx).compute(futureSteps);
            }
            case 5 -> {
                Vec3d[] ring = Arrays.copyOf(history, history.length);
                int currentIdx = history.length - 1;
                return new MathUtils.AcceleratePredictor2(ring, () -> currentIdx).compute(futureSteps);
            }
            case 6 -> {
                Vec3d[] ring = Arrays.copyOf(history, history.length);
                int currentIdx = history.length - 1;
                return new MathUtils.AcceleratePredictor(ring, () -> currentIdx).compute(futureSteps);
            }

            default -> {
                return currentPos;
            }
        }
    }

    public List<KnownPosition> getLastKnownPositions(int lastNumber) {
        if (lastNumber <= 0) return Collections.emptyList();

        // 先收集已有的历史记录（从旧到新）
        List<KnownPosition> result;
        Vec3d currentPos;
        synchronized (this) {
            result = new ArrayList<>(positions);
            currentPos = currentTrackedPosition.getPos();
        }
        // 如果历史记录超过所需数量，只保留最后 lastNumber 个
        if (result.size() > lastNumber) {
            result = result.subList(result.size() - lastNumber, result.size());
        }

        // 如果不足，用当前同步位置补全（添加在末尾）
        int missing = lastNumber - result.size();
        if (missing > 0) {
            int currentTick = Tasks.getTick();
            for (int i = 0; i < missing; i++) {
                result.add(new KnownPosition(currentPos, currentTick));
            }
        }

        return result;
    }

    private void addRecord(KnownPosition record) {
        // 若与队尾记录相同，则不重复添加（避免重复记录同一时刻）
        var pos = positions.peekLast();
        // remove duplicate packets
        if (!Objects.equals(pos, record)) {
            positions.add(record);
        }
    }

    public static record KnownPosition(Vec3d vec3d, int tick) {}
}
