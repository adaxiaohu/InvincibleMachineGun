package com.codigohasta.addon.utils.gcore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 延迟/优先级任务队列。
 *
 * <p>移植自 {@code dev.godden.core.Manager.TaskQueueManager}（GCore）。
 *
 * <p><b>关于源实现的一个细节</b>：{@code handler()} 的末尾是
 * <pre>
 * this.taskList.clear();
 * this.taskList.removeAll(list);
 * </pre>
 * 先 {@code clear()} 再 {@code removeAll(list)} —— 后者对空列表是空操作。
 * 这看着像 bug（意图大概是「移除已执行的」），但**行为上等价于清空全部任务**。
 * 移植时**保持原样**（只在注释里指出），因为改了就与源码不一致了。
 */
public final class GcoreTaskQueueManager {

    private final List<Task> taskList = new ArrayList<>();

    /** 执行所有到期任务（按优先级），然后清空队列。 */
    public void handler() {
        if (this.taskList.isEmpty()) {
            return;
        }

        // 源：filter { it.readyToExecute() } then sortedWith(comparingInt { priority.level })
        List<Task> ready = new ArrayList<>();
        for (Task it : this.taskList) {
            if (!it.readyToExecute()) continue;
            ready.add(it);
        }
        ready.sort(Comparator.comparingInt(t -> t.getPriority().getLevel()));

        for (Task it : ready) {
            it.run();
        }

        // ★ 源就是这个顺序（先 clear 再 removeAll），保留以免行为偏差
        this.taskList.clear();
        this.taskList.removeAll(ready);
    }

    /** 以指定优先级与执行时刻排入任务。 */
    public void execute(Task.Priority priority, long executionTime, Runnable task) {
        this.taskList.add(new Task(priority, executionTime, task));
    }

    /** 延迟 {@code delay} 毫秒执行。 */
    public void execute(int delay, Runnable task) {
        this.execute(Task.Priority.E, System.currentTimeMillis() + (long) delay, task);
    }

    /** 立刻执行。 */
    public void execute(Runnable task) {
        this.execute(Task.Priority.E, System.currentTimeMillis(), task);
    }

    /** 一条任务。 */
    public static final class Task {

        private final Priority priority;
        private final long executionTime;
        private final Runnable task;

        public Task(Priority priority, long executionTime, Runnable task) {
            this.priority = priority;
            this.executionTime = executionTime;
            this.task = task;
        }

        public Priority getPriority() {
            return this.priority;
        }

        /** 是否到执行时刻。 */
        public boolean readyToExecute() {
            return System.currentTimeMillis() >= this.executionTime;
        }

        /** 执行（task 为 null 时什么都不做）。 */
        public void run() {
            if (this.task != null) {
                this.task.run();
            }
        }

        /** 优先级，{@code A} 最高。 */
        public enum Priority {
            A, B, C, D, E;

            /** 数值越小越先执行。 */
            public int getLevel() {
                return this.ordinal();
            }
        }
    }
}
