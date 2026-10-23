package com.codigohasta.addon.utils.gcore;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;

/**
 * 每 tick 缓存世界实体列表 + 驱动任务队列。
 *
 * <p>移植自 {@code dev.godden.core.SlientEvents.TickUpdate}（GCore）。
 *
 * <p><b>源行为</b>（从字节码逐条还原）：
 * <ol>
 *     <li>{@code synchronized} 锁住列表 → {@code clear()} → 从
 *         {@code mc.world.getEntities()} 全部加入 → 解锁</li>
 *     <li>{@code ItemManager.getInventory().update()} —— 刷新模拟背包</li>
 *     <li>{@code mainThreadTask.handler()} —— 执行到期任务</li>
 * </ol>
 *
 * <p><b>框架适配</b>：源实现 LB 的 {@code Listenable} 并用
 * {@code EventManager.registerEventHook(PlayerTickEvent, ...)} 注册；
 * Meteor 下改为 {@code @EventHandler} + {@code TickEvent.Post}。
 * 类的静态字段与 {@link #getWorldEntitiesList()} 语义保持不变。
 *
 * <p><b>注册方式</b>：Meteor 的事件总线需要对象实例，所以这里用
 * {@link #INSTANCE} 在模块 {@code onActivate()} 时注册、{@code onDeactivate()} 时注销，
 * 与源「全局单例常驻」的效果一致但避免影响未启用时的性能。
 */
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

    /**
     * 取世界实体列表的**副本**。
     *
     * <p>源实现加了 {@code synchronized} 再复制，移植时保持同样的同步语义。
     */
    public static ArrayList<net.minecraft.entity.Entity> getWorldEntitiesList() {
        synchronized (worldEntitiesList) {
            return new ArrayList<>(worldEntitiesList);
        }
    }

    /**
     * 事件总线的**订阅引用计数**。
     *
     * <p><b>★ 为什么必须用计数而不是布尔</b>：{@link #INSTANCE} 是全局单例，
     * 而多个模块都会在自己的生命周期里操作它 ——
     * {@code TpAnchorAura} 与 {@code GLightTpAnchorAura} 的
     * {@code onActivate()} 都调 {@link #register()}、{@code onDeactivate()} 都调
     * {@link #unregister()}。
     *
     * <p>曾用单个 {@code boolean registered}，于是：
     * 先开 A、再开 B（B 的 register 因已订阅而空转），
     * 然后**关掉 A** → 退订 → **B 虽然还开着，tick 循环却死了**。
     * 表现为 {@code worldEntitiesList} 不再刷新（实体列表过期）→
     * {@code canPlaceAtPos} 判断错误 → 「总卡住不工作」，
     * 且 {@code mainThreadTask.handler()} 不再执行。
     *
     * <p>源里 {@code TickUpdate} 是 LB 的 {@code Listenable}，
     * 在**类静态初始化**时注册且**全程常驻**，不存在「按模块开关」，
     * 所以这里用计数来模拟「只要还有模块在用就保持常驻」的语义。
     */
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

    /**
     * 每 tick：刷新实体列表 → 执行任务队列。
     *
     * <p>对应源 {@code TickUpdate$onUpdate$1.invoke(PlayerTickEvent)}。
     *
     * <p><b>★ 版本适配（1.20.4 → 1.21.11）</b>：源里写的是
     * {@code worldEntitiesList.addAll(world.getEntities())} ——
     * 因为 1.20.4 的 {@code ClientWorld.getEntities()} 返回 {@code List}。
     *
     * <p>但 **1.21.11 的返回类型改成了 {@code Iterable<Entity>}**
     * （运行时是 Guava 的 {@code Iterables$UnmodifiableIterable}）。
     * 直接 {@code addAll} 会抛
     * {@code ClassCastException: UnmodifiableIterable cannot be cast to List}
     * （曾因此崩溃）。所以这里用 for-each 逐个添加 —— 对 {@code Iterable} 通用。
     *
     * <p><b>★ 方案 2 已移除 {@code ItemManager.getInventory().update()}</b>：
     * 影子背包已删除，物品操作直接读真实背包，不再需要每 tick 同步副本。
     */
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
