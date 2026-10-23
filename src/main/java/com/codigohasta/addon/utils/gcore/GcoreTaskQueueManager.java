package com.codigohasta.addon.utils.gcore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
