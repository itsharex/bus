/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2023 aoju.org and other contributors.                      *
 *                                                                               *
 * Permission is hereby granted, free of charge, to any person obtaining a copy  *
 * of this software and associated documentation files (the "Software"), to deal *
 * in the Software without restriction, including without limitation the rights  *
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell     *
 * copies of the Software, and to permit persons to whom the Software is         *
 * furnished to do so, subject to the following conditions:                      *
 *                                                                               *
 * The above copyright notice and this permission notice shall be included in    *
 * all copies or substantial portions of the Software.                           *
 *                                                                               *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR    *
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,      *
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE   *
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER        *
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, *
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN     *
 * THE SOFTWARE.                                                                 *
 *                                                                               *
 ********************************************************************************/
package org.aoju.bus.cron;

import org.aoju.bus.core.exception.CrontabException;
import org.aoju.bus.core.toolkit.StringKit;
import org.aoju.bus.cron.factory.CronTask;
import org.aoju.bus.cron.factory.Task;
import org.aoju.bus.cron.pattern.CronPattern;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 定时任务表
 * 任务表将ID、表达式、任务一一对应,定时任务执行过程中,会周期性检查定时任务表中的所有任务表达式匹配情况,从而执行其对应的任务
 * 任务的添加、移除使用读写锁保证线程安全性
 *
 * @author Kimi Liu
 * @since Java 17+
 */
public class Repertoire implements Serializable {

    public static final int DEFAULT_CAPACITY = 10;
    private static final long serialVersionUID = 1L;
    private final ReadWriteLock lock;

    private final List<String> ids;
    private final List<CronPattern> patterns;
    private final List<Task> tasks;
    private int size;

    /**
     * 构造
     */
    public Repertoire() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 构造
     *
     * @param initialCapacity 容量，即预估的最大任务数
     */
    public Repertoire(int initialCapacity) {
        lock = new ReentrantReadWriteLock();

        ids = new ArrayList<>(initialCapacity);
        patterns = new ArrayList<>(initialCapacity);
        tasks = new ArrayList<>(initialCapacity);
    }

    /**
     * 新增Task
     *
     * @param id      ID
     * @param pattern {@link CronPattern}
     * @param task    {@link Task}
     * @return this
     */
    public Repertoire add(String id, CronPattern pattern, Task task) {
        final Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (ids.contains(id)) {
                throw new CrontabException("Id [{}] has been existed!", id);
            }
            ids.add(id);
            patterns.add(pattern);
            tasks.add(task);
            size++;
        } finally {
            writeLock.unlock();
        }
        return this;
    }

    /**
     * 获取所有ID，返回不可变列表，即列表不可修改
     *
     * @return ID列表
     */
    public List<String> getIds() {
        final Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return Collections.unmodifiableList(this.ids);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 获取所有定时任务表达式，返回不可变列表，即列表不可修改
     *
     * @return 定时任务表达式列表
     */
    public List<CronPattern> getPatterns() {
        final Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return Collections.unmodifiableList(this.patterns);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 获取所有定时任务，返回不可变列表，即列表不可修改
     *
     * @return 定时任务列表
     */
    public List<Task> getTasks() {
        final Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return Collections.unmodifiableList(this.tasks);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 移除Task
     *
     * @param id Task的ID
     * @return 是否成功移除，{@code false}表示未找到对应ID的任务
     */
    public boolean remove(String id) {
        final Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            final int index = ids.indexOf(id);
            if (index < 0) {
                return false;
            }
            tasks.remove(index);
            patterns.remove(index);
            ids.remove(index);
            size--;
        } finally {
            writeLock.unlock();
        }
        return true;
    }

    /**
     * 更新某个Task的定时规则
     *
     * @param id      Task的ID
     * @param pattern 新的表达式
     * @return 是否更新成功，如果id对应的规则不存在则不更新
     */
    public boolean updatePattern(String id, CronPattern pattern) {
        final Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            final int index = ids.indexOf(id);
            if (index > -1) {
                patterns.set(index, pattern);
                return true;
            }
        } finally {
            writeLock.unlock();
        }
        return false;
    }

    /**
     * 获得指定位置的{@link Task}
     *
     * @param index 位置
     * @return {@link Task}
     */
    public Task getTask(int index) {
        final Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return tasks.get(index);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 获得指定id的{@link Task}
     *
     * @param id ID
     * @return {@link Task}
     */
    public Task getTask(String id) {
        final int index = ids.indexOf(id);
        if (index > -1) {
            return getTask(index);
        }
        return null;
    }

    /**
     * 获得指定位置的{@link CronPattern}
     *
     * @param index 位置
     * @return {@link CronPattern}
     */
    public CronPattern getPattern(int index) {
        final Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return patterns.get(index);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 任务表大小,加入的任务数
     *
     * @return 任务表大小, 加入的任务数
     */
    public int size() {
        return this.size;
    }

    /**
     * 任务表是否为空
     *
     * @return true为空
     */
    public boolean isEmpty() {
        return this.size < 1;
    }

    /**
     * 获得指定id的{@link CronPattern}
     *
     * @param id ID
     * @return {@link CronPattern}
     */
    public CronPattern getPattern(String id) {
        final int index = ids.indexOf(id);
        if (index > -1) {
            return getPattern(index);
        }
        return null;
    }

    /**
     * 如果时间匹配则执行相应的Task,带读锁
     *
     * @param scheduler {@link Scheduler}
     * @param millis    时间毫秒
     */
    public void executeTaskIfMatch(Scheduler scheduler, long millis) {
        final Lock readLock = lock.readLock();
        readLock.lock();
        try {
            executeTaskIfMatchInternal(scheduler, millis);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 如果时间匹配则执行相应的Task,无锁
     *
     * @param scheduler {@link Scheduler}
     * @param millis    时间毫秒
     */
    protected void executeTaskIfMatchInternal(Scheduler scheduler, long millis) {
        for (int i = 0; i < size; i++) {
            if (patterns.get(i).match(scheduler.config.timezone, millis, scheduler.config.matchSecond)) {
                scheduler.manager.spawnExecutor(new CronTask(ids.get(i), patterns.get(i), tasks.get(i)));
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = StringKit.builder();
        for (int i = 0; i < size; i++) {
            builder.append(StringKit.format("[{}] [{}] [{}]\n",
                    ids.get(i), patterns.get(i), tasks.get(i)));
        }
        return builder.toString();
    }

}
