package com.codigohasta.addon.utils.gcore;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;

public final class GcoreTickUpdate {

    public static final GcoreTickUpdate INSTANCE = new GcoreTickUpdate();

    /** 主线程任务队列。 */
    private static final GcoreTaskQueueManager mainThreadTask = new GcoreTaskQueueManager();

    /** 缓存的世界实体列表（每 tick 刷新）。 */
    private static final ArrayList<net.minecraft.entity.Entity> worldEntitiesList = new ArrayList<>();

    private GcoreTickUpdate() {
    }

    public GcoreTaskQueueManager getMainThreadTask() {
        return mainThreadTask;
    }

    public static ArrayList<net.minecraft.entity.Entity> getWorldEntitiesList() {
        synchronized (worldEntitiesList) {
            return new ArrayList<>(worldEntitiesList);
        }
    }

    private int refCount = 0;

    /**
     * 注册到事件总线（引用计数 +1）。
     *
     * <p>只有从 0 → 1 时才真正订阅。
     */
    public synchronized void register() {
        if (this.refCount++ == 0) {
            meteordevelopment.meteorclient.MeteorClient.EVENT_BUS.subscribe(this);
        }
    }

    /**
     * 从事件总线注销（引用计数 -1）。
     *
     * <p>只有归零时才真正退订。见 {@link #refCount} 的说明。
     */
    public synchronized void unregister() {
        if (this.refCount == 0) return;
        if (--this.refCount == 0) {
            meteordevelopment.meteorclient.MeteorClient.EVENT_BUS.unsubscribe(this);
        }
    }

    @EventHandler
    private void onUpdate(TickEvent.Post event) {
        synchronized (worldEntitiesList) {
            worldEntitiesList.clear();
            var world = meteordevelopment.meteorclient.MeteorClient.mc.world;
            if (world != null) {
                for (net.minecraft.entity.Entity entity : world.getEntities()) {
                    worldEntitiesList.add(entity);
                }
            }
        }

        mainThreadTask.handler();
    }
}
